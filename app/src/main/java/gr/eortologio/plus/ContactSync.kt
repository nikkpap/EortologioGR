package gr.eortologio.plus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import java.time.LocalDate
import java.time.Year

/** Επαφή του κινητού με γιορτή ή γενέθλια. */
class Person(val id: Long, val displayName: String) {
    var givenName: String? = null
    var phone: String? = null
    /** 4 = κύριο κινητό, 3 = κινητό, 2 = κύριο σταθερό, 1 = άλλο. */
    var phoneRank = -1
    val canon = LinkedHashSet<String>()
    var bMonth = 0
    var bDay = 0
    var bYear = 0

    /** Μικρό όνομα για την ευχή. */
    fun callName(): String {
        givenName?.trim()?.takeIf { it.isNotEmpty() }?.let { return Names.pretty(it) }
        val t = displayName.trim().split(Regex("\\s+"))
        for (s in t) if (Names.match(s).isNotEmpty()) return Names.pretty(s)
        return Names.pretty(t.firstOrNull() ?: displayName)
    }

    fun hasBirthday() = bMonth > 0 && bDay > 0

    fun birthdayOn(y: Int): LocalDate? = try {
        LocalDate.of(y, bMonth, if (bMonth == 2 && bDay == 29 && !Year.isLeap(y.toLong())) 28 else bDay)
    } catch (_: Exception) { null }

    /** Κλειδί επαφής: ο αριθμός (σταθερός ακόμα κι αν αλλάξει το id της επαφής), αλλιώς το όνομα. */
    val key: String get() = phone?.let { Wishes.intl(it) } ?: "name:$displayName"
}

/** Μια γιορτή/γενέθλια επαφής σε συγκεκριμένη ημερομηνία. */
data class Hit(val person: Person, val date: LocalDate, val birthday: Boolean, val reason: String)

/** Διαβάζει τις επαφές του κινητού και βρίσκει ποιοι γιορτάζουν / έχουν γενέθλια. Μόνο ανάγνωση. */
object ContactSync {
    @Volatile private var cache: List<Person>? = null
    @Volatile private var cacheTime = 0L

