package gr.eortologio.plus

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import java.time.LocalDate

/**
 * Αυτόματη αποστολή «Χρόνια πολλά» με SMS σε όσες επαφές γιορτάζουν / έχουν γενέθλια σήμερα.
 * Ασφάλειες: μόνο αν το ενεργοποιήσει ο χρήστης, ένα SMS ανά αριθμό ανά ημέρα, όριο ανά ημέρα, ιστορικό.
 */
object AutoWishes {
    const val TAG = "EortologioAutoSms"
    const val MODE_NAMEDAY = "nameday"
    const val MODE_BIRTHDAY = "birthday"
    const val MODE_BOTH = "both"
    const val DAILY_LIMIT = 40
    private const val LOG_MAX = 200

    fun switchedOn(c: Context) = Prefs.sp(c).getBoolean("autoSms", false)
    fun enabled(c: Context) = switchedOn(c) && hasPermission(c)

    fun hasPermission(c: Context) =
        c.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun mode(c: Context) = Prefs.sp(c).getString("autoSmsMode", MODE_BOTH) ?: MODE_BOTH

    fun modeLabel(m: String) = when (m) {
        MODE_NAMEDAY -> T.t("Μόνο γιορτές")
        MODE_BIRTHDAY -> T.t("Μόνο γενέθλια")
        else -> T.t("Γιορτές και γενέθλια")
    }

    // ---------- Εξαιρέσεις: επαφές που ΔΕΝ παίρνουν ποτέ αυτόματο SMS ----------
    fun blocked(c: Context): Set<String> = HashSet(Prefs.sp(c).getStringSet("smsBlocked", emptySet()) ?: emptySet())

    fun isBlocked(c: Context, p: Person) = p.key in blocked(c)

    fun setBlocked(c: Context, p: Person, block: Boolean) = setBlockedMany(c, mapOf(p to block))

    fun setBlockedMany(c: Context, changes: Map<Person, Boolean>) {
        val b = blocked(c).toMutableSet()
        for ((p, block) in changes) if (block) b.add(p.key) else b.remove(p.key)
        Prefs.sp(c).edit().putStringSet("smsBlocked", b).apply()
    }

    fun confirmFirst(c: Context) = Prefs.sp(c).getBoolean("autoSmsConfirm", true)

    private fun doneToday(sp: SharedPreferences, key: String): MutableSet<String> {
        val done = HashSet<String>()
        if (key == sp.getString("autoSmsDay", "")) done.addAll(sp.getStringSet("autoSmsDone", emptySet()) ?: emptySet())
        return done
    }

    fun cancelledToday(c: Context) = LocalDate.now().toString() == Prefs.sp(c).getString("autoSmsCancelDay", "")

    fun cancelToday(c: Context) = Prefs.sp(c).edit().putString("autoSmsCancelDay", LocalDate.now().toString()).apply()

    /** Ποιοι θα πάρουν σήμερα αυτόματο SMS (μετά από φίλτρα: τύπος, εξαιρέσεις, ήδη σταλμένα, όριο). */
    fun pending(ctx: Context): List<Hit> {
        if (!enabled(ctx)) return emptyList()
        val sp = Prefs.sp(ctx)
        val mode = mode(ctx)
        val done = doneToday(sp, LocalDate.now().toString())
        val block = blocked(ctx)
        val seen = HashSet(done)
        val out = ArrayList<Hit>()
        ContactSync.invalidate() // φρέσκια ανάγνωση: μπορεί να άλλαξαν οι επαφές
        for (h in ContactSync.hits(ctx, EventStore.get(ctx), LocalDate.now(), 0)) {
            if (h.birthday && mode == MODE_NAMEDAY) continue
            if (!h.birthday && mode == MODE_BIRTHDAY) continue
            val num = h.person.phone ?: continue
            if (num.replace(Regex("\\D"), "").length < 8 || !ContactSync.isMobile(num)) continue // όχι σταθερά
            if (h.person.key in block) continue
            if (!seen.add(Wishes.intl(num))) continue
            out.add(h)
            if (done.size + out.size >= DAILY_LIMIT) break
        }
        return out
    }

    /** Στέλνει τις σημερινές ευχές. Επιστρέφει τα ονόματα όσων στάλθηκαν τώρα. */
    @Synchronized fun sendToday(ctx: Context): List<String> {
        val sentNow = ArrayList<String>()
        if (!enabled(ctx) || cancelledToday(ctx)) return sentNow
        val sp = Prefs.sp(ctx)
        val key = LocalDate.now().toString()
        val done = doneToday(sp, key) // ποιοι αριθμοί πήραν ήδη SMS σήμερα (π.χ. μετά από επανεκκίνηση)
        val sms: SmsManager = try {
            (if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault())
                ?: return sentNow
        } catch (e: Exception) {
            Log.w(TAG, "no SmsManager", e); return sentNow
        }
        val log = (sp.getString("autoSmsLog", "") ?: "").split("\n").filter { it.isNotEmpty() }.toMutableList()
        for (h in pending(ctx)) {
            if (done.size >= DAILY_LIMIT) { Log.w(TAG, "daily limit reached"); break }
            val num = h.person.phone ?: continue
            if (!done.add(Wishes.intl(num))) continue // ήδη στάλθηκε σήμερα (ή διπλή επαφή με ίδιο αριθμό)
            val msg = Wishes.message(ctx, h)
            val ok = try {
                val parts = sms.divideMessage(msg)
                if (parts.size > 1) sms.sendMultipartTextMessage(num, null, parts, null, null)
                else sms.sendTextMessage(num, null, msg, null, null)
                sentNow.add(h.person.displayName)
                Log.i(TAG, "sent to " + h.person.displayName)
                true
            } catch (e: Exception) {
                Log.w(TAG, "send failed " + h.person.displayName, e)
                false
            }
            log.add(0, "$key · " + (if (ok) "✓ " else "✗ ") + h.person.displayName + " (" + (if (h.birthday) T.t("γενέθλια") else T.t("γιορτή")) + ")")
        }
        while (log.size > LOG_MAX) log.removeAt(log.size - 1)
        sp.edit().putString("autoSmsDay", key).putStringSet("autoSmsDone", done)
            .putString("autoSmsLog", log.joinToString("\n")).apply()
        return sentNow
    }

    fun history(c: Context): String {
        val h = Prefs.sp(c).getString("autoSmsLog", "") ?: ""
        return h.ifEmpty { T.t("Δεν έχουν σταλεί ακόμα αυτόματες ευχές.") }
    }

    fun sentThisYear(c: Context, year: Int): Int =
        (Prefs.sp(c).getString("autoSmsLog", "") ?: "").lines().count { it.startsWith(year.toString()) && it.contains("✓") }
}
