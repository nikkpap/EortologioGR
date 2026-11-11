package gr.eortologio.plus

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Παλέτα της εφαρμογής (φωτεινό / σκοτεινό). */
@Immutable
data class Pal(
    val dark: Boolean,
    val bg: Color, val surface: Color, val surface2: Color,
    val ink: Color, val sub: Color,
    val accent: Color, val accentSoft: Color,
    val pink: Color, val green: Color, val line: Color,
    val info: Color, val personal: Color, val guide: Color,
)

val LightPal = Pal(
    dark = false,
    bg = Color(0xFFF4F3FB), surface = Color.White, surface2 = Color(0xFFEEECFA),
    ink = Color(0xFF17182C), sub = Color(0xFF676B84),
    accent = Color(0xFF5B4FD8), accentSoft = Color(0x1F5B4FD8),
    pink = Color(0xFFDB2777), green = Color(0xFF16A34A), line = Color(0xFFE6E4F2),
    info = Color(0xFFE0F2FE), personal = Color(0xFFFDE7F3), guide = Color(0xFFEEEAFF),
)

val DarkPal = Pal(
    dark = true,
    bg = Color(0xFF0E0F1C), surface = Color(0xFF1A1B2E), surface2 = Color(0xFF25273F),
    ink = Color(0xFFF2F2FB), sub = Color(0xFFA5A8C4),
    accent = Color(0xFFA59BFF), accentSoft = Color(0x33A59BFF),
    pink = Color(0xFFFF7AB6), green = Color(0xFF4ADE80), line = Color(0xFF2C2E48),
    info = Color(0xFF16324A), personal = Color(0xFF3A2346), guide = Color(0xFF23224A),
)

val LocalPal = staticCompositionLocalOf { LightPal }

val BrandGradient = Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFFDB2777)))

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPal else LightPal
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = Color(0xFF1B1446), primaryContainer = Color(0xFF3B3480), onPrimaryContainer = p.ink,
        secondary = p.pink, tertiary = p.green, background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink, surfaceVariant = p.surface2, onSurfaceVariant = p.sub,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface2, surfaceContainerHighest = p.surface2,
        surfaceContainerLow = p.surface, outline = p.sub, outlineVariant = p.line,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = Color.White, primaryContainer = Color(0xFFE4E1FF), onPrimaryContainer = Color(0xFF1B1446),
        secondary = p.pink, tertiary = p.green, background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink, surfaceVariant = p.surface2, onSurfaceVariant = p.sub,
        surfaceContainer = p.surface, surfaceContainerHigh = Color(0xFFF7F6FD), surfaceContainerHighest = p.surface2,
        surfaceContainerLow = p.surface, outline = p.sub, outlineVariant = p.line,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography()) {
        CompositionLocalProvider(LocalPal provides p, content = content)
    }
}
