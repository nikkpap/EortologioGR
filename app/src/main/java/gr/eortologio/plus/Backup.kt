package gr.eortologio.plus

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Date

/** Αντίγραφο ασφαλείας: «Δικά μου» + ρυθμίσεις (μηνύματα, εξαιρέσεις, οικογένεια) σε αρχείο JSON. */
object Backup {
    /** Ρυθμίσεις που ΔΕΝ μεταφέρονται (εσωτερικές / ανά συσκευή). */
    private val SKIP = setOf("lastOta", "autoSmsDay", "autoSmsDone", "autoSmsCancelDay", "askedNotif", "guideDone", "askedSms")

    fun export(ctx: Context): String {
        val root = JSONObject()
        root.put("app", "EortologioGR")
        root.put("version", 1)
        root.put("created", Date().toString())
        root.put("personal", JSONArray(EventStore.get(ctx).rawPersonal()))
        val st = JSONObject()
        for ((k, v) in Prefs.sp(ctx).all) {
            if (k in SKIP) continue
            val item = JSONObject()
            when (v) {
                is Boolean -> item.put("b", v)
                is Int -> item.put("i", v)
                is Long -> item.put("l", v)
                is String -> item.put("s", v)
                is Set<*> -> item.put("set", JSONArray(v))
                else -> continue
            }
            st.put(k, item)
        }
        root.put("settings", st)
        return root.toString(2)
    }

    fun write(ctx: Context, uri: Uri) {
        val os = ctx.contentResolver.openOutputStream(uri, "wt") ?: throw IOException(T.t("Δεν άνοιξε το αρχείο"))
        os.use { it.write(export(ctx).toByteArray(Charsets.UTF_8)) }
    }

    /** Επιστρέφει πόσες προσωπικές ημερομηνίες επαναφέρθηκαν. */
    fun restore(ctx: Context, uri: Uri): Int {
        val text = (ctx.contentResolver.openInputStream(uri) ?: throw IOException(T.t("Δεν άνοιξε το αρχείο")))
            .use { it.readBytes().toString(Charsets.UTF_8) }
        val root = JSONObject(text)
        if (root.optString("app") != "EortologioGR") throw IOException(T.t("Δεν είναι αντίγραφο του Εορτολόγιο+"))
        val personal = root.optJSONArray("personal")
        if (personal != null) EventStore.get(ctx).restoreRaw(personal.toString())
        root.optJSONObject("settings")?.let { st ->
            val ed = Prefs.sp(ctx).edit()
            for (k in st.keys()) {
                if (k in SKIP) continue
                val v = st.getJSONObject(k)
                when {
                    v.has("b") -> ed.putBoolean(k, v.getBoolean("b"))
                    v.has("i") -> ed.putInt(k, v.getInt("i"))
                    v.has("l") -> ed.putLong(k, v.getLong("l"))
                    v.has("s") -> ed.putString(k, v.getString("s"))
                    v.has("set") -> {
                        val a = v.getJSONArray("set")
                        ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                }
            }
            ed.apply()
        }
        ContactSync.invalidate()
        return personal?.length() ?: 0
    }
}
