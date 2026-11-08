package gr.eortologio.plus;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Δίνει στον Package Installer (μόνο ανάγνωση) το APK της ενημέρωσης από την cache. */
public class UpdateProvider extends ContentProvider {
  private File apk() { return new File(getContext().getCacheDir(), "eortologio-update.apk"); }

  @Override public boolean onCreate() { return true; }

  @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }

  @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!"apk".equals(uri.getLastPathSegment()) || !"r".equals(mode)) throw new FileNotFoundException();
    return ParcelFileDescriptor.open(apk(), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
    if (!"apk".equals(uri.getLastPathSegment())) return null;
    MatrixCursor c = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
    c.addRow(new Object[] { "EortologioGR-update.apk", apk().length() });
    return c;
  }

  @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
  @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
  @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
}
