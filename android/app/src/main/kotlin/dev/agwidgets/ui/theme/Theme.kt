package dev.agwidgets.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColors = darkColorScheme(
    primary = DarkAgColors.glow,
    onPrimary = DarkAgColors.onGlow,
    primaryContainer = DarkAgColors.surface,
    onPrimaryContainer = DarkAgColors.text,
    secondary = DarkAgColors.violet,
    onSecondary = DarkAgColors.onGlow,
    secondaryContainer = DarkAgColors.surface,
    onSecondaryContainer = DarkAgColors.text,
    tertiary = DarkAgColors.pink,
    onTertiary = DarkAgColors.onGlow,
    background = DarkAgColors.background,
    onBackground = DarkAgColors.text,
    surface = DarkAgColors.surface,
    onSurface = DarkAgColors.text,
    surfaceVariant = DarkAgColors.surface,
    onSurfaceVariant = DarkAgColors.muted,
    outline = DarkAgColors.border,
    outlineVariant = DarkAgColors.track,
    error = DarkAgColors.rose,
    onError = DarkAgColors.onGlow,
)

private val LightColors = lightColorScheme(
    primary = LightAgColors.glow,
    onPrimary = LightAgColors.onGlow,
    primaryContainer = LightAgColors.surface,
    onPrimaryContainer = LightAgColors.text,
    secondary = LightAgColors.violet,
    onSecondary = LightAgColors.onGlow,
    secondaryContainer = LightAgColors.surface,
    onSecondaryContainer = LightAgColors.text,
    tertiary = LightAgColors.pink,
    onTertiary = LightAgColors.onGlow,
    background = LightAgColors.background,
    onBackground = LightAgColors.text,
    surface = LightAgColors.surface,
    onSurface = LightAgColors.text,
    surfaceVariant = LightAgColors.surface,
    onSurfaceVariant = LightAgColors.muted,
    outline = LightAgColors.border,
    outlineVariant = LightAgColors.track,
    error = LightAgColors.rose,
    onError = LightAgColors.onGlow,
)

@Composable
fun AGWidgetsTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkAgColors else LightAgColors
    CompositionLocalProvider(LocalAgColors provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content,
        )
    }
}
