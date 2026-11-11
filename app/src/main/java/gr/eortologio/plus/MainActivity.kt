package gr.eortologio.plus

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Ενέργειες που χρειάζονται άδειες / άλλες εφαρμογές — διαθέσιμες σε όλες τις οθόνες. */
class Actions(
    val askNotifications: () -> Unit,
    val askContacts: () -> Unit,
    val askBattery: () -> Unit,
    val startAutoSms: () -> Unit,
    val pickContact: ((String, String?) -> Unit) -> Unit,
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit,
    val openWish: (Hit) -> Unit,
    val openName: (String) -> Unit,
    val edit: (EditRequest) -> Unit,
    val message: (Dlg) -> Unit,
    val choose: (Choice) -> Unit,
    val openAppSettings: () -> Unit,
)

val LocalActions = staticCompositionLocalOf<Actions> { error("Actions not provided") }

/** Απλός διάλογος μηνύματος. */
data class Dlg(
    val title: String?, val text: String, val confirm: String = "OK", val onConfirm: (() -> Unit)? = null,
    val dismiss: String? = null,
)

/** Διάλογος επιλογής μίας τιμής. */
data class Choice(val title: String, val options: List<String>, val selected: Int, val onPick: (Int) -> Unit)

/** Αίτημα φόρμας «Δικά μου». */
data class EditRequest(val event: Event? = null, val presetTitle: String? = null, val presetDate: LocalDate? = null)

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        T.init(this)
        when (Prefs.sp(this).getString("theme", "auto")) {
            "light" -> setTheme(R.style.AppTheme_Light)
            "dark" -> setTheme(R.style.AppTheme_Dark)
        }
        super.onCreate(savedInstanceState)
        val dark = isDark()
        val clear = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(clear), // λευκά εικονίδια πάνω στο gradient
            navigationBarStyle = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear),
        )
        if (savedInstanceState == null) handleIntent(intent)
        Thread { try { ReminderReceiver.schedule(applicationContext) } catch (_: Exception) {} }.start()
        setContent { AppTheme(dark) { App(vm) } }
        vm.checkOta(manual = false)
    }

    private fun isDark(): Boolean = when (Prefs.sp(this).getString("theme", "auto")) {
        "dark" -> true
        "light" -> false
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh() // ενημερώνει κατάσταση αδειών/μπαταρίας όταν επιστρέφουμε από τις Ρυθμίσεις
        val app = applicationContext
        Thread { TodayWidget.updateAll(app) }.start()
    }

    private fun handleIntent(i: Intent?) {
        i?.getStringExtra("page")?.let { vm.go(it) }
    }
}

