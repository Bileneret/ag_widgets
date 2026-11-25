package dev.agwidgets.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** User-selectable theme modes; stored as lowercase ids in SharedPreferences. */
object AgThemeMode {
    const val SYSTEM = "system"
    const val GRAPHITE = "graphite"
    const val PAPER = "paper"
    const val OLED = "oled"
    const val GLASS = "glass"
    const val CLASSIC = "classic"

    val all: List<String> = listOf(SYSTEM, GRAPHITE, PAPER, OLED, GLASS, CLASSIC)
}

/** A concrete theme: renderer id, dark flag and whether the author's decor applies. */
data class ResolvedAgTheme(
    val id: String,
    val isDark: Boolean,
    val isClassic: Boolean,
)

/**
 * Maps stored values onto a known mode. Legacy `dark`/`light` come from older
 * releases; anything unknown falls back to `system` instead of throwing.
 */
fun normalizeThemeMode(raw: String?): String {
    val value = raw?.trim()?.lowercase().orEmpty()
    return when (value) {
        "dark" -> AgThemeMode.GRAPHITE
        "light" -> AgThemeMode.PAPER
        in AgThemeMode.all -> value
        else -> AgThemeMode.SYSTEM
    }
}

/** Resolves a mode against the current system theme. */
fun resolveAgTheme(mode: String?, systemDark: Boolean): ResolvedAgTheme = when (normalizeThemeMode(mode)) {
    AgThemeMode.GRAPHITE -> ResolvedAgTheme(AgThemeMode.GRAPHITE, isDark = true, isClassic = false)
    AgThemeMode.PAPER -> ResolvedAgTheme(AgThemeMode.PAPER, isDark = false, isClassic = false)
    AgThemeMode.OLED -> ResolvedAgTheme(AgThemeMode.OLED, isDark = true, isClassic = false)
    AgThemeMode.GLASS -> ResolvedAgTheme(
        if (systemDark) "glass_dark" else "glass_light",
        isDark = systemDark,
        isClassic = false,
    )
    AgThemeMode.CLASSIC -> ResolvedAgTheme(
        if (systemDark) "classic_dark" else "classic_light",
        isDark = systemDark,
        isClassic = true,
    )
    else -> ResolvedAgTheme(
        if (systemDark) AgThemeMode.GRAPHITE else AgThemeMode.PAPER,
        isDark = systemDark,
        isClassic = false,
    )
}

private fun materialScheme(colors: AgColors) = if (colors.isDark) {
    darkColorScheme(
        primary = colors.glow,
        onPrimary = colors.onGlow,
        primaryContainer = colors.surface,
        onPrimaryContainer = colors.text,
        secondary = colors.violet,
        onSecondary = colors.onGlow,
        secondaryContainer = colors.surface,
        onSecondaryContainer = colors.text,
        tertiary = colors.pink,
        onTertiary = colors.onGlow,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        surfaceVariant = colors.surface,
        onSurfaceVariant = colors.muted,
        outline = colors.border,
        outlineVariant = colors.track,
        error = colors.rose,
        onError = colors.onGlow,
    )
} else {
    lightColorScheme(
        primary = colors.glow,
        onPrimary = colors.onGlow,
        primaryContainer = colors.surface,
        onPrimaryContainer = colors.text,
        secondary = colors.violet,
        onSecondary = colors.onGlow,
        secondaryContainer = colors.surface,
        onSecondaryContainer = colors.text,
        tertiary = colors.pink,
        onTertiary = colors.onGlow,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        surfaceVariant = colors.surface,
        onSurfaceVariant = colors.muted,
        outline = colors.border,
        outlineVariant = colors.track,
        error = colors.rose,
        onError = colors.onGlow,
    )
}

/** Theme entry point: resolves [themeMode] against [systemDark] and provides the palette. */
@Composable
fun AGWidgetsTheme(
    themeMode: String = AgThemeMode.SYSTEM,
    systemDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val resolved = resolveAgTheme(themeMode, systemDark)
    val palette = agColorsFor(resolved)
    CompositionLocalProvider(LocalAgColors provides palette) {
        MaterialTheme(
            colorScheme = materialScheme(palette),
            typography = Typography,
            content = content,
        )
    }
}

/** Pre-7.0 call site (`AGWidgetsTheme(darkTheme = ...)`): keeps the Classic palette. */
@Composable
fun AGWidgetsTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) ClassicDarkAgColors else ClassicLightAgColors
    CompositionLocalProvider(LocalAgColors provides palette) {
        MaterialTheme(
            colorScheme = materialScheme(palette),
            typography = Typography,
            content = content,
        )
    }
}
