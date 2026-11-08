package gr.eortologio.plus;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** Ευχές «Χρόνια πολλά»: μήνυμα από πρότυπο και αποστολή μέσω SMS / WhatsApp / Viber / άλλης εφαρμογής. */
final class Wishes {
  static final String DEFAULT_NAMEDAY_EL = String.join("\n",
      "Χρόνια πολλά {name}! 🎉 Να χαίρεσαι το όνομά σου, με υγεία, αγάπη και ευτυχία!",
      "{name}, χρόνια πολλά για τη γιορτή σου! 🥳 Ό,τι επιθυμείς να γίνεται πραγματικότητα!",
      "Χρόνια σου πολλά {name}! 🌟 Υγεία, χαρά και πολλές επιτυχίες!",
      "Να χαίρεσαι το όνομά σου {name}! 🎊 Χρόνια πολλά και καλά!",
      "Χρόνια πολλά {name}! 🎈 Να περάσεις υπέροχα σήμερα με τους δικούς σου ανθρώπους!",
      "{name} χρόνια πολλά! 🍀 Πάντα γερός/γερή και ευτυχισμένος/η!");
  static final String DEFAULT_BIRTHDAY_EL = String.join("\n",
      "Χρόνια πολλά {name}! 🎂 Να τα εκατοστήσεις, με υγεία και χαρά!",
      "Happy birthday {name}! 🥳 Χρόνια πολλά, να είσαι πάντα καλά!",
      "{name}, χρόνια πολλά για τα γενέθλιά σου! 🎉 Να έχεις μια χρονιά γεμάτη χαμόγελα!",
      "Χρόνια πολλά {name}! 🎈 Υγεία, αγάπη και ό,τι ποθεί η καρδιά σου!",
      "Να τα εκατοστήσεις {name}! 🎂 Μια όμορφη χρονιά σου εύχομαι!");
  static final String DEFAULT_CLOSE_EL = String.join("\n",
      "Χρόνια πολλά {name} μου! ❤️ Να είσαι πάντα γερός/γερή και χαμογελαστός/ή, σε αγαπώ πολύ!",
      "{name} μου χρόνια πολλά! 🤗 Ό,τι πιο όμορφο για σένα, σήμερα και πάντα!",
      "Χρόνια πολλά αγαπημένε/η μου {name}! 💐 Να έχεις υγεία, αγάπη και πολλή χαρά!");

  static final String DEFAULT_NAMEDAY_EN = String.join("\n",
      "Happy name day {name}! 🎉 Wishing you health, love and happiness!",
      "{name}, happy name day! 🥳 May all your wishes come true!",
      "Χρόνια πολλά {name}! 🌟 Health, joy and lots of success!",
      "Happy name day {name}! 🎈 Have a wonderful day with your loved ones!");
  static final String DEFAULT_BIRTHDAY_EN = String.join("\n",
      "Happy birthday {name}! 🎂 Wishing you health and joy!",
      "Happy birthday {name}! 🥳 Have an amazing year ahead!",
      "{name}, many happy returns! 🎉 May your year be full of smiles!");
  static final String DEFAULT_CLOSE_EN = String.join("\n",
      "Happy day my dear {name}! ❤️ Love you lots, stay happy and healthy!",
      "{name}, wishing you all the best today and always! 🤗");

  static String defNameday() { return T.en() ? DEFAULT_NAMEDAY_EN : DEFAULT_NAMEDAY_EL; }
  static String defBirthday() { return T.en() ? DEFAULT_BIRTHDAY_EN : DEFAULT_BIRTHDAY_EL; }
  static String defClose() { return T.en() ? DEFAULT_CLOSE_EN : DEFAULT_CLOSE_EL; }

  private Wishes() {}

  /** Μηνύματα (ένα ανά γραμμή) για τον τύπο. */
  static java.util.List<String> templates(Context ctx, String key, String def) {
    String raw = ctx.getSharedPreferences("settings", 0).getString(key, def);
    java.util.List<String> out = new java.util.ArrayList<>();
    for (String l : raw.split("\n")) if (!l.trim().isEmpty()) out.add(l.trim());
    if (out.isEmpty()) for (String l : def.split("\n")) out.add(l.trim());
    return out;
  }

