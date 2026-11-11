package gr.eortologio.plus

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

/** Εκκλησιαστικό ημερολόγιο (νηστείες) και επίσημες αργίες / τριήμερα στην Ελλάδα. */
object Church {
    enum class Fast { NONE, FREE, FAST, FISH, DAIRY, STRICT }

    data class FastInfo(val type: Fast, val label: String) {
        val icon: String get() = when (type) {
            Fast.STRICT -> "🍞"; Fast.FAST -> "🥗"; Fast.FISH -> "🐟"
            Fast.DAIRY -> "🧀"; Fast.FREE -> "🍖"; Fast.NONE -> ""
        }
    }

    private fun between(d: LocalDate, a: LocalDate, b: LocalDate) = !d.isBefore(a) && !d.isAfter(b)
    private fun fi(t: Fast, label: String) = FastInfo(t, T.t(label))

    /** Πληροφορία νηστείας για μια ημέρα (απλοποιημένο τυπικό, για καθημερινή ενημέρωση). */
    fun fast(d: LocalDate): FastInfo {
        val y = d.year
        val e = EventStore.orthodoxEaster(y)
        val k = EventStore.daysUntil(e, d).toInt() // ημέρες από το Πάσχα (αρνητικό = πριν)
        val md = MonthDay.from(d)
        val dow = d.dayOfWeek
        val wedFri = dow == DayOfWeek.WEDNESDAY || dow == DayOfWeek.FRIDAY

        // --- Περίοδοι κατάλυσης ---
        if (between(d, LocalDate.of(y, 12, 25), LocalDate.of(y, 12, 31)) || between(d, LocalDate.of(y, 1, 1), LocalDate.of(y, 1, 4)))
            return fi(Fast.FREE, "Κατάλυση εις πάντα (Δωδεκαήμερο)")
        if (k in 0..6) return fi(Fast.FREE, "Κατάλυση εις πάντα (Διακαινήσιμος)")
        if (k in 49..55) return fi(Fast.FREE, "Κατάλυση εις πάντα (εβδομάδα Πεντηκοστής)")
        if (k in -70..-64) return fi(Fast.FREE, "Κατάλυση εις πάντα (Τελώνου και Φαρισαίου)")

        // --- Μεγάλη Σαρακοστή & Μεγάλη Εβδομάδα (πριν από τις μονοήμερες, ώστε η Μ. Εβδομάδα να υπερισχύει) ---
        if (k in -55..-49) return fi(Fast.DAIRY, "Τυροφάγου: χωρίς κρέας (γαλακτοκομικά, αυγά, ψάρι επιτρέπονται)")
        if (k in -48..-1) {
            if (k == -2) return fi(Fast.STRICT, "Μεγάλη Παρασκευή · αυστηρή νηστεία")
            if (k >= -6) return fi(Fast.STRICT, "Μεγάλη Εβδομάδα · αυστηρή νηστεία")
            if (k == -7 || md == MonthDay.of(3, 25)) return fi(Fast.FISH, "Μ. Σαρακοστή · κατάλυση ιχθύος")
            return fi(Fast.FAST, "Μεγάλη Σαρακοστή · νηστεία")
        }

        // --- Αυστηρές μονοήμερες νηστείες ---
        if (md == MonthDay.of(1, 5)) return fi(Fast.STRICT, "Αυστηρή νηστεία (παραμονή Θεοφανείων)")
        if (md == MonthDay.of(8, 29)) return fi(Fast.STRICT, "Αυστηρή νηστεία (Αποτομή Τιμίου Προδρόμου)")
        if (md == MonthDay.of(9, 14)) return fi(Fast.STRICT, "Αυστηρή νηστεία (Ύψωση Τιμίου Σταυρού)")

        // --- Των Αγίων Αποστόλων: από Δευτέρα μετά των Αγίων Πάντων έως 28/6 ---
        val apStart = e.plusDays(57)
        val apEnd = LocalDate.of(y, 6, 28)
        if (!apStart.isAfter(apEnd) && between(d, apStart, apEnd))
            return if (wedFri) fi(Fast.FAST, "Νηστεία Αγίων Αποστόλων") else fi(Fast.FISH, "Νηστεία Αγίων Αποστόλων · κατάλυση ιχθύος")

        // --- Δεκαπενταύγουστος ---
        if (between(d, LocalDate.of(y, 8, 1), LocalDate.of(y, 8, 14))) {
            if (md == MonthDay.of(8, 6)) return fi(Fast.FISH, "Μεταμόρφωση · κατάλυση ιχθύος")
            return fi(Fast.FAST, "Νηστεία Δεκαπενταύγουστου")
        }

        // --- Σαρανταήμερο Χριστουγέννων ---
        if (between(d, LocalDate.of(y, 11, 15), LocalDate.of(y, 12, 24))) {
            if (md == MonthDay.of(11, 21)) return fi(Fast.FISH, "Εισόδια · κατάλυση ιχθύος")
            val fishOk = !d.isAfter(LocalDate.of(y, 12, 17)) && !wedFri
            return if (fishOk) fi(Fast.FISH, "Νηστεία Χριστουγέννων · κατάλυση ιχθύος") else fi(Fast.FAST, "Νηστεία Χριστουγέννων")
        }

        // --- Τετάρτη & Παρασκευή ---
        if (wedFri) {
            val feasts = setOf(MonthDay.of(2, 2), MonthDay.of(3, 25), MonthDay.of(6, 24), MonthDay.of(6, 29),
                MonthDay.of(8, 15), MonthDay.of(9, 8))
            if (md in feasts || k == 39) return fi(Fast.FISH, "Τετάρτη/Παρασκευή εορτής · κατάλυση ιχθύος")
            return if (dow == DayOfWeek.WEDNESDAY) fi(Fast.FAST, "Τετάρτη · νηστεία") else fi(Fast.FAST, "Παρασκευή · νηστεία")
        }
        return FastInfo(Fast.NONE, "")
    }

