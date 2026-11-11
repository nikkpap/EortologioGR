package gr.eortologio.plus

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Όλοι οι υπολογισμοί των οθονών, σε ένα μέρος. Τρέχουν στο παρασκήνιο (Dispatchers.Default),
 * ώστε η οθόνη να μη «κολλάει» ποτέ — ούτε με χιλιάδες επαφές.
 */
object Repo {
    // ---------------- Σήμερα ----------------
    data class TodayData(
        val names: List<String>,
        val holidays: List<String>,
        val fast: Church.FastInfo,
        val longWeekend: Church.LongWeekend?,
        val contactsGranted: Boolean,
        val hits: List<Hit>,
        val personalToday: List<Event>,
        val upcoming: List<Pair<LocalDate, List<Event>>>,
    )

    fun today(ctx: Context, today: LocalDate): TodayData {
        val store = EventStore.get(ctx)
        val evs = store.on(today)
        return TodayData(
            names = evs.filter { it.isNameDay }.map { it.canon }.distinct(),
            holidays = evs.filter { !it.isNameDay && !it.personal }.map { T.t(it.title) },
            fast = Church.fast(today),
            longWeekend = Church.longWeekends(today, 45).firstOrNull(),
            contactsGranted = ContactSync.granted(ctx),
            hits = ContactSync.hits(ctx, store, today, 1),
            personalToday = store.editable().filter { it.onYear(today.year) == today }.distinctBy { it.id },
            upcoming = (1..14).mapNotNull { i ->
                val d = today.plusDays(i.toLong())
                store.on(d).takeIf { it.isNotEmpty() }?.let { d to it }
            },
        )
    }

    // ---------------- Αναζήτηση ----------------
    data class NameResult(val canon: String, val variants: List<String>, val dates: List<LocalDate>, val contactCount: Int)
    data class SearchData(val query: String, val names: List<NameResult>, val people: List<Hit>, val others: List<Pair<Event, LocalDate>>) {
        val empty get() = names.isEmpty() && people.isEmpty() && others.isEmpty()
    }

    @Volatile private var index: List<Pair<String, List<String>>>? = null
    @Volatile private var indexRev = -1

    /** Χτίζεται μία φορά: κάθε όνομα με όλα τα κλειδιά αναζήτησης (ελληνικά, χωρίς τόνους, greeklish, ξένα). */
    private fun searchIndex(store: EventStore): List<Pair<String, List<String>>> {
        index?.let { if (indexRev == store.revision) return it }
        val idx = store.allCanons().map { it to Names.searchKeys(it) }
        index = idx
        indexRev = store.revision
        return idx
    }

    fun search(ctx: Context, query: String, today: LocalDate): SearchData {
        val q = Names.norm(query)
        if (q.isEmpty()) return SearchData(query, emptyList(), emptyList(), emptyList())
        val store = EventStore.get(ctx)
        // Ονόματα: πρώτα όσα ξεκινούν με το κείμενο, μετά όσα το περιέχουν
        val starts = ArrayList<String>()
        val contains = ArrayList<String>()
        for ((canon, keys) in searchIndex(store)) {
            if (keys.any { it.startsWith(q) }) starts.add(canon)
            else if (keys.any { it.contains(q) }) contains.add(canon)
        }
        val people = ContactSync.load(ctx)
        val names = (starts.sortedBy { Names.norm(it) } + contains.sortedBy { Names.norm(it) }).take(40).map { canon ->
            NameResult(
                canon, Names.variantsOf(canon),
                store.nameDaysOf(canon).map { it.next(today) }.distinct().sorted(),
                people.count { canon in it.canon },
            )
        }
        val hits = people.filter { Names.norm(it.displayName).contains(q) }.take(30)
            .distinctBy { it.key }
            .mapNotNull { ContactSync.nextFor(it, store, today) }.sortedBy { it.date }
        val others = store.all().filter { !it.isNameDay && (Names.norm(it.title).contains(q) || Names.norm(T.t(it.title)).contains(q)) }
            .distinctBy { it.id }.map { it to it.next(today) }.sortedBy { it.second }
        return SearchData(query, names, hits, others)
    }

    // ---------------- Όνομα (λεπτομέρειες) ----------------
    data class NameInfo(val canon: String, val variants: List<String>, val dates: List<LocalDate>, val people: List<Person>)

