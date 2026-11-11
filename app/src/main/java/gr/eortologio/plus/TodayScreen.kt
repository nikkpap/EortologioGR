package gr.eortologio.plus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

val ListPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 96.dp)

@Composable
fun TodayScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val rev = vm.rev
    val today = remember(rev) { LocalDate.now() }
    val data = rememberLoad(rev) { Repo.today(ctx, today) }
    val query = vm.query
    val results = produceState<Repo.SearchData?>(null, query, rev) {
        if (query.isBlank()) { value = null; return@produceState }
        delay(150) // debounce: δεν ψάχνει σε κάθε γράμμα
        value = withContext(Dispatchers.Default) { Repo.search(ctx, query, today) }
    }.value

    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = ListPadding) {
        item(key = "search") { SearchBar(query) { vm.query = it } }
        if (query.isNotBlank()) {
            if (results != null) searchResults(results, today, vm)
        } else if (data != null) {
            todayContent(data, today, vm)
        }
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    val p = LocalPal.current
    val focus = LocalFocusManager.current
    TextField(
        value = query, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        placeholder = { Text(T.t("Αναζήτηση ονόματος, π.χ. Γιώργος, Nikos"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = p.sub) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange(""); focus.clearFocus() }) { Icon(Icons.Rounded.Close, T.t("Καθαρισμός"), tint = p.sub) }
        },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = p.surface, unfocusedContainerColor = p.surface,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = p.accent,
        ),
    )
}

// ---------------------------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.todayContent(d: Repo.TodayData, today: LocalDate, vm: AppViewModel) {
    item(key = "guide") { SetupGuide(vm) }

    item(key = "hero") {
        val p = LocalPal.current
        val a = LocalActions.current
        AppCard {
            TitleText(T.t("🎉 Σήμερα γιορτάζουν"), size = 18)
            if (d.names.isEmpty()) {
                BodyText(T.t("Καμία ονομαστική γιορτή σήμερα στο εορτολόγιο. Δες τις επόμενες παρακάτω 👇"), Modifier.padding(top = 8.dp))
            } else {
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in d.names) Pill(nameLabel(n), onClick = { a.openName(n) })
                }
            }
            for (h in d.holidays) Text("📌 $h", color = p.pink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            if (d.fast.type != Church.Fast.NONE) BodyText(d.fast.icon + " " + d.fast.label, Modifier.padding(top = 10.dp), size = 13)
        }
    }

    d.longWeekend?.let { lw ->
        item(key = "lw") {
            val p = LocalPal.current
            val dd = EventStore.daysUntil(today, lw.start)
            AppCard(color = p.info) {
                TitleText("🏖️ " + (if (lw.days >= 4) lw.days.toString() + T.t("ήμερο") else T.t("Τριήμερο")) + ": " + T.t(lw.name), size = 16)
                BodyText(Fmt.short(lw.start) + " – " + Fmt.short(lw.end) + " · " + (if (dd <= 0) T.t("τώρα!") else Fmt.whenText(dd)) +
                    (if (lw.bridge) T.t(" · με «γέφυρα» 1 ημέρας") else ""), size = 13)
            }
        }
    }

    if (!d.contactsGranted) item(key = "connect") {
        val a = LocalActions.current
        AppCard {
            TitleText(T.t("👥 Ποιος από τους δικούς σου γιορτάζει;"), size = 16)
            BodyText(T.t("Σύνδεσε τις επαφές του κινητού και θα σου λέω ποιος γιορτάζει ή έχει γενέθλια, για να στείλεις «Χρόνια πολλά» με ένα πάτημα. Οι επαφές δεν φεύγουν ποτέ από το κινητό."),
                Modifier.padding(top = 6.dp, bottom = 12.dp))
            MainButton(T.t("Σύνδεση επαφών"), Modifier.fillMaxWidth()) { a.askContacts() }
        }
    }

    if (d.hits.isNotEmpty()) {
        item(key = "hitsTitle") { SectionTitle(T.t("Από τις επαφές σου"), T.t("Όλες ›")) { vm.go("contacts") } }
        items(d.hits, key = { "hit:" + it.person.key + it.date + it.birthday }) { h -> ContactCard(h, today, buttons = true) }
    }

    items(d.personalToday, key = { "pt:" + it.id }) { e ->
        val p = LocalPal.current
        val a = LocalActions.current
        val y = e.yearsAt(today)
        AppCard(color = p.personal, onClick = { a.edit(EditRequest(e)) }) {
            TitleText(Repo.icon(e) + " " + e.title, size = 17)
            BodyText(T.t(e.category) + (if (y > 0) " · " + y + T.t(" χρόνια") else ""))
        }
    }

    item(key = "upTitle") { SectionTitle(T.t("Έρχονται σύντομα"), T.t("Ημερολόγιο ›")) { vm.go("calendar") } }
    items(d.upcoming, key = { "up:" + it.first }) { (day, list) -> DayRow(day, list, today) { vm.openDay(day) } }
}

