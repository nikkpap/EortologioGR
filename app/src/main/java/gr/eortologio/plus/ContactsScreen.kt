package gr.eortologio.plus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun ContactsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val a = LocalActions.current
    val rev = vm.rev
    val today = remember(rev) { LocalDate.now() }
    val data = rememberLoad(rev) { Repo.contacts(ctx, today) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
        if (data == null) return@LazyColumn
        if (!data.granted) {
            item(key = "connect") {
                AppCard {
                    TitleText(T.t("👥 Συγχρονισμός επαφών"), size = 18)
                    BodyText(T.t("1. Πάτα «Σύνδεση επαφών».\n2. Επίλεξε «Να επιτρέπεται».\n3. Έτοιμο! Θα βλέπεις εδώ ποιος γιορτάζει ή έχει γενέθλια και θα στέλνεις «Χρόνια πολλά» με SMS, WhatsApp ή Viber με ένα πάτημα.\n\n🔒 Οι επαφές διαβάζονται μόνο τοπικά, δεν αποθηκεύονται και δεν στέλνονται πουθενά."),
                        Modifier.padding(top = 8.dp, bottom = 14.dp))
                    MainButton(T.t("Σύνδεση επαφών"), Modifier.fillMaxWidth()) { a.askContacts() }
                }
            }
            return@LazyColumn
        }
        item(key = "info") {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(T.t("✓ Συγχρονισμένες επαφές"), color = p.green, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        BodyText(data.named.toString() + T.t(" με ονομαστική γιορτή · ") + data.bdays + T.t(" με γενέθλια"), size = 13)
                    }
                    IconButton(onClick = { ContactSync.invalidate(); Wishes.toast(ctx, T.t("Ανανεώθηκε")); vm.refresh() }) {
                        Icon(Icons.Rounded.Refresh, T.t("↻ Ανανέωση"), tint = p.accent)
                    }
                }
                BodyText(T.t("📊 Αυτόν τον μήνα γιορτάζουν/έχουν γενέθλια: ") + data.monthCount +
                    (if (data.sentYear > 0) T.t("  ·  ✉️ Φέτος στάλθηκαν ") + data.sentYear + T.t(" αυτόματες ευχές") else ""),
                    Modifier.padding(top = 8.dp), size = 13)
            }
        }
        if (data.sections.isEmpty()) item(key = "none") {
            EmptyState("🤷", T.t("Δεν βρέθηκαν επαφές με γνωστό όνομα."),
                T.t("Το ταίριασμα γίνεται με το μικρό όνομα (π.χ. «Νίκος», «Giorgos»). Αν μια επαφή λείπει, πρόσθεσέ την χειροκίνητα στα «Δικά μου»."))
        }
        for (s in data.sections) {
            item(key = "s:" + s.title) { SectionTitle(s.title) }
            items(s.hits, key = { "c:" + it.person.key + it.date + it.birthday }) { h ->
                if (s.big) ContactCard(h, today, buttons = true)
                else {
                    val what = if (h.birthday) T.t("🎂 Γενέθλια") else "🎉 " + h.reason
                    DateRow(h.date, h.person.displayName, what + " · " + Fmt.whenText(EventStore.daysUntil(today, h.date)), today, onClick = { a.openWish(h) })
                }
            }
        }
    }
}

/** Κάρτα επαφής που γιορτάζει, με κουμπιά ευχών. */
@Composable
fun ContactCard(h: Hit, today: LocalDate, buttons: Boolean) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val a = LocalActions.current
    val dd = EventStore.daysUntil(today, h.date)
    AppCard(onClick = { a.openWish(h) }, padding = PaddingValues(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(h.person.displayName, h.person.id)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(h.person.displayName, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val what = if (h.birthday) T.t("🎂 Γενέθλια") + (if (h.person.bYear > 0) " · " + (h.date.year - h.person.bYear) + T.t(" χρόνια") else "")
                else T.t("🎉 Γιορτή · ") + h.reason
                Text(what + " · " + (if (dd <= 1) Fmt.whenText(dd) else Fmt.day(h.date)),
                    color = if (dd == 0L) p.pink else p.sub, fontSize = 13.sp,
                    fontWeight = if (dd == 0L) FontWeight.SemiBold else FontWeight.Normal)
            }
            IconButton(onClick = { a.openWish(h) }) { Icon(Icons.Rounded.MoreVert, T.t("Περισσότερα"), tint = p.sub) }
        }
        if (buttons) Row(Modifier.fillMaxWidth().padding(top = 12.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val shape = RoundedCornerShape(14.dp)
            val pad = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            Button(onClick = { Wishes.sms(ctx, h) }, Modifier.weight(1f).height(44.dp), shape = shape, contentPadding = pad) {
                Text("💬 SMS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
            val tonal = ButtonDefaults.filledTonalButtonColors(containerColor = p.accentSoft, contentColor = p.accent)
            FilledTonalButton(onClick = { Wishes.whatsapp(ctx, h) }, Modifier.weight(1f).height(44.dp), shape = shape, contentPadding = pad, colors = tonal) {
                Text("WhatsApp", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
            FilledTonalButton(onClick = { Wishes.viber(ctx, h) }, Modifier.weight(1f).height(44.dp), shape = shape, contentPadding = pad, colors = tonal) {
                Text("Viber", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
}