    fun nameInfo(ctx: Context, canon: String, today: LocalDate): NameInfo {
        val store = EventStore.get(ctx)
        return NameInfo(
            canon, Names.variantsOf(canon),
            store.nameDaysOf(canon).map { it.next(today) }.distinct().sorted(),
            ContactSync.load(ctx).filter { canon in it.canon },
        )
    }

    // ---------------- Ημερολόγιο ----------------
    data class Cell(val date: LocalDate, val names: Boolean, val personal: Boolean, val holiday: Boolean, val contact: Boolean, val dayOff: Boolean)
    data class MonthData(val ym: YearMonth, val cells: List<Cell?>)

    fun month(ctx: Context, ym: YearMonth): MonthData {
        val store = EventStore.get(ctx)
        val contactDays = ContactSync.hits(ctx, store, ym.atDay(1), ym.lengthOfMonth() - 1).map { it.date }.toHashSet()
        val lead = ym.atDay(1).dayOfWeek.value - 1 // Δευτέρα = 0
        val total = ((lead + ym.lengthOfMonth() + 6) / 7) * 7
        val cells = (0 until total).map { i ->
            val n = i - lead + 1
            if (n < 1 || n > ym.lengthOfMonth()) null
            else {
                val d = ym.atDay(n)
                val es = store.on(d)
                Cell(
                    d,
                    names = es.any { it.isNameDay },
                    personal = es.any { it.personal },
                    holiday = es.any { !it.isNameDay && !it.personal } || Church.holiday(d) != null,
                    contact = d in contactDays,
                    dayOff = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY || Church.holiday(d) != null,
                )
            }
        }
        return MonthData(ym, cells)
    }

    data class DayData(
        val date: LocalDate, val names: List<String>, val events: List<Event>,
        val publicHoliday: String?, val fast: Church.FastInfo, val hits: List<Hit>,
    )

    fun day(ctx: Context, d: LocalDate): DayData {
        val store = EventStore.get(ctx)
        val es = store.on(d)
        return DayData(
            d, es.filter { it.isNameDay }.map { it.canon }.distinct(), es.filter { !it.isNameDay },
            Church.holiday(d)?.let { T.t(it) }, Church.fast(d), ContactSync.hits(ctx, store, d, 0),
        )
    }

    // ---------------- Επαφές ----------------
    data class ContactSection(val title: String, val big: Boolean, val hits: List<Hit>)
    data class ContactsData(
        val granted: Boolean, val named: Int, val bdays: Int, val monthCount: Int, val sentYear: Int,
        val sections: List<ContactSection>,
    )

    fun contacts(ctx: Context, today: LocalDate): ContactsData {
        if (!ContactSync.enabled(ctx)) return ContactsData(false, 0, 0, 0, 0, emptyList())
        val store = EventStore.get(ctx)
        val people = ContactSync.load(ctx)
        val hits = ContactSync.hits(ctx, store, today, 365)
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
        val monthCount = hits.filter { !it.date.isAfter(endOfMonth) }.map { it.person.key }.distinct().size
        val titles = listOf(T.t("🎉 Σήμερα"), T.t("Αύριο"), T.t("Αυτή την εβδομάδα"), T.t("Αυτόν τον μήνα"), T.t("Αργότερα"))
        val buckets = List(5) { ArrayList<Hit>() }
        val laterSeen = HashSet<String>()
        for (h in hits) {
            val dd = EventStore.daysUntil(today, h.date)
            val b = when {
                dd <= 0 -> 0
                dd == 1L -> 1
                dd <= 7 -> 2
                dd <= 31 -> 3
                else -> 4
            }
            if (b == 4 && (!laterSeen.add(h.person.key) || buckets[4].size >= 150)) continue // μόνο η πρώτη ανά επαφή
            buckets[b].add(h)
        }
        return ContactsData(
            true, people.count { it.canon.isNotEmpty() }, people.count { it.hasBirthday() }, monthCount,
            AutoWishes.sentThisYear(ctx, today.year),
            buckets.mapIndexedNotNull { i, l -> if (l.isEmpty()) null else ContactSection(titles[i], i <= 1, l) },
        )
    }

    // ---------------- Δικά μου ----------------
    fun personal(ctx: Context, today: LocalDate): List<Pair<Event, LocalDate>> =
        EventStore.get(ctx).editable().distinctBy { it.id }.map { it to it.next(today) }.sortedBy { it.second }

    fun icon(e: Event) = when (e.category) {
        EventStore.CAT_BIRTHDAY -> "🎂"
        EventStore.CAT_NAME -> "🎉"
        EventStore.CAT_ANNIVERSARY -> "💍"
        else -> "⭐"
    }
}
