package gr.eortologio.plus

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.UUID

/** Υπολογισμός στο παρασκήνιο· κρατά την προηγούμενη τιμή μέχρι να έρθει η νέα (χωρίς «αναβόσβημα»). */
@Composable
fun <R> rememberLoad(vararg keys: Any?, calc: () -> R): R? {
    val state = produceState<R?>(initialValue = null, *keys) {
        value = try { withContext(Dispatchers.Default) { calc() } } catch (e: Exception) { value }
    }
    return state.value
}

// ======================================================================
// Ευχές σε επαφή
// ======================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishSheet(h: Hit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val msg = remember(h) { Wishes.message(ctx, h) }
    var blocked by remember { mutableStateOf(AutoWishes.isBlocked(ctx, h.person)) }
    var close by remember { mutableStateOf(Wishes.isClose(ctx, h.person)) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(h.person.displayName, h.person.id)
                Column(Modifier.padding(start = 14.dp)) {
                    TitleText(h.person.displayName, size = 18)
                    BodyText((if (h.birthday) T.t("🎂 Γενέθλια") else T.t("🎉 Γιορτή · ") + h.reason) + " · " + Fmt.day(h.date), size = 13)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("«$msg»", color = p.ink, fontSize = 15.sp, lineHeight = 21.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.accentSoft).padding(14.dp))
            Spacer(Modifier.height(10.dp))
            SheetRow("💬", T.t("Αποστολή με SMS")) { Wishes.sms(ctx, h) }
            SheetRow("🟢", "WhatsApp") { Wishes.whatsapp(ctx, h) }
            SheetRow("🟣", "Viber") { Wishes.viber(ctx, h) }
            SheetRow("📤", T.t("Άλλη εφαρμογή")) { Wishes.share(ctx, h, null) }
            SheetRow("📞", T.t("Κλήση")) { Wishes.call(ctx, h) }
            SheetRow("📋", T.t("Αντιγραφή μηνύματος")) { Wishes.copy(ctx, h) }
            Divider()
            SwitchRow("❤️", T.t("Οικογένεια / φίλος (πιο ζεστή ευχή)"), close) {
                close = it
                Wishes.setClose(ctx, h.person, it)
                Wishes.toast(ctx, if (it) T.t("❤️ Θα παίρνει πιο ζεστή ευχή") else T.t("Θα παίρνει κανονική ευχή"))
            }
            if (AutoWishes.switchedOn(ctx)) SwitchRow("✉️", T.t("Να παίρνει αυτόματο SMS"), !blocked) {
                blocked = !it
                AutoWishes.setBlocked(ctx, h.person, !it)
                Wishes.toast(ctx, if (it) T.t("Θα παίρνει αυτόματο SMS") else T.t("Δεν θα παίρνει αυτόματο SMS"))
            }
        }
    }
}

@Composable
private fun SheetRow(emoji: String, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 13.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 20.sp, modifier = Modifier.width(36.dp))
        Text(label, color = LocalPal.current.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SwitchRow(emoji: String, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!checked) }.padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 20.sp, modifier = Modifier.width(36.dp))
        Text(label, color = LocalPal.current.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

// ======================================================================
// Λεπτομέρειες ονόματος
// ======================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NameSheet(canon: String, vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val today = LocalDate.now()
    val info = rememberLoad(canon) { Repo.nameInfo(ctx, canon, today) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            TitleText(canon + if (T.en()) "  ·  " + Names.latinPretty(canon) else "", size = 22)
            if (info == null) return@Column
            if (info.variants.isNotEmpty()) BodyText(T.t("Παραλλαγές: ") + info.variants.joinToString(", "), Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(14.dp))
            TitleText(T.t("📅 Πότε γιορτάζει"), size = 15)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in info.dates) {
                    val dd = EventStore.daysUntil(today, d)
                    Pill(Fmt.day(d) + " · " + Fmt.whenText(dd), onClick = { onDismiss(); vm.openDay(d) }, strong = dd <= 1)
                }
            }
            if (info.people.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                TitleText(T.t("👥 Από τις επαφές σου"), size = 15)
                BodyText(info.people.joinToString(", ") { it.displayName }, Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                MainButton(T.t("Στείλε ευχές"), Modifier.fillMaxWidth()) { onDismiss(); vm.go("contacts") }
            }
        }
    }
}