    fun granted(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun enabled(ctx: Context) = Prefs.sp(ctx).getBoolean("contacts", true) && granted(ctx)

    fun invalidate() { cache = null }

    /** Επαφές με γιορτή ή γενέθλια. Cache 10 λεπτών ώστε να μη ξαναδιαβάζεται η βάση σε κάθε οθόνη. */
    @Synchronized fun load(ctx: Context): List<Person> {
        if (!enabled(ctx)) return emptyList()
        val now = System.currentTimeMillis()
        cache?.let { if (now - cacheTime < 10 * 60_000L) return it }
        Names.ensure(ctx)
        val map = HashMap<Long, Person>()
        val proj = arrayOf(
            ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME, ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.IS_PRIMARY,
        )
        val sel = ContactsContract.Data.MIMETYPE + " IN (?,?,?)"
        val args = arrayOf(
            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            CommonDataKinds.Event.CONTENT_ITEM_TYPE,
        )
        try {
            ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI, proj, sel, args, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val dn = c.getString(1) ?: continue
                    val p = map.getOrPut(id) { Person(id, dn) }
                    val mime = c.getString(2)
                    val d1 = c.getString(3)
                    val type = if (c.isNull(4)) 0 else c.getInt(4)
                    when (mime) {
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            // DATA2 = GIVEN_NAME
                            c.getString(4)?.trim()?.takeIf { it.isNotEmpty() }?.let { p.givenName = it }
                        }
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> if (d1 != null) {
                            // Προτιμάται ΠΑΝΤΑ το κινητό (το SMS δεν πάει σε σταθερό), μετά το «κύριο».
                            val mobile = type == CommonDataKinds.Phone.TYPE_MOBILE || isMobile(d1)
                            val primary = c.getInt(5) != 0
                            val rank = if (mobile) (if (primary) 4 else 3) else (if (primary) 2 else 1)
                            if (rank > p.phoneRank) { p.phone = d1; p.phoneRank = rank }
                        }
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE ->
                            if (type == CommonDataKinds.Event.TYPE_BIRTHDAY) parseBirthday(p, d1)
                    }
                }
            }
        } catch (_: Exception) {
        }

        val out = ArrayList<Person>()
        for (p in map.values) {
            p.givenName?.let { p.canon.addAll(Names.match(it)) }
            if (p.canon.isEmpty()) {
                // Χωρίς δομημένο όνομα: δοκίμασε κάθε λέξη ("Νίκος Π.", "Παπαδόπουλος Νίκος", "Giorgos")
                for (t in p.displayName.split(Regex("[\\s,.()\\-]+"))) {
                    val m = Names.match(t)
                    if (m.isNotEmpty()) {
                        p.canon.addAll(m)
                        if (p.givenName == null) p.givenName = t
                        break
                    }
                }
            }
            if (p.canon.isNotEmpty() || p.hasBirthday()) out.add(p)
        }
        out.sortWith { a, b -> a.displayName.compareTo(b.displayName, ignoreCase = true) }
        cache = out
        cacheTime = now
        return out
    }

    /** Κινητό; Ελλάδα: 69…· εξωτερικό: δεκτό (δεν ξέρουμε). */
    fun isMobile(phone: String): Boolean {
        val n = Wishes.intl(phone)
        return if (n.startsWith("30")) n.startsWith("306") else n.length >= 8
    }

    /** "1985-03-12", "--03-12", "12/03/1985" */
    private fun parseBirthday(p: Person, raw: String?) {
        val s = raw?.trim() ?: return
        try {
            when {
                s.startsWith("--") -> { p.bMonth = s.substring(2, 4).toInt(); p.bDay = s.substring(5, 7).toInt() }
                s.length >= 10 && s[4] == '-' -> {
                    p.bYear = s.substring(0, 4).toInt(); p.bMonth = s.substring(5, 7).toInt(); p.bDay = s.substring(8, 10).toInt()
                }
                s.contains('/') -> {
                    val x = s.split('/')
                    p.bDay = x[0].trim().toInt(); p.bMonth = x[1].trim().toInt()
                    if (x.size > 2) p.bYear = x[2].trim().take(4).toIntOrNull() ?: 0
                }
            }
            if (p.bMonth !in 1..12 || p.bDay !in 1..31) { p.bMonth = 0; p.bDay = 0 }
            if (p.bYear < 1900) p.bYear = 0
        } catch (_: Exception) {
            p.bMonth = 0; p.bDay = 0; p.bYear = 0
        }
    }

    /** Ποιες επαφές γιορτάζουν ή έχουν γενέθλια από [from] έως [from]+[days]. Ταξινομημένα κατά ημερομηνία. */
    fun hits(ctx: Context, store: EventStore, from: LocalDate, days: Int): List<Hit> {
        val people = load(ctx)
        val end = from.plusDays(days.toLong())
        val out = ArrayList<Hit>()
        if (people.isNotEmpty()) {
            // canonical → ημερομηνίες εντός παραθύρου (μία προσπέλαση του ευρετηρίου ανά ημέρα)
            val nameDates = HashMap<String, MutableList<LocalDate>>()
            var d = from
            while (!d.isAfter(end)) {
                for (e in store.on(d)) if (e.isNameDay) nameDates.getOrPut(e.canon) { ArrayList(2) }.add(d)
                d = d.plusDays(1)
            }
            for (p in people) {
                val added = HashSet<LocalDate>()
                for (c in p.canon) nameDates[c]?.forEach { dt -> if (added.add(dt)) out.add(Hit(p, dt, false, c)) }
                if (p.hasBirthday()) for (y in from.year..end.year) {
                    val b = p.birthdayOn(y) ?: continue
                    if (!b.isBefore(from) && !b.isAfter(end)) out.add(Hit(p, b, true, T.t("Γενέθλια")))
                }
            }
        }
        // «Δικά μου» συνδεδεμένα με επαφή (π.χ. γενέθλια που δεν υπάρχουν στις επαφές)
        for (e in store.editable()) {
            val phone = e.phone ?: continue
            val bday = e.category == EventStore.CAT_BIRTHDAY
            for (y in from.year..end.year) {
                val dt = try { e.onYear(y) } catch (_: Exception) { continue }
                if (dt.isBefore(from) || dt.isAfter(end)) continue
                val name = e.linkName?.takeIf { it.isNotEmpty() } ?: e.title
                val p = Person(-((e.id + name).hashCode().toLong() and 0x7fffffffL) - 1, name)
                p.phone = phone
                p.givenName = name.trim().split(Regex("\\s+")).first()
                if (bday) { p.bMonth = e.month; p.bDay = e.day; p.bYear = e.year }
                out.add(Hit(p, dt, bday, if (bday) T.t("Γενέθλια") else e.title))
            }
        }
        // Χωρίς διπλοεγγραφές (ίδια επαφή σε 2 λογαριασμούς / SIM): ίδιος αριθμός ή όνομα + ημερομηνία + τύπος
        val uniq = HashSet<String>()
        val res = out.filter { h ->
            uniq.add((h.person.phone?.let { Wishes.intl(it) } ?: Names.norm(h.person.displayName)) + "|" + h.date + "|" + h.birthday)
        }
        return res.sortedWith(compareBy<Hit> { it.date }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.person.displayName })
    }

    /** Η επόμενη γιορτή ή γενέθλια μιας επαφής. */
    fun nextFor(p: Person, store: EventStore, today: LocalDate): Hit? {
        var best: Hit? = null
        for (c in p.canon) for (e in store.nameDaysOf(c)) {
            val d = e.next(today)
            if (best == null || d.isBefore(best.date)) best = Hit(p, d, false, c)
        }
        if (p.hasBirthday()) {
            var b = p.birthdayOn(today.year)
            if (b != null && b.isBefore(today)) b = p.birthdayOn(today.year + 1)
            if (b != null && (best == null || b.isBefore(best.date))) best = Hit(p, b, true, T.t("Γενέθλια"))
        }
        return best
    }
}
