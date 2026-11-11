package gr.eortologio.plus

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private data class Tpl(val key: String, val def: String, val title: String)

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val a = LocalActions.current
    val p = LocalPal.current
    val sp = remember { Prefs.sp(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val r = tick + vm.rev // ξαναδιαβάζει τις ρυθμίσεις όταν αλλάζουν
    fun put(block: (android.content.SharedPreferences.Editor) -> Unit) {
        val e = sp.edit(); block(e); e.apply(); tick++
    }

    var timeDlg by remember { mutableStateOf(false) }
    var tpl by remember { mutableStateOf<Tpl?>(null) }
    var smsPeople by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ListPadding)) {
        // ---------------- Εμφάνιση ----------------
        SectionTitle(T.t("🎨 Εμφάνιση"))
        AppCard {
            val thKeys = listOf("auto", "light", "dark")
            val thNames = listOf(T.t("🌗 Αυτόματο (όπως το κινητό)"), T.t("☀️ Φωτεινό"), T.t("🌙 Σκοτεινό"))
            val cur = thKeys.indexOf(sp.getString("theme", "auto")).coerceAtLeast(0)
            SettingRow(T.t("Θέμα"), thNames[cur], Icons.Rounded.Palette) {
                a.choose(Choice(T.t("Θέμα εμφάνισης"), thNames, cur) { w ->
                    if (w != cur) { sp.edit().putString("theme", thKeys[w]).apply(); activity.recreate() }
                })
            }
            Divider()
            val lgKeys = listOf("el", "en", "auto")
            val lgNames = listOf("🇬🇷 Ελληνικά", "🇬🇧 English", "🌐 Αυτόματα (γλώσσα κινητού) / Auto")
            val lcur = lgKeys.indexOf(sp.getString("lang", "el")).coerceAtLeast(0)
            SettingRow("Γλώσσα / Language", lgNames[lcur], Icons.Rounded.Language) {
                a.choose(Choice("Γλώσσα / Language", lgNames, lcur) { w ->
                    if (w != lcur) {
                        sp.edit().putString("lang", lgKeys[w]).apply()
                        T.init(ctx)
                        val app = ctx.applicationContext
                        Thread { TodayWidget.updateAll(app) }.start()
                        activity.recreate()
                    }
                })
            }
        }

        // ---------------- Ειδοποιήσεις ----------------
        SectionTitle(T.t("🔔 Ειδοποιήσεις"))
        AppCard {
            val on = sp.getBoolean("notifications", true)
            ToggleRow(T.t("Ημερήσια ειδοποίηση"), T.t("Κάθε μέρα στην ώρα που ορίζεις"), on, Icons.Rounded.NotificationsActive) { v ->
                put { it.putBoolean("notifications", v) }
                if (v && !Prefs.canNotify(ctx)) a.askNotifications()
                ReminderReceiver.schedule(ctx)
            }
            Divider()
            SettingRow(T.t("Ώρα ειδοποίησης"), Prefs.timeText(ctx), Icons.Rounded.Schedule) { timeDlg = true }
            Divider()
            ToggleRow(T.t("Ονομαστικές γιορτές της ημέρας"), T.t("«Σήμερα γιορτάζουν: …» κάθε πρωί"), sp.getBoolean("notifyNames", true), Icons.Rounded.Celebration) { v ->
                put { it.putBoolean("notifyNames", v) }
            }
            Divider()
            ToggleRow(T.t("Μόνο όταν γιορτάζει επαφή μου"), T.t("Λιγότερες ειδοποιήσεις, μόνο για τους δικούς σου"), sp.getBoolean("onlyContacts", false), Icons.Rounded.People) { v ->
                put { it.putBoolean("onlyContacts", v) }
            }
            Divider()
            val lead = sp.getInt("lead", 1)
            val leadVals = listOf(0, 1, 2, 3, 7)
            val leadNames = listOf(T.t("Μόνο την ημέρα"), T.t("1 ημέρα πριν"), T.t("2 ημέρες πριν"), T.t("3 ημέρες πριν"), T.t("1 εβδομάδα πριν"))
            SettingRow(T.t("Προειδοποίηση για τα «Δικά μου»"),
                if (lead == 0) T.t("Μόνο την ημέρα") else lead.toString() + if (lead == 1) T.t(" ημέρα πριν") else T.t(" ημέρες πριν"), Icons.Rounded.Today) {
                a.choose(Choice(T.t("Προειδοποίηση"), leadNames, leadVals.indexOf(lead).coerceAtLeast(0)) { w -> put { it.putInt("lead", leadVals[w]) } })
            }
            Divider()
            val s1 = sp.getInt("snooze1", 60)
            val s2 = sp.getInt("snooze2", 180)
            val sv = listOf(15 to 60, 30 to 120, 60 to 180, 120 to 300)
            val sn = listOf(T.t("15′ και 1 ώρα"), T.t("30′ και 2 ώρες"), T.t("1 ώρα και 3 ώρες"), T.t("2 ώρες και 5 ώρες"))
            SettingRow(T.t("Κουμπιά αναβολής (snooze)"), ReminderReceiver.label(s1) + " · " + ReminderReceiver.label(s2), Icons.Rounded.Snooze) {
                a.choose(Choice(T.t("Αναβολή ειδοποίησης"), sn, sv.indexOf(s1 to s2)) { w ->
                    put { it.putInt("snooze1", sv[w].first).putInt("snooze2", sv[w].second) }
                })
            }
            Divider()
            SettingRow(T.t("Δοκιμαστική ειδοποίηση"), T.t("Δες πώς φαίνεται τώρα"), Icons.Rounded.Science) {
                if (!Prefs.canNotify(ctx)) { a.askNotifications(); return@SettingRow }
                val app = ctx.applicationContext
                Thread {
                    val ok = ReminderReceiver.post(app, snoozed = false)
                    activity.runOnUiThread {
                        Wishes.toast(app, if (ok) T.t("Στάλθηκε ✓") else T.t("Τίποτα για σήμερα (ή οι ειδοποιήσεις είναι κλειστές)"))
                    }
                }.start()
            }
        }

        // ---------------- Μπαταρία ----------------
        SectionTitle(T.t("🔋 Μπαταρία"))
        AppCard {
            val ok = Prefs.batteryOk(ctx)
            SettingRow(if (ok) T.t("✓ Χωρίς περιορισμούς") else T.t("⚠️ Περιορισμένη από το σύστημα"),
                if (ok) T.t("Οι ειδοποιήσεις θα έρχονται στην ώρα τους") else T.t("Πάτα για εξαίρεση — αλλιώς η ειδοποίηση μπορεί να καθυστερεί"),
                Icons.Rounded.BatteryChargingFull) { a.askBattery() }
            BodyText(T.t("Η εφαρμογή δεν τρέχει στο παρασκήνιο: ξυπνάει μία φορά την ημέρα για λίγα δευτερόλεπτα. Σε Xiaomi/Huawei/Samsung ενεργοποίησε επίσης την «Αυτόματη εκκίνηση» αν υπάρχει."),
                Modifier.padding(top = 6.dp), size = 12)
        }

        // ---------------- Επαφές & ευχές ----------------
        SectionTitle(T.t("👥 Επαφές & ευχές"))
        AppCard {
            val granted = ContactSync.granted(ctx)
            ToggleRow(T.t("Συγχρονισμός επαφών"),
                if (granted) T.t("Ταίριασμα ονομάτων & γενεθλίων") else T.t("Χρειάζεται άδεια — πάτα για ενεργοποίηση"),
                sp.getBoolean("contacts", true) && granted, Icons.Rounded.Contacts) { v ->
                ContactSync.invalidate()
                if (v) a.askContacts() else put { it.putBoolean("contacts", false) }
                vm.refresh()
            }
            Divider()
            val autoOn = AutoWishes.switchedOn(ctx)
            val hasPerm = AutoWishes.hasPermission(ctx)
            ToggleRow(T.t("✉️ Αυτόματες ευχές με SMS"),
                if (!autoOn) T.t("Στέλνει μόνο του «Χρόνια πολλά» σε όσους γιορτάζουν")
                else if (hasPerm) T.t("Ενεργό · στέλνει κάθε μέρα στις ") + Prefs.timeText(ctx)
                else T.t("⚠️ Λείπει η άδεια SMS — πάτα για ενεργοποίηση"),
                autoOn && hasPerm, Icons.Rounded.Sms, detailColor = if (autoOn && !hasPerm) p.pink else null) { v ->
                if (v) a.startAutoSms()
                else {
                    put { it.putBoolean("autoSms", false) }
                    ReminderReceiver.schedule(ctx)
                    Wishes.toast(ctx, T.t("Οι αυτόματες ευχές απενεργοποιήθηκαν"))
                }
            }
            if (autoOn) {
                Divider()
                SettingRow(T.t("Σε ποιους στέλνει αυτόματα"), AutoWishes.modeLabel(AutoWishes.mode(ctx)), Icons.Rounded.Cake) {
                    chooseAutoMode(ctx, a) { tick++ }
                }
                Divider()
                ToggleRow(T.t("Ερώτηση πριν την αποστολή"), T.t("Ειδοποίηση με [Αποστολή τώρα] / [Όχι σήμερα] — αλλιώς στέλνονται μόνα τους σε 1 ώρα"),
                    AutoWishes.confirmFirst(ctx), Icons.Rounded.QuestionAnswer) { v -> put { it.putBoolean("autoSmsConfirm", v) } }
                Divider()
                val nb = AutoWishes.blocked(ctx).size
                SettingRow(T.t("Ποιες επαφές παίρνουν SMS"),
                    if (nb == 0) T.t("Όλες (καμία εξαίρεση)") else nb.toString() + if (nb == 1) T.t(" επαφή εξαιρείται") else T.t(" επαφές εξαιρούνται"),
                    Icons.Rounded.Block) { smsPeople = true }
                Divider()
                SettingRow(T.t("Ιστορικό αυτόματων SMS"), T.t("Τι στάλθηκε και πότε"), Icons.Rounded.History) {
                    a.message(Dlg(T.t("Ιστορικό αποστολών"), AutoWishes.history(ctx)))
                }
            }
            Divider()
            SettingRow(T.t("🎉 Μηνύματα για γιορτή"), tplSummary(ctx, "tplNameday", Wishes.defNameday()), Icons.Rounded.Celebration) {
                tpl = Tpl("tplNameday", Wishes.defNameday(), T.t("Μηνύματα για γιορτή"))
            }
            Divider()
            SettingRow(T.t("🎂 Μηνύματα για γενέθλια"), tplSummary(ctx, "tplBirthday", Wishes.defBirthday()), Icons.Rounded.Cake) {
                tpl = Tpl("tplBirthday", Wishes.defBirthday(), T.t("Μηνύματα για γενέθλια"))
            }
            Divider()
            val nc = (sp.getStringSet("closeSet", emptySet()) ?: emptySet()).size
            SettingRow(T.t("❤️ Μηνύματα για οικογένεια & φίλους"),
                tplSummary(ctx, "tplClose", Wishes.defClose()) + " · " +
                    (if (nc == 0) T.t("κανείς ακόμα — από το ⋮ της επαφής") else nc.toString() + if (nc == 1) T.t(" άτομο") else T.t(" άτομα")),
                Icons.Rounded.Favorite) { tpl = Tpl("tplClose", Wishes.defClose(), T.t("Οικογένεια & φίλοι")) }
        }

        // ---------------- Ενημερώσεις ----------------
        SectionTitle(T.t("⬆️ Ενημερώσεις"))
        AppCard {
            ToggleRow(T.t("Αυτόματος έλεγχος"), T.t("Κάθε φορά που ανοίγεις την εφαρμογή (ανά 6 ώρες)"), sp.getBoolean("autoUpdate", true), Icons.Rounded.CloudDownload) { v ->
                put { it.putBoolean("autoUpdate", v) }
            }
            Divider()
            SettingRow(T.t("Έλεγχος τώρα"), T.t("Έκδοση ") + OtaUpdater.currentVersion(ctx), Icons.Rounded.SystemUpdate) { vm.checkOta(manual = true) }
        }

        // ---------------- Αντίγραφο ----------------
        SectionTitle(T.t("💾 Αντίγραφο ασφαλείας"))
        AppCard {
            SettingRow(T.t("Αποθήκευση αντιγράφου"), T.t("«Δικά μου», μηνύματα, εξαιρέσεις → αρχείο (π.χ. στο Drive)"), Icons.Rounded.Save) { a.exportBackup() }
            Divider()
            SettingRow(T.t("Επαναφορά από αντίγραφο"), T.t("Διάλεξε το αρχείο .json"), Icons.Rounded.Restore) { a.importBackup() }
        }

        // ---------------- Σχετικά ----------------
        SectionTitle(T.t("ℹ️ Σχετικά"))
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(BrandGradient), contentAlignment = Alignment.Center) {
                    Text("🎉", fontSize = 26.sp)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    TitleText(T.t("Εορτολόγιο+"), size = 18)
                    BodyText(T.t("Έκδοση ") + OtaUpdater.currentVersion(ctx), size = 13)
                }
            }
            Text(T.t("Ανάπτυξη: Νικόλαος Κ. Παρίδης\nALU DEV TEAM · Πτολεμαΐδα"), color = p.ink, fontSize = 14.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            BodyText(T.t("Ελληνικό εορτολόγιο με υπενθυμίσεις και ευχές στις επαφές σου. Όλα τα δεδομένα μένουν στη συσκευή."), size = 13)
            Divider()
            SettingRow("GitHub", "github.com/nikkpap/EortologioGR", Icons.Rounded.Code) { open(ctx, "https://github.com/nikkpap/EortologioGR") }
            Divider()
            SettingRow(T.t("Τι νέο"), T.t("Σημειώσεις εκδόσεων"), Icons.Rounded.NewReleases) { open(ctx, "https://github.com/nikkpap/EortologioGR/releases") }
            Text("© " + LocalDate.now().year + " ALU DEV TEAM", color = p.sub, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
        if (sp.getBoolean("guideDone", false)) {
            Text(T.t("Εμφάνιση οδηγού ρύθμισης ξανά"), color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp))
                    .clickable { sp.edit().putBoolean("guideDone", false).apply(); vm.go("today"); vm.refresh() }
                    .padding(8.dp))
        }
    }

    if (timeDlg) TimeDialog(Prefs.hour(ctx), Prefs.minute(ctx), onDismiss = { timeDlg = false }) { h, m ->
        put { it.putInt("hour", h).putInt("minute", m) }
        ReminderReceiver.schedule(ctx)
    }
    tpl?.let { t -> TemplateDialog(t.key, t.def, t.title, onDismiss = { tpl = null; tick++ }) }
    if (smsPeople) SmsContactsDialog(onDismiss = { smsPeople = false; tick++ })
}

private fun tplSummary(ctx: android.content.Context, key: String, def: String): String {
    val n = Wishes.templates(ctx, key, def).size
    return if (n == 1) T.t("1 μήνυμα") else n.toString() + T.t(" μηνύματα (εναλλάσσονται)")
}

private fun open(ctx: android.content.Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
}
