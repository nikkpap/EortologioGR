package gr.eortologio.plus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Κεφαλίδα με gradient (σταθερή στο πάνω μέρος κάθε οθόνης). */
@Composable
fun Header(title: String, subtitle: String, emoji: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(BrandGradient)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)
    ) {
        Text(emoji, fontSize = 54.sp, modifier = Modifier.align(Alignment.CenterEnd).alpha(0.28f))
        Column(Modifier.padding(end = 56.dp)) {
            Text(T.t("ΕΟΡΤΟΛΟΓΙΟ +"), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
            Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Στρογγυλεμένη κάρτα. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = LocalPal.current.surface,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(22.dp)
    val elev = if (p.dark) 0.dp else 1.dp
    val m = modifier.fillMaxWidth().padding(bottom = 12.dp)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = m, shape = shape, color = color, shadowElevation = elev) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = m, shape = shape, color = color, shadowElevation = elev) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 0.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

@Composable
fun Pill(text: String, onClick: (() -> Unit)?, strong: Boolean = false) {
    val p = LocalPal.current
    val bg = if (strong) p.accent else p.accentSoft
    val fg = if (strong) Color.White else p.accent
    val m = Modifier.clip(RoundedCornerShape(18.dp)).background(bg)
    Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = (if (onClick != null) m.clickable(onClick = onClick) else m).padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = LocalPal.current.sub, size: Int = 14) {
    Text(text, color = color, fontSize = size.sp, lineHeight = (size + 5).sp, modifier = modifier)
}

@Composable
fun TitleText(text: String, modifier: Modifier = Modifier, size: Int = 17, color: Color = LocalPal.current.ink) {
    Text(text, color = color, fontSize = size.sp, fontWeight = FontWeight.Bold, lineHeight = (size + 5).sp, modifier = modifier)
}

@Composable
fun MainButton(text: String, modifier: Modifier = Modifier, filled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val pad = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    if (filled) Button(onClick = onClick, modifier = modifier, shape = shape, contentPadding = pad) {
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    } else FilledTonalButton(onClick = onClick, modifier = modifier, shape = shape, contentPadding = pad,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = LocalPal.current.accentSoft, contentColor = LocalPal.current.accent)) {
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** Στρογγυλό «σήμα» ημερομηνίας: μέρα + μήνας. */
@Composable
fun DateBadge(d: LocalDate, highlight: Boolean) {
    val p = LocalPal.current
    Column(
        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (highlight) p.accent else p.surface2),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(d.dayOfMonth.toString(), color = if (highlight) Color.White else p.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
        Text(Fmt.monthShort(d), color = if (highlight) Color.White.copy(alpha = 0.85f) else p.sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, lineHeight = 12.sp)
    }
}

private val avatarColors = listOf(
    Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFFF59E0B),
    Color(0xFF8B5CF6), Color(0xFF0EA5E9), Color(0xFFEF4444), Color(0xFF22C55E),
)

@Composable
fun Avatar(name: String, seed: Long) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase(Names.EL) }
    Box(Modifier.size(46.dp).clip(CircleShape).background(avatarColors[Math.floorMod(seed, avatarColors.size.toLong()).toInt()]), contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Γραμμή ρυθμίσεων: τίτλος + τιμή + βελάκι. */
@Composable
fun SettingRow(title: String, value: String, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = p.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = p.sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.sub)
    }
}

/** Γραμμή με διακόπτη. */
@Composable
fun ToggleRow(title: String, detail: String, checked: Boolean, icon: ImageVector? = null, detailColor: Color? = null, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = p.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = detailColor ?: p.sub, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
    }
}

@Composable
fun Divider() = HorizontalDivider(Modifier.padding(vertical = 2.dp), thickness = 0.8.dp, color = LocalPal.current.line)

/** Βήμα του οδηγού ρύθμισης. */
@Composable
fun GuideStep(n: Int, title: String, detail: String, done: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(if (done) p.green else p.accent), contentAlignment = Alignment.Center) {
            Text(if (done) "✓" else n.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = p.sub, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (done) Text(T.t("Έτοιμο"), color = p.green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        else MainButton(T.t("Ενεργοποίηση"), onClick = onClick)
    }
}

/** Γραμμή με ημερομηνία αριστερά και κείμενα δεξιά. */
@Composable
fun DateRow(d: LocalDate, title: String, detail: String, today: LocalDate, titleColor: Color? = null, onClick: (() -> Unit)?) {
    AppCard(padding = PaddingValues(start = 12.dp, top = 12.dp, end = 16.dp, bottom = 12.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBadge(d, d == today)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                TitleText(title, size = 16, color = titleColor ?: LocalPal.current.ink)
                BodyText(detail, size = 13)
            }
        }
    }
}

/** Κουκίδα στο ημερολόγιο. */
@Composable
fun Dot(color: Color) = Box(Modifier.padding(horizontal = 1.5.dp).size(5.dp).clip(CircleShape).background(color))

@Composable
fun EmptyState(emoji: String, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 46.sp)
        Spacer(Modifier.height(8.dp))
        TitleText(title, size = 17, modifier = Modifier.padding(bottom = 4.dp))
        Text(text, color = LocalPal.current.sub, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
    }
}

@Composable
fun Outlined(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.border(1.dp, LocalPal.current.line, RoundedCornerShape(14.dp)).padding(12.dp)) { content() }
}

/** Μορφές ημερομηνίας στη γλώσσα της εφαρμογής. */
object Fmt {
    private fun f(el: String, en: String) = DateTimeFormatter.ofPattern(if (T.en()) en else el, T.locale())
    fun long(d: LocalDate) = cap(d.format(f("EEEE d MMMM yyyy", "EEEE, MMMM d, yyyy")))
    fun day(d: LocalDate) = d.format(f("EEE d MMMM", "EEE, MMM d"))
    fun short(d: LocalDate) = d.format(f("d MMM", "MMM d"))
    fun month(y: java.time.YearMonth) = cap(y.format(DateTimeFormatter.ofPattern("LLLL yyyy", T.locale())))
    fun weekday(d: LocalDate) = cap(d.format(DateTimeFormatter.ofPattern("EEEE", T.locale())))
    fun monthShort(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("MMM", T.locale())).replace(".", "").uppercase(Names.EL)
    fun cap(s: String) = if (s.isEmpty()) s else s.substring(0, 1).uppercase(T.locale()) + s.substring(1)

    fun whenText(days: Long): String = when {
        days == 0L -> T.t("Σήμερα")
        days == 1L -> T.t("Αύριο")
        T.en() -> if (days < 0) "${-days} days ago" else "in $days days"
        days < 0 -> "πριν ${-days} ημέρες"
        else -> "σε $days ημέρες"
    }
}
