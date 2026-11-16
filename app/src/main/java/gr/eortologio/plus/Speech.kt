package gr.eortologio.plus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Φωνητική ανακοίνωση με τη μηχανή ομιλίας (TTS) του κινητού:
 * «Καλημέρα! Σήμερα γιορτάζουν ο Γιώργος και η Μαρία. Μην ξεχάσεις να τους ευχηθείς χρόνια πολλά!»
 * Δεν χρειάζεται internet (αν είναι εγκατεστημένη η ελληνική φωνή) και δεν μιλάει όταν το κινητό είναι σε σίγαση/δόνηση.
 */
object Speech {
    fun enabled(c: Context) = Prefs.sp(c).getBoolean("voice", false)

    /** Επαναλήψεις της ειδοποίησης (0, 1 ή 2), κάθε μία ώρα μετά την προηγούμενη. */
    fun repeats(c: Context) = Prefs.sp(c).getInt("repeat", 0)

    private fun locale() = if (T.en()) Locale.ENGLISH else Names.EL

    /** «ο Γιώργος», «η Μαρία» (ή σκέτο όνομα αν δεν ξέρουμε το φύλο). */
    private fun withArticle(name: String, g: Names.Gender?): String = when {
        T.en() -> name
        g == Names.Gender.M -> "ο $name"
        g == Names.Gender.F -> "η $name"
        else -> name
    }

    private fun joinAnd(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + (if (T.en()) " and " else " και ") + items.last()
    }

    /** Το κείμενο που θα ειπωθεί σήμερα (null = τίποτα για σήμερα). */
    fun compose(ctx: Context): String? {
        val sp = Prefs.sp(ctx)
        val store = EventStore.get(ctx)
        val today = LocalDate.now()
        val hits = ContactSync.hits(ctx, store, today, 0)
        val named = hits.filter { !it.birthday }.map { withArticle(it.person.callName(), Wishes.gender(ctx, it.person)) }.distinct().take(6)
        val bdays = hits.filter { it.birthday }.map { withArticle(it.person.callName(), Wishes.gender(ctx, it.person)) }.distinct().take(6)
        val personal = store.editable().filter { it.next(today) == today }.map { it.title }.take(3)
        val sb = StringBuilder(if (T.en()) "Good morning! " else greeting())
        if (named.isNotEmpty()) {
            sb.append(if (T.en()) "Today is the name day of " + joinAnd(named) + ". "
            else (if (named.size == 1) "Σήμερα γιορτάζει " else "Σήμερα γιορτάζουν ") + joinAnd(named) + ". ")
        }
        if (bdays.isNotEmpty()) {
            sb.append(if (T.en()) "Birthday today: " + joinAnd(bdays) + ". "
            else (if (bdays.size == 1) "Έχει γενέθλια " else "Έχουν γενέθλια ") + joinAnd(bdays) + ". ")
        }
        for (p in personal) sb.append(if (T.en()) "Today: $p. " else "Σήμερα: $p. ")
        if (named.isEmpty() && bdays.isEmpty() && personal.isEmpty()) {
            if (!sp.getBoolean("notifyNames", true) || sp.getBoolean("onlyContacts", false)) return null
            // Κανείς από τις επαφές: λέμε τα πιο γνωστά ονόματα της ημέρας (σε καθημερινή μορφή: Γιώργος, όχι Γεώργιος)
            val names = store.on(today).filter { it.isNameDay }.map { it.canon }.distinct()
                .map { c -> if (T.en()) Names.latinPretty(c) else Names.variantsOf(c).firstOrNull()?.takeIf { it.length > 2 } ?: c }
                .distinct().take(5)
            if (names.isEmpty()) return null
            sb.append(if (T.en()) "Today's name days: " + joinAnd(names) + ". Happy name day!"
            else "Σήμερα γιορτάζουν όσοι λέγονται " + joinAnd(names) + ". Χρόνια πολλά!")
            return sb.toString()
        }
        sb.append(
            if (T.en()) "Don't forget to send your wishes!"
            else if (named.size + bdays.size == 1) {
                val g = hits.firstOrNull()?.let { Wishes.gender(ctx, it.person) }
                when (g) {
                    Names.Gender.M -> "Μην ξεχάσεις να του ευχηθείς χρόνια πολλά!"
                    Names.Gender.F -> "Μην ξεχάσεις να της ευχηθείς χρόνια πολλά!"
                    else -> "Μην ξεχάσεις να ευχηθείς χρόνια πολλά!"
                }
            } else if (named.size + bdays.size > 1) "Μην ξεχάσεις να τους ευχηθείς χρόνια πολλά!"
            else "",
        )
        return sb.toString().trim()
    }

