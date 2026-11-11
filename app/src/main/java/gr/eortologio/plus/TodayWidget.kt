package gr.eortologio.plus

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Widget αρχικής οθόνης: «Σήμερα γιορτάζουν» + επαφές που γιορτάζουν + αύριο. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pr = goAsync()
        val app = ctx.applicationContext
        Thread {
            try {
                updateAll(app)
                ReminderReceiver.schedule(app) // βάζει και το alarm μεσάνυχτων τώρα που υπάρχει widget
            } catch (_: Exception) {
            } finally {
                pr.finish()
            }
        }.start()
    }

    companion object {
        fun hasWidgets(ctx: Context): Boolean = try {
            AppWidgetManager.getInstance(ctx)?.getAppWidgetIds(ComponentName(ctx, TodayWidget::class.java))?.isNotEmpty() == true
        } catch (_: Exception) { false }

        private fun build(ctx: Context): RemoteViews {
            T.init(ctx)
            val store = EventStore.get(ctx)
            val today = LocalDate.now()
            val v = RemoteViews(ctx.packageName, R.layout.widget_today)
            val d = today.format(DateTimeFormatter.ofPattern(if (T.en()) "EEEE, MMMM d" else "EEEE d MMMM", T.locale()))
            v.setTextViewText(R.id.w_date, (T.t("ΕΟΡΤΟΛΟΓΙΟ+ · ") + d).uppercase(T.locale()))

            val names = ArrayList<String>()
            val holidays = ArrayList<String>()
            for (e in store.on(today)) {
                if (e.isNameDay) { if (e.canon !in names) names.add(e.canon) }
                else if (!e.personal) holidays.add(T.t(e.title))
            }
            v.setTextViewText(R.id.w_title, if (names.isEmpty()) T.t("📅 Σήμερα") else T.t("🎉 Σήμερα γιορτάζουν"))
            val body = StringBuilder(names.joinToString(", "))
            for (h in holidays) body.append(if (body.isNotEmpty()) "\n" else "").append("📌 ").append(h)
            v.setTextViewText(R.id.w_names, if (body.isNotEmpty()) body else T.t("Καμία γιορτή σήμερα"))

            val people = ContactSync.hits(ctx, store, today, 0).map { it.person.callName() + if (it.birthday) " 🎂" else "" }
            if (people.isEmpty()) v.setViewVisibility(R.id.w_contacts, View.GONE)
            else {
                v.setViewVisibility(R.id.w_contacts, View.VISIBLE)
                v.setTextViewText(R.id.w_contacts, "👥 " + people.joinToString(", "))
            }
            val tomorrow = store.on(today.plusDays(1)).filter { it.isNameDay }.map { it.canon }.distinct()
            v.setTextViewText(R.id.w_next, if (tomorrow.isEmpty()) "" else T.t("Αύριο: ") + tomorrow.joinToString(", "))

            val open = Intent(ctx, MainActivity::class.java).putExtra("page", if (people.isEmpty()) "today" else "contacts")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            v.setOnClickPendingIntent(R.id.w_root,
                PendingIntent.getActivity(ctx, 7, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return v
        }

        /** Ανανεώνει όλα τα widgets (αν δεν υπάρχει κανένα, δεν κάνει τίποτα). */
        fun updateAll(ctx: Context) {
            try {
                val m = AppWidgetManager.getInstance(ctx) ?: return
                val ids = m.getAppWidgetIds(ComponentName(ctx, TodayWidget::class.java))
                if (ids.isEmpty()) return
                m.updateAppWidget(ids, build(ctx))
            } catch (_: Exception) {
            }
        }
    }
}
