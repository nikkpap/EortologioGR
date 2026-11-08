package gr.eortologio.plus;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Ονόματα: κανονικοποίηση, παραλλαγές (Γιώργος → Γεώργιος), greeklish, κλητική. */
final class Names {
  private static final Pattern MARKS = Pattern.compile("\\p{M}+");
  /** normalized variant → canonical names (ένα υποκοριστικό μπορεί να ανήκει σε >1 όνομα). */
  private static Map<String, Set<String>> index;
  /** canonical → εμφανιζόμενες παραλλαγές (μόνο ελληνικές). */
  private static Map<String, List<String>> variants;
  /** normalized → σωστή γραφή (π.χ. "μιχαλησ" → "Μιχάλης") για όμορφη εμφάνιση ονομάτων γραμμένων με κεφαλαία. */
  private static Map<String, String> display;
  /** canonical → λατινικά κλειδιά (George, John…) — για να μη σαρώνεται όλο το ευρετήριο σε κάθε αναζήτηση. */
  private static Map<String, List<String>> latinOf;

  private Names() {}

  /** Πεζά, χωρίς τόνους/διαλυτικά, ς→σ. */
  static String norm(String s) {
    if (s == null) return "";
    String n = Normalizer.normalize(s.trim().toLowerCase(new Locale("el", "GR")), Normalizer.Form.NFD);
    n = MARKS.matcher(n).replaceAll("");
    return n.replace('ς', 'σ');
  }

