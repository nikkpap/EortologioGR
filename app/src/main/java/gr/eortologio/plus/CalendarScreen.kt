package gr.eortologio.plus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val a = LocalActions.current
    val rev = vm.rev
    val today = remember(rev) { LocalDate.now() }
    val ym = vm.month
    val month = rememberLoad(ym, rev) { Repo.month(ctx, ym) }
    val sel = vm.selected
    val day = rememberLoad(sel, rev) { Repo.day(ctx, sel) }

    fun shift(n: Long) {
        val m = vm.month.plusMonths(n)
        val now = LocalDate.now()
        vm.month = m
        vm.selected = if (m == YearMonth.from(now)) now else m.atDay(1)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
        item(key = "grid") {
            AppCard(padding = PaddingValues(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 12.dp),
                modifier = Modifier.pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 120) shift(-1) else if (total < -120) shift(1) },
                    ) { _, dx -> total += dx }
                }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { shift(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, T.t("Προηγούμενος μήνας"), tint = p.accent) }
                    Text(Fmt.month(ym), color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .clickable { vm.month = YearMonth.from(today); vm.selected = today }.padding(vertical = 6.dp))
                    IconButton(onClick = { shift(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, T.t("Επόμενος μήνας"), tint = p.accent) }
                }
                Row(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                    for (i in 1..7) Text(
                        DayOfWeek.of(i).getDisplayName(TextStyle.NARROW_STANDALONE, T.locale()).uppercase(T.locale()),
                        color = if (i >= 6) p.pink else p.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
                val cells = month?.takeIf { it.ym == ym }?.cells ?: month?.cells
                if (cells != null) for (week in cells.chunked(7)) Row {
                    for (c in week) Box(Modifier.weight(1f).height(52.dp).padding(2.dp)) {
                        if (c != null) DayCell(c, sel, today) { vm.selected = c.date }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Legend(p.accent, T.t("γιορτές")); Legend(p.green, T.t("επαφές")); Legend(p.pink, T.t("δικά μου"))
                }
            }
        }

        item(key = "dayTitle") { SectionTitle(Fmt.long(sel)) }
        if (day != null && day.date == sel) {
            item(key = "day") {
                AppCard {
                    if (day.names.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (n in day.names) Pill(nameLabel(n), onClick = { a.openName(n) })
                    }
                    for (e in day.events) Text(
                        if (e.personal) Repo.icon(e) + " " + e.title else "📌 " + T.t(e.title),
                        color = if (e.personal) p.pink else p.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp).then(if (e.personal) Modifier.clickable { a.edit(EditRequest(e)) } else Modifier),
                    )
                    if (day.names.isEmpty() && day.events.isEmpty()) BodyText(T.t("Δεν υπάρχει γιορτή αυτή τη μέρα."))
                    day.publicHoliday?.let { Text(T.t("🏖️ Επίσημη αργία: ") + it, color = p.pink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)) }
                    if (day.fast.type != Church.Fast.NONE) BodyText(day.fast.icon + " " + day.fast.label, Modifier.padding(top = 10.dp), size = 13)
                    Text(T.t("+ Πρόσθεσε δική σου ημερομηνία εδώ"), color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(10.dp)).clickable { a.edit(EditRequest(presetDate = sel)) }.padding(vertical = 4.dp))
                }
            }
            items(day.hits, key = { "dh:" + it.person.key + it.birthday }) { h -> ContactCard(h, today, buttons = true) }
        }
    }
}

@Composable
private fun DayCell(c: Repo.Cell, selected: LocalDate, today: LocalDate, onClick: () -> Unit) {
    val p = LocalPal.current
    val sel = c.date == selected
    val isToday = c.date == today
    val shape = RoundedCornerShape(14.dp)
    var m = Modifier.fillMaxSize().clip(shape).background(if (sel) p.accent else Color.Transparent)
    if (isToday && !sel) m = m.border(1.5.dp, p.accent, shape)
    Column(m.clickable(onClick = onClick), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(c.date.dayOfMonth.toString(),
            color = if (sel) Color.White else if (c.dayOff || c.holiday) p.pink else p.ink,
            fontSize = 15.sp, fontWeight = if (sel || isToday) FontWeight.Bold else FontWeight.Normal)
        Row(Modifier.padding(top = 3.dp).height(5.dp)) {
            if (c.names) Dot(if (sel) Color.White else p.accent)
            if (c.contact) Dot(if (sel) Color.White else p.green)
            if (c.personal) Dot(if (sel) Color.White else p.pink)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Text(label, color = LocalPal.current.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
    }
}
