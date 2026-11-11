package gr.eortologio.plus

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

@Composable
fun PersonalScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val a = LocalActions.current
    val rev = vm.rev
    val today = remember(rev) { LocalDate.now() }
    val list = rememberLoad(rev) { Repo.personal(ctx, today) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
        if (list == null) return@LazyColumn
        if (list.isEmpty()) item(key = "empty") {
            AppCard {
                EmptyState("🎂", T.t("Δεν έχεις προσθέσει ακόμα τίποτα"),
                    T.t("Πρόσθεσε γενέθλια, επετείους ή γιορτές που δεν υπάρχουν στο εορτολόγιο. Θα σε ειδοποιώ από την προηγούμενη μέρα."))
                MainButton(T.t("+  Νέα ημερομηνία"), Modifier.fillMaxWidth()) { a.edit(EditRequest()) }
            }
        }
        items(list, key = { "e:" + it.first.id }) { (e, d) ->
            val y = e.yearsAt(d)
            DateRow(
                d, Repo.icon(e) + " " + e.title,
                T.t(e.category) + (if (y > 0) " · " + y + T.t(" χρόνια") else "") + " · " + Fmt.whenText(EventStore.daysUntil(today, d)) +
                    (if (e.phone != null) " · 👤" else ""),
                today, onClick = { a.edit(EditRequest(e)) },
            )
        }
    }
}
