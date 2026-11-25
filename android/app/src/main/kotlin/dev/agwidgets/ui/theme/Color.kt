/*
 * AG Widgets palette — Aetheria obsidian / paper.
 * Dark: near-black glass + warm cream accent.
 * Light: paper white + violet accent, matching the desktop widget.
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
)

val DarkAgColors = AgColors(
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
)

val LightAgColors = AgColors(
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
)

val LocalAgColors = staticCompositionLocalOf { DarkAgColors }

object AgTheme {
    val colors: AgColors
        @Composable get() = LocalAgColors.current
}