// ======================================================================
// Νέα / επεξεργασία ημερομηνίας
// ======================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditDialog(req: EditRequest, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val a = LocalActions.current
    val p = LocalPal.current
    val e = req.event
    val fresh = e == null
    val now = LocalDate.now()
    var title by remember { mutableStateOf(e?.title ?: req.presetTitle ?: "") }
    var titleError by remember { mutableStateOf(false) }
    val cats = listOf(EventStore.CAT_BIRTHDAY, EventStore.CAT_NAME, EventStore.CAT_ANNIVERSARY, EventStore.CAT_PERSONAL)
    val catLabels = listOf(T.t("🎂 Γενέθλια"), T.t("🎉 Γιορτή"), T.t("💍 Επέτειος"), T.t("⭐ Άλλο"))
    var cat by remember { mutableStateOf(e?.category?.takeIf { it in cats } ?: if (req.presetTitle != null) EventStore.CAT_NAME else EventStore.CAT_BIRTHDAY) }
    var date by remember { mutableStateOf(initialDate(e, req.presetDate, now)) }
    var withYear by remember { mutableStateOf((e?.year ?: 0) > 0) }
    var link by remember { mutableStateOf(if (e?.phone != null) e.phone to e.linkName else null) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text(if (fresh) T.t("Νέα ημερομηνία") else T.t("Επεξεργασία")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it; titleError = false },
                    label = { Text(T.t("Όνομα ή γεγονός (π.χ. Γενέθλια Μαρίας)")) },
                    isError = titleError, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = { if (titleError) Text(T.t("Γράψε ένα όνομα")) },
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cats.forEachIndexed { i, c ->
                        FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(catLabels[i]) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = p.accentSoft, selectedLabelColor = p.accent))
                    }
                }
                Spacer(Modifier.height(8.dp))
                val dateText = "📅  " + date.dayOfMonth + " " + date.month.getDisplayName(if (T.en()) TextStyle.FULL else TextStyle.FULL_STANDALONE, T.locale()).let {
                    if (T.en()) it else genitiveMonth(date.monthValue)
                } + (if (withYear) " " + date.year else "")
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(dateText + T.t("   ·  αλλαγή"))
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { withYear = !withYear }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(T.t("Να μετράει τα χρόνια (π.χ. ηλικία)"), modifier = Modifier.weight(1f), fontSize = 14.sp, color = p.ink)
                    Switch(withYear, { withYear = it })
                }
                OutlinedButton(onClick = {
                    if (link != null) link = null
                    else a.pickContact { num, nm ->
                        link = num to nm
                        if (title.isBlank() && nm != null) title = nm
                    }
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    val l = link
                    Text(if (l == null) T.t("👤  Σύνδεση με επαφή (για ευχές / SMS)") else "👤  " + (l.second ?: "") + " · " + l.first + "   ✕")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = title.trim()
                if (t.isEmpty()) { titleError = true; return@TextButton }
                if (withYear && date.year > now.year) { Wishes.toast(ctx, T.t("Το έτος είναι στο μέλλον")); return@TextButton }
                val ne = Event(
                    id = e?.id ?: ("user:" + UUID.randomUUID()), title = t, category = cat,
                    day = date.dayOfMonth, month = date.monthValue, offset = 0, year = if (withYear) date.year else 0,
                    movable = false, personal = true, phone = link?.first?.ifBlank { null }, linkName = link?.second,
                )
                EventStore.get(ctx).save(ne)
                ReminderReceiver.schedule(ctx)
                Wishes.toast(ctx, T.t("Αποθηκεύτηκε ✓"))
                onSaved()
            }) { Text(T.t("Αποθήκευση"), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (!fresh) TextButton(onClick = { confirmDelete = true }) { Text(T.t("Διαγραφή"), color = p.pink) }
                TextButton(onClick = onDismiss) { Text(T.t("Άκυρο")) }
            }
        },
    )

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(T.t("Άκυρο")) } },
        ) { DatePicker(state = state) }
    }
    if (confirmDelete && e != null) {
        MessageDialog(Dlg(null, T.t("Διαγραφή του «") + e.title + "»;", confirm = T.t("Διαγραφή"), dismiss = T.t("Όχι"), onConfirm = {
            EventStore.get(ctx).delete(e.id)
            ReminderReceiver.schedule(ctx)
            onSaved()
        }), onDismiss = { confirmDelete = false })
    }
}

