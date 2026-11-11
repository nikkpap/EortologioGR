package gr.eortologio.plus

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.MonthDay
import java.time.Year
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/** Ένα γεγονός: ονομαστική γιορτή, αργία ή προσωπική ημερομηνία. */
data class Event(
    val id: String,
    val title: String,
    val category: String,
    val day: Int,
    val month: Int,
    val offset: Int,
    /** Έτος (για ηλικία / χρόνια επετείου), 0 = χωρίς έτος. */
    val year: Int,
    val movable: Boolean,
    val personal: Boolean,
    /** Προαιρετική σύνδεση με επαφή (για ευχές / αυτόματο SMS). */
    val phone: String? = null,
    val linkName: String? = null,
) {
    /** Για ονομαστικές γιορτές: το επίσημο όνομα (κλειδί παραλλαγών). */
    val canon: String = if (category == EventStore.CAT_NAME) Names.canonicalOf(title) else title

    val isNameDay: Boolean get() = category == EventStore.CAT_NAME

    fun onYear(y: Int): LocalDate {
        val easter = EventStore.orthodoxEaster(y)
        if (movable) return easter.plusDays(offset.toLong())
        val d = if (day == 29 && month == 2 && !Year.isLeap(y.toLong())) 28 else day
        val date = LocalDate.of(y, month, d)
        // Αγ. Γεώργιος / Αγ. Μάρκος: αν πέφτουν πριν ή μέσα στη Μ. Εβδομάδα γιορτάζονται μετά το Πάσχα.
        if (isNameDay && month == 4) {
            if (day == 23 && !easter.isBefore(date)) return easter.plusDays(1)
            if (day == 25 && !easter.plusDays(1).isBefore(date)) return easter.plusDays(2)
        }
        return date
    }

    /** Η επόμενη (ή σημερινή) ημερομηνία από το [from]. */
    fun next(from: LocalDate): LocalDate {
        val d = onYear(from.year)
        return if (d.isBefore(from)) onYear(from.year + 1) else d
    }

    /** Χρόνια που συμπληρώνονται στην ημερομηνία [on] (0 αν δεν υπάρχει έτος). */
    fun yearsAt(on: LocalDate): Int = if (year > 0 && on.year > year) on.year - year else 0

    fun json(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("category", category)
        put("day", day); put("month", month); put("offset", offset)
        put("birthYear", year); put("movable", movable)
        if (!phone.isNullOrEmpty()) { put("phone", phone); put("linkName", linkName ?: "") }
    }

    companion object {
        fun parse(o: JSONObject): Event {
            val ph = o.optString("phone", "")
            return Event(
                id = o.optString("id"), title = o.optString("title"),
                category = o.optString("category", EventStore.CAT_PERSONAL),
                day = o.optInt("day", 1), month = o.optInt("month", 1), offset = o.optInt("offset"),
                year = o.optInt("birthYear"), movable = o.optBoolean("movable"), personal = true,
                phone = ph.ifEmpty { null },
                linkName = if (ph.isEmpty()) null else o.optString("linkName", "").ifEmpty { null },
            )
        }
    }
}

/**
 * Πηγή όλων των γεγονότων: ενσωματωμένο εορτολόγιο (διαβάζεται ΜΙΑ φορά) + προσωπικά (SharedPreferences).
 * Κρατά ευρετήριο ημερομηνία → γεγονότα ανά έτος και τα προσωπικά σε μνήμη, ώστε οι οθόνες να είναι άμεσες.
 */
class EventStore private constructor(private val app: Context) {
    private val prefs: SharedPreferences = app.getSharedPreferences("events", Context.MODE_PRIVATE)
    private val builtIn: List<Event>
    @Volatile private var personalCache: List<Event>? = null
    @Volatile private var allCache: List<Event>? = null
    private val yearIndex = ConcurrentHashMap<Int, Map<LocalDate, List<Event>>>()
    @Volatile private var byCanon: Map<String, List<Event>>? = null

    /** Αυξάνεται σε κάθε αλλαγή των «Δικά μου» (για ανανέωση οθονών/cache). */
    @Volatile var revision: Int = 0
        private set

    init {
        Names.ensure(app)
        val b = ArrayList<Event>(420)
        load("TODAY.DAT", false, b)
        load("CUSTOM.INI", true, b)
        builtIn = b
    }

    // ---------- προσωπικά ----------
    fun editable(): List<Event> {
        personalCache?.let { return it }
        val r = ArrayList<Event>()
        try {
            val a = JSONArray(prefs.getString("personal", "[]"))
            for (i in 0 until a.length()) r.add(Event.parse(a.getJSONObject(i)))
        } catch (_: Exception) {
        }
        personalCache = r
        return r
    }

    fun rawPersonal(): String = prefs.getString("personal", "[]") ?: "[]"

