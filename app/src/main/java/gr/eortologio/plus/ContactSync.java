package gr.eortologio.plus;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import java.time.LocalDate;
import java.util.*;

/** Διαβάζει τις επαφές του κινητού και βρίσκει ποιοι γιορτάζουν / έχουν γενέθλια. Μόνο ανάγνωση. */
final class ContactSync {
  static final class Person {
    final long id;
    final String displayName;
    String givenName;
    String phone;
    int phoneRank = -1; // 3 = κύριο, 2 = κινητό, 1 = άλλο
    final Set<String> canon = new LinkedHashSet<>();
    int bMonth, bDay, bYear;

    Person(long id, String displayName) { this.id = id; this.displayName = displayName; }

    /** Μικρό όνομα για την ευχή. */
    String callName() {
      if (givenName != null && !givenName.trim().isEmpty()) return Names.pretty(givenName.trim());
      String[] t = displayName.trim().split("\\s+");
      for (String s : t) if (!Names.match(s).isEmpty()) return Names.pretty(s);
      return Names.pretty(t.length > 0 ? t[0] : displayName);
    }

    boolean hasBirthday() { return bMonth > 0 && bDay > 0; }
  }

  /** Μια γιορτή/γενέθλια επαφής σε συγκεκριμένη ημερομηνία. */
  static final class Hit {
    final Person person; final LocalDate date; final boolean birthday; final String reason;
    Hit(Person p, LocalDate d, boolean birthday, String reason) { person = p; date = d; this.birthday = birthday; this.reason = reason; }
  }

  private static List<Person> cache;
  private static long cacheTime;

  private ContactSync() {}

  static boolean granted(Context ctx) {
    return ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
  }

  static boolean enabled(Context ctx) {
    return ctx.getSharedPreferences("settings", 0).getBoolean("contacts", true) && granted(ctx);
  }

  static synchronized void invalidate() { cache = null; }

