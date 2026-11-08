package gr.eortologio.plus;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
  // ---------- Παλέτα (φωτεινό / σκοτεινό θέμα) ----------
  private boolean dark;
  private int bg, surface, surface2, ink, sub, accent, accentSoft, pink, green, line;
  private static final int G1 = 0xff4f46e5, G2 = 0xff7c3aed, G3 = 0xffdb2777;

  private static final Locale EL = new Locale("el", "GR");
  private static final DateTimeFormatter F_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", EL);
  private static final DateTimeFormatter F_DAY = DateTimeFormatter.ofPattern("EEE d MMMM", EL);
  private static final DateTimeFormatter F_SHORT = DateTimeFormatter.ofPattern("d MMM", EL);
  private static final DateTimeFormatter F_MONTH = DateTimeFormatter.ofPattern("LLLL yyyy", EL);

  private static final int RC_NOTIF = 12, RC_CONTACTS = 13;

  private EventStore store;
  private SharedPreferences prefs;
  private LinearLayout root, body, header;
  private View nav;
  private String page = "today";
  private LocalDate chosenMonth = LocalDate.now().withDayOfMonth(1), selectedDay = LocalDate.now();
  private String query = "";
  private LinearLayout searchResults, todaySections;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Runnable pendingSearch;
  private List<Object[]> searchIndex; // {canon, keys(List<String>)}
  private int topInset, bottomInset;

  // ======================================================================
  @Override public void onCreate(Bundle b) {
    prefs = getSharedPreferences("settings", 0);
    String th = prefs.getString("theme", "auto");
    if ("light".equals(th)) setTheme(R.style.AppTheme_Light);
    else if ("dark".equals(th)) setTheme(R.style.AppTheme_Dark);
    super.onCreate(b);
    store = new EventStore(this);
    if (b != null) {
      page = b.getString("page", page);
      query = b.getString("query", "");
    }
    handleIntent(getIntent());
    setupWindow();
    ReminderReceiver.schedule(this);
    if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("askedNotif", false)
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      prefs.edit().putBoolean("askedNotif", true).apply();
      requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, RC_NOTIF);
    }
    OtaUpdater.check(this, false);
  }

  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleIntent(intent);
    render();
  }

  private void handleIntent(Intent i) {
    if (i != null && i.getStringExtra("page") != null) { page = i.getStringExtra("page"); query = ""; }
  }

  @Override protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("page", page);
    out.putString("query", query);
  }

  @Override protected void onResume() {
    super.onResume();
    render(); // ενημερώνει κατάσταση αδειών/μπαταρίας όταν επιστρέφουμε από τις Ρυθμίσεις
    new Thread(() -> TodayWidget.updateAll(getApplicationContext())).start();
  }

  @Override public void onBackPressed() {
    if (!query.isEmpty()) { query = ""; render(); return; }
    if (!"today".equals(page)) { page = "today"; render(); return; }
    super.onBackPressed();
  }

  @Override public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
    super.onRequestPermissionsResult(rc, perms, res);
    if (rc == RC_SMS) {
      if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) enableAutoSms();
      else openAppSettings("Χωρίς την άδεια «SMS» δεν μπορούν να σταλούν αυτόματες ευχές. Ρυθμίσεις → Άδειες → SMS → Να επιτρέπεται.");
      return;
    }
    if (rc == RC_CONTACTS) {
      ContactSync.invalidate();
      if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
        prefs.edit().putBoolean("contacts", true).apply();
        toast("Οι επαφές συγχρονίστηκαν ✓");
      } else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
        openAppSettings("Η άδεια επαφών είναι απενεργοποιημένη. Άνοιξε Ρυθμίσεις → Άδειες → Επαφές → Να επιτρέπεται.");
      }
    }
    render();
  }

  // ======================================================================
  // Παράθυρο, θέμα, insets (Android 15 edge-to-edge)
  // ======================================================================
  private void setupWindow() {
    String th = prefs.getString("theme", "auto");
    boolean systemDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    dark = "dark".equals(th) || (!"light".equals(th) && systemDark);
    if (dark) {
      bg = 0xff0e0f1c; surface = 0xff1a1b2e; surface2 = 0xff25273f; ink = 0xfff2f2fb; sub = 0xffa5a8c4;
      accent = 0xffa59bff; accentSoft = 0x33a59bff; pink = 0xffff7ab6; green = 0xff4ade80; line = 0xff2c2e48;
    } else {
      bg = 0xfff4f3fb; surface = 0xffffffff; surface2 = 0xffeeecfa; ink = 0xff17182c; sub = 0xff676b84;
      accent = 0xff5b4fd8; accentSoft = 0x1f5b4fd8; pink = 0xffdb2777; green = 0xff16a34a; line = 0xffe6e4f2;
    }
    getWindow().getDecorView(); // δημιουργεί το παράθυρο πριν ζητηθεί ο insets controller (αλλιώς NPE)
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    if (Build.VERSION.SDK_INT >= 30) {
      getWindow().setDecorFitsSystemWindows(false);
      getWindow().setNavigationBarColor(Color.TRANSPARENT);
      WindowInsetsController c = null;
      try { c = getWindow().getInsetsController(); } catch (Exception ignored) { }
      if (c != null) c.setSystemBarsAppearance(dark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
          WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
    } else {
      getWindow().setStatusBarColor(G1);
      getWindow().setNavigationBarColor(surface);
      if (!dark) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
  }

  private void applyInsets(View v, WindowInsets in) {
    if (Build.VERSION.SDK_INT < 30) return;
    android.graphics.Insets sys = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
    android.graphics.Insets ime = in.getInsets(WindowInsets.Type.ime());
    topInset = sys.top;
    bottomInset = Math.max(sys.bottom, ime.bottom);
    boolean imeOpen = ime.bottom > sys.bottom;
    if (header != null) header.setPadding(dp(20), dp(16) + topInset, dp(20), dp(22));
    if (nav != null) {
      nav.setVisibility(imeOpen ? View.GONE : View.VISIBLE);
      nav.setPadding(dp(6), dp(6), dp(6), dp(6) + (imeOpen ? 0 : sys.bottom));
    }
    root.setPadding(sys.left, 0, sys.right, imeOpen ? ime.bottom : 0);
  }

  // ======================================================================
  // Μικρά helpers UI
  // ======================================================================
  int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }

  TextView tv(CharSequence s, float size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextColor(color);
    t.setTextSize(size);
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    t.setLineSpacing(0, 1.08f);
    return t;
  }

  LinearLayout col() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }

  LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

  GradientDrawable round(int color, float radiusDp) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(radiusDp));
    return g;
  }

  /** Εφέ ripple στο πάτημα πάνω σε στρογγυλεμένο φόντο. */
  void clickable(View v, Drawable content, float radius, Runnable action) {
    GradientDrawable mask = round(Color.WHITE, radius);
    v.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x33ffffff : 0x225b4fd8), content, mask));
    v.setClickable(true);
    v.setFocusable(true);
    v.setOnClickListener(x -> action.run());
  }

  LinearLayout card() {
    LinearLayout c = col();
    c.setBackground(round(surface, 22));
    c.setPadding(dp(18), dp(16), dp(18), dp(16));
    c.setElevation(dark ? 0 : dp(1.5f));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(12);
    c.setLayoutParams(p);
    return c;
  }

  void section(String title, String action, Runnable onAction) {
    LinearLayout r = row();
    r.setPadding(dp(4), dp(14), dp(4), dp(8));
    TextView t = tv(title, 18, ink, true);
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    if (action != null) {
      TextView a = tv(action, 14, accent, true);
      a.setPadding(dp(10), dp(6), dp(10), dp(6));
      clickable(a, round(Color.TRANSPARENT, 12), 12, onAction);
      r.addView(a);
    }
    body.addView(r);
  }

  TextView chip(String text, int fg, int bgc, Runnable onClick) {
    TextView c = tv(text, 14, fg, true);
    c.setPadding(dp(14), dp(8), dp(14), dp(8));
    if (onClick != null) clickable(c, round(bgc, 18), 18, onClick); else c.setBackground(round(bgc, 18));
    return c;
  }

  TextView button(String text, boolean filled, Runnable onClick) {
    TextView b = tv(text, 14, filled ? Color.WHITE : accent, true);
    b.setGravity(Gravity.CENTER);
    b.setSingleLine(true);
    b.setPadding(dp(16), dp(11), dp(16), dp(11));
    clickable(b, round(filled ? accent : accentSoft, 16), 16, onClick);
    return b;
  }

  /** Οριζόντια ροή που αναδιπλώνεται (για chips ονομάτων). */
  ViewGroup flow() {
    return new ViewGroup(this) {
      @Override protected void onMeasure(int ws, int hs) {
        int max = MeasureSpec.getSize(ws) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowH = 0, gap = dp(8);
        for (int i = 0; i < getChildCount(); i++) {
          View c = getChildAt(i);
          c.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
          if (x > 0 && x + c.getMeasuredWidth() > max) { x = 0; y += rowH + gap; rowH = 0; }
          x += c.getMeasuredWidth() + gap;
          rowH = Math.max(rowH, c.getMeasuredHeight());
        }
        setMeasuredDimension(MeasureSpec.getSize(ws), y + rowH + getPaddingTop() + getPaddingBottom());
      }

      @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int max = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowH = 0, gap = dp(8);
        for (int i = 0; i < getChildCount(); i++) {
          View c = getChildAt(i);
          int w = c.getMeasuredWidth(), h = c.getMeasuredHeight();
          if (x > 0 && x + w > max) { x = 0; y += rowH + gap; rowH = 0; }
          c.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + w, getPaddingTop() + y + h);
          x += w + gap;
          rowH = Math.max(rowH, h);
        }
      }
    };
  }

  /** Στρογγυλό «σήμα» ημερομηνίας: μέρα + μήνας. */
  View dateBadge(LocalDate d, boolean highlight) {
    LinearLayout b = col();
    b.setGravity(Gravity.CENTER);
    b.setBackground(round(highlight ? accent : surface2, 16));
    TextView n = tv(String.valueOf(d.getDayOfMonth()), 18, highlight ? Color.WHITE : ink, true);
    n.setGravity(Gravity.CENTER);
    TextView m = tv(d.format(DateTimeFormatter.ofPattern("MMM", EL)).replace(".", "").toUpperCase(EL), 10, highlight ? 0xddffffff : sub, true);
    m.setGravity(Gravity.CENTER);
    b.addView(n); b.addView(m);
    b.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(52)));
    return b;
  }

  View avatar(String name, int seed) {
    int[] cols = { 0xff6366f1, 0xffec4899, 0xff14b8a6, 0xfff59e0b, 0xff8b5cf6, 0xff0ea5e9, 0xffef4444, 0xff22c55e };
    String in = "";
    for (String p : name.trim().split("\\s+")) if (!p.isEmpty() && in.length() < 2) in += p.substring(0, 1).toUpperCase(EL);
    TextView a = tv(in, 16, Color.WHITE, true);
    a.setGravity(Gravity.CENTER);
    GradientDrawable g = new GradientDrawable();
    g.setShape(GradientDrawable.OVAL);
    g.setColor(cols[Math.abs(seed) % cols.length]);
    a.setBackground(g);
    a.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
    return a;
  }

  static String when(long days) {
    if (days == 0) return "Σήμερα";
    if (days == 1) return "Αύριο";
    if (days < 0) return "πριν " + (-days) + " ημέρες";
    return "σε " + days + " ημέρες";
  }

  void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

  void spacer(int h) { View v = new View(this); body.addView(v, new LinearLayout.LayoutParams(1, dp(h))); }

  // ======================================================================
  // Κύρια διάταξη
  // ======================================================================
  void render() {
    LocalDate today = LocalDate.now();
    root = col();
    root.setBackgroundColor(bg);
    setContentView(root);
    if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((v, in) -> { applyInsets(v, in); return in; });

    // ---- Κεφαλίδα με gradient ----
    header = col();
    GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] { G1, G2, G3 });
    float r = dp(28);
    hg.setCornerRadii(new float[] { 0, 0, 0, 0, r, r, r, r });
    header.setBackground(hg);
    header.setPadding(dp(20), dp(16) + topInset, dp(20), dp(22));
    String[] titles = pageTitle();
    header.addView(tv("ΕΟΡΤΟΛΟΓΙΟ +", 12, 0xccffffff, true));
    TextView h1 = tv(titles[0], 26, Color.WHITE, true);
    h1.setPadding(0, dp(4), 0, dp(2));
    header.addView(h1);
    header.addView(tv(titles[1], 14, 0xe6ffffff, false));
    root.addView(header);

    // ---- Περιεχόμενο ----
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    body = col();
    body.setPadding(dp(16), dp(14), dp(16), dp(24));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

    switch (page) {
      case "calendar": calendar(today); break;
      case "contacts": contacts(today); break;
      case "personal": personal(today); break;
      case "settings": settings(); break;
      default: today(today);
    }

    root.addView(nav = bottomNav());
    root.requestApplyInsets();
  }

  String[] pageTitle() {
    String date = LocalDate.now().format(F_LONG);
    date = date.substring(0, 1).toUpperCase(EL) + date.substring(1);
    switch (page) {
      case "calendar": return new String[] { "Ημερολόγιο", "Όλες οι γιορτές του μήνα" };
      case "contacts": return new String[] { "Επαφές", "Ποιος γιορτάζει — στείλε ευχές" };
      case "personal": return new String[] { "Δικά μου", "Γενέθλια, επέτειοι & σημαντικές μέρες" };
      case "settings": return new String[] { "Ρυθμίσεις", "Ειδοποιήσεις, επαφές, μπαταρία" };
      default: return new String[] { "Χρόνια πολλά!", date };
    }
  }

  View bottomNav() {
    LinearLayout n = row();
    n.setBackgroundColor(surface);
    n.setElevation(dp(8));
    n.setPadding(dp(6), dp(6), dp(6), dp(6) + bottomInset);
    String[] ids = { "today", "calendar", "contacts", "personal", "settings" };
    String[] icons = { "🎉", "📅", "👥", "⭐", "⚙️" };
    String[] names = { "Σήμερα", "Ημερολόγιο", "Επαφές", "Δικά μου", "Ρυθμίσεις" };
    for (int i = 0; i < ids.length; i++) {
      final String id = ids[i];
      boolean sel = page.equals(id);
      LinearLayout item = col();
      item.setGravity(Gravity.CENTER);
      item.setPadding(0, dp(6), 0, dp(6));
      TextView ic = tv(icons[i], 19, ink, false);
      ic.setGravity(Gravity.CENTER);
      ic.setPadding(dp(14), dp(2), dp(14), dp(2));
      if (sel) ic.setBackground(round(accentSoft, 14));
      TextView lb = tv(names[i], 11, sel ? accent : sub, sel);
      lb.setGravity(Gravity.CENTER);
      lb.setSingleLine();
      lb.setPadding(0, dp(3), 0, 0);
      item.addView(ic); item.addView(lb);
      clickable(item, round(Color.TRANSPARENT, 16), 16, () -> {
        if (!page.equals(id)) { page = id; query = ""; render(); }
      });
      n.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }
    return n;
  }

  // ======================================================================
  // ΣΗΜΕΡΑ + Αναζήτηση
  // ======================================================================
  void today(LocalDate today) {
    body.addView(searchBar());
    searchResults = col();
    todaySections = col();
    body.addView(searchResults);
    body.addView(todaySections);
    LinearLayout saved = body;
    body = todaySections;

    setupGuide();

    List<EventStore.Event> evs = store.on(today);
    LinearLayout hero = card();
    hero.addView(tv("🎉 Σήμερα γιορτάζουν", 18, ink, true));
    ViewGroup chips = flow();
    chips.setPadding(0, dp(12), 0, 0);
    List<String> holidays = new ArrayList<>();
    Set<String> shown = new HashSet<>();
    for (EventStore.Event e : evs) {
      if (e.isNameDay()) {
        if (shown.add(e.canon)) chips.addView(nameChip(e.canon));
      } else if (!e.personal) holidays.add(e.title);
    }
    if (chips.getChildCount() == 0) {
      TextView none = tv("Καμία ονομαστική γιορτή σήμερα στο εορτολόγιο. Δες τις επόμενες παρακάτω 👇", 14, sub, false);
      none.setPadding(0, dp(8), 0, 0);
      hero.addView(none);
    } else hero.addView(chips);
    for (String h : holidays) {
      TextView t = tv("📌 " + h, 14, pink, true);
      t.setPadding(0, dp(12), 0, 0);
      hero.addView(t);
    }
    body.addView(hero);

    // ---- Επαφές που γιορτάζουν σήμερα/αύριο ----
    if (!ContactSync.granted(this)) {
      LinearLayout c = card();
      c.addView(tv("👥 Ποιος από τους δικούς σου γιορτάζει;", 16, ink, true));
      TextView d = tv("Σύνδεσε τις επαφές του κινητού και θα σου λέω ποιος γιορτάζει ή έχει γενέθλια, για να στείλεις «Χρόνια πολλά» με ένα πάτημα. Οι επαφές δεν φεύγουν ποτέ από το κινητό.", 14, sub, false);
      d.setPadding(0, dp(6), 0, dp(12));
      c.addView(d);
      c.addView(button("Σύνδεση επαφών", true, this::askContacts));
      body.addView(c);
    } else if (ContactSync.enabled(this)) {
      List<ContactSync.Hit> hits = ContactSync.hits(this, store, today, 1);
      if (!hits.isEmpty()) {
        section("Από τις επαφές σου", "Όλες ›", () -> { page = "contacts"; render(); });
        for (ContactSync.Hit h : hits) body.addView(contactCard(h, today, true));
      }
    }

    // ---- Προσωπικά σήμερα ----
    for (EventStore.Event e : store.editable()) {
      if (e.onYear(today.getYear()).equals(today)) {
        LinearLayout c = card();
        c.setBackground(round(dark ? 0xff3a2346 : 0xfffde7f3, 22));
        int y = e.yearsAt(today);
        c.addView(tv("⭐ " + e.title, 17, ink, true));
        c.addView(tv(e.category + (y > 0 ? " · " + y + " χρόνια" : ""), 14, sub, false));
        body.addView(c);
      }
    }

    // ---- Έρχονται σύντομα (14 ημέρες, ομαδοποιημένα ανά ημέρα) ----
    section("Έρχονται σύντομα", "Ημερολόγιο ›", () -> { page = "calendar"; render(); });
    for (int i = 1; i <= 14; i++) {
      LocalDate d = today.plusDays(i);
      List<EventStore.Event> list = store.on(d);
      if (list.isEmpty()) continue;
      body.addView(dayRow(d, list, today));
    }

    body = saved;
    if (!query.isEmpty()) runSearch();
  }

  View searchBar() {
    LinearLayout bar = row();
    bar.setBackground(round(surface, 20));
    bar.setElevation(dark ? 0 : dp(1.5f));
    bar.setPadding(dp(16), dp(4), dp(8), dp(4));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
    lp.bottomMargin = dp(12);
    bar.setLayoutParams(lp);
    bar.addView(tv("🔍", 17, sub, false));
    EditText e = new EditText(this);
    e.setHint("Αναζήτηση ονόματος, π.χ. Γιώργος, Nikos");
    e.setHintTextColor(sub);
    e.setTextColor(ink);
    e.setTextSize(16);
    e.setBackground(null);
    e.setSingleLine();
    e.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    e.setPadding(dp(10), 0, dp(4), 0);
    e.setText(query);
    bar.addView(e, new LinearLayout.LayoutParams(0, -1, 1));
    TextView clear = tv("✕", 16, sub, false);
    clear.setPadding(dp(12), dp(8), dp(12), dp(8));
    clear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
    clickable(clear, round(Color.TRANSPARENT, 16), 16, () -> {
      e.setText("");
      InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
      if (im != null) im.hideSoftInputFromWindow(e.getWindowToken(), 0);
      e.clearFocus();
    });
    bar.addView(clear);
    e.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
      @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
      @Override public void afterTextChanged(Editable s) {
        query = s.toString().trim();
        clear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        pendingSearch = MainActivity.this::runSearch;
        handler.postDelayed(pendingSearch, 160); // debounce: δεν ψάχνει σε κάθε γράμμα
      }
    });
    e.setOnEditorActionListener((v, id, ev) -> {
      InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
      if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
      return true;
    });
    return bar;
  }

  /** Χτίζεται μία φορά: κάθε όνομα με όλα τα κλειδιά αναζήτησης (ελληνικά, χωρίς τόνους, greeklish, ξένα). */
  private void buildSearchIndex() {
    if (searchIndex != null) return;
    searchIndex = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (EventStore.Event e : store.all()) {
      if (!e.isNameDay() || !seen.add(e.canon)) continue;
      searchIndex.add(new Object[] { e.canon, Names.searchKeys(e.canon) });
    }
  }

  void runSearch() {
    if (searchResults == null || todaySections == null) return;
    searchResults.removeAllViews();
    if (query.isEmpty()) { todaySections.setVisibility(View.VISIBLE); return; }
    todaySections.setVisibility(View.GONE);
    LinearLayout saved = body;
    body = searchResults;
    buildSearchIndex();
    String q = Names.norm(query);
    LocalDate today = LocalDate.now();

    // Ονόματα: πρώτα όσα ξεκινούν με το κείμενο, μετά όσα το περιέχουν
    List<String> starts = new ArrayList<>(), contains = new ArrayList<>();
    for (Object[] o : searchIndex) {
      @SuppressWarnings("unchecked") List<String> keys = (List<String>) o[1];
      boolean st = false, ct = false;
      for (String k : keys) {
        if (k.startsWith(q)) { st = true; break; }
        if (!ct && k.contains(q)) ct = true;
      }
      if (st) starts.add((String) o[0]); else if (ct) contains.add((String) o[0]);
    }
    starts.addAll(contains);

    // Προσωπικά & αργίες
    List<EventStore.Event> others = new ArrayList<>();
    for (EventStore.Event e : store.all()) if (!e.isNameDay() && Names.norm(e.title).contains(q)) others.add(e);

    // Επαφές
    List<ContactSync.Person> people = new ArrayList<>();
    for (ContactSync.Person p : ContactSync.load(this)) if (Names.norm(p.displayName).contains(q)) people.add(p);

    if (starts.isEmpty() && others.isEmpty() && people.isEmpty()) {
      LinearLayout c = card();
      c.addView(tv("Δεν βρέθηκε «" + query + "»", 16, ink, true));
      TextView d = tv("Δοκίμασε άλλη γραφή (π.χ. Γιώργος / Γεώργιος / Giorgos) ή πρόσθεσέ το στα «Δικά μου».", 14, sub, false);
      d.setPadding(0, dp(6), 0, dp(12));
      c.addView(d);
      c.addView(button("+ Προσθήκη στα Δικά μου", false, () -> edit(null, query)));
      body.addView(c);
    }
    int n = 0;
    for (String canon : starts) {
      if (++n > 40) break;
      body.addView(nameResult(canon, today));
    }
    if (!people.isEmpty()) {
      section("Επαφές", null, null);
      for (ContactSync.Person p : people) {
        LocalDate next = null; String why = "";
        for (String c : p.canon) for (EventStore.Event e : store.nameDaysOf(c)) {
          LocalDate d = e.next(today);
          if (next == null || d.isBefore(next)) { next = d; why = "Γιορτή · " + c; }
        }
        if (p.hasBirthday()) {
          try {
            LocalDate b = LocalDate.of(today.getYear(), p.bMonth, p.bMonth == 2 && p.bDay == 29 && !today.isLeapYear() ? 28 : p.bDay);
            if (b.isBefore(today)) b = b.plusYears(1);
            if (next == null || b.isBefore(next)) { next = b; why = "Γενέθλια"; }
          } catch (Exception ignored) { }
        }
        if (next != null) body.addView(contactCard(new ContactSync.Hit(p, next, "Γενέθλια".equals(why), why), today, false));
      }
    }
    if (!others.isEmpty()) {
      section("Άλλα", null, null);
      for (EventStore.Event e : others) {
        LocalDate d = e.next(today);
        body.addView(simpleRow(d, e.title, e.category + " · " + when(EventStore.daysUntil(today, d)), today, e.personal ? () -> edit(e, null) : null));
      }
    }
    body = saved;
  }

  View nameResult(String canon, LocalDate today) {
    LinearLayout c = card();
    LinearLayout top = row();
    LinearLayout txt = col();
    txt.addView(tv(canon, 19, ink, true));
    List<String> v = Names.variantsOf(canon);
    if (!v.isEmpty()) txt.addView(tv(TextUtils.join(" · ", v.subList(0, Math.min(5, v.size()))), 13, sub, false));
    top.addView(txt, new LinearLayout.LayoutParams(0, -2, 1));
    c.addView(top);
    ViewGroup chips = flow();
    chips.setPadding(0, dp(10), 0, 0);
    List<EventStore.Event> days = store.nameDaysOf(canon);
    days.sort(Comparator.comparing(e -> e.next(today)));
    for (EventStore.Event e : days) {
      LocalDate d = e.next(today);
      long dd = EventStore.daysUntil(today, d);
      chips.addView(chip("📅 " + d.format(F_DAY) + " · " + when(dd), dd <= 1 ? Color.WHITE : accent, dd <= 1 ? accent : accentSoft, () -> {
        page = "calendar"; chosenMonth = d.withDayOfMonth(1); selectedDay = d; query = ""; render();
      }));
    }
    c.addView(chips);
    int count = 0;
    for (ContactSync.Person p : ContactSync.load(this)) if (p.canon.contains(canon)) count++;
    if (count > 0) {
      TextView t = tv("👥 " + count + (count == 1 ? " επαφή σου έχει αυτό το όνομα" : " επαφές σου έχουν αυτό το όνομα"), 13, green, true);
      t.setPadding(0, dp(10), 0, 0);
      c.addView(t);
    }
    return c;
  }

  TextView nameChip(String canon) {
    List<String> v = Names.variantsOf(canon);
    String label = v.isEmpty() ? canon : canon + "  ·  " + v.get(0);
    return chip(label, accent, accentSoft, () -> showName(canon));
  }

  void showName(String canon) {
    LocalDate today = LocalDate.now();
    StringBuilder s = new StringBuilder();
    List<String> v = Names.variantsOf(canon);
    if (!v.isEmpty()) s.append("Παραλλαγές: ").append(TextUtils.join(", ", v)).append("\n\n");
    for (EventStore.Event e : store.nameDaysOf(canon)) {
      LocalDate d = e.next(today);
      s.append("📅 ").append(d.format(F_DAY)).append(" — ").append(when(EventStore.daysUntil(today, d))).append('\n');
    }
    List<String> ppl = new ArrayList<>();
    for (ContactSync.Person p : ContactSync.load(this)) if (p.canon.contains(canon)) ppl.add(p.displayName);
    if (!ppl.isEmpty()) s.append("\n👥 Επαφές: ").append(TextUtils.join(", ", ppl));
    AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(canon).setMessage(s.toString().trim()).setPositiveButton("OK", null);
    if (!ppl.isEmpty()) b.setNeutralButton("Στείλε ευχές", (d, w) -> { page = "contacts"; render(); });
    b.show();
  }

  View dayRow(LocalDate d, List<EventStore.Event> list, LocalDate today) {
    LinearLayout c = card();
    c.setPadding(dp(12), dp(12), dp(16), dp(12));
    LinearLayout r = row();
    r.addView(dateBadge(d, false));
    LinearLayout t = col();
    t.setPadding(dp(14), 0, 0, 0);
    StringBuilder names = new StringBuilder();
    List<String> extra = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (EventStore.Event e : list) {
      if (e.isNameDay()) { if (seen.add(e.canon)) { if (names.length() > 0) names.append(", "); names.append(e.canon); } }
      else extra.add((e.personal ? "⭐ " : "📌 ") + e.title);
    }
    String head = d.format(DateTimeFormatter.ofPattern("EEEE", EL)) + " · " + when(EventStore.daysUntil(today, d));
    t.addView(tv(head.substring(0, 1).toUpperCase(EL) + head.substring(1), 12, sub, true));
    if (names.length() > 0) {
      TextView n = tv(names, 15, ink, true);
      n.setMaxLines(3);
      n.setEllipsize(TextUtils.TruncateAt.END);
      t.addView(n);
    }
    for (String x : extra) t.addView(tv(x, 14, x.startsWith("⭐") ? pink : accent, true));
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    c.addView(r);
    final LocalDate dd = d;
    clickable(c, round(surface, 22), 22, () -> { page = "calendar"; chosenMonth = dd.withDayOfMonth(1); selectedDay = dd; render(); });
    return c;
  }

  View simpleRow(LocalDate d, String title, String detail, LocalDate today, Runnable action) {
    LinearLayout c = card();
    c.setPadding(dp(12), dp(12), dp(16), dp(12));
    LinearLayout r = row();
    r.addView(dateBadge(d, d.equals(today)));
    LinearLayout t = col();
    t.setPadding(dp(14), 0, 0, 0);
    t.addView(tv(title, 16, ink, true));
    t.addView(tv(detail, 13, sub, false));
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    c.addView(r);
    if (action != null) clickable(c, round(surface, 22), 22, action);
    return c;
  }

  // ---- Οδηγός πρώτης ρύθμισης (βήμα-βήμα) ----
  void setupGuide() {
    if (prefs.getBoolean("guideDone", false)) return;
    boolean notif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    boolean contacts = ContactSync.granted(this);
    boolean battery = batteryOk();
    if (notif && contacts && battery) { prefs.edit().putBoolean("guideDone", true).apply(); return; }
    LinearLayout c = card();
    c.setBackground(round(dark ? 0xff23224a : 0xffeeeaff, 22));
    LinearLayout top = row();
    top.addView(tv("🚀 Γρήγορη ρύθμιση (3 βήματα)", 16, ink, true), new LinearLayout.LayoutParams(0, -2, 1));
    TextView x = tv("✕", 15, sub, false);
    x.setPadding(dp(10), dp(4), dp(4), dp(4));
    clickable(x, round(Color.TRANSPARENT, 12), 12, () -> { prefs.edit().putBoolean("guideDone", true).apply(); render(); });
    top.addView(x);
    c.addView(top);
    c.addView(step(1, "Ειδοποιήσεις", "Για να σου θυμίζω τις γιορτές κάθε πρωί", notif, this::askNotifications));
    c.addView(step(2, "Επαφές", "Για να βρίσκω ποιοι φίλοι σου γιορτάζουν", contacts, this::askContacts));
    c.addView(step(3, "Μπαταρία χωρίς περιορισμό", "Για να έρχονται οι ειδοποιήσεις στην ώρα τους", battery, this::askBattery));
    body.addView(c);
  }

  View step(int n, String title, String detail, boolean done, Runnable action) {
    LinearLayout r = row();
    r.setPadding(0, dp(12), 0, 0);
    TextView num = tv(done ? "✓" : String.valueOf(n), 14, Color.WHITE, true);
    num.setGravity(Gravity.CENTER);
    GradientDrawable g = new GradientDrawable();
    g.setShape(GradientDrawable.OVAL);
    g.setColor(done ? green : accent);
    num.setBackground(g);
    r.addView(num, new LinearLayout.LayoutParams(dp(28), dp(28)));
    LinearLayout t = col();
    t.setPadding(dp(12), 0, dp(8), 0);
    t.addView(tv(title, 15, ink, true));
    t.addView(tv(detail, 12, sub, false));
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    if (!done) r.addView(button("Ενεργοποίηση", true, action));
    else r.addView(tv("Έτοιμο", 13, green, true));
    return r;
  }

  // ======================================================================
  // ΗΜΕΡΟΛΟΓΙΟ (πλέγμα μήνα)
  // ======================================================================
  void calendar(LocalDate today) {
    LinearLayout c = card();
    c.setPadding(dp(10), dp(12), dp(10), dp(14));
    LinearLayout bar = row();
    TextView prev = tv("‹", 26, accent, true), next = tv("›", 26, accent, true);
    prev.setPadding(dp(16), 0, dp(16), dp(4));
    next.setPadding(dp(16), 0, dp(16), dp(4));
    String m = chosenMonth.format(F_MONTH);
    TextView mid = tv(m.substring(0, 1).toUpperCase(EL) + m.substring(1), 18, ink, true);
    mid.setGravity(Gravity.CENTER);
    clickable(prev, round(Color.TRANSPARENT, 16), 16, () -> { chosenMonth = chosenMonth.minusMonths(1); selectedDay = chosenMonth; render(); });
    clickable(next, round(Color.TRANSPARENT, 16), 16, () -> { chosenMonth = chosenMonth.plusMonths(1); selectedDay = chosenMonth; render(); });
    clickable(mid, round(Color.TRANSPARENT, 12), 12, () -> { chosenMonth = today.withDayOfMonth(1); selectedDay = today; render(); });
    bar.addView(prev);
    bar.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
    bar.addView(next);
    c.addView(bar);

    // Ονόματα ημερών (Δευτέρα πρώτη)
    LinearLayout wd = row();
    wd.setPadding(0, dp(10), 0, dp(4));
    String[] w = { "Δ", "Τ", "Τ", "Π", "Π", "Σ", "Κ" };
    for (int i = 0; i < 7; i++) {
      TextView t = tv(w[i], 12, i >= 5 ? pink : sub, true);
      t.setGravity(Gravity.CENTER);
      wd.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    }
    c.addView(wd);

    // Ποιες μέρες έχουν επαφές που γιορτάζουν (ένα πέρασμα για όλο τον μήνα)
    YearMonth ym = YearMonth.from(chosenMonth);
    Set<LocalDate> contactDays = new HashSet<>();
    for (ContactSync.Hit h : ContactSync.hits(this, store, ym.atDay(1), ym.lengthOfMonth() - 1)) contactDays.add(h.date);

    int lead = ym.atDay(1).getDayOfWeek().getValue() - 1; // Δευτέρα = 0
    int cells = (int) Math.ceil((lead + ym.lengthOfMonth()) / 7.0) * 7;
    LinearLayout week = null;
    for (int i = 0; i < cells; i++) {
      if (i % 7 == 0) { week = row(); c.addView(week); }
      int dayNum = i - lead + 1;
      if (dayNum < 1 || dayNum > ym.lengthOfMonth()) {
        week.addView(new View(this), new LinearLayout.LayoutParams(0, dp(50), 1));
        continue;
      }
      LocalDate date = ym.atDay(dayNum);
      week.addView(dayCell(date, today, contactDays.contains(date)), new LinearLayout.LayoutParams(0, dp(50), 1));
    }
    body.addView(c);

    // Λεπτομέρειες επιλεγμένης ημέρας
    if (!YearMonth.from(selectedDay).equals(ym)) selectedDay = ym.atDay(1);
    String head = selectedDay.format(F_LONG);
    section(head.substring(0, 1).toUpperCase(EL) + head.substring(1), null, null);
    List<EventStore.Event> list = store.on(selectedDay);
    LinearLayout d = card();
    ViewGroup chips = flow();
    Set<String> seen = new HashSet<>();
    for (EventStore.Event e : list) if (e.isNameDay() && seen.add(e.canon)) chips.addView(nameChip(e.canon));
    if (chips.getChildCount() > 0) d.addView(chips);
    for (EventStore.Event e : list) {
      if (e.isNameDay()) continue;
      TextView t = tv((e.personal ? "⭐ " : "📌 ") + e.title, 15, e.personal ? pink : accent, true);
      t.setPadding(0, dp(10), 0, 0);
      if (e.personal) clickable(t, round(Color.TRANSPARENT, 8), 8, () -> edit(e, null));
      d.addView(t);
    }
    if (list.isEmpty()) d.addView(tv("Δεν υπάρχει γιορτή αυτή τη μέρα.", 14, sub, false));
    TextView add = tv("+ Πρόσθεσε δική σου ημερομηνία εδώ", 14, accent, true);
    add.setPadding(0, dp(14), 0, 0);
    final LocalDate sd = selectedDay;
    add.setOnClickListener(v -> editAt(sd));
    d.addView(add);
    body.addView(d);
    for (ContactSync.Hit h : ContactSync.hits(this, store, selectedDay, 0)) body.addView(contactCard(h, today, true));
  }

  View dayCell(LocalDate date, LocalDate today, boolean contact) {
    List<EventStore.Event> es = store.on(date);
    boolean names = false, personal = false, holiday = false;
    for (EventStore.Event e : es) {
      if (e.isNameDay()) names = true; else if (e.personal) personal = true; else holiday = true;
    }
    boolean sel = date.equals(selectedDay), isToday = date.equals(today);
    boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    LinearLayout cell = col();
    cell.setGravity(Gravity.CENTER);
    TextView n = tv(String.valueOf(date.getDayOfMonth()), 15, sel ? Color.WHITE : (holiday || weekend) ? pink : ink, sel || isToday);
    n.setGravity(Gravity.CENTER);
    cell.addView(n);
    LinearLayout dots = row();
    dots.setGravity(Gravity.CENTER);
    dots.setPadding(0, dp(3), 0, 0);
    if (names) dots.addView(dot(sel ? Color.WHITE : accent));
    if (contact) dots.addView(dot(sel ? Color.WHITE : green));
    if (personal) dots.addView(dot(sel ? Color.WHITE : pink));
    if (dots.getChildCount() == 0) dots.addView(dot(Color.TRANSPARENT));
    cell.addView(dots);
    GradientDrawable g = round(sel ? accent : Color.TRANSPARENT, 14);
    if (isToday && !sel) g.setStroke(dp(1.5f), accent);
    final LocalDate d = date;
    LinearLayout wrap = new LinearLayout(this);
    wrap.setPadding(dp(2), dp(2), dp(2), dp(2));
    clickable(cell, g, 14, () -> { selectedDay = d; render(); });
    wrap.addView(cell, new LinearLayout.LayoutParams(-1, -1));
    return wrap;
  }

  View dot(int color) {
    View v = new View(this);
    GradientDrawable g = new GradientDrawable();
    g.setShape(GradientDrawable.OVAL);
    g.setColor(color);
    v.setBackground(g);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(5), dp(5));
    p.leftMargin = p.rightMargin = dp(1.5f);
    v.setLayoutParams(p);
    return v;
  }

  // ======================================================================
  // ΕΠΑΦΕΣ: ποιος γιορτάζει + ευχές
  // ======================================================================
  void contacts(LocalDate today) {
    if (!ContactSync.granted(this) || !prefs.getBoolean("contacts", true)) {
      LinearLayout c = card();
      c.addView(tv("👥 Συγχρονισμός επαφών", 18, ink, true));
      TextView d = tv("1. Πάτα «Σύνδεση επαφών».\n2. Επίλεξε «Να επιτρέπεται».\n3. Έτοιμο! Θα βλέπεις εδώ ποιος γιορτάζει ή έχει γενέθλια και θα στέλνεις «Χρόνια πολλά» με SMS, WhatsApp ή Viber με ένα πάτημα.\n\n🔒 Οι επαφές διαβάζονται μόνο τοπικά, δεν αποθηκεύονται και δεν στέλνονται πουθενά.", 14, sub, false);
      d.setPadding(0, dp(8), 0, dp(14));
      c.addView(d);
      c.addView(button("Σύνδεση επαφών", true, this::askContacts));
      body.addView(c);
      return;
    }
    List<ContactSync.Person> people = ContactSync.load(this);
    LinearLayout info = card();
    LinearLayout r = row();
    int named = 0, bdays = 0;
    for (ContactSync.Person p : people) { if (!p.canon.isEmpty()) named++; if (p.hasBirthday()) bdays++; }
    LinearLayout t = col();
    t.addView(tv("✓ Συγχρονισμένες επαφές", 15, green, true));
    t.addView(tv(named + " με ονομαστική γιορτή · " + bdays + " με γενέθλια", 13, sub, false));
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    r.addView(button("↻ Ανανέωση", false, () -> { ContactSync.invalidate(); toast("Ανανεώθηκε"); render(); }));
    info.addView(r);
    body.addView(info);

    List<ContactSync.Hit> hits = ContactSync.hits(this, store, today, 365);
    if (hits.isEmpty()) {
      LinearLayout c = card();
      c.addView(tv("Δεν βρέθηκαν επαφές με γνωστό όνομα.", 15, ink, true));
      TextView d = tv("Το ταίριασμα γίνεται με το μικρό όνομα (π.χ. «Νίκος», «Giorgos»). Αν μια επαφή λείπει, πρόσθεσέ την χειροκίνητα στα «Δικά μου».", 13, sub, false);
      d.setPadding(0, dp(6), 0, 0);
      c.addView(d);
      body.addView(c);
      return;
    }
    String[] titles = { "🎉 Σήμερα", "Αύριο", "Αυτή την εβδομάδα", "Αυτόν τον μήνα", "Αργότερα" };
    int[] limits = { 0, 1, 7, 31, 366 };
    int idx = 0, bucket = -1, laterCount = 0;
    Set<Long> laterSeen = new HashSet<>();
    for (ContactSync.Hit h : hits) {
      long dd = EventStore.daysUntil(today, h.date);
      while (idx < limits.length && dd > limits[idx]) idx++;
      if (idx != bucket) { bucket = idx; section(titles[Math.min(idx, 4)], null, null); }
      if (idx <= 1) body.addView(contactCard(h, today, true));
      else {
        if (idx == 4) { if (!laterSeen.add(h.person.id) || ++laterCount > 150) continue; } // μόνο η πρώτη ανά επαφή
        body.addView(contactRow(h, today));
      }
    }
  }

  /** Μεγάλη κάρτα με κουμπιά ευχών. */
  View contactCard(ContactSync.Hit h, LocalDate today, boolean buttons) {
    LinearLayout c = card();
    LinearLayout r = row();
    r.addView(avatar(h.person.displayName, (int) h.person.id));
    LinearLayout t = col();
    t.setPadding(dp(14), 0, 0, 0);
    t.addView(tv(h.person.displayName, 16, ink, true));
    long dd = EventStore.daysUntil(today, h.date);
    String what = h.birthday ? "🎂 Γενέθλια" + (h.person.bYear > 0 ? " · " + (h.date.getYear() - h.person.bYear) + " χρόνια" : "") : "🎉 Γιορτή · " + h.reason;
    t.addView(tv(what + " · " + (dd <= 1 ? when(dd) : h.date.format(F_DAY)), 13, dd == 0 ? pink : sub, dd == 0));
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    c.addView(r);
    if (buttons) {
      LinearLayout b = row();
      b.setPadding(0, dp(14), 0, 0);
      addWishButton(b, "SMS", true, () -> Wishes.sms(this, h));
      addWishButton(b, "WhatsApp", false, () -> Wishes.whatsapp(this, h));
      addWishButton(b, "Viber", false, () -> Wishes.viber(this, h));
      addWishButton(b, "⋯", false, () -> wishMenu(h));
      c.addView(b);
    } else clickable(c, round(surface, 22), 22, () -> wishMenu(h));
    return c;
  }

  void addWishButton(LinearLayout parent, String label, boolean filled, Runnable r) {
    TextView b = button(label, filled, r);
    b.setPadding(dp(4), 0, dp(4), 0);
    fitOneLine(b);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), label.equals("⋯") ? 0.55f : 1);
    p.rightMargin = dp(6);
    parent.addView(b, p);
  }

  /** Μία γραμμή, ολόκληρη η λέξη: μικραίνει τα γράμματα μέχρι να χωρέσουν στο πλάτος του κουμπιού. */
  void fitOneLine(TextView t) {
    t.setMaxLines(1);
    t.setEllipsize(null);
    final float max = t.getTextSize(), min = max * 0.55f;
    t.addOnLayoutChangeListener((v, l, top, r, bot, ol, ot, or, ob) -> {
      int avail = (r - l) - t.getPaddingLeft() - t.getPaddingRight();
      if (avail <= 0) return;
      String txt = t.getText().toString();
      android.text.TextPaint p = new android.text.TextPaint(t.getPaint());
      float size = max;
      p.setTextSize(size);
      while (size > min && p.measureText(txt) > avail) { size -= 0.5f; p.setTextSize(size); }
      if (Math.abs(size - t.getTextSize()) > 0.1f) {
        final float fs = size;
        t.post(() -> t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, fs));
      }
    });
  }

  View contactRow(ContactSync.Hit h, LocalDate today) {
    String what = h.birthday ? "🎂 Γενέθλια" : "🎉 " + h.reason;
    return simpleRow(h.date, h.person.displayName, what + " · " + when(EventStore.daysUntil(today, h.date)), today, () -> wishMenu(h));
  }

  void wishMenu(ContactSync.Hit h) {
    String msg = Wishes.message(this, h);
    boolean blocked = AutoWishes.isBlocked(this, h.person);
    String[] items = { "💬 SMS", "🟢 WhatsApp", "🟣 Viber", "📤 Άλλη εφαρμογή", "📞 Κλήση", "📋 Αντιγραφή μηνύματος",
        blocked ? "✅ Να παίρνει αυτόματο SMS" : "🚫 Να μην παίρνει αυτόματο SMS" };
    new AlertDialog.Builder(this).setTitle(h.person.displayName + "\n«" + msg + "»").setItems(items, (d, w) -> {
      switch (w) {
        case 0: Wishes.sms(this, h); break;
        case 1: Wishes.whatsapp(this, h); break;
        case 2: Wishes.viber(this, h); break;
        case 3: Wishes.share(this, h, null); break;
        case 4: Wishes.call(this, h); break;
        case 6:
          AutoWishes.setBlocked(this, h.person, !blocked);
          toast(blocked ? "Θα παίρνει αυτόματο SMS" : "Δεν θα παίρνει αυτόματο SMS");
          break;
        default:
          android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
          if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Ευχές", msg));
          toast("Αντιγράφηκε");
      }
    }).setNegativeButton("Κλείσιμο", null).show();
  }

  // ======================================================================
  // ΔΙΚΑ ΜΟΥ
  // ======================================================================
  void personal(LocalDate today) {
    TextView add = button("+  Νέα ημερομηνία", true, () -> edit(null, null));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(8);
    body.addView(add, p);
    List<EventStore.Event> list = new ArrayList<>(store.editable());
    if (list.isEmpty()) {
      LinearLayout c = card();
      c.addView(tv("Δεν έχεις προσθέσει ακόμα τίποτα", 16, ink, true));
      TextView d = tv("Πρόσθεσε γενέθλια, επετείους ή γιορτές που δεν υπάρχουν στο εορτολόγιο. Θα σε ειδοποιώ από την προηγούμενη μέρα.", 14, sub, false);
      d.setPadding(0, dp(6), 0, 0);
      c.addView(d);
      body.addView(c);
      return;
    }
    // Υπολογισμός next() μία φορά ανά γεγονός (όχι μέσα στον comparator)
    Map<EventStore.Event, LocalDate> next = new HashMap<>();
    for (EventStore.Event e : list) next.put(e, e.next(today));
    list.sort(Comparator.comparing(next::get));
    for (EventStore.Event e : list) {
      LocalDate d = next.get(e);
      int y = e.yearsAt(d);
      String icon = EventStore.CAT_BIRTHDAY.equals(e.category) ? "🎂 " : EventStore.CAT_NAME.equals(e.category) ? "🎉 " : EventStore.CAT_ANNIVERSARY.equals(e.category) ? "💍 " : "⭐ ";
      body.addView(simpleRow(d, icon + e.title, e.category + (y > 0 ? " · " + y + " χρόνια" : "") + " · " + when(EventStore.daysUntil(today, d)), today, () -> edit(e, null)));
    }
  }

  void editAt(LocalDate d) {
    edit(new EventStore.Event(null, "", EventStore.CAT_PERSONAL, d.getDayOfMonth(), d.getMonthValue(), 0, 0, false, true), null);
  }

  /** Φόρμα προσθήκης/επεξεργασίας με επιλογή ημερομηνίας από ημερολόγιο (όχι πληκτρολόγηση αριθμών). */
  void edit(EventStore.Event e, String presetTitle) {
    boolean fresh = e == null || e.id == null;
    LocalDate now = LocalDate.now();
    final int[] date = { e != null ? e.day : now.getDayOfMonth(), e != null ? e.month : now.getMonthValue(), e != null ? e.year : 0 };
    LinearLayout form = col();
    form.setPadding(dp(22), dp(8), dp(22), 0);
    EditText name = new EditText(this);
    name.setHint("Όνομα ή γεγονός (π.χ. Γενέθλια Μαρίας)");
    name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    if (e != null) name.setText(e.title);
    else if (presetTitle != null) name.setText(presetTitle);
    form.addView(name);

    String[] cats = { EventStore.CAT_BIRTHDAY, EventStore.CAT_NAME, EventStore.CAT_ANNIVERSARY, EventStore.CAT_PERSONAL };
    String[] catLabels = { "🎂 Γενέθλια", "🎉 Γιορτή", "💍 Επέτειος", "⭐ Άλλο" };
    RadioGroup rg = new RadioGroup(this);
    rg.setOrientation(RadioGroup.HORIZONTAL);
    HorizontalScrollView hs = new HorizontalScrollView(this);
    hs.addView(rg);
    int selCat = 3;
    for (int i = 0; i < cats.length; i++) {
      RadioButton rb = new RadioButton(this);
      rb.setText(catLabels[i]);
      rb.setId(1000 + i);
      rg.addView(rb);
      if (e != null && cats[i].equals(e.category)) selCat = i;
    }
    rg.check(1000 + selCat);
    form.addView(hs);

    TextView pick = button("", false, () -> { });
    LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
    pp.topMargin = dp(10);
    form.addView(pick, pp);
    CheckBox withYear = new CheckBox(this);
    withYear.setText("Να μετράει τα χρόνια (π.χ. ηλικία)");
    withYear.setChecked(date[2] > 0);
    form.addView(withYear);

    Runnable refresh = () -> {
      String s = "📅  " + date[0] + " " + java.time.Month.of(date[1]).getDisplayName(java.time.format.TextStyle.FULL, EL);
      if (withYear.isChecked() && date[2] > 0) s += " " + date[2];
      pick.setText(s + "   ·  αλλαγή");
    };
    refresh.run();
    pick.setOnClickListener(v -> {
      int y = date[2] > 0 ? date[2] : now.getYear();
      DatePickerDialog dp = new DatePickerDialog(this, (view, yy, mm, dd) -> {
        date[0] = dd; date[1] = mm + 1;
        if (withYear.isChecked()) date[2] = yy;
        refresh.run();
      }, y, date[1] - 1, Math.min(date[0], YearMonth.of(y, date[1]).lengthOfMonth()));
      dp.show();
    });
    withYear.setOnCheckedChangeListener((b, on) -> {
      if (on && date[2] == 0) { date[2] = now.getYear(); toast("Επίλεξε και το έτος από την ημερομηνία"); }
      refresh.run();
    });

    AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(fresh ? "Νέα ημερομηνία" : "Επεξεργασία").setView(form).setNegativeButton("Άκυρο", null);
    if (!fresh) b.setNeutralButton("Διαγραφή", (d, w) -> new AlertDialog.Builder(this).setMessage("Διαγραφή του «" + e.title + "»;")
        .setNegativeButton("Όχι", null).setPositiveButton("Διαγραφή", (d2, w2) -> { store.delete(e.id); searchIndex = null; render(); }).show());
    b.setPositiveButton("Αποθήκευση", null);
    AlertDialog dlg = b.create();
    dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      String title = name.getText().toString().trim();
      if (title.isEmpty()) { name.setError("Γράψε ένα όνομα"); return; }
      int year = withYear.isChecked() ? date[2] : 0;
      if (year > now.getYear()) { toast("Το έτος είναι στο μέλλον"); return; }
      String cat = cats[Math.max(0, rg.getCheckedRadioButtonId() - 1000)];
      store.save(new EventStore.Event(fresh ? "user:" + UUID.randomUUID() : e.id, title, cat, date[0], date[1], 0, year, false, true));
      ReminderReceiver.schedule(this);
      toast("Αποθηκεύτηκε ✓");
      dlg.dismiss();
      render();
    }));
    dlg.show();
  }

  // ======================================================================
  // ΡΥΘΜΙΣΕΙΣ
  // ======================================================================
  void settings() {
    section("🎨 Εμφάνιση", null, null);
    LinearLayout ap = card();
    String th = prefs.getString("theme", "auto");
    String[] thKeys = { "auto", "light", "dark" };
    String[] thNames = { "🌗 Αυτόματο (όπως το κινητό)", "☀️ Φωτεινό", "🌙 Σκοτεινό" };
    int cur = "light".equals(th) ? 1 : "dark".equals(th) ? 2 : 0;
    ap.addView(settingRow("Θέμα", thNames[cur], () -> new AlertDialog.Builder(this).setTitle("Θέμα εμφάνισης")
        .setSingleChoiceItems(thNames, cur, (d, w) -> {
          d.dismiss();
          if (w != cur) { prefs.edit().putString("theme", thKeys[w]).apply(); recreate(); }
        }).setNegativeButton("Άκυρο", null).show()));
    body.addView(ap);

    section("🔔 Ειδοποιήσεις", null, null);
    LinearLayout n = card();
    n.addView(toggle("Ημερήσια ειδοποίηση", "Κάθε μέρα στην ώρα που ορίζεις", "notifications", true, on -> {
      if (on) askNotifications();
      ReminderReceiver.schedule(this);
    }));
    n.addView(divider());
    n.addView(settingRow("Ώρα ειδοποίησης", String.format(EL, "%02d:%02d", prefs.getInt("hour", 9), prefs.getInt("minute", 0)), () ->
        new TimePickerDialog(this, (v, h, m) -> {
          prefs.edit().putInt("hour", h).putInt("minute", m).apply();
          ReminderReceiver.schedule(this);
          render();
        }, prefs.getInt("hour", 9), prefs.getInt("minute", 0), true).show()));
    n.addView(divider());
    n.addView(toggle("Ονομαστικές γιορτές της ημέρας", "«Σήμερα γιορτάζουν: …» κάθε πρωί", "notifyNames", true, null));
    n.addView(divider());
    n.addView(toggle("Μόνο όταν γιορτάζει επαφή μου", "Λιγότερες ειδοποιήσεις, μόνο για τους δικούς σου", "onlyContacts", false, null));
    n.addView(divider());
    int lead = prefs.getInt("lead", 1);
    n.addView(settingRow("Προειδοποίηση για τα «Δικά μου»", lead == 0 ? "Μόνο την ημέρα" : lead + (lead == 1 ? " ημέρα πριν" : " ημέρες πριν"), () -> {
      String[] o = { "Μόνο την ημέρα", "1 ημέρα πριν", "2 ημέρες πριν", "3 ημέρες πριν", "1 εβδομάδα πριν" };
      int[] v = { 0, 1, 2, 3, 7 };
      new AlertDialog.Builder(this).setTitle("Προειδοποίηση").setItems(o, (d, w) -> { prefs.edit().putInt("lead", v[w]).apply(); render(); }).show();
    }));
    n.addView(divider());
    int s1 = prefs.getInt("snooze1", 60), s2 = prefs.getInt("snooze2", 180);
    n.addView(settingRow("Κουμπιά αναβολής (snooze)", ReminderReceiver.label(s1) + " · " + ReminderReceiver.label(s2), () -> {
      String[] o = { "15′ και 1 ώρα", "30′ και 2 ώρες", "1 ώρα και 3 ώρες", "2 ώρες και 5 ώρες" };
      int[][] v = { { 15, 60 }, { 30, 120 }, { 60, 180 }, { 120, 300 } };
      new AlertDialog.Builder(this).setTitle("Αναβολή ειδοποίησης").setItems(o, (d, w) -> {
        prefs.edit().putInt("snooze1", v[w][0]).putInt("snooze2", v[w][1]).apply();
        render();
      }).show();
    }));
    n.addView(divider());
    n.addView(settingRow("Δοκιμαστική ειδοποίηση", "Δες πώς φαίνεται τώρα", () -> {
      if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { askNotifications(); return; }
      new Thread(() -> {
        boolean ok = ReminderReceiver.post(getApplicationContext(), false);
        runOnUiThread(() -> toast(ok ? "Στάλθηκε ✓" : "Τίποτα για σήμερα (ή οι ειδοποιήσεις είναι κλειστές)"));
      }).start();
    }));
    body.addView(n);

    section("🔋 Μπαταρία", null, null);
    LinearLayout bt = card();
    boolean ok = batteryOk();
    bt.addView(settingRow(ok ? "✓ Χωρίς περιορισμούς" : "⚠️ Περιορισμένη από το σύστημα",
        ok ? "Οι ειδοποιήσεις θα έρχονται στην ώρα τους" : "Πάτα για εξαίρεση — αλλιώς η ειδοποίηση μπορεί να καθυστερεί", this::askBattery));
    TextView note = tv("Η εφαρμογή δεν τρέχει στο παρασκήνιο: ξυπνάει μία φορά την ημέρα για λίγα δευτερόλεπτα. Σε Xiaomi/Huawei/Samsung ενεργοποίησε επίσης την «Αυτόματη εκκίνηση» αν υπάρχει.", 12, sub, false);
    note.setPadding(0, dp(10), 0, 0);
    bt.addView(note);
    body.addView(bt);

    section("👥 Επαφές & ευχές", null, null);
    LinearLayout ct = card();
    ct.addView(toggle("Συγχρονισμός επαφών", ContactSync.granted(this) ? "Ταίριασμα ονομάτων & γενεθλίων" : "Χρειάζεται άδεια — πάτα για ενεργοποίηση", "contacts", true, on -> {
      ContactSync.invalidate();
      if (on && !ContactSync.granted(this)) askContacts();
    }));
    ct.addView(divider());
    ct.addView(autoSmsToggle());
    if (prefs.getBoolean("autoSms", false)) {
      ct.addView(divider());
      ct.addView(settingRow("Σε ποιους στέλνει αυτόματα", AutoWishes.modeLabel(prefs.getString("autoSmsMode", AutoWishes.MODE_BOTH)), () -> chooseAutoMode(null)));
      ct.addView(divider());
      ct.addView(toggle("Ερώτηση πριν την αποστολή", "Ειδοποίηση με [Αποστολή τώρα] / [Όχι σήμερα] — αλλιώς στέλνονται μόνα τους σε 1 ώρα", "autoSmsConfirm", true, null));
      ct.addView(divider());
      int nb = AutoWishes.blocked(this).size();
      ct.addView(settingRow("Ποιες επαφές παίρνουν SMS", nb == 0 ? "Όλες (καμία εξαίρεση)" : nb + (nb == 1 ? " επαφή εξαιρείται" : " επαφές εξαιρούνται"), this::chooseSmsContacts));
      ct.addView(divider());
      ct.addView(settingRow("Ιστορικό αυτόματων SMS", "Τι στάλθηκε και πότε", () ->
          new AlertDialog.Builder(this).setTitle("Ιστορικό αποστολών").setMessage(AutoWishes.history(this)).setPositiveButton("OK", null).show()));
    }
    ct.addView(divider());
    ct.addView(settingRow("Μήνυμα για γιορτή", prefs.getString("tplNameday", Wishes.DEFAULT_NAMEDAY), () -> editTemplate("tplNameday", Wishes.DEFAULT_NAMEDAY)));
    ct.addView(divider());
    ct.addView(settingRow("Μήνυμα για γενέθλια", prefs.getString("tplBirthday", Wishes.DEFAULT_BIRTHDAY), () -> editTemplate("tplBirthday", Wishes.DEFAULT_BIRTHDAY)));
    body.addView(ct);

    section("⬆️ Ενημερώσεις", null, null);
    LinearLayout up = card();
    up.addView(toggle("Αυτόματος έλεγχος", "Κάθε φορά που ανοίγεις την εφαρμογή (ανά 6 ώρες)", "autoUpdate", true, null));
    up.addView(divider());
    up.addView(settingRow("Έλεγχος τώρα", "Έκδοση " + OtaUpdater.currentVersion(this), () -> OtaUpdater.check(this, true)));
    body.addView(up);
    section("ℹ️ Σχετικά", null, null);
    LinearLayout ab = card();
    LinearLayout head = row();
    TextView logo = tv("🎉", 26, Color.WHITE, false);
    logo.setGravity(Gravity.CENTER);
    logo.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] { G1, G2, G3 }) {{ setCornerRadius(dp(16)); }});
    head.addView(logo, new LinearLayout.LayoutParams(dp(56), dp(56)));
    LinearLayout ht = col();
    ht.setPadding(dp(14), 0, 0, 0);
    ht.addView(tv("Εορτολόγιο+", 18, ink, true));
    ht.addView(tv("Έκδοση " + OtaUpdater.currentVersion(this), 13, sub, false));
    head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
    ab.addView(head);
    TextView by = tv("Ανάπτυξη: Νικόλαος Κ. Παρίδης\nALU DEV TEAM · Πτολεμαΐδα", 14, ink, false);
    by.setPadding(0, dp(14), 0, dp(4));
    ab.addView(by);
    ab.addView(tv("Ελληνικό εορτολόγιο με υπενθυμίσεις και ευχές στις επαφές σου. Όλα τα δεδομένα μένουν στη συσκευή.", 13, sub, false));
    ab.addView(divider());
    ab.addView(settingRow("GitHub", "github.com/nikkpap/EortologioGR", () ->
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nikkpap/EortologioGR")))));
    ab.addView(divider());
    ab.addView(settingRow("Τι νέο", "Σημειώσεις εκδόσεων", () ->
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nikkpap/EortologioGR/releases")))));
    TextView cp = tv("© " + LocalDate.now().getYear() + " ALU DEV TEAM", 12, sub, false);
    cp.setGravity(Gravity.CENTER);
    cp.setPadding(0, dp(12), 0, 0);
    ab.addView(cp);
    body.addView(ab);

    if (prefs.getBoolean("guideDone", false)) {
      TextView g = tv("Εμφάνιση οδηγού ρύθμισης ξανά", 14, accent, true);
      g.setPadding(dp(4), dp(12), 0, 0);
      g.setOnClickListener(v -> { prefs.edit().putBoolean("guideDone", false).apply(); page = "today"; render(); });
      body.addView(g);
    }
  }

  private static final int RC_SMS = 14;

  /** Διακόπτης αυτόματων SMS: πάντα με επιβεβαίωση και προειδοποίηση χρεώσεων. */
  View autoSmsToggle() {
    LinearLayout r = row();
    r.setPadding(0, dp(6), 0, dp(6));
    LinearLayout t = col();
    t.addView(tv("✉️ Αυτόματες ευχές με SMS", 15, ink, true));
    boolean on = prefs.getBoolean("autoSms", false);
    String d = on ? (AutoWishes.hasPermission(this) ? "Ενεργό · στέλνει κάθε μέρα στις " + String.format(EL, "%02d:%02d", prefs.getInt("hour", 9), prefs.getInt("minute", 0))
        : "⚠️ Λείπει η άδεια SMS — πάτα για ενεργοποίηση") : "Στέλνει μόνο του «Χρόνια πολλά» σε όσους γιορτάζουν";
    t.addView(tv(d, 12, on && !AutoWishes.hasPermission(this) ? pink : sub, false));
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    Switch sw = new Switch(this);
    sw.setChecked(on);
    sw.setOnClickListener(v -> {
      if (sw.isChecked()) { sw.setChecked(false); confirmAutoSms(); }
      else { prefs.edit().putBoolean("autoSms", false).apply(); toast("Οι αυτόματες ευχές απενεργοποιήθηκαν"); render(); }
    });
    r.addView(sw);
    r.setOnClickListener(v -> sw.performClick());
    return r;
  }

  void confirmAutoSms() {
    if (!ContactSync.granted(this)) { toast("Σύνδεσε πρώτα τις επαφές"); askContacts(); return; }
    new AlertDialog.Builder(this)
        .setTitle("⚠️ Αυτόματη αποστολή SMS")
        .setMessage("Κάθε μέρα στις " + String.format(EL, "%02d:%02d", prefs.getInt("hour", 9), prefs.getInt("minute", 0))
            + " η εφαρμογή θα στέλνει ΜΟΝΗ ΤΗΣ SMS «Χρόνια πολλά» στις επαφές που γιορτάζουν ή έχουν γενέθλια.\n\n"
            + "• Μπορεί να υπάρξουν ΧΡΕΩΣΕΙΣ από τον πάροχό σου (ανάλογα με το πρόγραμμά σου).\n"
            + "• Ένα SMS ανά άτομο την ημέρα, έως " + AutoWishes.DAILY_LIMIT + " την ημέρα.\n"
            + "• Θα βλέπεις ειδοποίηση με το σε ποιους στάλθηκαν και ιστορικό στις Ρυθμίσεις.\n\n"
            + "Θέλεις να το ενεργοποιήσεις;")
        .setNegativeButton("Όχι", null)
        .setPositiveButton("Ναι, συνέχεια", (d, w) -> chooseAutoMode(() -> {
          if (AutoWishes.hasPermission(this)) enableAutoSms();
          else requestPermissions(new String[] { Manifest.permission.SEND_SMS }, RC_SMS);
        })).show();
  }

  void chooseAutoMode(Runnable then) {
    String[] keys = { AutoWishes.MODE_NAMEDAY, AutoWishes.MODE_BIRTHDAY, AutoWishes.MODE_BOTH };
    String[] names = { "🎉 Μόνο σε όσους γιορτάζουν", "🎂 Μόνο σε όσους έχουν γενέθλια", "🎉🎂 Και στα δύο" };
    String cur = prefs.getString("autoSmsMode", AutoWishes.MODE_BOTH);
    int sel = cur.equals(keys[0]) ? 0 : cur.equals(keys[1]) ? 1 : 2;
    new AlertDialog.Builder(this).setTitle("Σε ποιους να στέλνει;")
        .setSingleChoiceItems(names, sel, (d, w) -> {
          prefs.edit().putString("autoSmsMode", keys[w]).apply();
          d.dismiss();
          if (then != null) then.run(); else render();
        }).setNegativeButton("Άκυρο", null).show();
  }

  /** Λίστα επαφών με γιορτή/γενέθλια: τσεκαρισμένη = παίρνει αυτόματο SMS. */
  void chooseSmsContacts() {
    List<ContactSync.Person> ppl = new ArrayList<>();
    for (ContactSync.Person p : ContactSync.load(this)) if (p.phone != null) ppl.add(p);
    if (ppl.isEmpty()) { toast("Δεν βρέθηκαν επαφές με γιορτή/γενέθλια και τηλέφωνο"); return; }
    String[] names = new String[ppl.size()];
    boolean[] checked = new boolean[ppl.size()];
    Set<String> block = AutoWishes.blocked(this);
    for (int i = 0; i < ppl.size(); i++) {
      ContactSync.Person p = ppl.get(i);
      String what = p.canon.isEmpty() ? "γενέθλια" : TextUtils.join("/", p.canon);
      names[i] = p.displayName + "  ·  " + what;
      checked[i] = !block.contains(AutoWishes.blockKey(p));
    }
    AlertDialog dlg = new AlertDialog.Builder(this).setTitle("✅ = παίρνει αυτόματο SMS")
        .setMultiChoiceItems(names, checked, (d, w, on) -> checked[w] = on)
        .setNeutralButton("Όλοι / Κανένας", null)
        .setNegativeButton("Άκυρο", null)
        .setPositiveButton("Αποθήκευση", (d, w) -> {
          for (int i = 0; i < ppl.size(); i++) AutoWishes.setBlocked(this, ppl.get(i), !checked[i]);
          toast("Αποθηκεύτηκε ✓");
          render();
        }).create();
    dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
      boolean any = false;
      for (boolean c : checked) any |= c;
      for (int i = 0; i < checked.length; i++) { checked[i] = !any; dlg.getListView().setItemChecked(i, !any); }
    }));
    dlg.show();
  }

  void enableAutoSms() {
    prefs.edit().putBoolean("autoSms", true).apply();
    ReminderReceiver.schedule(this);
    toast("Οι αυτόματες ευχές ενεργοποιήθηκαν ✓");
    render();
  }

  interface OnToggle { void on(boolean v); }

  View toggle(String title, String detail, String key, boolean def, OnToggle cb) {
    LinearLayout r = row();
    r.setPadding(0, dp(6), 0, dp(6));
    LinearLayout t = col();
    t.addView(tv(title, 15, ink, true));
    t.addView(tv(detail, 12, sub, false));
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    Switch sw = new Switch(this);
    sw.setChecked(prefs.getBoolean(key, def));
    sw.setOnCheckedChangeListener((v, on) -> {
      prefs.edit().putBoolean(key, on).apply();
      if (cb != null) cb.on(on);
    });
    r.addView(sw);
    r.setOnClickListener(v -> sw.toggle());
    return r;
  }

  View settingRow(String title, String value, Runnable action) {
    LinearLayout r = row();
    r.setPadding(0, dp(8), 0, dp(8));
    LinearLayout t = col();
    t.addView(tv(title, 15, ink, true));
    TextView v = tv(value, 13, sub, false);
    v.setMaxLines(2);
    v.setEllipsize(TextUtils.TruncateAt.END);
    t.addView(v);
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    r.addView(tv("›", 22, sub, false));
    clickable(r, round(Color.TRANSPARENT, 10), 10, action);
    return r;
  }

  View divider() {
    View v = new View(this);
    v.setBackgroundColor(line);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.8f)));
    p.topMargin = p.bottomMargin = dp(4);
    v.setLayoutParams(p);
    return v;
  }

  void editTemplate(String key, String def) {
    EditText e = new EditText(this);
    e.setText(prefs.getString(key, def));
    e.setMinLines(3);
    LinearLayout f = col();
    f.setPadding(dp(22), dp(8), dp(22), 0);
    f.addView(e);
    f.addView(tv("Το {name} αντικαθίσταται αυτόματα με το όνομα στην κλητική (π.χ. «Νίκο», «Γιώργο»).", 12, sub, false));
    new AlertDialog.Builder(this).setTitle("Μήνυμα ευχών").setView(f)
        .setNeutralButton("Προεπιλογή", (d, w) -> { prefs.edit().remove(key).apply(); render(); })
        .setNegativeButton("Άκυρο", null)
        .setPositiveButton("Αποθήκευση", (d, w) -> { prefs.edit().putString(key, e.getText().toString()).apply(); render(); }).show();
  }

  // ======================================================================
  // Άδειες & μπαταρία
  // ======================================================================
  void askNotifications() {
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      if (prefs.getBoolean("askedNotif", false) && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try { startActivity(i); } catch (Exception ex) { openAppSettings(null); }
      } else {
        prefs.edit().putBoolean("askedNotif", true).apply();
        requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, RC_NOTIF);
      }
    } else toast("Οι ειδοποιήσεις είναι ήδη ενεργές ✓");
  }

  void askContacts() {
    prefs.edit().putBoolean("contacts", true).apply();
    if (!ContactSync.granted(this)) requestPermissions(new String[] { Manifest.permission.READ_CONTACTS }, RC_CONTACTS);
    else { ContactSync.invalidate(); render(); }
  }

  boolean batteryOk() {
    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
    return pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());
  }

  void askBattery() {
    if (batteryOk()) { toast("Η μπαταρία είναι ήδη χωρίς περιορισμό ✓"); return; }
    try {
      startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
    } catch (Exception e) {
      try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
      catch (Exception e2) { openAppSettings("Βρες το Εορτολόγιο+ → Μπαταρία → «Χωρίς περιορισμούς»."); }
    }
  }

  void openAppSettings(String msg) {
    Runnable go = () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    if (msg == null) { go.run(); return; }
    new AlertDialog.Builder(this).setMessage(msg).setNegativeButton("Άκυρο", null).setPositiveButton("Ρυθμίσεις", (d, w) -> go.run()).show();
  }
}
