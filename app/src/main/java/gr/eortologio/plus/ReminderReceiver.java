package gr.eortologio.plus;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Ημερήσια ειδοποίηση «Σήμερα γιορτάζουν» με κουμπιά Αναβολής (snooze) και «Ευχές».
 * Χωρίς background service: ένα alarm την ημέρα (setAndAllowWhileIdle → λειτουργεί και σε Doze
 * χωρίς άδεια ακριβούς ώρας και με ελάχιστη κατανάλωση μπαταρίας).
 */
public class ReminderReceiver extends BroadcastReceiver {
  public static final String CHANNEL = "eortologio_today";
  static final String ACTION_DAILY = "gr.eortologio.plus.DAILY";
  static final String ACTION_SNOOZE = "gr.eortologio.plus.SNOOZE";
  static final String ACTION_SHOW = "gr.eortologio.plus.SHOW";
  static final String EXTRA_MIN = "minutes";
  static final String EXTRA_DAY = "day";
  static final int NOTIF_ID = 41;
  private static final int RC_DAILY = 41, RC_SNOOZE = 42;

  static SharedPreferences sp(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }

  /** Προγραμματίζει την επόμενη ημερήσια ειδοποίηση (ακυρώνει την προηγούμενη). */
  public static void schedule(Context ctx) {
    AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    if (am == null) return;
    PendingIntent p = pending(ctx, ACTION_DAILY, RC_DAILY, null);
    am.cancel(p);
    SharedPreferences sp = sp(ctx);
    if (!sp.getBoolean("notifications", true)) return;
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime next = now.toLocalDate().atTime(sp.getInt("hour", 9), sp.getInt("minute", 0));
    if (!next.isAfter(now)) next = next.plusDays(1);
    long when = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
  }

  private static PendingIntent pending(Context ctx, String action, int rc, Intent extras) {
    Intent i = new Intent(ctx, ReminderReceiver.class).setAction(action);
    if (extras != null) i.putExtras(extras);
    return PendingIntent.getBroadcast(ctx, rc, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  @Override public void onReceive(Context ctx, Intent intent) {
    String action = intent.getAction() == null ? ACTION_DAILY : intent.getAction();
    PendingResult pr = goAsync();
    Context app = ctx.getApplicationContext();
    new Thread(() -> {
      PowerManager.WakeLock wl = null;
      try {
        PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        if (pm != null) { wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Eortologio:notify"); wl.acquire(15_000); }
        switch (action) {
          case ACTION_SNOOZE: snooze(app, intent.getIntExtra(EXTRA_MIN, 60)); break;
          case ACTION_SHOW: {
            String day = intent.getStringExtra(EXTRA_DAY);
            if (day == null || day.equals(LocalDate.now().toString())) post(app, true);
            break;
          }
          default:
            try { post(app, false); } finally { schedule(app); }
        }
      } catch (Exception ignored) {
      } finally {
        if (wl != null && wl.isHeld()) wl.release();
        pr.finish();
      }
    }).start();
  }

  private static void snooze(Context ctx, int minutes) {
    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm != null) nm.cancel(NOTIF_ID);
    AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    if (am == null) return;
    Intent extra = new Intent().putExtra(EXTRA_DAY, LocalDate.now().toString());
    long when = System.currentTimeMillis() + minutes * 60_000L;
    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(ctx, ACTION_SHOW, RC_SNOOZE, extra));
  }

  static void ensureChannel(Context ctx) {
    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
      NotificationChannel ch = new NotificationChannel(CHANNEL, "Σήμερα γιορτάζουν", NotificationManager.IMPORTANCE_DEFAULT);
      ch.setDescription("Ημερήσια ειδοποίηση για γιορτές, γενέθλια επαφών και προσωπικές ημερομηνίες");
      nm.createNotificationChannel(ch);
    }
  }

  /** Συνθέτει και εμφανίζει την ειδοποίηση της ημέρας. Επιστρέφει false αν δεν υπάρχει τίποτα να ειπωθεί. */
  static boolean post(Context ctx, boolean snoozed) {
    SharedPreferences sp = sp(ctx);
    if (!sp.getBoolean("notifications", true)) return false;
    if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm == null) return false;

