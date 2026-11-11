package gr.eortologio.plus

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Ημερήσια ειδοποίηση «Σήμερα γιορτάζουν» με κουμπιά Αναβολής (snooze) και «Ευχές».
 * Χωρίς background service: ένα alarm την ημέρα (setAndAllowWhileIdle → λειτουργεί και σε Doze
 * χωρίς άδεια ακριβούς ώρας και με ελάχιστη κατανάλωση μπαταρίας).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        T.init(ctx)
        val action = intent.action ?: ACTION_DAILY
        val pr = goAsync()
        val app = ctx.applicationContext
        Thread {
            val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Eortologio:notify")
            try {
                wl?.acquire(20_000)
                when (action) {
                    ACTION_SNOOZE -> snooze(app, intent.getIntExtra(EXTRA_MIN, 60))
                    ACTION_SHOW -> {
                        val day = intent.getStringExtra(EXTRA_DAY)
                        if (day == null || day == LocalDate.now().toString()) post(app, snoozed = true)
                    }
                    ACTION_MIDNIGHT -> {
                        EventStore.get(app).invalidate()
                        TodayWidget.updateAll(app)
                        scheduleMidnight(app)
                    }
                    ACTION_AUTOSMS_SEND -> {
                        cancelSmsTimer(app)
                        val day = intent.getStringExtra(EXTRA_DAY)
                        if (day == null || day == LocalDate.now().toString()) postSent(app, AutoWishes.sendToday(app))
                    }
                    ACTION_AUTOSMS_CANCEL -> {
                        cancelSmsTimer(app)
                        AutoWishes.cancelToday(app)
                        nm(app)?.cancel(NOTIF_SMS)
                    }
                    else -> try {
                        var sent: List<String>? = null
                        if (AutoWishes.enabled(app) && !AutoWishes.cancelledToday(app)) {
                            if (AutoWishes.confirmFirst(app)) askBeforeSending(app) else sent = AutoWishes.sendToday(app)
                        }
                        post(app, snoozed = false, autoSent = sent)
                        TodayWidget.updateAll(app)
                    } finally {
                        schedule(app)
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (wl?.isHeld == true) wl.release()
                pr.finish()
            }
        }.start()
    }

    companion object {
        const val CHANNEL = "eortologio_today"
        const val ACTION_DAILY = "gr.eortologio.plus.DAILY"
        const val ACTION_SNOOZE = "gr.eortologio.plus.SNOOZE"
        const val ACTION_SHOW = "gr.eortologio.plus.SHOW"
        const val ACTION_AUTOSMS_SEND = "gr.eortologio.plus.AUTOSMS_SEND"
        const val ACTION_AUTOSMS_CANCEL = "gr.eortologio.plus.AUTOSMS_CANCEL"
        const val ACTION_MIDNIGHT = "gr.eortologio.plus.MIDNIGHT"
        const val EXTRA_MIN = "minutes"
        const val EXTRA_DAY = "day"
        const val NOTIF_ID = 41
        const val NOTIF_SMS = 43
        private const val RC_DAILY = 41
        private const val RC_SNOOZE = 42
        private const val RC_SMS = 43
        private const val RC_MIDNIGHT = 44
        const val CONFIRM_MINUTES = 60
        private const val COLOR = 0xff6657dd.toInt()

        private fun nm(c: Context) = c.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        /**
         * Προγραμματίζει το επόμενο ημερήσιο ξύπνημα (ακυρώνει το προηγούμενο).
         * Χρειάζεται αν είναι ανοιχτή η ειδοποίηση Ή οι αυτόματες ευχές (παλιό bug: με κλειστές
         * ειδοποιήσεις δεν έφευγαν ποτέ τα αυτόματα SMS).
         */
        fun schedule(ctx: Context) {
            val am = am(ctx) ?: return
            val p = pending(ctx, ACTION_DAILY, RC_DAILY, null)
            am.cancel(p)
            scheduleMidnight(ctx)
            val sp = Prefs.sp(ctx)
            if (!sp.getBoolean("notifications", true) && !AutoWishes.switchedOn(ctx)) return
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(Prefs.hour(ctx), Prefs.minute(ctx))
            if (!next.isAfter(now)) next = next.plusDays(1)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), p)
        }

        /** Ανανέωση widget λίγο μετά τα μεσάνυχτα — μόνο αν υπάρχει widget (αλλιώς κανένα επιπλέον ξύπνημα). */
        fun scheduleMidnight(ctx: Context) {
            val am = am(ctx) ?: return
            val m = pending(ctx, ACTION_MIDNIGHT, RC_MIDNIGHT, null)
            am.cancel(m)
            if (!TodayWidget.hasWidgets(ctx)) return
            val whenMs = LocalDate.now().plusDays(1).atTime(0, 1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            am.set(AlarmManager.RTC, whenMs, m) // RTC (όχι WAKEUP): ανανεώνεται όταν ανοίξει η οθόνη
        }

        private fun pending(ctx: Context, action: String, rc: Int, extras: Intent?): PendingIntent {
            val i = Intent(ctx, ReminderReceiver::class.java).setAction(action)
            if (extras != null) i.putExtras(extras)
            return PendingIntent.getBroadcast(ctx, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun snooze(ctx: Context, minutes: Int) {
            nm(ctx)?.cancel(NOTIF_ID)
            val am = am(ctx) ?: return
            val extra = Intent().putExtra(EXTRA_DAY, LocalDate.now().toString())
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L,
                pending(ctx, ACTION_SHOW, RC_SNOOZE, extra))
        }

        fun ensureChannel(ctx: Context) {
            val nm = nm(ctx) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, T.t("Σήμερα γιορτάζουν"), NotificationManager.IMPORTANCE_DEFAULT)
                ch.description = T.t("Ημερήσια ειδοποίηση για γιορτές, γενέθλια επαφών και προσωπικές ημερομηνίες")
                nm.createNotificationChannel(ch)
            }
        }

        private fun openIntent(ctx: Context, rc: Int, page: String): PendingIntent {
            val open = Intent(ctx, MainActivity::class.java).putExtra("page", page)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(ctx, rc, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /** Συνθέτει και εμφανίζει την ειδοποίηση της ημέρας. Επιστρέφει false αν δεν υπάρχει τίποτα να ειπωθεί. */
        fun post(ctx: Context, snoozed: Boolean, autoSent: List<String>? = null): Boolean {
            val sp = Prefs.sp(ctx)
            if (!sp.getBoolean("notifications", true) || !Prefs.canNotify(ctx)) return false
            val nm = nm(ctx) ?: return false

            val store = EventStore.get(ctx)
            val today = LocalDate.now()
            val lead = sp.getInt("lead", 1)
            val names = sp.getBoolean("notifyNames", true)
            val onlyContacts = sp.getBoolean("onlyContacts", false)

            val todayNames = ArrayList<String>()
            val lines = ArrayList<String>()
            for (e in store.on(today)) {
                if (e.isNameDay) { if (e.canon !in todayNames) todayNames.add(e.canon) }
                else if (!e.personal) lines.add("📅 " + T.t(e.title))
            }
            val hits = ContactSync.hits(ctx, store, today, 0)
            val people = hits.map { it.person.displayName + if (it.birthday) " 🎂" else "" }

            for (e in store.editable()) {
                val n = e.next(today)
                val d = EventStore.daysUntil(today, n)
                if (d == 0L) {
                    val y = e.yearsAt(n)
                    lines.add(0, "⭐ " + e.title + if (y > 0) " (" + y + T.t(" χρόνια)") else "")
                } else if (lead > 0 && d == lead.toLong()) {
                    lines.add(T.t("⏳ Σε ") + d + (if (d == 1L) T.t(" ημέρα: ") else T.t(" ημέρες: ")) + e.title)
                }
            }
            if (!autoSent.isNullOrEmpty()) lines.add(0, T.t("✉️ Στάλθηκαν αυτόματα ευχές με SMS σε: ") + autoSent.joinToString(", "))
            val showNames = names && !onlyContacts && todayNames.isNotEmpty()
            if (lines.isEmpty() && people.isEmpty() && !showNames) return false

            val big = StringBuilder()
            if (people.isNotEmpty()) big.append(T.t("👥 Από τις επαφές σου: ")).append(people.joinToString(", ")).append('\n')
            if (names && todayNames.isNotEmpty()) big.append(T.t("🎉 Γιορτάζουν: ")).append(todayNames.joinToString(", ")).append('\n')
            for (l in lines) big.append(l).append('\n')

            val title: String
            val text: String
            when {
                people.isNotEmpty() -> {
                    title = if (people.size == 1) T.t("Γιορτάζει ο/η ") + hits[0].person.callName() + " 🎉"
                    else people.size.toString() + T.t(" επαφές σου γιορτάζουν σήμερα 🎉")
                    text = people.joinToString(", ")
                }
                showNames -> { title = T.t("Σήμερα γιορτάζουν"); text = todayNames.joinToString(", ") }
                else -> { title = T.t("Εορτολόγιο+"); text = lines.firstOrNull() ?: todayNames.joinToString(", ") }
            }

            ensureChannel(ctx)
            val pi = openIntent(ctx, 0, if (people.isEmpty()) "today" else "contacts")
            val s1 = sp.getInt("snooze1", 60)
            val s2 = sp.getInt("snooze2", 180)
            val b = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_event)
                .setColor(COLOR)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(big.toString().trim()))
                .setAutoCancel(true)
                .setOnlyAlertOnce(snoozed)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(pi)
            if (people.isNotEmpty()) b.addAction(Notification.Action.Builder(null, T.t("💬 Ευχές"), pi).build())
            b.addAction(Notification.Action.Builder(null, "⏰ " + label(s1), snoozeIntent(ctx, s1, 1)).build())
            b.addAction(Notification.Action.Builder(null, "⏰ " + label(s2), snoozeIntent(ctx, s2, 2)).build())
            nm.notify(NOTIF_ID, b.build())
            return true
        }

        /** Ειδοποίηση «Θα σταλούν ευχές σε …» με [Αποστολή τώρα] [Όχι σήμερα]· αν δεν απαντηθεί, στέλνονται μετά από 1 ώρα. */
        fun askBeforeSending(ctx: Context) {
            val list = AutoWishes.pending(ctx)
            if (list.isEmpty()) return
            val extra = Intent().putExtra(EXTRA_DAY, LocalDate.now().toString())
            am(ctx)?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + CONFIRM_MINUTES * 60_000L,
                pending(ctx, ACTION_AUTOSMS_SEND, RC_SMS, extra))
            val nm = nm(ctx) ?: return
            if (!Prefs.canNotify(ctx)) return
            ensureChannel(ctx)
            val sendNow = PendingIntent.getBroadcast(ctx, 110,
                Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_AUTOSMS_SEND).putExtras(extra),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val cancel = PendingIntent.getBroadcast(ctx, 111,
                Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_AUTOSMS_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val who = list.joinToString(", ") { it.person.displayName + if (it.birthday) " 🎂" else "" }
            val b = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_event).setColor(COLOR)
                .setContentTitle(T.t("✉️ Ευχές με SMS σε ") + list.size + (if (list.size == 1) T.t(" άτομο") else T.t(" άτομα")))
                .setContentText(who)
                .setStyle(Notification.BigTextStyle().bigText(who + T.t("\n\nΑν δεν πατήσεις τίποτα, θα σταλούν αυτόματα σε ") + CONFIRM_MINUTES + T.t(" λεπτά.")))
                .setContentIntent(openIntent(ctx, 112, "contacts")).setAutoCancel(false).setCategory(Notification.CATEGORY_REMINDER)
                .addAction(Notification.Action.Builder(null, T.t("✅ Αποστολή τώρα"), sendNow).build())
                .addAction(Notification.Action.Builder(null, T.t("✖ Όχι σήμερα"), cancel).build())
            nm.notify(NOTIF_SMS, b.build())
        }

        fun cancelSmsTimer(ctx: Context) {
            am(ctx)?.cancel(pending(ctx, ACTION_AUTOSMS_SEND, RC_SMS, null))
        }

        fun postSent(ctx: Context, sent: List<String>?) {
            val nm = nm(ctx) ?: return
            nm.cancel(NOTIF_SMS)
            if (sent.isNullOrEmpty() || !Prefs.canNotify(ctx)) return
            ensureChannel(ctx)
            val who = sent.joinToString(", ")
            nm.notify(NOTIF_SMS, Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_event).setColor(COLOR)
                .setContentTitle(T.t("✉️ Στάλθηκαν ευχές σε ") + sent.size + (if (sent.size == 1) T.t(" άτομο") else T.t(" άτομα")))
                .setContentText(who).setStyle(Notification.BigTextStyle().bigText(who)).setAutoCancel(true).build())
        }

        private fun snoozeIntent(ctx: Context, minutes: Int, slot: Int): PendingIntent {
            val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_SNOOZE).putExtra(EXTRA_MIN, minutes)
            return PendingIntent.getBroadcast(ctx, 100 + slot, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun label(minutes: Int): String = when {
            minutes < 60 -> "$minutes′"
            minutes % 60 == 0 -> (minutes / 60).toString() + if (minutes == 60) T.t(" ώρα") else T.t(" ώρες")
            else -> (minutes / 60).toString() + T.t("ω ") + (minutes % 60) + "′"
        }
    }
}
