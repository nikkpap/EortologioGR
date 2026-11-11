package gr.eortologio.plus

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/** Ονόματα: κανονικοποίηση, παραλλαγές (Γιώργος → Γεώργιος), greeklish, κλητική. */
object Names {
    private val MARKS = Regex("\\p{M}+")
    val EL: Locale = Locale("el", "GR")

    private class Data(
        /** normalized variant → canonical names (ένα υποκοριστικό μπορεί να ανήκει σε >1 όνομα). */
        val index: Map<String, Set<String>>,
        /** canonical → εμφανιζόμενες ελληνικές παραλλαγές. */
        val variants: Map<String, List<String>>,
        /** normalized → σωστή γραφή («μιχαλησ» → «Μιχάλης»). */
        val display: Map<String, String>,
        /** canonical → λατινικές μορφές του NAMES.DAT (George, John…). */
        val latinOf: Map<String, List<String>>,
        /** Ξένες μορφές (George, Chris…) — δεν κλίνονται σε κλητική. */
        val foreign: Set<String>,
    )

    @Volatile private var data: Data? = null

    /** Πεζά, χωρίς τόνους/διαλυτικά, ς→σ. */
    fun norm(s: String?): String {
        if (s == null) return ""
        val n = Normalizer.normalize(s.trim().lowercase(EL), Normalizer.Form.NFD)
        return MARKS.replace(n, "").replace('ς', 'σ')
    }

    fun ensure(ctx: Context) {
        if (data != null) return
        synchronized(this) {
            if (data != null) return
            data = try {
                ctx.assets.open("NAMES.DAT").bufferedReader(Charsets.UTF_8).use { load(it.readLines()) }
            } catch (e: Exception) {
                Data(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptySet())
            }
        }
    }

