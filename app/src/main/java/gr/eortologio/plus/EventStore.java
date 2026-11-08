package gr.eortologio.plus;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Πηγή όλων των γεγονότων: ενσωματωμένο εορτολόγιο (διαβάζεται ΜΙΑ φορά και μένει στη μνήμη)
 * + προσωπικά γεγονότα (SharedPreferences). Διατηρεί ευρετήριο ανά έτος για γρήγορες αναζητήσεις.
 */
public final class EventStore {
  static final String CAT_NAME = "Ονομαστική γιορτή";
  static final String CAT_HOLIDAY = "Εορτή / Αργία";
  static final String CAT_PERSONAL = "Προσωπικό";
  static final String CAT_BIRTHDAY = "Γενέθλια";
  static final String CAT_ANNIVERSARY = "Επέτειος";

  static final class Event {
    final String id, title, category;
    final int day, month, offset, year;
    final boolean movable, personal;
    /** Για ονομαστικές γιορτές: το επίσημο όνομα (κλειδί παραλλαγών). */
    final String canon;
    /** Προαιρετική σύνδεση με επαφή (για ευχές/αυτόματο SMS σε προσωπικές ημερομηνίες). */
    String phone, linkName;

    Event(String id, String title, String category, int day, int month, int offset, int year, boolean movable, boolean personal) {
      this.id = id; this.title = title; this.category = category; this.day = day; this.month = month;
      this.offset = offset; this.year = year; this.movable = movable; this.personal = personal;
      this.canon = CAT_NAME.equals(category) ? Names.canonicalOf(title) : title;
    }

    boolean isNameDay() { return CAT_NAME.equals(category); }

    LocalDate onYear(int y) {
      LocalDate easter = orthodoxEaster(y);
      if (movable) return easter.plusDays(offset);
      int d = (day == 29 && month == 2 && !Year.isLeap(y)) ? 28 : day;
      LocalDate date = LocalDate.of(y, month, d);
      // Αγ. Γεώργιος / Αγ. Μάρκος: αν πέφτουν πριν ή μέσα στη Μ. Εβδομάδα γιορτάζονται μετά το Πάσχα.
      if (isNameDay() && month == 4) {
        if (day == 23 && !easter.isBefore(date)) return easter.plusDays(1);
        if (day == 25 && !easter.plusDays(1).isBefore(date)) return easter.plusDays(2);
      }
      return date;
    }

    /** Η επόμενη (ή σημερινή) ημερομηνία από το {@code from}. */
    LocalDate next(LocalDate from) {
      LocalDate d = onYear(from.getYear());
      return d.isBefore(from) ? onYear(from.getYear() + 1) : d;
    }

    /** Ηλικία/χρόνια που συμπληρώνονται στην ημερομηνία {@code on} (0 αν δεν υπάρχει έτος). */
    int yearsAt(LocalDate on) { return year > 0 && on.getYear() > year ? on.getYear() - year : 0; }

    JSONObject json() throws JSONException {
      JSONObject x = new JSONObject();
      x.put("id", id); x.put("title", title); x.put("category", category);
      x.put("day", day); x.put("month", month); x.put("offset", offset);
      x.put("birthYear", year); x.put("movable", movable);
      if (phone != null) { x.put("phone", phone); x.put("linkName", linkName == null ? "" : linkName); }
      return x;
    }

    static Event parse(JSONObject o) {
      Event e = new Event(o.optString("id"), o.optString("title"), o.optString("category", CAT_PERSONAL),
          o.optInt("day", 1), o.optInt("month", 1), o.optInt("offset"), o.optInt("birthYear"), o.optBoolean("movable"), true);
      if (o.has("phone") && !o.optString("phone").isEmpty()) { e.phone = o.optString("phone"); e.linkName = o.optString("linkName", null); }
      return e;
    }
  }

  // ---------- κοινή cache (διεργασία) ----------
  private static List<Event> builtIn;
  private static final Map<Integer, Map<LocalDate, List<Event>>> yearIndex = new HashMap<>();
  private static List<Event> allCache;
  private static final Map<Integer, LocalDate> easterCache = new HashMap<>();

  private final SharedPreferences prefs;
  private final Context app;

  EventStore(Context ctx) {
    app = ctx.getApplicationContext();
    prefs = app.getSharedPreferences("events", Context.MODE_PRIVATE);
    Names.ensure(app);
  }