    fun restoreRaw(json: String) {
        prefs.edit().putString("personal", json).apply()
        invalidate()
    }

    @Synchronized fun save(e: Event) {
        val es = editable().toMutableList()
        val i = es.indexOfFirst { it.id == e.id }
        if (i >= 0) es[i] = e else es.add(e)
        write(es)
    }

    @Synchronized fun delete(id: String) = write(editable().filter { it.id != id })

    private fun write(es: List<Event>) {
        val a = JSONArray()
        for (e in es) a.put(e.json())
        prefs.edit().putString("personal", a.toString()).apply()
        invalidate()
    }

    fun invalidate() {
        personalCache = null
        allCache = null
        byCanon = null
        yearIndex.clear()
        revision++
    }

    // ---------- όλα ----------
    fun all(): List<Event> {
        allCache?.let { return it }
        val out = ArrayList<Event>(builtIn.size + 32)
        out.addAll(builtIn)
        out.addAll(editable())
        allCache = out
        return out
    }

    /** Ευρετήριο ημερομηνία → γεγονότα για ένα έτος (υπολογίζεται μία φορά ανά έτος). */
    fun index(year: Int): Map<LocalDate, List<Event>> = yearIndex.getOrPut(year) {
        val m = HashMap<LocalDate, MutableList<Event>>(420)
        for (e in all()) {
            try { m.getOrPut(e.onYear(year)) { ArrayList(4) }.add(e) } catch (_: Exception) {}
        }
        m
    }

    fun on(day: LocalDate): List<Event> = index(day.year)[day] ?: emptyList()

    /** Ονομαστικές γιορτές ανά κανονικό όνομα (π.χ. "Ιωάννης" → 7/1, 24/6, ...). */
    fun nameDaysOf(canon: String): List<Event> {
        val m = byCanon ?: all().filter { it.isNameDay }.groupBy { it.canon }.also { byCanon = it }
        return m[canon] ?: emptyList()
    }

    /** Όλα τα διαφορετικά κανονικά ονόματα του εορτολογίου. */
    fun allCanons(): List<String> {
        val m = byCanon ?: all().filter { it.isNameDay }.groupBy { it.canon }.also { byCanon = it }
        return m.keys.toList()
    }

    private fun load(filename: String, personal: Boolean, out: MutableList<Event>) {
        try {
            app.assets.open(filename).bufferedReader(Charsets.UTF_8).useLines { lines ->
                val seen = HashSet<String>()
                lines.forEachIndexed { idx, line ->
                    val s = line.trim()
                    if (s.isEmpty() || s.startsWith(";")) return@forEachIndexed
                    val p = s.split(Regex("\\s+"), limit = 2)
                    if (p.size != 2) return@forEachIndexed
                    var title = p[1].trim().replace("?", "").trim()
                    val holiday = title.startsWith("\"")
                    if (holiday) title = title.replace("\"", "").trim()
                    if (title.isEmpty()) return@forEachIndexed
                    try {
                        val movable = p[0].startsWith("+") || p[0].startsWith("-")
                        var day = 1
                        var month = 1
                        var offset = 0
                        if (movable) offset = p[0].toInt()
                        else {
                            val dm = p[0].split("/")
                            day = dm[0].toInt(); month = dm[1].toInt()
                            MonthDay.of(month, day) // επικύρωση (επιτρέπει 29/2)
                        }
                        if (!seen.add(p[0] + "|" + Names.norm(title))) return@forEachIndexed // διπλοεγγραφές
                        val cat = if (personal) CAT_PERSONAL else if (holiday) CAT_HOLIDAY else CAT_NAME
                        out.add(Event("$filename:${idx + 1}", title, cat, day, month, offset, 0, movable, personal))
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val CAT_NAME = "Ονομαστική γιορτή"
        const val CAT_HOLIDAY = "Εορτή / Αργία"
        const val CAT_PERSONAL = "Προσωπικό"
        const val CAT_BIRTHDAY = "Γενέθλια"
        const val CAT_ANNIVERSARY = "Επέτειος"

        @Volatile private var instance: EventStore? = null

        fun get(ctx: Context): EventStore =
            instance ?: synchronized(this) { instance ?: EventStore(ctx.applicationContext).also { instance = it } }

        fun daysUntil(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

        private val easterCache = ConcurrentHashMap<Int, LocalDate>()

        /** Ορθόδοξο Πάσχα (Meeus, Ιουλιανό → Γρηγοριανό, έγκυρο 1900–2099). */
        fun orthodoxEaster(year: Int): LocalDate = easterCache.getOrPut(year) {
            val a = year % 4
            val b = year % 7
            val c = year % 19
            val d = (19 * c + 15) % 30
            val e = (2 * a + 4 * b - d + 34) % 7
            LocalDate.of(year, 3, 22).plusDays((d + e + 13).toLong())
        }
    }
}