@Composable
fun App(vm: AppViewModel) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val sp = remember { Prefs.sp(ctx) }
    val p = LocalPal.current

    var wish by remember { mutableStateOf<Hit?>(null) }
    var nameSheet by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<EditRequest?>(null) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var choice by remember { mutableStateOf<Choice?>(null) }
    var smsWarning by remember { mutableStateOf(false) }
    var pickCb by remember { mutableStateOf<((String, String?) -> Unit)?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    fun toast(s: String) = Wishes.toast(ctx, s)
    fun openAppSettings() {
        try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))) } catch (_: Exception) {}
    }
    fun smsHelp() {
        dlg = Dlg(
            T.t("🔒 Άδεια SMS"),
            if (Build.VERSION.SDK_INT >= 33) T.t("Το Android κλειδώνει την άδεια SMS σε εφαρμογές που δεν είναι από το Play Store. Ξεκλείδωμα μία φορά:\n\n1. Πάτα «Ρυθμίσεις» παρακάτω (Πληροφορίες εφαρμογής).\n2. Πάνω δεξιά ⋮ → «Επιτρέψτε τις περιορισμένες ρυθμίσεις» (Allow restricted settings) → δακτυλικό/PIN.\n3. Άδειες → SMS → Να επιτρέπεται.\n4. Γύρνα εδώ και άνοιξε ξανά τις αυτόματες ευχές.\n\nΑν δεν βλέπεις το ⋮, πάτα πρώτα μία φορά το γκρι «Να επιτρέπεται» στην άδεια SMS.")
            else T.t("Χωρίς την άδεια «SMS» δεν μπορούν να σταλούν αυτόματες ευχές. Ρυθμίσεις → Άδειες → SMS → Να επιτρέπεται."),
            confirm = T.t("Ρυθμίσεις"), onConfirm = { openAppSettings() }, dismiss = T.t("Άκυρο"),
        )
    }
    fun enableAutoSms() {
        sp.edit().putBoolean("autoSms", true).apply()
        ReminderReceiver.schedule(ctx)
        toast(T.t("Οι αυτόματες ευχές ενεργοποιήθηκαν ✓"))
        vm.refresh()
    }

    // ---------- Άδειες & εξωτερικές εφαρμογές ----------
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ReminderReceiver.schedule(ctx); vm.refresh()
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        ContactSync.invalidate()
        if (ok) {
            sp.edit().putBoolean("contacts", true).apply()
            toast(T.t("Οι επαφές συγχρονίστηκαν ✓"))
        } else if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            dlg = Dlg(null, T.t("Η άδεια επαφών είναι απενεργοποιημένη. Άνοιξε Ρυθμίσεις → Άδειες → Επαφές → Να επιτρέπεται."),
                confirm = T.t("Ρυθμίσεις"), onConfirm = { openAppSettings() }, dismiss = T.t("Άκυρο"))
        }
        vm.refresh()
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) enableAutoSms() else smsHelp()
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        val cb = pickCb
        pickCb = null
        if (uri == null || cb == null) return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) cb(c.getString(0) ?: "", c.getString(1))
            }
        } catch (_: Exception) {
            toast(T.t("Δεν διαβάστηκε η επαφή"))
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            Backup.write(ctx, uri); toast(T.t("Το αντίγραφο αποθηκεύτηκε ✓"))
        } catch (e: Exception) {
            toast(T.t("Αποτυχία: ") + e.message)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    val actions = remember {
        Actions(
            askNotifications = {
                if (!Prefs.canNotify(ctx)) {
                    if (sp.getBoolean("askedNotif", false) && !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                        } catch (_: Exception) { openAppSettings() }
                    } else {
                        sp.edit().putBoolean("askedNotif", true).apply()
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else toast(T.t("Οι ειδοποιήσεις είναι ήδη ενεργές ✓"))
            },
            askContacts = {
                sp.edit().putBoolean("contacts", true).apply()
                ContactSync.invalidate()
                if (!ContactSync.granted(ctx)) contactsLauncher.launch(Manifest.permission.READ_CONTACTS) else vm.refresh()
            },
            askBattery = {
                if (Prefs.batteryOk(ctx)) toast(T.t("Η μπαταρία είναι ήδη χωρίς περιορισμό ✓"))
                else try {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
                } catch (_: Exception) {
                    try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {
                        dlg = Dlg(null, T.t("Βρες το Εορτολόγιο+ → Μπαταρία → «Χωρίς περιορισμούς»."), confirm = T.t("Ρυθμίσεις"),
                            onConfirm = { openAppSettings() }, dismiss = T.t("Άκυρο"))
                    }
                }
            },
            startAutoSms = {
                if (!ContactSync.granted(ctx)) {
                    toast(T.t("Σύνδεσε πρώτα τις επαφές"))
                    sp.edit().putBoolean("contacts", true).apply()
                    contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                } else smsWarning = true
            },
            pickContact = { cb ->
                pickCb = cb
                try {
                    pickLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                } catch (_: Exception) {
                    pickCb = null; toast(T.t("Δεν βρέθηκε εφαρμογή επαφών"))
                }
            },
            exportBackup = {
                try { exportLauncher.launch("eortologio-backup-" + LocalDate.now() + ".json") } catch (_: Exception) { toast(T.t("Δεν υποστηρίζεται σε αυτή τη συσκευή")) }
            },
            importBackup = {
                try { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) } catch (_: Exception) { toast(T.t("Δεν υποστηρίζεται σε αυτή τη συσκευή")) }
            },
            openWish = { wish = it },
            openName = { nameSheet = it },
            edit = { edit = it },
            message = { dlg = it },
            choose = { choice = it },
            openAppSettings = { openAppSettings() },
        )
    }

    // Πρώτη φορά (Android 13+): άδεια ειδοποιήσεων
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !sp.getBoolean("askedNotif", false) && !Prefs.canNotify(ctx)) {
            sp.edit().putBoolean("askedNotif", true).apply()
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = vm.query.isNotEmpty() || vm.page != "today") {
        if (vm.query.isNotEmpty()) vm.query = "" else vm.go("today")
    }

    CompositionLocalProvider(LocalActions provides actions) {
        Scaffold(
            containerColor = p.bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                val (title, sub, emoji) = pageTitle(vm.page)
                Header(title, sub, emoji)
            },
            bottomBar = { BottomNav(vm.page) { vm.go(it) } },
            floatingActionButton = {
                if (vm.page == "personal") ExtendedFloatingActionButton(
                    onClick = { edit = EditRequest() },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text(T.t("Νέα ημερομηνία"), fontWeight = FontWeight.SemiBold) },
                    containerColor = p.accent, contentColor = Color.White,
                )
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                Crossfade(targetState = vm.page, animationSpec = tween(160), label = "page") { page ->
                    when (page) {
                        "calendar" -> CalendarScreen(vm)
                        "contacts" -> ContactsScreen(vm)
                        "personal" -> PersonalScreen(vm)
                        "settings" -> SettingsScreen(vm)
                        else -> TodayScreen(vm)
                    }
                }
            }
        }

        // ---------- Διάλογοι / φύλλα ----------
        wish?.let { WishSheet(it, onDismiss = { wish = null; vm.refresh() }) }
        nameSheet?.let { n -> NameSheet(n, vm, onDismiss = { nameSheet = null }) }
        edit?.let { r -> EditDialog(r, onDismiss = { edit = null }, onSaved = { edit = null; vm.refresh() }) }
        choice?.let { c -> ChoiceDialog(c, onDismiss = { choice = null }) }
        dlg?.let { d -> MessageDialog(d, onDismiss = { dlg = null }) }
        if (smsWarning) SmsWarningDialog(
            onDismiss = { smsWarning = false },
            onYes = {
                smsWarning = false
                chooseAutoMode(ctx, actions) {
                    when {
                        AutoWishes.hasPermission(ctx) -> enableAutoSms()
                        sp.getBoolean("askedSms", false) && !activity.shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS) -> smsHelp()
                        else -> {
                            sp.edit().putBoolean("askedSms", true).apply()
                            smsLauncher.launch(Manifest.permission.SEND_SMS)
                        }
                    }
                }
            },
        )
        pendingImport?.let { uri ->
            MessageDialog(
                Dlg(T.t("Επαναφορά;"), T.t("Θα αντικατασταθούν τα «Δικά μου» και οι ρυθμίσεις με αυτά του αντιγράφου."),
                    confirm = T.t("Επαναφορά"), dismiss = T.t("Άκυρο"), onConfirm = {
                        try {
                            val n = Backup.restore(ctx, uri)
                            ReminderReceiver.schedule(ctx)
                            toast(T.t("Επαναφέρθηκαν ") + n + T.t(" ημερομηνίες ✓"))
                            T.init(ctx)
                            activity.recreate()
                        } catch (e: Exception) {
                            toast(T.t("Αποτυχία: ") + e.message)
                        }
                    }),
                onDismiss = { pendingImport = null },
            )
        }
        OtaDialogs(vm)
    }
}

