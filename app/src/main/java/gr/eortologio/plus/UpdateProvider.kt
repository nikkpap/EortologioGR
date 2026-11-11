package gr.eortologio.plus

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Δίνει στον Package Installer (μόνο ανάγνωση) το APK της ενημέρωσης από την cache. */
class UpdateProvider : ContentProvider() {
    private fun apk() = File(context!!.cacheDir, OtaUpdater.APK_FILE)

    override fun onCreate() = true

    override fun getType(uri: Uri) = "application/vnd.android.package-archive"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri.lastPathSegment != "apk" || mode != "r") throw FileNotFoundException()
        return ParcelFileDescriptor.open(apk(), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        if (uri.lastPathSegment != "apk") return null
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>("EortologioGR-update.apk", apk().length()))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, where: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, where: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
}
