package gr.eortologio.plus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Widget αρχικής οθόνης: «Σήμερα γιορτάζουν» + επαφές που γιορτάζουν + αύριο. Ανανεώνεται στο ημερήσιο alarm, τα μεσάνυχτα και στο άνοιγμα της εφαρμογής. */
public class TodayWidget extends AppWidgetProvider {
  private static final Locale EL = new Locale("el", "GR");

  @Override public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
    PendingResult pr = goAsync();
    Context app = ctx.getApplicationContext();
    new Thread(() -> {
      try { updateAll(app); } finally { pr.finish(); }
      ReminderReceiver.schedule(app); // βάζει και το alarm μεσάνυχτων τώρα που υπάρχει widget
    }).start();
  }

  static boolean hasWidgets(Context ctx) {
    try {
      AppWidgetManager m = AppWidgetManager.getInstance(ctx);
      return m != null && m.getAppWidgetIds(new ComponentName(ctx, TodayWidget.class)).length > 0;
    } catch (Exception e) { return false; }
  }

  static RemoteViews build(Context ctx) {
    EventStore store = new EventStore(ctx);
    LocalDate today = LocalDate.now();
    RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_today);
    String d = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", EL));
    v.setTextViewText(R.id.w_date, ("ΕΟΡΤΟΛΟΓΙΟ+ · " + d).toUpperCase(EL));

    List<String> names = new ArrayList<>(), holidays = new ArrayList<>();
    for (EventStore.Event e : store.on(today)) {
      if (e.isNameDay()) { if (!names.contains(e.canon)) names.add(e.canon); }
      else if (!e.personal) holidays.add(e.title);
    }
    v.setTextViewText(R.id.w_title, names.isEmpty() ? "📅 Σήμερα" : "🎉 Σήμερα γιορτάζουν");
    StringBuilder body = new StringBuilder(String.join(", ", names));
    for (String h : holidays) body.append(body.length() > 0 ? "\n" : "").append("📌 ").append(h);
    v.setTextViewText(R.id.w_names, body.length() > 0 ? body : "Καμία γιορτή σήμερα");

    List<String> people = new ArrayList<>();
    for (ContactSync.Hit h : ContactSync.hits(ctx, store, today, 0)) people.add(h.person.callName() + (h.birthday ? " 🎂" : ""));
    if (people.isEmpty()) v.setViewVisibility(R.id.w_contacts, View.GONE);
    else {
      v.setViewVisibility(R.id.w_contacts, View.VISIBLE);
      v.setTextViewText(R.id.w_contacts, "👥 " + String.join(", ", people));
    }

    List<String> tomorrow = new ArrayList<>();
    for (EventStore.Event e : store.on(today.plusDays(1))) if (e.isNameDay() && !tomorrow.contains(e.canon)) tomorrow.add(e.canon);
    v.setTextViewText(R.id.w_next, tomorrow.isEmpty() ? "" : "Αύριο: " + String.join(", ", tomorrow));

    Intent open = new Intent(ctx, MainActivity.class).putExtra("page", people.isEmpty() ? "today" : "contacts")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 7, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    return v;
  }

  /** Ανανεώνει όλα τα widgets (αν δεν υπάρχει κανένα, δεν κάνει τίποτα). */
  static void updateAll(Context ctx) {
    try {
      AppWidgetManager m = AppWidgetManager.getInstance(ctx);
      if (m == null) return;
      int[] ids = m.getAppWidgetIds(new ComponentName(ctx, TodayWidget.class));
      if (ids.length == 0) return;
      m.updateAppWidget(ids, build(ctx));
    } catch (Exception ignored) { }
  }
}