/** Επιλογή «σε ποιους στέλνει» και μετά συνέχεια. */
fun chooseAutoMode(ctx: android.content.Context, actions: Actions, then: (() -> Unit)?) {
    val keys = listOf(AutoWishes.MODE_NAMEDAY, AutoWishes.MODE_BIRTHDAY, AutoWishes.MODE_BOTH)
    val names = listOf(T.t("🎉 Μόνο σε όσους γιορτάζουν"), T.t("🎂 Μόνο σε όσους έχουν γενέθλια"), T.t("🎉🎂 Και στα δύο"))
    actions.choose(Choice(T.t("Σε ποιους να στέλνει;"), names, keys.indexOf(AutoWishes.mode(ctx)).coerceAtLeast(0)) { w ->
        Prefs.sp(ctx).edit().putString("autoSmsMode", keys[w]).apply()
        then?.invoke()
    })
}

private fun pageTitle(page: String): Triple<String, String, String> = when (page) {
    "calendar" -> Triple(T.t("Ημερολόγιο"), T.t("Όλες οι γιορτές του μήνα"), "📅")
    "contacts" -> Triple(T.t("Επαφές"), T.t("Ποιος γιορτάζει — στείλε ευχές"), "👥")
    "personal" -> Triple(T.t("Δικά μου"), T.t("Γενέθλια, επέτειοι & σημαντικές μέρες"), "⭐")
    "settings" -> Triple(T.t("Ρυθμίσεις"), T.t("Ειδοποιήσεις, επαφές, μπαταρία"), "⚙️")
    else -> Triple(T.t("Χρόνια πολλά!"), Fmt.long(LocalDate.now()), "🎉")
}

@Composable
private fun BottomNav(page: String, onGo: (String) -> Unit) {
    val p = LocalPal.current
    val items = listOf(
        Triple("today", T.t("Σήμερα"), Icons.Rounded.Celebration),
        Triple("calendar", T.t("Ημερολόγιο"), Icons.Rounded.CalendarMonth),
        Triple("contacts", T.t("Επαφές"), Icons.Rounded.Groups),
        Triple("personal", T.t("Δικά μου"), Icons.Rounded.Star),
        Triple("settings", T.t("Ρυθμίσεις"), Icons.Rounded.Settings),
    )
    NavigationBar(containerColor = p.surface, tonalElevation = 0.dp) {
        for ((id, label, icon) in items) {
            NavigationBarItem(
                selected = page == id,
                onClick = { if (page != id) onGo(id) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = p.accent, selectedTextColor = p.accent, indicatorColor = p.accentSoft,
                    unselectedIconColor = p.sub, unselectedTextColor = p.sub,
                ),
            )
        }
    }
}
