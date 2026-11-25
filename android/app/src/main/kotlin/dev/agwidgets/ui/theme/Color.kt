/*
 * AG Widgets palette — six theme modes.
 *
 * Modern themes (graphite / paper / oled / glass) are calm, high-contrast
 * utility surfaces. The `classic` pair keeps the original Aetheria styling
 * (near-black glass + warm cream accent / paper white + violet accent) 1:1.
 */

package dev.agwidgets.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AgColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val cardShadow: Color,
    val border: Color,
    val text: Color,
    val secondary: Color,
    val muted: Color,
    val glow: Color,
    val onGlow: Color,
    val violet: Color,
    val pink: Color,
    val green: Color,
    val amber: Color,
    val rose: Color,
    val badge: Color,
    val track: Color,
    val grid: Color,
    val glowMagenta: Color,
    val glowBlue: Color,
    val glowOrange: Color,
    val isDark: Boolean = true,
    val isClassic: Boolean = false,
    /** Corner radius for cards: 14 for modern themes, 10 for Classic. */
    val cardCornerDp: Int = 14,
)

/** Calm dark charcoal with a soft ice-blue accent. */
val GraphiteAgColors = AgColors(
    background = Color(0xFF121417),
    surface = Color(0xFF16191E),
    card = Color(0xFF1B1E23),
    cardShadow = Color(0x1F000000),
    border = Color(0x14FFFFFF),
    text = Color(0xFFE6EAF0),
    secondary = Color(0xFF9BA3B0),
    muted = Color(0xFF788292),
    glow = Color(0xFF60A5FA),
    onGlow = Color(0xFF0B1220),
    violet = Color(0xFF60A5FA),
    pink = Color(0xFF93C5FD),
    green = Color(0xFF34D399),
    amber = Color(0xFFFBBF24),
    rose = Color(0xFFF87171),
    badge = Color(0x0FFFFFFF),
    track = Color(0x1FFFFFFF),
    grid = Color(0x00000000),
    glowMagenta = Color(0x00000000),
    glowBlue = Color(0x00000000),
    glowOrange = Color(0x00000000),
    isDark = true,
    isClassic = false,
    cardCornerDp = 14,
)

/** Soft warm light paper, never blinding white. */
val PaperAgColors = AgColors(
    background = Color(0xFFF2F1EC),
    surface = Color(0xFFF6F5F1),
    card = Color(0xFFFAF9F6),
    cardShadow = Color(0x14000000),
    border = Color(0x1A18181B),
    text = Color(0xFF1F2328),
    secondary = Color(0xFF57606A),
    muted = Color(0xFF6E7781),
    glow = Color(0xFF2563EB),
    onGlow = Color(0xFFFFFFFF),
    violet = Color(0xFF2563EB),
    pink = Color(0xFF1D4ED8),
    green = Color(0xFF047857),
    amber = Color(0xFFB45309),
    rose = Color(0xFFBE123C),
    badge = Color(0x0A18181B),
    track = Color(0x1F18181B),
    grid = Color(0x00000000),
    glowMagenta = Color(0x00000000),
    glowBlue = Color(0x00000000),
    glowOrange = Color(0x00000000),
    isDark = false,
    isClassic = false,
    cardCornerDp = 14,
)

/** Pure black AMOLED surface. */
val OledAgColors = AgColors(
    background = Color(0xFF000000),
    surface = Color(0xFF060709),
    card = Color(0xFF0B0D10),
    cardShadow = Color(0x33000000),
    border = Color(0x21FFFFFF),
    text = Color(0xFFF4F6F8),
    secondary = Color(0xFFB4BCC6),
    muted = Color(0xFF8A94A0),
    glow = Color(0xFF60A5FA),
    onGlow = Color(0xFF05070A),
    violet = Color(0xFF60A5FA),
    pink = Color(0xFF93C5FD),
    green = Color(0xFF34D399),
    amber = Color(0xFFFBBF24),
    rose = Color(0xFFF87171),
    badge = Color(0x0FFFFFFF),
    track = Color(0x24FFFFFF),
    grid = Color(0x00000000),
    glowMagenta = Color(0x00000000),
    glowBlue = Color(0x00000000),
    glowOrange = Color(0x00000000),
    isDark = true,
    isClassic = false,
    cardCornerDp = 14,
)

