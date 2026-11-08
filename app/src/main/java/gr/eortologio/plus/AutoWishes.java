package gr.eortologio.plus;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;
import java.time.LocalDate;
import java.util.*;

/**
 * Αυτόματη αποστολή «Χρόνια πολλά» με SMS σε όσες επαφές γιορτάζουν / έχουν γενέθλια σήμερα.
 * Ασφάλειες: μόνο αν το ενεργοποιήσει ο χρήστης, ένα SMS ανά αριθμό ανά ημέρα, όριο ανά ημέρα, ιστορικό.
 */
final class AutoWishes {
  static final String TAG = "EortologioAutoSms";
  static final String MODE_NAMEDAY = "nameday", MODE_BIRTHDAY = "birthday", MODE_BOTH = "both";
  static final int DAILY_LIMIT = 40;
  private static final int LOG_MAX = 200;

  private AutoWishes() {}

  static boolean enabled(Context c) {
    return ReminderReceiver.sp(c).getBoolean("autoSms", false) && hasPermission(c);
  }

  static boolean hasPermission(Context c) {
    return c.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
  }

  static String modeLabel(String m) {
    if (MODE_NAMEDAY.equals(m)) return "Μόνο γιορτές";
    if (MODE_BIRTHDAY.equals(m)) return "Μόνο γενέθλια";
    return "Γιορτές και γενέθλια";
  }

  // ---------- Εξαιρέσεις: επαφές που ΔΕΝ παίρνουν ποτέ αυτόματο SMS ----------
  static Set<String> blocked(Context c) {
    return new HashSet<>(ReminderReceiver.sp(c).getStringSet("smsBlocked", Collections.emptySet()));
  }

  static boolean isBlocked(Context c, ContactSync.Person p) { return blocked(c).contains(blockKey(p)); }

  /** Κλειδί επαφής: ο αριθμός (σταθερός ακόμα κι αν αλλάξει το id της επαφής), αλλιώς το όνομα. */
  static String blockKey(ContactSync.Person p) {
    return p.phone != null ? Wishes.intl(p.phone) : "name:" + p.displayName;
  }

  static void setBlocked(Context c, ContactSync.Person p, boolean block) {
    Set<String> b = blocked(c);
    if (block) b.add(blockKey(p)); else b.remove(blockKey(p));
    ReminderReceiver.sp(c).edit().putStringSet("smsBlocked", b).apply();
  }

  static boolean confirmFirst(Context c) { return ReminderReceiver.sp(c).getBoolean("autoSmsConfirm", true); }

  private static Set<String> doneToday(SharedPreferences sp, String key) {
    Set<String> done = new HashSet<>();
    if (key.equals(sp.getString("autoSmsDay", ""))) done.addAll(sp.getStringSet("autoSmsDone", Collections.emptySet()));
    return done;
  }

  static boolean cancelledToday(Context c) {
    return LocalDate.now().toString().equals(ReminderReceiver.sp(c).getString("autoSmsCancelDay", ""));
  }

  static void cancelToday(Context c) {
    ReminderReceiver.sp(c).edit().putString("autoSmsCancelDay", LocalDate.now().toString()).apply();
  }

  /** Ποιοι θα πάρουν σήμερα αυτόματο SMS (μετά από φίλτρα: τύπος, εξαιρέσεις, ήδη σταλμένα, όριο). */
  static List<ContactSync.Hit> pending(Context ctx) {
    List<ContactSync.Hit> out = new ArrayList<>();
    if (!enabled(ctx) || !ContactSync.enabled(ctx)) return out;
    SharedPreferences sp = ReminderReceiver.sp(ctx);
    String mode = sp.getString("autoSmsMode", MODE_BOTH);
    Set<String> done = doneToday(sp, LocalDate.now().toString());
    Set<String> block = blocked(ctx);
    Set<String> seen = new HashSet<>(done);
    ContactSync.invalidate();
    for (ContactSync.Hit h : ContactSync.hits(ctx, new EventStore(ctx), LocalDate.now(), 0)) {
      if (h.birthday && MODE_NAMEDAY.equals(mode)) continue;
      if (!h.birthday && MODE_BIRTHDAY.equals(mode)) continue;
      String num = h.person.phone;
      if (num == null || num.replaceAll("\\D", "").length() < 8) continue;
      if (block.contains(blockKey(h.person))) continue;
      if (!seen.add(Wishes.intl(num))) continue;
      out.add(h);
      if (done.size() + out.size() >= DAILY_LIMIT) break;
    }
    return out;
  }

  /** Στέλνει τις σημερινές ευχές. Επιστρέφει τα ονόματα όσων στάλθηκαν τώρα. */
  static synchronized List<String> sendToday(Context ctx) {
    List<String> sentNow = new ArrayList<>();
    if (!enabled(ctx) || !ContactSync.enabled(ctx) || cancelledToday(ctx)) return sentNow;
    SharedPreferences sp = ReminderReceiver.sp(ctx);
    LocalDate today = LocalDate.now();
    String key = today.toString();
    // Ποιοι αριθμοί πήραν ήδη SMS σήμερα (για να μη σταλεί δεύτερο, π.χ. μετά από επανεκκίνηση)
    Set<String> done = doneToday(sp, key);

    SmsManager sms;
    try {
      sms = Build.VERSION.SDK_INT >= 31 ? ctx.getSystemService(SmsManager.class) : SmsManager.getDefault();
    } catch (Exception e) { Log.w(TAG, "no SmsManager", e); return sentNow; }
    if (sms == null) return sentNow;

    List<ContactSync.Hit> hits = pending(ctx);
    List<String> log = new ArrayList<>(Arrays.asList(sp.getString("autoSmsLog", "").split("\n")));
    log.removeIf(String::isEmpty);
    for (ContactSync.Hit h : hits) {
      if (done.size() >= DAILY_LIMIT) { Log.w(TAG, "daily limit reached"); break; }
      String num = h.person.phone;
      String norm = Wishes.intl(num);
      if (!done.add(norm)) continue; // ήδη στάλθηκε σήμερα (ή διπλή επαφή με ίδιο αριθμό)
      String msg = Wishes.message(ctx, h);
      boolean ok;
      try {
        ArrayList<String> parts = sms.divideMessage(msg);
        if (parts.size() > 1) sms.sendMultipartTextMessage(num, null, parts, null, null);
        else sms.sendTextMessage(num, null, msg, null, null);
        ok = true;
        sentNow.add(h.person.displayName);
        Log.i(TAG, "sent to " + h.person.displayName);
      } catch (Exception e) {
        ok = false;
        Log.w(TAG, "send failed " + h.person.displayName, e);
      }
      log.add(0, key + " · " + (ok ? "✓ " : "✗ ") + h.person.displayName + " (" + (h.birthday ? "γενέθλια" : "γιορτή") + ")");
    }
    while (log.size() > LOG_MAX) log.remove(log.size() - 1);
    sp.edit().putString("autoSmsDay", key).putStringSet("autoSmsDone", done)
        .putString("autoSmsLog", String.join("\n", log)).apply();
    return sentNow;
  }

  static String history(Context c) {
    String h = ReminderReceiver.sp(c).getString("autoSmsLog", "");
    return h.isEmpty() ? "Δεν έχουν σταλεί ακόμα αυτόματες ευχές." : h;
  }
}
