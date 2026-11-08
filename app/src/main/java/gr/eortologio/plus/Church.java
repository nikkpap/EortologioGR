package gr.eortologio.plus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.*;

/** Εκκλησιαστικό ημερολόγιο (νηστείες) και επίσημες αργίες / τριήμερα στην Ελλάδα. */
final class Church {
  private Church() {}

  /** Τύπος ημέρας για νηστεία. */
  enum Fast { NONE, FREE, FAST, FISH, DAIRY, STRICT }

  static final class FastInfo {
    final Fast type; final String label;
    FastInfo(Fast t, String l) { type = t; label = l; }
  }

  private static boolean between(LocalDate d, LocalDate a, LocalDate b) { return !d.isBefore(a) && !d.isAfter(b); }

  /** Πληροφορία νηστείας για μια ημέρα (απλοποιημένο τυπικό, για καθημερινή ενημέρωση). */
  static FastInfo fast(LocalDate d) {
    LocalDate e = EventStore.orthodoxEaster(d.getYear());
    long k = EventStore.daysUntil(e, d); // ημέρες από το Πάσχα (αρνητικό = πριν)
    MonthDay md = MonthDay.from(d);
    DayOfWeek dow = d.getDayOfWeek();
    boolean wedFri = dow == DayOfWeek.WEDNESDAY || dow == DayOfWeek.FRIDAY;

    // --- Περίοδοι κατάλυσης (αρτύσιμο όλη την εβδομάδα) ---
    if (between(d, LocalDate.of(d.getYear(), 12, 25), LocalDate.of(d.getYear(), 12, 31))
        || between(d, LocalDate.of(d.getYear(), 1, 1), LocalDate.of(d.getYear(), 1, 4)))
      return new FastInfo(Fast.FREE, "Κατάλυση εις πάντα (Δωδεκαήμερο)");
    if (k >= 0 && k <= 6) return new FastInfo(Fast.FREE, "Κατάλυση εις πάντα (Διακαινήσιμος)");
    if (k >= 49 && k <= 55) return new FastInfo(Fast.FREE, "Κατάλυση εις πάντα (εβδομάδα Πεντηκοστής)");
    if (k >= -70 && k <= -64) return new FastInfo(Fast.FREE, "Κατάλυση εις πάντα (Τελώνου και Φαρισαίου)");

    // --- Αυστηρές μονοήμερες νηστείες ---
    if (md.equals(MonthDay.of(1, 5))) return new FastInfo(Fast.STRICT, "Αυστηρή νηστεία (παραμονή Θεοφανείων)");
    if (md.equals(MonthDay.of(8, 29))) return new FastInfo(Fast.STRICT, "Αυστηρή νηστεία (Αποτομή Τιμίου Προδρόμου)");
    if (md.equals(MonthDay.of(9, 14))) return new FastInfo(Fast.STRICT, "Αυστηρή νηστεία (Ύψωση Τιμίου Σταυρού)");

    // --- Μεγάλη Σαρακοστή & Μεγάλη Εβδομάδα ---
    if (k >= -55 && k <= -49) return new FastInfo(Fast.DAIRY, "Τυροφάγου: χωρίς κρέας (γαλακτοκομικά, αυγά, ψάρι επιτρέπονται)");
    if (k >= -48 && k <= -1) {
      if (k == -7 || md.equals(MonthDay.of(3, 25))) return new FastInfo(Fast.FISH, "Μ. Σαρακοστή · κατάλυση ιχθύος");
      if (k == -2) return new FastInfo(Fast.STRICT, "Μεγάλη Παρασκευή · αυστηρή νηστεία");
      if (k >= -6) return new FastInfo(Fast.STRICT, "Μεγάλη Εβδομάδα · αυστηρή νηστεία");
      return new FastInfo(Fast.FAST, "Μεγάλη Σαρακοστή · νηστεία");
    }

    // --- Των Αγίων Αποστόλων: από Δευτέρα μετά των Αγίων Πάντων έως 28/6 ---
    LocalDate apStart = e.plusDays(57), apEnd = LocalDate.of(d.getYear(), 6, 28);
    if (!apStart.isAfter(apEnd) && between(d, apStart, apEnd))
      return wedFri ? new FastInfo(Fast.FAST, "Νηστεία Αγίων Αποστόλων") : new FastInfo(Fast.FISH, "Νηστεία Αγίων Αποστόλων · κατάλυση ιχθύος");

    // --- Δεκαπενταύγουστος ---
    if (between(d, LocalDate.of(d.getYear(), 8, 1), LocalDate.of(d.getYear(), 8, 14))) {
      if (md.equals(MonthDay.of(8, 6))) return new FastInfo(Fast.FISH, "Μεταμόρφωση · κατάλυση ιχθύος");
      return new FastInfo(Fast.FAST, "Νηστεία Δεκαπενταύγουστου");
    }

    // --- Σαρανταήμερο Χριστουγέννων ---
    if (between(d, LocalDate.of(d.getYear(), 11, 15), LocalDate.of(d.getYear(), 12, 24))) {
      boolean fishOk = !d.isAfter(LocalDate.of(d.getYear(), 12, 17)) && !wedFri;
      if (md.equals(MonthDay.of(11, 21))) return new FastInfo(Fast.FISH, "Εισόδια · κατάλυση ιχθύος");
      return fishOk ? new FastInfo(Fast.FISH, "Νηστεία Χριστουγέννων · κατάλυση ιχθύος")
          : new FastInfo(Fast.FAST, "Νηστεία Χριστουγέννων");
    }

    // --- Τετάρτη & Παρασκευή ---
    if (wedFri) {
      // μεγάλες γιορτές σε Τετάρτη/Παρασκευή: κατάλυση ιχθύος
      if (md.equals(MonthDay.of(2, 2)) || md.equals(MonthDay.of(3, 25)) || md.equals(MonthDay.of(6, 24))
          || md.equals(MonthDay.of(6, 29)) || md.equals(MonthDay.of(8, 15)) || md.equals(MonthDay.of(9, 8)) || k == 39)
        return new FastInfo(Fast.FISH, "Τετάρτη/Παρασκευή εορτής · κατάλυση ιχθύος");
      return new FastInfo(Fast.FAST, dow == DayOfWeek.WEDNESDAY ? "Τετάρτη · νηστεία" : "Παρασκευή · νηστεία");
    }
    return new FastInfo(Fast.NONE, "");
  }

