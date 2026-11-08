package gr.eortologio.plus;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** Ευχές «Χρόνια πολλά»: μήνυμα από πρότυπο και αποστολή μέσω SMS / WhatsApp / Viber / άλλης εφαρμογής. */
final class Wishes {
  static final String DEFAULT_NAMEDAY = "Χρόνια πολλά {name}! 🎉 Να χαίρεσαι το όνομά σου, με υγεία, αγάπη και ευτυχία!";
  static final String DEFAULT_BIRTHDAY = "Χρόνια πολλά {name}! 🎂 Να τα εκατοστήσεις, με υγεία και χαρά!";

  private Wishes() {}

  static String message(Context ctx, ContactSync.Hit h) {
    android.content.SharedPreferences p = ctx.getSharedPreferences("settings", 0);
    String tpl = h.birthday ? p.getString("tplBirthday", DEFAULT_BIRTHDAY) : p.getString("tplNameday", DEFAULT_NAMEDAY);
    return tpl.replace("{name}", Names.vocative(h.person.callName()));
  }

  static void sms(Activity a, ContactSync.Hit h) {
    String phone = h.person.phone == null ? "" : h.person.phone;
    Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)));
    i.putExtra("sms_body", message(a, h));
    start(a, i, "Δεν βρέθηκε εφαρμογή μηνυμάτων");
  }

  static void whatsapp(Activity a, ContactSync.Hit h) {
    String num = intl(h.person.phone);
    if (num.isEmpty()) { share(a, h, "com.whatsapp"); return; }
    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + num + "?text=" + Uri.encode(message(a, h))));
    start(a, i, "Το WhatsApp δεν είναι εγκατεστημένο");
  }

  static void viber(Activity a, ContactSync.Hit h) { share(a, h, "com.viber.voip"); }

  static void call(Activity a, ContactSync.Hit h) {
    if (h.person.phone == null) { toast(a, "Η επαφή δεν έχει τηλέφωνο"); return; }
    start(a, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(h.person.phone))), "Δεν βρέθηκε εφαρμογή κλήσεων");
  }

  /** Κοινοποίηση κειμένου· αν δοθεί πακέτο, πάει κατευθείαν σε αυτή την εφαρμογή. */
  static void share(Activity a, ContactSync.Hit h, String pkg) {
    Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message(a, h));
    if (pkg != null) {
      i.setPackage(pkg);
      try { a.startActivity(i); return; } catch (ActivityNotFoundException e) { i.setPackage(null); }
    }
    a.startActivity(Intent.createChooser(i, "Στείλε ευχές στον/στην " + h.person.callName()));
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