  static synchronized void ensure(Context ctx) {
    if (index != null) return;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(ctx.getAssets().open("NAMES.DAT"), StandardCharsets.UTF_8))) {
      loadFrom(br);
    } catch (Exception e) {
      index = new HashMap<>();
      variants = new HashMap<>();
    }
  }

  static synchronized void loadFrom(BufferedReader br) throws java.io.IOException {
    Map<String, Set<String>> idx = new HashMap<>(1024);
    Map<String, List<String>> vars = new HashMap<>(256);
    Map<String, String> disp = new HashMap<>(1024);
    Map<String, List<String>> lat = new HashMap<>(256);
    String line;
    while ((line = br.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith(";")) continue;
      int eq = line.indexOf('=');
      String canon = (eq < 0 ? line : line.substring(0, eq)).trim();
      if (canon.isEmpty()) continue;
      List<String> shown = vars.computeIfAbsent(canon, k -> new ArrayList<>());
      addKey(idx, canon, canon);
      disp.putIfAbsent(norm(canon), canon);
      if (eq >= 0) {
        for (String v : line.substring(eq + 1).split(",")) {
          String t = v.trim();
          if (t.isEmpty()) continue;
          addKey(idx, t, canon);
          if (isGreek(t)) disp.putIfAbsent(norm(t), t);
          else lat.computeIfAbsent(canon, k -> new ArrayList<>()).add(norm(t));
          if (isGreek(t) && !norm(t).equals(norm(canon)) && !containsNorm(shown, t)) shown.add(t);
        }
      }
    }
    index = idx;
    variants = vars;
    display = disp;
    latinOf = lat;
  }

  /** «ΜΙΧΑΛΗΣ» → «Μιχάλης», «MIXALIS» → «Mixalis»· αλλιώς όπως είναι. */
  static String pretty(String s) {
    if (s == null || s.isEmpty()) return s;
    if (isGreek(s) && display != null) {
      String d = display.get(norm(s));
      if (d != null) return d;
    }
    boolean allUp = s.equals(s.toUpperCase(Locale.ROOT)), allLow = s.equals(s.toLowerCase(Locale.ROOT));
    if (allUp || allLow) {
      String l = s.toLowerCase(new Locale("el", "GR"));
      return l.substring(0, 1).toUpperCase(new Locale("el", "GR")) + l.substring(1);
    }
    return s;
  }

  private static boolean containsNorm(List<String> list, String s) {
    String n = norm(s);
    for (String x : list) if (norm(x).equals(n)) return true;
    return false;
  }

  private static void addKey(Map<String, Set<String>> idx, String name, String canon) {
    String n = norm(name);
    idx.computeIfAbsent(n, k -> new LinkedHashSet<>()).add(canon);
    if (isGreek(name)) for (String l : latin(n)) idx.computeIfAbsent(l, k -> new LinkedHashSet<>()).add(canon);
  }

  /** Κανονικό όνομα για όνομα του εορτολογίου (π.χ. "Γιάννης" → "Ιωάννης"). */
  static String canonicalOf(String title) {
    if (index == null) return title;
    Set<String> c = index.get(norm(title));
    if (c == null) {
      int sp = title.indexOf(' ');
      if (sp > 0) c = index.get(norm(title.substring(0, sp)));
    }
    return c == null || c.isEmpty() ? title : c.iterator().next();
  }

  /** Όλα τα κανονικά ονόματα που αντιστοιχούν σε ένα μικρό όνομα επαφής (ελληνικά ή λατινικά). */
  static Set<String> match(String firstName) {
    if (index == null || firstName == null) return Collections.emptySet();
    Set<String> c = index.get(norm(firstName));
    return c == null ? Collections.emptySet() : c;
  }

  static List<String> variantsOf(String canon) {
    if (variants == null) return Collections.emptyList();
    List<String> v = variants.get(canon);
    return v == null ? Collections.emptyList() : v;
  }

  /** Κλειδιά αναζήτησης ενός ονόματος (ελληνικά + greeklish όλων των παραλλαγών). */
  static List<String> searchKeys(String canon) {
    Set<String> keys = new LinkedHashSet<>();
    keys.add(norm(canon));
    keys.addAll(latin(norm(canon)));
    if (variants != null) {
      List<String> v = variants.get(canon);
      if (v != null) for (String x : v) { keys.add(norm(x)); keys.addAll(latin(norm(x))); }
    }
    if (latinOf != null) {
      List<String> l = latinOf.get(canon); // λατινικές μορφές του NAMES.DAT (George, John...)
      if (l != null) keys.addAll(l);
    }
    return new ArrayList<>(keys);
  }

  static boolean isGreek(String s) {
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if ((ch >= 0x370 && ch <= 0x3FF) || (ch >= 0x1F00 && ch <= 0x1FFF)) return true;
    }
    return false;
  }

  /** Μεταγραφές greeklish ενός κανονικοποιημένου ελληνικού ονόματος (2-3 πιθανές μορφές). */
  static List<String> latin(String n) {
    StringBuilder a = new StringBuilder(), b = new StringBuilder();
    for (int i = 0; i < n.length(); i++) {
      char c = n.charAt(i);
      char nx = i + 1 < n.length() ? n.charAt(i + 1) : 0;
      String two = nx == 0 ? "" : "" + c + nx;
      switch (two) {
        case "ου": a.append("ou"); b.append("ou"); i++; continue;
        case "αυ": a.append("av"); b.append("au"); i++; continue;
        case "ευ": a.append("ev"); b.append("eu"); i++; continue;
        case "γγ": a.append("ng"); b.append("gg"); i++; continue;
        case "γκ": a.append("g"); b.append("gk"); i++; continue;
        case "μπ": a.append(i == 0 ? "b" : "mp"); b.append(i == 0 ? "b" : "b"); i++; continue;
        case "ντ": a.append(i == 0 ? "d" : "nt"); b.append(i == 0 ? "d" : "d"); i++; continue;
        default: break;
      }
      String m;
      String alt = null;
      switch (c) {
        case 'α': m = "a"; break; case 'β': m = "v"; break; case 'γ': m = "g"; break;
        case 'δ': m = "d"; break; case 'ε': m = "e"; break; case 'ζ': m = "z"; break;
        case 'η': m = "i"; break; case 'θ': m = "th"; break; case 'ι': m = "i"; break;
        case 'κ': m = "k"; alt = "c"; break; case 'λ': m = "l"; break; case 'μ': m = "m"; break;
        case 'ν': m = "n"; break; case 'ξ': m = "x"; alt = "ks"; break; case 'ο': m = "o"; break;
        case 'π': m = "p"; break; case 'ρ': m = "r"; break; case 'σ': m = "s"; break;
        case 'τ': m = "t"; break; case 'υ': m = "y"; alt = "i"; break; case 'φ': m = "f"; alt = "ph"; break;
        case 'χ': m = "ch"; alt = "h"; break; case 'ψ': m = "ps"; break; case 'ω': m = "o"; break;
        default: m = String.valueOf(c);
      }
      a.append(m);
      b.append(alt != null ? alt : m);
    }
    String sa = a.toString(), sb = b.toString();
    List<String> out = new ArrayList<>(3);
    out.add(sa);
    if (!sb.equals(sa)) out.add(sb);
    String y = sa.replace("gi", "yi");
    if (!y.equals(sa)) out.add(y);
    return out;
  }

  /** Κλητική για ευχή: Νίκος → Νίκο, Αλέξανδρος → Αλέξανδρε, Κώστας → Κώστα, Δημήτρης → Δημήτρη. */
  static String vocative(String name) {
    if (name == null || name.length() < 3) return name;
    if (!isGreek(name)) { // greeklish: Giorgos → Giorgo, Kostas → Kosta
      String l = name.toLowerCase(Locale.ROOT);
      if (l.endsWith("os")) return name.substring(0, name.length() - 1);
      if (l.endsWith("as") || l.endsWith("is") || l.endsWith("es")) return name.substring(0, name.length() - 1);
      return name;
    }
    String lower = name.toLowerCase(new Locale("el", "GR"));
    if (lower.endsWith("ος") || lower.endsWith("ός")) {
      String stem = name.substring(0, name.length() - 2);
      return stem + (vowelGroupsAfterAccent(stem) >= 1 ? "ε" : "ο");
    }
    if (lower.endsWith("ας") || lower.endsWith("ης") || lower.endsWith("ής") || lower.endsWith("άς")
        || lower.endsWith("ούς") || lower.endsWith("ους") || lower.endsWith("ες")) {
      return name.substring(0, name.length() - 1);
    }
    return name;
  }

  /** Πόσες συλλαβές (ομάδες φωνηέντων) ακολουθούν τον τονισμένο φθόγγο στο θέμα. */
  private static int vowelGroupsAfterAccent(String stem) {
    String nfd = Normalizer.normalize(stem.toLowerCase(new Locale("el", "GR")), Normalizer.Form.NFD);
    int accent = nfd.indexOf('́');
    if (accent < 0) return 0;
    String rest = MARKS.matcher(nfd.substring(accent + 1)).replaceAll("");
    int groups = 0;
    boolean inVowel = false;
    for (int i = 0; i < rest.length(); i++) {
      boolean v = "αεηιουω".indexOf(rest.charAt(i)) >= 0;
      if (v && !inVowel) groups++;
      inVowel = v;
    }
    return groups;
  }
}