    private fun load(lines: List<String>): Data {
        val idx = HashMap<String, MutableSet<String>>(1024)
        val vars = HashMap<String, MutableList<String>>(256)
        val disp = HashMap<String, String>(1024)
        val lat = HashMap<String, MutableList<String>>(256)
        val explicitLatin = HashSet<String>()
        val generated = HashSet<String>()
        fun addKey(name: String, canon: String) {
            val n = norm(name)
            idx.getOrPut(n) { LinkedHashSet() }.add(canon)
            if (isGreek(name)) for (l in latin(n)) {
                idx.getOrPut(l) { LinkedHashSet() }.add(canon)
                generated.add(l)
            }
        }
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith(";")) continue
            val eq = line.indexOf('=')
            val canon = (if (eq < 0) line else line.substring(0, eq)).trim()
            if (canon.isEmpty()) continue
            val shown = vars.getOrPut(canon) { ArrayList() }
            addKey(canon, canon)
            disp.putIfAbsent(norm(canon), canon)
            if (eq >= 0) {
                for (v in line.substring(eq + 1).split(',')) {
                    val t = v.trim()
                    if (t.isEmpty()) continue
                    addKey(t, canon)
                    if (isGreek(t)) {
                        disp.putIfAbsent(norm(t), t)
                        if (norm(t) != norm(canon) && shown.none { norm(it) == norm(t) }) shown.add(t)
                    } else {
                        lat.getOrPut(canon) { ArrayList() }.add(norm(t))
                        explicitLatin.add(norm(t))
                    }
                }
            }
        }
        return Data(idx, vars, disp, lat, explicitLatin - generated)
    }

    /** «ΜΙΧΑΛΗΣ» → «Μιχάλης», «MIXALIS» → «Mixalis»· αλλιώς όπως είναι. */
    fun pretty(s: String): String {
        if (s.isEmpty()) return s
        if (isGreek(s)) data?.display?.get(norm(s))?.let { return it }
        val allUp = s == s.uppercase(Locale.ROOT)
        val allLow = s == s.lowercase(Locale.ROOT)
        if (allUp || allLow) {
            val l = s.lowercase(EL)
            return l.substring(0, 1).uppercase(EL) + l.substring(1)
        }
        return s
    }

    /** Κανονικό όνομα για όνομα του εορτολογίου (π.χ. "Γιάννης" → "Ιωάννης"). */
    fun canonicalOf(title: String): String {
        val idx = data?.index ?: return title
        var c = idx[norm(title)]
        if (c == null) {
            val sp = title.indexOf(' ')
            if (sp > 0) c = idx[norm(title.substring(0, sp))]
        }
        return c?.firstOrNull() ?: title
    }

    /** Όλα τα κανονικά ονόματα που αντιστοιχούν σε ένα μικρό όνομα επαφής (ελληνικά ή λατινικά). */
    fun match(firstName: String?): Set<String> {
        if (firstName == null) return emptySet()
        return data?.index?.get(norm(firstName)) ?: emptySet()
    }

    fun variantsOf(canon: String): List<String> = data?.variants?.get(canon) ?: emptyList()

    /** Κλειδιά αναζήτησης ενός ονόματος (ελληνικά + greeklish όλων των παραλλαγών). */
    fun searchKeys(canon: String): List<String> {
        val keys = LinkedHashSet<String>()
        keys.add(norm(canon))
        keys.addAll(latin(norm(canon)))
        for (x in variantsOf(canon)) {
            keys.add(norm(x)); keys.addAll(latin(norm(x)))
        }
        data?.latinOf?.get(canon)?.let { keys.addAll(it) }
        return keys.toList()
    }

    /** «Γεώργιος» → «Georgios» (για εμφάνιση στα αγγλικά). */
    fun latinPretty(canon: String): String {
        val l = latin(norm(canon)).first()
        return if (l.isEmpty()) canon else l.replaceFirstChar { it.uppercaseChar() }
    }

    fun isGreek(s: String): Boolean = s.any { it.code in 0x370..0x3FF || it.code in 0x1F00..0x1FFF }

    /** Μεταγραφές greeklish ενός κανονικοποιημένου ελληνικού ονόματος (2-3 πιθανές μορφές). */
    fun latin(n: String): List<String> {
        val a = StringBuilder()
        val b = StringBuilder()
        var i = 0
        while (i < n.length) {
            val c = n[i]
            if (i + 1 < n.length) {
                val two = "" + c + n[i + 1]
                val pair: Pair<String, String>? = when (two) {
                    "ου" -> "ou" to "ou"
                    "αυ" -> "av" to "au"
                    "ευ" -> "ev" to "eu"
                    "γγ" -> "ng" to "gg"
                    "γκ" -> "g" to "gk"
                    "μπ" -> (if (i == 0) "b" else "mp") to "b"
                    "ντ" -> (if (i == 0) "d" else "nt") to "d"
                    else -> null
                }
                if (pair != null) {
                    a.append(pair.first); b.append(pair.second); i += 2; continue
                }
            }
            val (m, alt) = when (c) {
                'α' -> "a" to null; 'β' -> "v" to null; 'γ' -> "g" to null
                'δ' -> "d" to null; 'ε' -> "e" to null; 'ζ' -> "z" to null
                'η' -> "i" to null; 'θ' -> "th" to null; 'ι' -> "i" to null
                'κ' -> "k" to "c"; 'λ' -> "l" to null; 'μ' -> "m" to null
                'ν' -> "n" to null; 'ξ' -> "x" to "ks"; 'ο' -> "o" to null
                'π' -> "p" to null; 'ρ' -> "r" to null; 'σ' -> "s" to null
                'τ' -> "t" to null; 'υ' -> "y" to "i"; 'φ' -> "f" to "ph"
                'χ' -> "ch" to "h"; 'ψ' -> "ps" to null; 'ω' -> "o" to null
                else -> c.toString() to null
            }
            a.append(m); b.append(alt ?: m)
            i++
        }
        val sa = a.toString()
        val sb = b.toString()
        val out = ArrayList<String>(3)
        out.add(sa)
        if (sb != sa) out.add(sb)
        val y = sa.replace("gi", "yi")
        if (y != sa) out.add(y)
        return out
    }

    /** Κλητική για ευχή: Νίκος → Νίκο, Αλέξανδρος → Αλέξανδρε, Κώστας → Κώστα, Δημήτρης → Δημήτρη. */
    fun vocative(name: String): String {
        if (name.length < 3) return name
        if (!isGreek(name)) {
            // greeklish (Giorgos → Giorgo)· ξένα ονόματα (George, Chris, Thomas Smith) μένουν όπως είναι
            val n = norm(name)
            val d = data
            if (d == null || n in d.foreign || d.index[n].isNullOrEmpty()) return name
            val l = name.lowercase(Locale.ROOT)
            return if (l.endsWith("os") || l.endsWith("as") || l.endsWith("is") || l.endsWith("es")) name.dropLast(1) else name
        }
        val lower = name.lowercase(EL)
        if (lower.endsWith("ος") || lower.endsWith("ός")) {
            val stem = name.dropLast(2)
            return stem + if (vowelGroupsAfterAccent(stem) >= 1) "ε" else "ο"
        }
        if (listOf("ας", "ης", "ής", "άς", "ούς", "ους", "ες").any { lower.endsWith(it) }) return name.dropLast(1)
        return name
    }

    /** Πόσες συλλαβές (ομάδες φωνηέντων) ακολουθούν τον τονισμένο φθόγγο στο θέμα. */
    private fun vowelGroupsAfterAccent(stem: String): Int {
        val nfd = Normalizer.normalize(stem.lowercase(EL), Normalizer.Form.NFD)
        val accent = nfd.indexOf('́')
        if (accent < 0) return 0
        val rest = MARKS.replace(nfd.substring(accent + 1), "")
        var groups = 0
        var inVowel = false
        for (ch in rest) {
            val v = "αεηιουω".indexOf(ch) >= 0
            if (v && !inVowel) groups++
            inVowel = v
        }
        return groups
    }
}