    // ---------------- Αργίες ----------------
    private val holCache = ConcurrentHashMap<Int, Map<LocalDate, String>>()

    /** Επίσημες αργίες (όνομα στα ελληνικά — μεταφράζεται με T.t) για ένα έτος. */
    fun holidays(y: Int): Map<LocalDate, String> = holCache.getOrPut(y) {
        val e = EventStore.orthodoxEaster(y)
        val m = TreeMap<LocalDate, String>()
        m[LocalDate.of(y, 1, 1)] = "Πρωτοχρονιά"
        m[LocalDate.of(y, 1, 6)] = "Θεοφάνεια"
        m[e.minusDays(48)] = "Καθαρά Δευτέρα"
        m[LocalDate.of(y, 3, 25)] = "25η Μαρτίου"
        // Πρωτομαγιά: αν πέφτει στη Μ. Εβδομάδα ή στη Διακαινήσιμο μετατίθεται (συνήθως την Τρίτη μετά το Πάσχα)
        val mayDay = LocalDate.of(y, 5, 1)
        val k = EventStore.daysUntil(e, mayDay).toInt()
        m[if (k in -6..1) e.plusDays(2) else mayDay] = "Πρωτομαγιά"
        m[e.minusDays(2)] = "Μεγάλη Παρασκευή"
        m[e.plusDays(1)] = "Δευτέρα του Πάσχα"
        m[e.plusDays(50)] = "Αγίου Πνεύματος"
        m[LocalDate.of(y, 8, 15)] = "Δεκαπενταύγουστος"
        m[LocalDate.of(y, 10, 28)] = "28η Οκτωβρίου"
        m[LocalDate.of(y, 12, 25)] = "Χριστούγεννα"
        m[LocalDate.of(y, 12, 26)] = "Σύναξη της Θεοτόκου"
        m
    }

    fun holiday(d: LocalDate): String? = holidays(d.year)[d]

    fun dayOff(d: LocalDate): Boolean =
        d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY || holiday(d) != null

    /** Μακριά σαββατοκύριακα (≥3 ημέρες ρεπό), με «γέφυρα» αν μένει μία εργάσιμη ανάμεσα. */
    data class LongWeekend(val start: LocalDate, val end: LocalDate, val name: String, val bridge: Boolean) {
        val days: Long get() = EventStore.daysUntil(start, end) + 1
    }

    fun longWeekends(from: LocalDate, horizonDays: Int): List<LongWeekend> {
        val out = ArrayList<LongWeekend>()
        val hol = TreeMap(holidays(from.year)).apply { putAll(holidays(from.year + 1)) }
        for ((d, name) in hol) {
            if (d.isBefore(from.minusDays(4)) || d.isAfter(from.plusDays(horizonDays.toLong()))) continue
            val w = d.dayOfWeek
            if (w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY) continue
            var s = d
            var e = d
            var bridge = false
            if (w == DayOfWeek.TUESDAY) { s = d.minusDays(1); bridge = true }
            if (w == DayOfWeek.THURSDAY) { e = d.plusDays(1); bridge = true }
            var guard = 0
            while (dayOff(s.minusDays(1)) && guard++ < 10) s = s.minusDays(1)
            guard = 0
            while (dayOff(e.plusDays(1)) && guard++ < 10) e = e.plusDays(1)
            if (EventStore.daysUntil(s, e) + 1 < 3 || e.isBefore(from)) continue
            if (out.any { !it.start.isAfter(e) && !it.end.isBefore(s) }) continue
            out.add(LongWeekend(s, e, name, bridge))
        }
        return out
    }
}
