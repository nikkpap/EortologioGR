package gr.eortologio.plus

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** OTA από GitHub Releases: έλεγχος έκδοσης, λήψη του υπογεγραμμένου APK και εγκατάσταση με ένα πάτημα. */
object OtaUpdater {
    // ⚠️ Μην αλλάξεις το repo: από εδώ παίρνουν ενημερώσεις όλες οι εγκατεστημένες εφαρμογές.
    private const val API = "https://api.github.com/repos/nikkpap/EortologioGR/releases/latest"
    const val APK_FILE = "eortologio-update.apk"

    data class Release(val tag: String, val apkUrl: String?, val page: String, val notes: String)

    /** Αυτόματος έλεγχος: το πολύ κάθε 6 ώρες και μόνο αν είναι ανοιχτή η ρύθμιση. */
    fun autoCheckDue(ctx: Context): Boolean {
        val sp = Prefs.sp(ctx)
        return sp.getBoolean("autoUpdate", true) && System.currentTimeMillis() - sp.getLong("lastOta", 0) >= 6 * 3600_000L
    }

    suspend fun fetchLatest(ctx: Context): Release = withContext(Dispatchers.IO) {
        Prefs.sp(ctx).edit().putLong("lastOta", System.currentTimeMillis()).apply()
        val j = JSONObject(get(API))
        val assets = j.optJSONArray("assets")
        var apk: String? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
        }
        val notes = j.optString("body")
            .replace(Regex("(?m)^(Co-Authored-By|Claude-Session|Signed-off-by):.*$|^.*claude\\.ai/code.*$"), "").trim()
        Release(j.optString("tag_name").replaceFirst(Regex("^[vV]"), ""), apk, j.optString("html_url"), notes)
    }

    /** Κατεβάζει το APK στην cache, αναφέροντας πρόοδο 0–100. */
    suspend fun download(ctx: Context, url: String, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val out = File(ctx.cacheDir, APK_FILE)
        try {
            val c = open(url)
            val total = c.contentLengthLong
            var done = 0L
            var last = -1
            c.inputStream.buffered().use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct != last) { last = pct; withContext(Dispatchers.Main) { progress(pct) } }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("Incomplete download")
            out
        } catch (e: Exception) {
            out.delete()
            throw e
        }
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun install(ctx: Context) {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://${ctx.packageName}.updates/apk"), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "EortologioGR-Android")
        c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
        if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
        return c
    }

    private fun get(url: String): String = open(url).inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (_: Exception) { "0" }

    /** Σύγκριση εκδόσεων "1.2.10" vs "1.2.9" αριθμητικά ανά τμήμα. */
    fun compare(x: String, y: String): Int {
        val a = x.split('.', '-')
        val b = y.split('.', '-')
        for (i in 0 until maxOf(a.size, b.size)) {
            val p = a.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val q = b.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            if (p != q) return p.compareTo(q)
        }
        return 0
    }
}