    private fun greeting(): String {
        val h = java.time.LocalTime.now().hour
        return when {
            h < 12 -> "Καλημέρα! "
            h < 18 -> "Γεια σου! "
            else -> "Καλησπέρα! "
        }
    }

    /** Μιλάει μόνο αν το κινητό ΔΕΝ είναι σε σίγαση/δόνηση. */
    fun ringerAllows(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return am.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    /** Ανακοίνωση της ημέρας, αν είναι ανοιχτή η ρύθμιση. Τρέχει σε background thread (μπλοκάρει έως ~40″). */
    fun announceIfOn(ctx: Context) {
        if (!enabled(ctx) || !ringerAllows(ctx)) return
        val text = try { compose(ctx) } catch (_: Exception) { null } ?: return
        speakBlocking(ctx, text)
    }

    private fun connect(ctx: Context): TextToSpeech? {
        val latch = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        val tts = TextToSpeech(ctx.applicationContext) { s -> status = s; latch.countDown() }
        if (!latch.await(8, TimeUnit.SECONDS) || status != TextToSpeech.SUCCESS) {
            try { tts.shutdown() } catch (_: Exception) {}
            return null
        }
        return tts
    }

    /** Διαθέσιμες φωνές της γλώσσας (όνομα → ετικέτα). Κενό = λείπει η φωνή / μηχανή. Background thread. */
    fun voices(ctx: Context): List<Pair<String, String>> {
        val tts = connect(ctx) ?: return emptyList()
        return try {
            val loc = locale()
            usable(tts, loc).mapIndexed { i, v ->
                v.name to (T.t("Φωνή ") + (i + 1) + (if (looksFemale(v)) " 👩" else "") +
                    (if (v.isNetworkConnectionRequired) T.t(" · θέλει internet") else T.t(" · χωρίς internet")) +
                    (if (v.quality >= Voice.QUALITY_HIGH) T.t(" · υψηλή ποιότητα") else ""))
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            tts.shutdown()
        }
    }

    /** Είναι εγκατεστημένη η φωνή της γλώσσας της εφαρμογής; Background thread. */
    fun languageReady(ctx: Context): Boolean {
        val tts = connect(ctx) ?: return false
        return try { tts.isLanguageAvailable(locale()) >= TextToSpeech.LANG_AVAILABLE } catch (_: Exception) { false } finally { tts.shutdown() }
    }

    private fun usable(tts: TextToSpeech, loc: Locale): List<Voice> =
        (tts.voices ?: emptySet()).filter {
            it.locale.language == loc.language &&
                !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }.sortedWith(compareBy<Voice>({ it.isNetworkConnectionRequired }, { -it.quality }, { it.name }))

    /** Οι μηχανές δεν δηλώνουν φύλο· κάποιες το έχουν στο όνομα (female, -f-, #female, «x-…f…»). */
    private fun looksFemale(v: Voice): Boolean {
        val n = v.name.lowercase(Locale.ROOT)
        return "female" in n || Regex("[-_#]f(\\d|-|_|$)").containsMatchIn(n) || "-x-vfz" in n || "-x-ela" in n
    }

    /** Μιλάει το κείμενο και περιμένει να τελειώσει. ΜΗΝ το καλείς από το main thread. */
    fun speakBlocking(ctx: Context, text: String): Boolean {
        val tts = connect(ctx) ?: return false
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
        try {
            val loc = locale()
            if (tts.isLanguageAvailable(loc) < TextToSpeech.LANG_AVAILABLE) return false
            tts.language = loc
            val want = Prefs.sp(ctx).getString("voiceName", "") ?: ""
            val list = usable(tts, loc)
            (list.firstOrNull { it.name == want } ?: list.firstOrNull { looksFemale(it) && !it.isNetworkConnectionRequired }
                ?: list.firstOrNull { !it.isNetworkConnectionRequired } ?: list.firstOrNull())?.let { tts.voice = it }
            tts.setPitch(1.08f)      // λίγο πιο ψηλά: πιο φιλικός, «γυναικείος» τόνος
            tts.setSpeechRate(0.92f) // λίγο πιο αργά: πιο ανθρώπινο
            tts.setAudioAttributes(attrs)
            val done = CountDownLatch(1)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) = done.countDown()
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) = done.countDown()
                override fun onError(id: String?, errorCode: Int) = done.countDown()
            })
            am?.requestAudioFocus(focus)
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "eortologio") != TextToSpeech.SUCCESS) return false
            return done.await(40, TimeUnit.SECONDS)
        } catch (_: Exception) {
            return false
        } finally {
            try { am?.abandonAudioFocusRequest(focus) } catch (_: Exception) {}
            try { tts.stop(); tts.shutdown() } catch (_: Exception) {}
        }
    }
}