  // ---------- προσωπικά ----------
  List<Event> editable() {
    List<Event> r = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(prefs.getString("personal", "[]"));
      for (int i = 0; i < a.length(); i++) r.add(Event.parse(a.getJSONObject(i)));
    } catch (Exception ignored) { }
    return r;
  }

  String rawPersonal() { return prefs.getString("personal", "[]"); }

  void restoreRaw(String json) {
    prefs.edit().putString("personal", json).apply();
    invalidate();
  }

  void save(Event e) {
    List<Event> es = editable();
    boolean replaced = false;
    for (int i = 0; i < es.size(); i++) if (es.get(i).id.equals(e.id)) { es.set(i, e); replaced = true; break; }
    if (!replaced) es.add(e);
    write(es);
  }

  void delete(String id) {
    List<Event> es = editable();
    es.removeIf(e -> e.id.equals(id));
    write(es);
  }

  private void write(List<Event> es) {
    JSONArray a = new JSONArray();
    for (Event e : es) try { a.put(e.json()); } catch (Exception ignored) { }
    prefs.edit().putString("personal", a.toString()).apply();
    invalidate();
  }

  static synchronized void invalidate() { allCache = null; yearIndex.clear(); }

  // ---------- όλα ----------
  synchronized List<Event> all() {
    if (allCache != null) return allCache;
    if (builtIn == null) {
      List<Event> b = new ArrayList<>(400);
      load("TODAY.DAT", false, b);
      load("CUSTOM.INI", true, b);
      builtIn = Collections.unmodifiableList(b);
    }
    List<Event> out = new ArrayList<>(builtIn.size() + 32);
    out.addAll(builtIn);
    out.addAll(editable());
    allCache = Collections.unmodifiableList(out);
    return allCache;
  }

  /** Ευρετήριο ημερομηνία → γεγονότα για ένα έτος (υπολογίζεται μία φορά ανά έτος). */
  synchronized Map<LocalDate, List<Event>> index(int year) {
    Map<LocalDate, List<Event>> m = yearIndex.get(year);
    if (m != null) return m;
    m = new HashMap<>(400);
    for (Event e : all()) {
      try { m.computeIfAbsent(e.onYear(year), k -> new ArrayList<>(4)).add(e); } catch (Exception ignored) { }
    }
    yearIndex.put(year, m);
    return m;
  }

  List<Event> on(LocalDate day) {
    List<Event> l = index(day.getYear()).get(day);
    return l == null ? Collections.emptyList() : l;
  }

  /** Τα επόμενα γεγονότα (χωρίς το {@code from}) μέσα σε {@code days} ημέρες. Υπολογίζει κάθε ημερομηνία μία φορά. */
  List<Map.Entry<LocalDate, Event>> upcoming(LocalDate from, int days, boolean includeFrom) {
    List<Map.Entry<LocalDate, Event>> out = new ArrayList<>();
    for (int i = includeFrom ? 0 : 1; i <= days; i++) {
      LocalDate d = from.plusDays(i);
      for (Event e : on(d)) out.add(new AbstractMap.SimpleImmutableEntry<>(d, e));
    }
    return out;
  }

  /** Ονομαστικές γιορτές ανά κανονικό όνομα (π.χ. "Ιωάννης" → 7/1, 24/6, ...). */
  List<Event> nameDaysOf(String canon) {
    List<Event> r = new ArrayList<>(3);
    for (Event e : all()) if (e.isNameDay() && e.canon.equals(canon)) r.add(e);
    return r;
  }

  private void load(String filename, boolean personal, List<Event> out) {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(app.getAssets().open(filename), StandardCharsets.UTF_8))) {
      String line;
      int n = 0;
      Set<String> seen = new HashSet<>();
      while ((line = br.readLine()) != null) {
        n++;
        String s = line.trim();
        if (s.isEmpty() || s.startsWith(";")) continue;
        String[] p = s.split("\\s+", 2);
        if (p.length != 2) continue;
        String title = p[1].trim().replace("?", "").trim();
        boolean holiday = title.startsWith("\"");
        if (holiday) title = title.replace("\"", "").trim();
        if (title.isEmpty()) continue;
        try {
          boolean movable = p[0].startsWith("+") || p[0].startsWith("-");
          int day = 1, month = 1, offset = 0;
          if (movable) offset = Integer.parseInt(p[0]);
          else {
            String[] dm = p[0].split("/");
            day = Integer.parseInt(dm[0]);
            month = Integer.parseInt(dm[1]);
            MonthDay.of(month, day); // επικύρωση (επιτρέπει 29/2)
          }
          if (!seen.add(p[0] + "|" + Names.norm(title))) continue; // διπλοεγγραφές
          String cat = personal ? CAT_PERSONAL : holiday ? CAT_HOLIDAY : CAT_NAME;
          out.add(new Event(filename + ":" + n, title, cat, day, month, offset, 0, movable, personal));
        } catch (Exception ignored) { }
      }
    } catch (Exception ignored) { }
  }

  static long daysUntil(LocalDate from, LocalDate to) { return ChronoUnit.DAYS.between(from, to); }

  /** Ορθόδοξο Πάσχα (Meeus, Ιουλιανό → Γρηγοριανό, έγκυρο 1900–2099). */
  static LocalDate orthodoxEaster(int year) {
    synchronized (easterCache) {
      LocalDate c = easterCache.get(year);
      if (c != null) return c;
      int a = year % 4, b = year % 7, cc = year % 19, d = (19 * cc + 15) % 30, e = (2 * a + 4 * b - d + 34) % 7;
      c = LocalDate.of(year, 3, 22).plusDays(d + e + 13);
      easterCache.put(year, c);
      return c;
    }
  }
}