    EventStore store = new EventStore(ctx);
    LocalDate today = LocalDate.now();
    int lead = sp.getInt("lead", 1);
    boolean names = sp.getBoolean("notifyNames", true), onlyContacts = sp.getBoolean("onlyContacts", false);

    List<String> todayNames = new ArrayList<>(), lines = new ArrayList<>();
    for (EventStore.Event e : store.on(today)) {
      if (e.isNameDay()) { if (!todayNames.contains(e.canon)) todayNames.add(e.canon); }
      else if (!e.personal) lines.add("📅 " + e.title);
    }
    List<ContactSync.Hit> hits = ContactSync.hits(ctx, store, today, 0);
    List<String> people = new ArrayList<>();
    for (ContactSync.Hit h : hits) people.add(h.person.displayName + (h.birthday ? " 🎂" : ""));

    for (EventStore.Event e : store.editable()) {
      LocalDate n = e.next(today);
      long d = EventStore.daysUntil(today, n);
      if (d == 0) {
        int y = e.yearsAt(n);
        lines.add(0, "⭐ " + e.title + (y > 0 ? " (" + y + " χρόνια)" : ""));
      } else if (lead > 0 && d == lead) lines.add("⏳ Σε " + d + (d == 1 ? " ημέρα: " : " ημέρες: ") + e.title);
    }

    boolean show = !lines.isEmpty() || !people.isEmpty() || (names && !onlyContacts && !todayNames.isEmpty());
    if (!show) return false;

    StringBuilder big = new StringBuilder();
    if (!people.isEmpty()) big.append("👥 Από τις επαφές σου: ").append(String.join(", ", people)).append('\n');
    if (names && !todayNames.isEmpty()) big.append("🎉 Γιορτάζουν: ").append(String.join(", ", todayNames)).append('\n');
    for (String l : lines) big.append(l).append('\n');

    String title;
    String text;
    if (!people.isEmpty()) {
      title = people.size() == 1 ? "Γιορτάζει ο/η " + hits.get(0).person.callName() + " 🎉" : people.size() + " επαφές σου γιορτάζουν σήμερα 🎉";
      text = String.join(", ", people);
    } else if (names && !todayNames.isEmpty()) {
      title = "Σήμερα γιορτάζουν";
      text = String.join(", ", todayNames);
    } else {
      title = "Εορτολόγιο+";
      text = lines.get(0);
    }

    ensureChannel(ctx);
    Intent open = new Intent(ctx, MainActivity.class).putExtra("page", people.isEmpty() ? "today" : "contacts")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    int s1 = sp.getInt("snooze1", 60), s2 = sp.getInt("snooze2", 180);
    Notification.Builder b = new Notification.Builder(ctx, CHANNEL)
        .setSmallIcon(R.drawable.ic_event)
        .setColor(0xff6657dd)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(new Notification.BigTextStyle().bigText(big.toString().trim()))
        .setAutoCancel(true)
        .setOnlyAlertOnce(snoozed)
        .setCategory(Notification.CATEGORY_REMINDER)
        .setContentIntent(pi);
    if (!people.isEmpty())
      b.addAction(new Notification.Action.Builder(null, "💬 Ευχές", pi).build());
    b.addAction(new Notification.Action.Builder(null, "⏰ " + label(s1), snoozeIntent(ctx, s1, 1)).build());
    b.addAction(new Notification.Action.Builder(null, "⏰ " + label(s2), snoozeIntent(ctx, s2, 2)).build());
    nm.notify(NOTIF_ID, b.build());
    return true;
  }

  private static PendingIntent snoozeIntent(Context ctx, int minutes, int slot) {
    Intent i = new Intent(ctx, ReminderReceiver.class).setAction(ACTION_SNOOZE).putExtra(EXTRA_MIN, minutes);
    return PendingIntent.getBroadcast(ctx, 100 + slot, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  static String label(int minutes) {
    if (minutes < 60) return minutes + "′";
    if (minutes % 60 == 0) return (minutes / 60) + (minutes == 60 ? " ώρα" : " ώρες");
    return (minutes / 60) + "ω " + (minutes % 60) + "′";
  }
}
