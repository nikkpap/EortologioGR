package gr.eortologio.plus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/** OTA από GitHub Releases: έλεγχος έκδοσης, λήψη του υπογεγραμμένου APK και εγκατάσταση με ένα πάτημα. */
final class OtaUpdater {
  private static final String API = "https://api.github.com/repos/nikkpap/EortologioGR/releases/latest";
  private static volatile boolean running;

  private OtaUpdater() {}

  /** manual=false: σιωπηλός έλεγχος το πολύ κάθε 6 ώρες, εμφανίζει διάλογο μόνο αν υπάρχει νεότερη έκδοση. */
  static void check(Activity a, boolean manual) {
    SharedPreferences sp = a.getSharedPreferences("settings", 0);
    long now = System.currentTimeMillis();
    if (!manual && (!sp.getBoolean("autoUpdate", true) || now - sp.getLong("lastOta", 0) < 6 * 3600_000L)) return;
    if (running) return;
    running = true;
    sp.edit().putLong("lastOta", now).apply();
    new Thread(() -> {
      try {
        JSONObject j = new JSONObject(get(API));
        String tag = j.optString("tag_name").replaceFirst("^[vV]", "");
        String mine = currentVersion(a);
        String apkUrl = null;
        JSONArray assets = j.optJSONArray("assets");
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
          JSONObject as = assets.getJSONObject(i);
          if (as.optString("name").endsWith(".apk")) { apkUrl = as.optString("browser_download_url"); break; }
        }
        final String url = apkUrl, page = j.optString("html_url"),
            notes = j.optString("body").replaceAll("(?m)^(Co-Authored-By|Claude-Session|Signed-off-by):.*$|^.*claude\\.ai/code.*$", "").trim();
        if (compare(tag, mine) <= 0) {
          if (manual) ui(a, () -> new AlertDialog.Builder(a).setTitle("Είσαι ενημερωμένος ✓")
              .setMessage("Έχεις την τελευταία έκδοση (" + mine + ").").setPositiveButton("OK", null).show());
          return;
        }
        ui(a, () -> new AlertDialog.Builder(a).setTitle("Νέα έκδοση " + tag)
            .setMessage("Εγκατεστημένη: " + mine + "\n\n" + (notes.length() > 600 ? notes.substring(0, 600) + "…" : notes))
            .setNegativeButton("Αργότερα", null)
            .setNeutralButton("GitHub", (d, w) -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(page))))
            .setPositiveButton(url != null ? "Ενημέρωση" : "Άνοιγμα", (d, w) -> {
              if (url != null) download(a, url); else a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(page)));
            }).show());
      } catch (Exception e) {
        if (manual) ui(a, () -> new AlertDialog.Builder(a).setTitle("Έλεγχος ενημέρωσης")
            .setMessage("Δεν ήταν δυνατός ο έλεγχος. Έλεγξε τη σύνδεση στο internet.\n(" + e.getMessage() + ")")
            .setPositiveButton("OK", null).show());
      } finally { running = false; }
    }).start();
  }

  private static void download(Activity a, String url) {
    if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
      new AlertDialog.Builder(a).setTitle("Μία φορά μόνο")
          .setMessage("Για να εγκαθίστανται οι ενημερώσεις, επίτρεψε στο Εορτολόγιο+ την «Εγκατάσταση άγνωστων εφαρμογών» και πάτα ξανά «Ενημέρωση».")
          .setPositiveButton("Ρυθμίσεις", (d, w) -> a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()))))
          .setNegativeButton("Άκυρο", null).show();
      return;
    }
    ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(100);
    TextView t = new TextView(a);
    t.setText("Λήψη…");
    LinearLayout l = new LinearLayout(a);
    l.setOrientation(LinearLayout.VERTICAL);
    int pad = (int) (24 * a.getResources().getDisplayMetrics().density);
    l.setPadding(pad, pad / 2, pad, 0);
    l.addView(t); l.addView(bar);
    AlertDialog dlg = new AlertDialog.Builder(a).setTitle("Ενημέρωση").setView(l).setCancelable(false).show();
    new Thread(() -> {
      File out = new File(a.getCacheDir(), "eortologio-update.apk");
      try {
        HttpURLConnection c = open(url);
        long total = c.getContentLengthLong(), done = 0;
        try (InputStream in = new BufferedInputStream(c.getInputStream()); OutputStream os = new FileOutputStream(out)) {
          byte[] buf = new byte[64 * 1024];
          int n, last = -1;
          while ((n = in.read(buf)) != -1) {
            os.write(buf, 0, n);
            done += n;
            int pct = total > 0 ? (int) (done * 100 / total) : 0;
            if (pct != last) { last = pct; final int p = pct; ui(a, () -> { bar.setProgress(p); t.setText("Λήψη… " + p + "%"); }); }
          }
        }
        ui(a, () -> {
          dlg.dismiss();
          Intent i = new Intent(Intent.ACTION_VIEW)
              .setDataAndType(Uri.parse("content://" + a.getPackageName() + ".updates/apk"), "application/vnd.android.package-archive")
              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
          a.startActivity(i);
        });
      } catch (Exception e) {
        out.delete();
        ui(a, () -> { dlg.dismiss(); new AlertDialog.Builder(a).setMessage("Η λήψη απέτυχε: " + e.getMessage()).setPositiveButton("OK", null).show(); });
      }
    }).start();
  }

  private static HttpURLConnection open(String url) throws IOException {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(30000);
    c.setInstanceFollowRedirects(true);
    c.setRequestProperty("User-Agent", "EortologioGR-Android");
    c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
    if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
    return c;
  }

  private static String get(String url) throws IOException {
    HttpURLConnection c = open(url);
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    try (InputStream s = c.getInputStream()) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = s.read(buf)) != -1) o.write(buf, 0, n);
    }
    return o.toString("UTF-8");
  }

  static String currentVersion(Activity a) {
    try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; } catch (Exception e) { return "0"; }
  }

  /** Σύγκριση εκδόσεων "1.2.10" vs "1.2.9" αριθμητικά ανά τμήμα. */
  static int compare(String x, String y) {
    String[] a = x.split("[.\\-]"), b = y.split("[.\\-]");
    for (int i = 0; i < Math.max(a.length, b.length); i++) {
      int p = i < a.length ? num(a[i]) : 0, q = i < b.length ? num(b[i]) : 0;
      if (p != q) return Integer.compare(p, q);
    }
    return 0;
  }

  private static int num(String s) {
    try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return 0; }
  }

  private static void ui(Activity a, Runnable r) {
    a.runOnUiThread(() -> { if (!a.isFinishing() && !a.isDestroyed()) r.run(); });
  }
}