  static boolean isClose(Context c, ContactSync.Person p) {
    return c.getSharedPreferences("settings", 0).getStringSet("closeSet", java.util.Collections.emptySet()).contains(AutoWishes.blockKey(p));
  }

  static void setClose(Context c, ContactSync.Person p, boolean close) {
    android.content.SharedPreferences sp = c.getSharedPreferences("settings", 0);
    java.util.Set<String> s = new java.util.HashSet<>(sp.getStringSet("closeSet", java.util.Collections.emptySet()));
    if (close) s.add(AutoWishes.blockKey(p)); else s.remove(AutoWishes.blockKey(p));
    sp.edit().putStringSet("closeSet", s).apply();
  }

  /**
   * Επιλέγει μήνυμα: οικογένεια/φίλοι → ζεστά μηνύματα, αλλιώς γιορτής ή γενεθλίων.
   * Η επιλογή αλλάζει ανά άτομο και ανά χρονιά (δεν παίρνουν όλοι το ίδιο, ούτε ο ίδιος το ίδιο κάθε χρόνο).
   */
  static String message(Context ctx, ContactSync.Hit h) {
    java.util.List<String> list = isClose(ctx, h.person) ? templates(ctx, "tplClose", defClose())
        : h.birthday ? templates(ctx, "tplBirthday", defBirthday()) : templates(ctx, "tplNameday", defNameday());
    int idx = Math.floorMod((AutoWishes.blockKey(h.person) + "|" + h.date.getYear() + "|" + h.birthday).hashCode(), list.size());
    return list.get(idx).replace("{name}", Names.vocative(h.person.callName()));
  }

  static void sms(Activity a, ContactSync.Hit h) {
    String phone = h.person.phone == null ? "" : h.person.phone;
    Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)));
    i.putExtra("sms_body", message(a, h));
    start(a, i, T.t("Δεν βρέθηκε εφαρμογή μηνυμάτων"));
  }

  static void whatsapp(Activity a, ContactSync.Hit h) {
    String num = intl(h.person.phone);
    if (num.isEmpty()) { share(a, h, "com.whatsapp"); return; }
    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + num + "?text=" + Uri.encode(message(a, h))));
    start(a, i, T.t("Το WhatsApp δεν είναι εγκατεστημένο"));
  }

  static void viber(Activity a, ContactSync.Hit h) { share(a, h, "com.viber.voip"); }

  static void call(Activity a, ContactSync.Hit h) {
    if (h.person.phone == null) { toast(a, T.t("Η επαφή δεν έχει τηλέφωνο")); return; }
    start(a, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(h.person.phone))), T.t("Δεν βρέθηκε εφαρμογή κλήσεων"));
  }

  /** Κοινοποίηση κειμένου· αν δοθεί πακέτο, πάει κατευθείαν σε αυτή την εφαρμογή. */
  static void share(Activity a, ContactSync.Hit h, String pkg) {
    Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message(a, h));
    if (pkg != null) {
      i.setPackage(pkg);
      try { a.startActivity(i); return; } catch (ActivityNotFoundException e) { i.setPackage(null); }
    }
    a.startActivity(Intent.createChooser(i, T.t("Στείλε ευχές στον/στην ") + h.person.callName()));
  }

  /** Διεθνής μορφή χωρίς '+', με προεπιλογή Ελλάδα (69xxxxxxxx → 3069xxxxxxxx). */
  static String intl(String phone) {
    if (phone == null) return "";
    String d = phone.replaceAll("[^0-9+]", "");
    if (d.startsWith("+")) return d.substring(1);
    if (d.startsWith("00")) return d.substring(2);
    if (d.length() == 10 && (d.startsWith("69") || d.startsWith("2"))) return "30" + d;
    return d;
  }

  private static void start(Activity a, Intent i, String err) {
    try { a.startActivity(i); } catch (ActivityNotFoundException e) { toast(a, err); }
  }

  private static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_LONG).show(); }
}
