package gr.eortologio.plus

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Ευχές «Χρόνια πολλά»: μήνυμα από πρότυπο και αποστολή μέσω SMS / WhatsApp / Viber / άλλης εφαρμογής. */
object Wishes {
    private val NAMEDAY_EL = listOf(
        "Χρόνια πολλά {name}! 🎉 Να χαίρεσαι το όνομά σου, με υγεία, αγάπη και ευτυχία!",
        "{name}, χρόνια πολλά για τη γιορτή σου! 🥳 Ό,τι επιθυμείς να γίνεται πραγματικότητα!",
        "Χρόνια σου πολλά {name}! 🌟 Υγεία, χαρά και πολλές επιτυχίες!",
        "Να χαίρεσαι το όνομά σου {name}! 🎊 Χρόνια πολλά και καλά!",
        "Χρόνια πολλά {name}! 🎈 Να περάσεις υπέροχα σήμερα με τους δικούς σου ανθρώπους!",
        "{name} χρόνια πολλά! 🍀 Πάντα γερός/γερή και ευτυχισμένος/η!",
    ).joinToString("\n")
    private val BIRTHDAY_EL = listOf(
        "Χρόνια πολλά {name}! 🎂 Να τα εκατοστήσεις, με υγεία και χαρά!",
        "Happy birthday {name}! 🥳 Χρόνια πολλά, να είσαι πάντα καλά!",
        "{name}, χρόνια πολλά για τα γενέθλιά σου! 🎉 Να έχεις μια χρονιά γεμάτη χαμόγελα!",
        "Χρόνια πολλά {name}! 🎈 Υγεία, αγάπη και ό,τι ποθεί η καρδιά σου!",
        "Να τα εκατοστήσεις {name}! 🎂 Μια όμορφη χρονιά σου εύχομαι!",
    ).joinToString("\n")
    private val CLOSE_EL = listOf(
        "Χρόνια πολλά {name} μου! ❤️ Να είσαι πάντα γερός/γερή και χαμογελαστός/ή, σε αγαπώ πολύ!",
        "{name} μου χρόνια πολλά! 🤗 Ό,τι πιο όμορφο για σένα, σήμερα και πάντα!",
        "Χρόνια πολλά αγαπημένε/η μου {name}! 💐 Να έχεις υγεία, αγάπη και πολλή χαρά!",
    ).joinToString("\n")
    private val NAMEDAY_EN = listOf(
        "Happy name day {name}! 🎉 Wishing you health, love and happiness!",
        "{name}, happy name day! 🥳 May all your wishes come true!",
        "Χρόνια πολλά {name}! 🌟 Health, joy and lots of success!",
        "Happy name day {name}! 🎈 Have a wonderful day with your loved ones!",
    ).joinToString("\n")
    private val BIRTHDAY_EN = listOf(
        "Happy birthday {name}! 🎂 Wishing you health and joy!",
        "Happy birthday {name}! 🥳 Have an amazing year ahead!",
        "{name}, many happy returns! 🎉 May your year be full of smiles!",
    ).joinToString("\n")
    private val CLOSE_EN = listOf(
        "Happy day my dear {name}! ❤️ Love you lots, stay happy and healthy!",
        "{name}, wishing you all the best today and always! 🤗",
    ).joinToString("\n")

    fun defNameday() = if (T.en()) NAMEDAY_EN else NAMEDAY_EL
    fun defBirthday() = if (T.en()) BIRTHDAY_EN else BIRTHDAY_EL
    fun defClose() = if (T.en()) CLOSE_EN else CLOSE_EL

    /** Μηνύματα (ένα ανά γραμμή) για τον τύπο. */
    fun templates(ctx: Context, key: String, def: String): List<String> {
        val raw = Prefs.sp(ctx).getString(key, def) ?: def
        val out = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return out.ifEmpty { def.lines().map { it.trim() }.filter { it.isNotEmpty() } }
    }

    fun isClose(c: Context, p: Person) = p.key in (Prefs.sp(c).getStringSet("closeSet", emptySet()) ?: emptySet())

    fun setClose(c: Context, p: Person, close: Boolean) {
        val sp = Prefs.sp(c)
        val s = HashSet(sp.getStringSet("closeSet", emptySet()) ?: emptySet())
        if (close) s.add(p.key) else s.remove(p.key)
        sp.edit().putStringSet("closeSet", s).apply()
    }

    /**
     * Επιλέγει μήνυμα: οικογένεια/φίλοι → ζεστά μηνύματα, αλλιώς γιορτής ή γενεθλίων.
     * Αλλάζει ανά άτομο και ανά χρονιά (δεν παίρνουν όλοι το ίδιο, ούτε ο ίδιος το ίδιο κάθε χρόνο).
     */
    fun message(ctx: Context, h: Hit): String {
        val list = when {
            isClose(ctx, h.person) -> templates(ctx, "tplClose", defClose())
            h.birthday -> templates(ctx, "tplBirthday", defBirthday())
            else -> templates(ctx, "tplNameday", defNameday())
        }
        val idx = Math.floorMod((h.person.key + "|" + h.date.year + "|" + h.birthday).hashCode(), list.size)
        return list[idx].replace("{name}", Names.vocative(h.person.callName()))
    }

    fun sms(ctx: Context, h: Hit) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(h.person.phone ?: "")))
            .putExtra("sms_body", message(ctx, h))
        start(ctx, i, T.t("Δεν βρέθηκε εφαρμογή μηνυμάτων"))
    }

    fun whatsapp(ctx: Context, h: Hit) {
        val num = intl(h.person.phone)
        if (num.isEmpty()) { share(ctx, h, "com.whatsapp"); return }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num?text=" + Uri.encode(message(ctx, h))))
        start(ctx, i, T.t("Το WhatsApp δεν είναι εγκατεστημένο"))
    }

    fun viber(ctx: Context, h: Hit) = share(ctx, h, "com.viber.voip")

    fun call(ctx: Context, h: Hit) {
        val ph = h.person.phone ?: run { toast(ctx, T.t("Η επαφή δεν έχει τηλέφωνο")); return }
        start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(ph))), T.t("Δεν βρέθηκε εφαρμογή κλήσεων"))
    }

    fun copy(ctx: Context, h: Hit) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(T.t("Ευχές"), message(ctx, h)))
        toast(ctx, T.t("Αντιγράφηκε"))
    }

    /** Κοινοποίηση κειμένου· αν δοθεί πακέτο, πάει κατευθείαν σε αυτή την εφαρμογή. */
    fun share(ctx: Context, h: Hit, pkg: String?) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message(ctx, h))
        if (pkg != null) {
            i.setPackage(pkg)
            try { ctx.startActivity(i); return } catch (_: ActivityNotFoundException) { i.setPackage(null) }
        }
        start(ctx, Intent.createChooser(i, T.t("Στείλε ευχές στον/στην ") + h.person.callName()), T.t("Δεν βρέθηκε εφαρμογή"))
    }

    /** Διεθνής μορφή χωρίς '+', με προεπιλογή Ελλάδα (69xxxxxxxx → 3069xxxxxxxx). */
    fun intl(phone: String?): String {
        if (phone == null) return ""
        val d = phone.replace(Regex("[^0-9+]"), "")
        return when {
            d.startsWith("+") -> d.substring(1).replace("+", "")
            d.startsWith("00") -> d.substring(2)
            d.length == 10 && (d.startsWith("69") || d.startsWith("2")) -> "30$d"
            else -> d.replace("+", "")
        }
    }

    private fun start(ctx: Context, i: Intent, err: String) {
        try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) { toast(ctx, err) }
    }

    fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()
}
