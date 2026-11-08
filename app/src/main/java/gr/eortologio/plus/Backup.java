package gr.eortologio.plus;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Αντίγραφο ασφαλείας: «Δικά μου» + ρυθμίσεις (μηνύματα, εξαιρέσεις, οικογένεια) σε αρχείο JSON. */
final class Backup {
  /** Ρυθμίσεις που ΔΕΝ μεταφέρονται (εσωτερικές / ανά συσκευή). */
  private static final Set<String> SKIP = new HashSet<>(Arrays.asList(
      "lastOta", "autoSmsDay", "autoSmsDone", "autoSmsCancelDay", "askedNotif", "guideDone"));

  private Backup() {}

  static String export(Context ctx) throws Exception {
    JSONObject root = new JSONObject();
    root.put("app", "EortologioGR");
    root.put("version", 1);
    root.put("created", new Date().toString());
    root.put("personal", new JSONArray(new EventStore(ctx).rawPersonal()));
    JSONObject st = new JSONObject();
    for (Map.Entry<String, ?> e : ReminderReceiver.sp(ctx).getAll().entrySet()) {
      if (SKIP.contains(e.getKey())) continue;
      Object v = e.getValue();
      JSONObject item = new JSONObject();
      if (v instanceof Boolean) item.put("b", v);
      else if (v instanceof Integer) item.put("i", v);
      else if (v instanceof Long) item.put("l", v);
      else if (v instanceof String) item.put("s", v);
      else if (v instanceof Set) item.put("set", new JSONArray((Set<?>) v));
      else continue;
      st.put(e.getKey(), item);
    }
    root.put("settings", st);
    return root.toString(2);
  }

  static void write(Context ctx, Uri uri) throws Exception {
    try (OutputStream os = ctx.getContentResolver().openOutputStream(uri, "wt")) {
      if (os == null) throw new IOException(T.t("Δεν άνοιξε το αρχείο"));
      os.write(export(ctx).getBytes(StandardCharsets.UTF_8));
    }
  }

  /** Επιστρέφει πόσες προσωπικές ημερομηνίες επαναφέρθηκαν. */
  static int restore(Context ctx, Uri uri) throws Exception {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(ctx.getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
      String l;
      while ((l = r.readLine()) != null) sb.append(l).append('\n');
    }
    JSONObject root = new JSONObject(sb.toString());
    if (!"EortologioGR".equals(root.optString("app"))) throw new IOException(T.t("Δεν είναι αντίγραφο του Εορτολόγιο+"));
    JSONArray personal = root.optJSONArray("personal");
    if (personal != null) new EventStore(ctx).restoreRaw(personal.toString());
    JSONObject st = root.optJSONObject("settings");
    if (st != null) {
      SharedPreferences.Editor ed = ReminderReceiver.sp(ctx).edit();
      for (Iterator<String> it = st.keys(); it.hasNext(); ) {
        String k = it.next();
        if (SKIP.contains(k)) continue;
        JSONObject v = st.getJSONObject(k);
        if (v.has("b")) ed.putBoolean(k, v.getBoolean("b"));
        else if (v.has("i")) ed.putInt(k, v.getInt("i"));
        else if (v.has("l")) ed.putLong(k, v.getLong("l"));
        else if (v.has("s")) ed.putString(k, v.getString("s"));
        else if (v.has("set")) {
          Set<String> set = new HashSet<>();
          JSONArray a = v.getJSONArray("set");
          for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
          ed.putStringSet(k, set);
        }
      }
      ed.apply();
    }
    ContactSync.invalidate();
    return personal == null ? 0 : personal.length();
  }
}