/** Matte glass that follows the system theme. */
val GlassDarkAgColors = AgColors(
    background = Color(0xFF0E1117),
    surface = Color(0xFF151A21),
    card = Color(0xFF1A1F26),
    cardShadow = Color(0x1F000000),
    border = Color(0x1FFFFFFF),
    text = Color(0xFFE8ECF2),
    secondary = Color(0xFFA8B2C0),
    muted = Color(0xFF8792A2),
    glow = Color(0xFF7DD3FC),
    onGlow = Color(0xFF0B1220),
    violet = Color(0xFF7DD3FC),
    pink = Color(0xFF93C5FD),
    green = Color(0xFF34D399),
    amber = Color(0xFFFBBF24),
    rose = Color(0xFFF87171),
    badge = Color(0x0FFFFFFF),
    track = Color(0x1FFFFFFF),
    grid = Color(0x00000000),
    glowMagenta = Color(0x00000000),
    glowBlue = Color(0x00000000),
    glowOrange = Color(0x00000000),
    isDark = true,
    isClassic = false,
    cardCornerDp = 14,
)

val GlassLightAgColors = AgColors(
    background = Color(0xFFECECE6),
    surface = Color(0xFFF4F4EF),
    card = Color(0xFFFBFBF8),
    cardShadow = Color(0x14000000),
    border = Color(0x1A1B1F24),
    text = Color(0xFF1B1F24),
    secondary = Color(0xFF5B6470),
    muted = Color(0xFF747D88),
    glow = Color(0xFF2563EB),
    onGlow = Color(0xFFFFFFFF),
    violet = Color(0xFF2563EB),
    pink = Color(0xFF1D4ED8),
    green = Color(0xFF047857),
    amber = Color(0xFFB45309),
    rose = Color(0xFFBE123C),
    badge = Color(0x0A1B1F24),
    track = Color(0x1F1B1F24),
    grid = Color(0x00000000),
    glowMagenta = Color(0x00000000),
    glowBlue = Color(0x00000000),
    glowOrange = Color(0x00000000),
    isDark = false,
    isClassic = false,
    cardCornerDp = 14,
)

/** Original author styling, dark: near-black glass + warm cream accent. */
val ClassicDarkAgColors = AgColors(
    background = Color(0xFF050308),
    surface = Color(0xFF0C0A10),
    card = Color(0xF20C0A10),
    cardShadow = Color(0x99000000),
    border = Color(0x33FFFFFF),
    text = Color(0xFFFFFFFF),
    secondary = Color(0xFFA1A1AA),
    muted = Color(0xFF8F8F9A),
    glow = Color(0xFFFFE9B8),
    onGlow = Color(0xFF050308),
    violet = Color(0xFFB026FF),
    pink = Color(0xFFF472B6),
    green = Color(0xFF4EF2D2),
    amber = Color(0xFFF5A623),
    rose = Color(0xFFFF4D6D),
    badge = Color(0x0FFFFFFF),
    track = Color(0x1FFFFFFF),
    grid = Color(0x09FFFFFF),
    glowMagenta = Color(0x55B026FF),
    glowBlue = Color(0x442E7FD6),
    glowOrange = Color(0x2EFF6B35),
    isDark = true,
    isClassic = true,
    cardCornerDp = 10,
)

/** Original author styling, light: paper white + violet accent. */
val ClassicLightAgColors = AgColors(
    background = Color(0xFFFBFBFA),
    surface = Color(0xFFFFFFFF),
    card = Color(0xF7FFFFFF),
    cardShadow = Color(0x24000000),
    border = Color(0x2409090B),
    text = Color(0xFF09090B),
    secondary = Color(0xFF52525B),
    muted = Color(0xFF6B6B76),
    glow = Color(0xFF7C3AED),
    onGlow = Color(0xFFFFFFFF),
    violet = Color(0xFF7C3AED),
    pink = Color(0xFFBE185D),
    green = Color(0xFF047857),
    amber = Color(0xFFB45309),
    rose = Color(0xFFBE123C),
    badge = Color(0x0A09090B),
    track = Color(0x1F09090B),
    grid = Color(0x0909090B),
    glowMagenta = Color(0x1FB026FF),
    glowBlue = Color(0x1F2E7FD6),
    glowOrange = Color(0x14FF6B35),
    isDark = false,
    isClassic = true,
    cardCornerDp = 10,
)

/** Backwards-compatible aliases for the pre-7.0 two-palette API. */
val DarkAgColors = ClassicDarkAgColors
val LightAgColors = ClassicLightAgColors

/** Palette for a resolved theme id; unknown ids fall back to the modern pair. */
fun agColorsFor(resolved: ResolvedAgTheme): AgColors = when (resolved.id) {
    "graphite" -> GraphiteAgColors
    "paper" -> PaperAgColors
    "oled" -> OledAgColors
    "glass_dark" -> GlassDarkAgColors
    "glass_light" -> GlassLightAgColors
    "classic_dark" -> ClassicDarkAgColors
    "classic_light" -> ClassicLightAgColors
    else -> if (resolved.isDark) GraphiteAgColors else PaperAgColors
}

val LocalAgColors = staticCompositionLocalOf { GraphiteAgColors }

object AgTheme {
    val colors: AgColors
        @Composable get() = LocalAgColors.current
}