fun nameLabel(canon: String): String {
    val v = Names.variantsOf(canon)
    return when {
        T.en() -> canon + "  ·  " + Names.latinPretty(canon)
        v.isEmpty() -> canon
        else -> canon + "  ·  " + v[0]
    }
}

@Composable
private fun SetupGuide(vm: AppViewModel) {
    val ctx = LocalContext.current
    val a = LocalActions.current
    val p = LocalPal.current
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val sp = Prefs.sp(ctx)
    if (sp.getBoolean("guideDone", false)) return
    val notif = Prefs.canNotify(ctx)
    val contacts = ContactSync.granted(ctx)
    val battery = Prefs.batteryOk(ctx)
    if (notif && contacts && battery) { sp.edit().putBoolean("guideDone", true).apply(); return }
    AppCard(color = p.guide) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TitleText(T.t("🚀 Γρήγορη ρύθμιση (3 βήματα)"), Modifier.weight(1f), size = 16)
            IconButton(onClick = { sp.edit().putBoolean("guideDone", true).apply(); vm.refresh() }) {
                Icon(Icons.Rounded.Close, T.t("Κλείσιμο"), tint = p.sub)
            }
        }
        GuideStep(1, T.t("Ειδοποιήσεις"), T.t("Για να σου θυμίζω τις γιορτές κάθε πρωί"), notif) { a.askNotifications() }
        GuideStep(2, T.t("Επαφές"), T.t("Για να βρίσκω ποιοι φίλοι σου γιορτάζουν"), contacts) { a.askContacts() }
        GuideStep(3, T.t("Μπαταρία χωρίς περιορισμό"), T.t("Για να έρχονται οι ειδοποιήσεις στην ώρα τους"), battery) { a.askBattery() }
    }
}

/** Κάρτα ημέρας: ημερομηνία + ποιοι γιορτάζουν + αργίες / δικά μου. */
@Composable
fun DayRow(d: LocalDate, list: List<Event>, today: LocalDate, onClick: () -> Unit) {
    val p = LocalPal.current
    val names = list.filter { it.isNameDay }.map { it.canon }.distinct()
    val extra = list.filter { !it.isNameDay }
    AppCard(padding = PaddingValues(start = 12.dp, top = 12.dp, end = 16.dp, bottom = 12.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBadge(d, false)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(Fmt.weekday(d) + " · " + Fmt.whenText(EventStore.daysUntil(today, d)), color = p.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (names.isNotEmpty()) Text(names.joinToString(", "), color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                for (e in extra) Text(
                    (if (e.personal) "⭐ " + e.title else "📌 " + T.t(e.title)),
                    color = if (e.personal) p.pink else p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.searchResults(r: Repo.SearchData, today: LocalDate, vm: AppViewModel) {
    if (r.empty) item(key = "nf") {
        val a = LocalActions.current
        AppCard {
            TitleText(T.t("Δεν βρέθηκε «") + r.query + "»", size = 16)
            BodyText(T.t("Δοκίμασε άλλη γραφή (π.χ. Γιώργος / Γεώργιος / Giorgos) ή πρόσθεσέ το στα «Δικά μου»."), Modifier.padding(top = 6.dp, bottom = 12.dp))
            MainButton(T.t("+ Προσθήκη στα Δικά μου"), filled = false) { a.edit(EditRequest(presetTitle = r.query.trim())) }
        }
    }
    items(r.names, key = { "n:" + it.canon }) { n ->
        val p = LocalPal.current
        val a = LocalActions.current
        AppCard(onClick = { a.openName(n.canon) }) {
            TitleText(n.canon + if (T.en()) "  ·  " + Names.latinPretty(n.canon) else "", size = 19)
            if (n.variants.isNotEmpty()) BodyText(n.variants.take(5).joinToString(" · "), size = 13)
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in n.dates) {
                    val dd = EventStore.daysUntil(today, d)
                    Pill("📅 " + Fmt.day(d) + " · " + Fmt.whenText(dd), onClick = { vm.openDay(d) }, strong = dd <= 1)
                }
            }
            if (n.contactCount > 0) Text(
                "👥 " + n.contactCount + if (n.contactCount == 1) T.t(" επαφή σου έχει αυτό το όνομα") else T.t(" επαφές σου έχουν αυτό το όνομα"),
                color = p.green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
    if (r.people.isNotEmpty()) {
        item(key = "pt") { SectionTitle(T.t("Επαφές")) }
        items(r.people, key = { "p:" + it.person.key + it.date }) { h -> ContactCard(h, today, buttons = false) }
    }
    if (r.others.isNotEmpty()) {
        item(key = "ot") { SectionTitle(T.t("Άλλα")) }
        items(r.others, key = { "o:" + it.first.id }) { (e, d) ->
            val a = LocalActions.current
            DateRow(d, (if (e.personal) Repo.icon(e) + " " + e.title else T.t(e.title)),
                T.t(e.category) + " · " + Fmt.whenText(EventStore.daysUntil(today, d)), today,
                onClick = if (e.personal) ({ a.edit(EditRequest(e)) }) else ({ vm.openDay(d) }))
        }
    }
    item(key = "sp") { Spacer(Modifier.height(8.dp)) }
}