  /** Επαφές με γιορτή ή γενέθλια. Cache 10 λεπτών ώστε να μη ξαναδιαβάζεται η βάση σε κάθε οθόνη. */
  static synchronized List<Person> load(Context ctx) {
    if (!enabled(ctx)) return Collections.emptyList();
    long now = System.currentTimeMillis();
    if (cache != null && now - cacheTime < 10 * 60_000L) return cache;
    Names.ensure(ctx);
    Map<Long, Person> map = new HashMap<>();
    ContentResolver cr = ctx.getContentResolver();
    String[] proj = {
        ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME, ContactsContract.Data.MIMETYPE,
        ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.IS_PRIMARY };
    String sel = ContactsContract.Data.MIMETYPE + " IN (?,?,?)";
    String[] args = { CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
        CommonDataKinds.Event.CONTENT_ITEM_TYPE };
    try (Cursor c = cr.query(ContactsContract.Data.CONTENT_URI, proj, sel, args, null)) {
      if (c != null) {
        while (c.moveToNext()) {
          long id = c.getLong(0);
          String dn = c.getString(1);
          if (dn == null) continue;
          Person p = map.get(id);
          if (p == null) { p = new Person(id, dn); map.put(id, p); }
          String mime = c.getString(2);
          String d1 = c.getString(3);
          if (CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mime)) {
            // DATA2 = GIVEN_NAME
            String given = c.getString(4);
            if (given != null && !given.trim().isEmpty()) p.givenName = given.trim();
          } else if (CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mime)) {
            if (d1 != null) {
              int type = c.isNull(4) ? 0 : c.getInt(4);
              int rank = c.getInt(5) != 0 ? 3 : type == CommonDataKinds.Phone.TYPE_MOBILE || isMobile(d1) ? 2 : 1;
              if (rank > p.phoneRank) { p.phone = d1; p.phoneRank = rank; }
            }
          } else if (CommonDataKinds.Event.CONTENT_ITEM_TYPE.equals(mime)) {
            int type = c.isNull(4) ? 0 : c.getInt(4);
            if (type == CommonDataKinds.Event.TYPE_BIRTHDAY) parseBirthday(p, d1);
          }
        }
      }
    } catch (Exception ignored) { }

    List<Person> out = new ArrayList<>();
    for (Person p : map.values()) {
      if (p.givenName != null) p.canon.addAll(Names.match(p.givenName));
      if (p.canon.isEmpty()) {
        // Χωρίς δομημένο όνομα: δοκίμασε κάθε λέξη ("Νίκος Π.", "Παπαδόπουλος Νίκος", "Giorgos")
        for (String t : p.displayName.split("[\\s,.()\\-]+")) {
          Set<String> m = Names.match(t);
          if (!m.isEmpty()) { p.canon.addAll(m); if (p.givenName == null) p.givenName = t; break; }
        }
      }
      if (!p.canon.isEmpty() || p.hasBirthday()) out.add(p);
    }
    out.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
    cache = out;
    cacheTime = now;
    return out;
  }

  /** Κινητό; Ελλάδα: 69…· εξωτερικό: δεκτό (δεν ξέρουμε). */
  static boolean isMobile(String phone) {
    String n = Wishes.intl(phone);
    return n.startsWith("30") ? n.startsWith("306") : n.length() >= 8;
  }

  /** "1985-03-12", "--03-12", "12/03/1985" */
  private static void parseBirthday(Person p, String s) {
    if (s == null) return;
    try {
      s = s.trim();
      if (s.startsWith("--")) {
        p.bMonth = Integer.parseInt(s.substring(2, 4)); p.bDay = Integer.parseInt(s.substring(5, 7));
      } else if (s.length() >= 10 && s.charAt(4) == '-') {
        p.bYear = Integer.parseInt(s.substring(0, 4)); p.bMonth = Integer.parseInt(s.substring(5, 7)); p.bDay = Integer.parseInt(s.substring(8, 10));
      } else if (s.contains("/")) {
        String[] x = s.split("/");
        p.bDay = Integer.parseInt(x[0].trim()); p.bMonth = Integer.parseInt(x[1].trim());
        if (x.length > 2) p.bYear = Integer.parseInt(x[2].trim().substring(0, 4));
      }
      if (p.bMonth < 1 || p.bMonth > 12 || p.bDay < 1 || p.bDay > 31) { p.bMonth = p.bDay = 0; }
      if (p.bYear < 1900) p.bYear = 0;
    } catch (Exception e) { p.bMonth = p.bDay = p.bYear = 0; }
  }

  /** Ποιες επαφές γιορτάζουν ή έχουν γενέθλια από {@code from} έως {@code from+days}. Ταξινομημένα κατά ημερομηνία. */
  static List<Hit> hits(Context ctx, EventStore store, LocalDate from, int days) {
    List<Person> people = load(ctx);
    // canonical → ημερομηνίες εντός παραθύρου (μία προσπέλαση του ευρετηρίου ανά ημέρα)
    Map<String, List<LocalDate>> nameDates = new HashMap<>();
    for (int i = 0; i <= days; i++) {
      LocalDate d = from.plusDays(i);
      for (EventStore.Event e : store.on(d))
        if (e.isNameDay()) nameDates.computeIfAbsent(e.canon, k -> new ArrayList<>(2)).add(d);
    }
    LocalDate end = from.plusDays(days);
    List<Hit> out = new ArrayList<>();
    for (Person p : people) {
      Set<LocalDate> added = new HashSet<>();
      for (String c : p.canon) {
        List<LocalDate> ds = nameDates.get(c);
        if (ds != null) for (LocalDate d : ds) if (added.add(d)) out.add(new Hit(p, d, false, c));
      }
      if (p.hasBirthday()) {
        for (int y = from.getYear(); y <= end.getYear(); y++) {
          try {
            int dd = (p.bMonth == 2 && p.bDay == 29 && !java.time.Year.isLeap(y)) ? 28 : p.bDay;
            LocalDate b = LocalDate.of(y, p.bMonth, dd);
            if (!b.isBefore(from) && !b.isAfter(end)) out.add(new Hit(p, b, true, "Γενέθλια"));
          } catch (Exception ignored) { }
        }
      }
    }
    // Προσωπικές ημερομηνίες συνδεδεμένες με επαφή (π.χ. γενέθλια που δεν υπάρχουν στις επαφές)
    Set<String> have = new HashSet<>();
    for (Hit h : out) if (h.person.phone != null) have.add(Wishes.intl(h.person.phone) + "|" + h.date + "|" + h.birthday);
    for (EventStore.Event e : store.editable()) {
      if (e.phone == null) continue;
      boolean bday = EventStore.CAT_BIRTHDAY.equals(e.category);
      for (int i = 0; i <= days; i++) {
        LocalDate d = from.plusDays(i);
        if (!e.onYear(d.getYear()).equals(d)) continue;
        if (!have.add(Wishes.intl(e.phone) + "|" + d + "|" + bday)) continue;
        String name = e.linkName != null && !e.linkName.isEmpty() ? e.linkName : e.title;
        Person p = new Person(-Math.abs((long) (e.id + name).hashCode()) - 1, name);
        p.phone = e.phone;
        String first = name.trim().split("\\s+")[0];
        p.givenName = first;
        if (bday) { p.bMonth = e.month; p.bDay = e.day; p.bYear = e.year; }
        out.add(new Hit(p, d, bday, bday ? "Γενέθλια" : e.title));
      }
    }
    // Χωρίς διπλοεγγραφές (ίδια επαφή σε 2 λογαριασμούς / SIM): ίδιος αριθμός ή όνομα + ημερομηνία + τύπος
    Set<String> uniq = new HashSet<>();
    out.removeIf(h -> !uniq.add((h.person.phone != null ? Wishes.intl(h.person.phone) : Names.norm(h.person.displayName)) + "|" + h.date + "|" + h.birthday));
    out.sort((a, b) -> {
      int c = a.date.compareTo(b.date);
      return c != 0 ? c : a.person.displayName.compareToIgnoreCase(b.person.displayName);
    });
    return out;
  }
}