private fun initialDate(e: Event?, preset: LocalDate?, now: LocalDate): LocalDate {
    if (e == null) return preset ?: now
    var y = if (e.year > 0) e.year else now.year
    if (e.month == 2 && e.day == 29) while (!Year.isLeap(y.toLong())) y--
    return try { LocalDate.of(y, e.month, e.day) } catch (_: Exception) { now }
}

/** Μήνας σε γενική («12 Μαρτίου»). */
fun genitiveMonth(m: Int): String = listOf(
    "Ιανουαρίου", "Φεβρουαρίου", "Μαρτίου", "Απριλίου", "Μαΐου", "Ιουνίου",
    "Ιουλίου", "Αυγούστου", "Σεπτεμβρίου", "Οκτωβρίου", "Νοεμβρίου", "Δεκεμβρίου",
)[m - 1]

// ======================================================================
// Γενικοί διάλογοι
// ======================================================================
@Composable
fun MessageDialog(d: Dlg, onDismiss: () -> Unit) {
    val titleText = d.title
    val dismissText = d.dismiss
    val titleSlot: (@Composable () -> Unit)? = if (titleText == null) null else ({ Text(titleText) })
    val dismissSlot: (@Composable () -> Unit)? =
        if (dismissText == null) null else ({ TextButton(onClick = onDismiss) { Text(dismissText) } })
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPal.current.surface,
        title = titleSlot,
        text = { Text(d.text, modifier = Modifier.verticalScroll(rememberScrollState()), lineHeight = 21.sp) },
        confirmButton = { TextButton(onClick = { onDismiss(); d.onConfirm?.invoke() }) { Text(d.confirm, fontWeight = FontWeight.Bold) } },
        dismissButton = dismissSlot,
    )
}

@Composable
fun ChoiceDialog(c: Choice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPal.current.surface,
        title = { Text(c.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                c.options.forEachIndexed { i, o ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .selectable(selected = i == c.selected, onClick = { onDismiss(); c.onPick(i) }).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = i == c.selected, onClick = null)
                        Text(o, modifier = Modifier.padding(start = 12.dp), fontSize = 15.sp, color = LocalPal.current.ink)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(T.t("Άκυρο")) } },
    )
}

@Composable
fun SmsWarningDialog(onDismiss: () -> Unit, onYes: () -> Unit) {
    val ctx = LocalContext.current
    MessageDialog(Dlg(
        T.t("⚠️ Αυτόματη αποστολή SMS"),
        T.t("Κάθε μέρα στις ") + Prefs.timeText(ctx) +
            T.t(" η εφαρμογή θα στέλνει ΜΟΝΗ ΤΗΣ SMS «Χρόνια πολλά» στις επαφές που γιορτάζουν ή έχουν γενέθλια.\n\n") +
            T.t("• Μπορεί να υπάρξουν ΧΡΕΩΣΕΙΣ από τον πάροχό σου (ανάλογα με το πρόγραμμά σου).\n") +
            T.t("• Ένα SMS ανά άτομο την ημέρα, έως ") + AutoWishes.DAILY_LIMIT + T.t(" την ημέρα.\n") +
            T.t("• Θα βλέπεις ειδοποίηση με το σε ποιους στάλθηκαν και ιστορικό στις Ρυθμίσεις.\n\n") +
            T.t("Θέλεις να το ενεργοποιήσεις;"),
        confirm = T.t("Ναι, συνέχεια"), onConfirm = onYes, dismiss = T.t("Όχι"),
    ), onDismiss = onDismiss)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPal.current.surface,
        title = { Text(T.t("Ώρα ειδοποίησης")) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onDismiss(); onPick(state.hour, state.minute) }) { Text("OK", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(T.t("Άκυρο")) } },
    )
}

@Composable
fun TemplateDialog(key: String, def: String, title: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sp = Prefs.sp(ctx)
    var text by remember { mutableStateOf(sp.getString(key, def) ?: def) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPal.current.surface,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BodyText(T.t("Ένα μήνυμα σε κάθε γραμμή. Κάθε άτομο παίρνει διαφορετικό. Το {name} γίνεται το όνομα στην κλητική (π.χ. «Νίκο», «Γιώργο»)."), size = 13)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp), minLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                TextButton(onClick = { text = def }) { Text(T.t("Έτοιμα μηνύματα")) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.trim() == def.trim()) sp.edit().remove(key).apply() else sp.edit().putString(key, text.trim()).apply()
                Wishes.toast(ctx, T.t("Αποθηκεύτηκε ✓"))
                onDismiss()
            }) { Text(T.t("Αποθήκευση"), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(T.t("Άκυρο")) } },
    )
}