  static String fastIcon(Fast f) {
    switch (f) {
      case STRICT: return "🍞";
      case FAST: return "🥗";
      case FISH: return "🐟";
      case DAIRY: return "🧀";
      case FREE: return "🍖";
      default: return "";
    }
  }

  // ---------------- Αργίες ----------------

  /** Επίσημες αργίες (όνομα) για ένα έτος. */
  static Map<LocalDate, String> holidays(int y) {
    LocalDate e = EventStore.orthodoxEaster(y);
    Map<LocalDate, String> m = new TreeMap<>();
    m.put(LocalDate.of(y, 1, 1), "Πρωτοχρονιά");
    m.put(LocalDate.of(y, 1, 6), "Θεοφάνεια");
    m.put(e.minusDays(48), "Καθαρά Δευτέρα");
    m.put(LocalDate.of(y, 3, 25), "25η Μαρτίου");
    m.put(e.minusDays(2), "Μεγάλη Παρασκευή");
    m.put(e.plusDays(1), "Δευτέρα του Πάσχα");
    // Πρωτομαγιά: αν πέφτει Μ. Εβδομάδα ή Διακαινήσιμο, μετατίθεται συνήθως — δείχνουμε την κανονική
    m.put(LocalDate.of(y, 5, 1), "Πρωτομαγιά");
    m.put(e.plusDays(50), "Αγίου Πνεύματος");
    m.put(LocalDate.of(y, 8, 15), "Δεκαπενταύγουστος");
    m.put(LocalDate.of(y, 10, 28), "28η Οκτωβρίου");
    m.put(LocalDate.of(y, 12, 25), "Χριστούγεννα");
    m.put(LocalDate.of(y, 12, 26), "Σύναξη της Θεοτόκου");
    return m;
  }

  static String holiday(LocalDate d) { return holidays(d.getYear()).get(d); }

  static boolean dayOff(LocalDate d) {
    return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY || holiday(d) != null;
  }

  /** Μακριά σαββατοκύριακα (≥3 ημέρες ρεπό), με «γέφυρα» αν μένει μία εργάσιμη ανάμεσα. */
  static final class LongWeekend {
    final LocalDate start, end; final String name; final boolean bridge;
    LongWeekend(LocalDate s, LocalDate e, String n, boolean b) { start = s; end = e; name = n; bridge = b; }
    long days() { return EventStore.daysUntil(start, end) + 1; }
  }

  static List<LongWeekend> longWeekends(LocalDate from, int horizonDays) {
    List<LongWeekend> out = new ArrayList<>();
    Map<LocalDate, String> hol = new TreeMap<>(holidays(from.getYear()));
    hol.putAll(holidays(from.getYear() + 1));
    for (Map.Entry<LocalDate, String> h : hol.entrySet()) {
      LocalDate d = h.getKey();
      if (d.isBefore(from.minusDays(4)) || d.isAfter(from.plusDays(horizonDays))) continue;
      DayOfWeek w = d.getDayOfWeek();
      if (w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY) continue;
      LocalDate s = d, e = d;
      boolean bridge = false;
      if (w == DayOfWeek.TUESDAY) { s = d.minusDays(1); bridge = true; }
      if (w == DayOfWeek.THURSDAY) { e = d.plusDays(1); bridge = true; }
      while (dayOff(s.minusDays(1))) s = s.minusDays(1);
      while (dayOff(e.plusDays(1))) e = e.plusDays(1);
      if (EventStore.daysUntil(s, e) + 1 < 3 || e.isBefore(from)) continue;
      boolean dup = false;
      for (LongWeekend lw : out) if (!lw.start.isAfter(e) && !lw.end.isBefore(s)) { dup = true; break; }
      if (!dup) out.add(new LongWeekend(s, e, h.getValue(), bridge));
    }
    return out;
  }
}