/** Λίστα επαφών με γιορτή/γενέθλια: τσεκαρισμένη = παίρνει αυτόματο SMS. */
@Composable
fun SmsContactsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val people = rememberLoad(Unit) { ContactSync.load(ctx).filter { it.phone != null } }
    val checked = remember { mutableStateListOf<Boolean>() }
    LaunchedEffect(people) {
        if (people != null && checked.isEmpty()) {
            val block = AutoWishes.blocked(ctx)
            checked.addAll(people.map { it.key !in block })
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text(T.t("✅ = παίρνει αυτόματο SMS")) },
        text = {
            when {
                people == null -> BodyText("…")
                people.isEmpty() -> BodyText(T.t("Δεν βρέθηκαν επαφές με γιορτή/γενέθλια και τηλέφωνο"))
                checked.size == people.size -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(people) { i, person ->
                        Row(Modifier.fillMaxWidth().clickable { checked[i] = !checked[i] }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked[i], { checked[i] = it })
                            Column {
                                Text(person.displayName, color = p.ink, fontSize = 15.sp)
                                Text(if (person.canon.isEmpty()) T.t("γενέθλια") else person.canon.joinToString("/"), color = p.sub, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val list = people
                if (list != null && checked.size == list.size) {
                    AutoWishes.setBlockedMany(ctx, list.indices.associate { list[it] to !checked[it] })
                    Wishes.toast(ctx, T.t("Αποθηκεύτηκε ✓"))
                }
                onDismiss()
            }) { Text(T.t("Αποθήκευση"), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val any = checked.any { it }
                    for (i in checked.indices) checked[i] = !any
                }) { Text(T.t("Όλοι / Κανένας")) }
                TextButton(onClick = onDismiss) { Text(T.t("Άκυρο")) }
            }
        },
    )
}

// ======================================================================
// Ενημερώσεις (OTA)
// ======================================================================
@Composable
fun OtaDialogs(vm: AppViewModel) {
    val ctx = LocalContext.current
    val close = { vm.ota = OtaState.Idle }
    when (val s = vm.ota) {
        is OtaState.UpToDate -> MessageDialog(Dlg(T.t("Είσαι ενημερωμένος ✓"), T.t("Έχεις την τελευταία έκδοση (") + s.version + ")."), close)
        is OtaState.Error -> MessageDialog(Dlg(T.t("Έλεγχος ενημέρωσης"),
            T.t("Δεν ήταν δυνατός ο έλεγχος. Έλεγξε τη σύνδεση στο internet.\n(") + s.message + ")"), close)
        is OtaState.NeedPermission -> MessageDialog(Dlg(T.t("Μία φορά μόνο"),
            T.t("Για να εγκαθίστανται οι ενημερώσεις, επίτρεψε στο Εορτολόγιο+ την «Εγκατάσταση άγνωστων εφαρμογών» και πάτα ξανά «Ενημέρωση»."),
            confirm = T.t("Ρυθμίσεις"), dismiss = T.t("Άκυρο"), onConfirm = {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)))
                } catch (_: Exception) {}
            }), close)
        is OtaState.Available -> {
            val r = s.release
            val notes = if (r.notes.length > 600) r.notes.take(600) + "…" else r.notes
            AlertDialog(
                onDismissRequest = close,
                containerColor = LocalPal.current.surface,
                title = { Text(T.t("Νέα έκδοση ") + r.tag + " 🎉") },
                text = { Text(T.t("Εγκατεστημένη: ") + s.installed + "\n\n" + notes, modifier = Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = {
                    TextButton(onClick = {
                        if (r.apkUrl != null) vm.startUpdate(r)
                        else { close(); try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.page))) } catch (_: Exception) {} }
                    }) { Text(if (r.apkUrl != null) T.t("Ενημέρωση") else T.t("Άνοιγμα"), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = close) { Text(T.t("Αργότερα")) } },
            )
        }
        is OtaState.Downloading -> AlertDialog(
            onDismissRequest = {},
            containerColor = LocalPal.current.surface,
            title = { Text(T.t("Ενημέρωση")) },
            text = {
                Column {
                    Text(T.t("Λήψη… ") + s.percent + "%")
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
        )
        else -> {}
    }
}
