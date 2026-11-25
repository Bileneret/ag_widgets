package dev.agwidgets.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.agwidgets.ui.theme.AgThemeMode
import dev.agwidgets.ui.theme.agColorsFor
import dev.agwidgets.ui.theme.normalizeThemeMode
import dev.agwidgets.ui.theme.resolveAgTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM contract for the six Android theme modes and their palettes.
 * No Android or Robolectric APIs are involved: only the theme resolution
 * functions and immutable Compose `Color` values.
 */
class ThemeResolutionTest {

    private val systemDarkStates = listOf(true, false)

    // ---- normalizeThemeMode ---------------------------------------------

    @Test
    fun normalizeAcceptsAllSixCanonicalModes() {
        assertEquals(6, AgThemeMode.all.size)
        AgThemeMode.all.forEach { mode -> assertEquals(mode, normalizeThemeMode(mode)) }
    }

    @Test
    fun normalizeIsCaseInsensitiveAndTrimsWhitespace() {
        assertEquals(AgThemeMode.GRAPHITE, normalizeThemeMode("Graphite"))
        assertEquals(AgThemeMode.PAPER, normalizeThemeMode("PAPER"))
        assertEquals(AgThemeMode.OLED, normalizeThemeMode("  Oled  "))
        assertEquals(AgThemeMode.GLASS, normalizeThemeMode("glass"))
        assertEquals(AgThemeMode.CLASSIC, normalizeThemeMode("Classic"))
        assertEquals(AgThemeMode.SYSTEM, normalizeThemeMode("SYSTEM"))
    }

    @Test
    fun normalizeMigratesLegacyModes() {
        assertEquals(AgThemeMode.GRAPHITE, normalizeThemeMode("dark"))
        assertEquals(AgThemeMode.PAPER, normalizeThemeMode("light"))
    }

    @Test
    fun normalizeFallsBackToSystemForMissingOrUnknownValues() {
        assertEquals(AgThemeMode.SYSTEM, normalizeThemeMode(null))
        assertEquals(AgThemeMode.SYSTEM, normalizeThemeMode(""))
        assertEquals(AgThemeMode.SYSTEM, normalizeThemeMode("   "))
        assertEquals(AgThemeMode.SYSTEM, normalizeThemeMode("neon"))
    }

    // ---- resolveAgTheme -------------------------------------------------

    @Test
    fun systemResolvesToGraphiteOrPaper() {
        systemDarkStates.forEach { systemDark ->
            val resolved = resolveAgTheme(AgThemeMode.SYSTEM, systemDark)
            assertEquals(if (systemDark) "graphite" else "paper", resolved.id)
            assertEquals(systemDark, resolved.isDark)
            assertFalse(resolved.isClassic)
        }
    }

    @Test
    fun graphiteAndOledAreAlwaysDark() {
        systemDarkStates.forEach { systemDark ->
            val graphite = resolveAgTheme(AgThemeMode.GRAPHITE, systemDark)
            assertEquals("graphite", graphite.id)
            assertTrue(graphite.isDark)
            assertFalse(graphite.isClassic)

            val oled = resolveAgTheme(AgThemeMode.OLED, systemDark)
            assertEquals("oled", oled.id)
            assertTrue(oled.isDark)
            assertFalse(oled.isClassic)
        }
    }

    @Test
    fun paperIsAlwaysLight() {
        systemDarkStates.forEach { systemDark ->
            val paper = resolveAgTheme(AgThemeMode.PAPER, systemDark)
            assertEquals("paper", paper.id)
            assertFalse(paper.isDark)
            assertFalse(paper.isClassic)
        }
    }

    @Test
    fun glassFollowsTheSystemBrightness() {
        systemDarkStates.forEach { systemDark ->
            val resolved = resolveAgTheme(AgThemeMode.GLASS, systemDark)
            assertEquals(if (systemDark) "glass_dark" else "glass_light", resolved.id)
            assertEquals(systemDark, resolved.isDark)
            assertFalse(resolved.isClassic)
        }
    }

    @Test
    fun classicFollowsTheSystemBrightness() {
        systemDarkStates.forEach { systemDark ->
            val resolved = resolveAgTheme(AgThemeMode.CLASSIC, systemDark)
            assertEquals(if (systemDark) "classic_dark" else "classic_light", resolved.id)
            assertEquals(systemDark, resolved.isDark)
            assertTrue(resolved.isClassic)
        }
    }

    @Test
    fun onlyClassicEnablesTheAuthorsDecorations() {
        AgThemeMode.all.forEach { mode ->
            systemDarkStates.forEach { systemDark ->
                val resolved = resolveAgTheme(mode, systemDark)
                if (mode == AgThemeMode.CLASSIC) {
                    assertTrue("$mode/$systemDark must be classic", resolved.isClassic)
                } else {
                    assertFalse("$mode/$systemDark must not be classic", resolved.isClassic)
                }
            }
        }
    }

    @Test
    fun legacyStoredModesResolveLikeTheirModernReplacement() {
        systemDarkStates.forEach { systemDark ->
            assertEquals(
                resolveAgTheme(AgThemeMode.GRAPHITE, systemDark),
                resolveAgTheme("dark", systemDark)
            )
            assertEquals(
                resolveAgTheme(AgThemeMode.PAPER, systemDark),
                resolveAgTheme("light", systemDark)
            )
        }
    }

    // ---- agColorsFor ----------------------------------------------------

    @Test
    fun cardCornerRadiusMatchesTheThemeLanguage() {
        assertEquals(10, agColorsFor(resolveAgTheme(AgThemeMode.CLASSIC, true)).cardCornerDp)
        assertEquals(10, agColorsFor(resolveAgTheme(AgThemeMode.CLASSIC, false)).cardCornerDp)
        listOf(AgThemeMode.GRAPHITE, AgThemeMode.PAPER, AgThemeMode.OLED).forEach { mode ->
            assertEquals(14, agColorsFor(resolveAgTheme(mode, true)).cardCornerDp)
        }
        assertEquals(14, agColorsFor(resolveAgTheme(AgThemeMode.GLASS, true)).cardCornerDp)
        assertEquals(14, agColorsFor(resolveAgTheme(AgThemeMode.GLASS, false)).cardCornerDp)
    }

    @Test
    fun paletteDarkFlagMatchesTheResolvedTheme() {
        AgThemeMode.all.forEach { mode ->
            systemDarkStates.forEach { systemDark ->
                val resolved = resolveAgTheme(mode, systemDark)
                assertEquals(
                    "$mode/$systemDark",
                    resolved.isDark,
                    agColorsFor(resolved).isDark
                )
            }
        }
    }

    @Test
    fun classicPalettesKeepTheClassicFlagAndModernDoNot() {
        assertTrue(agColorsFor(resolveAgTheme(AgThemeMode.CLASSIC, true)).isClassic)
        assertTrue(agColorsFor(resolveAgTheme(AgThemeMode.CLASSIC, false)).isClassic)
        listOf(AgThemeMode.GRAPHITE, AgThemeMode.PAPER, AgThemeMode.OLED).forEach { mode ->
            assertFalse(agColorsFor(resolveAgTheme(mode, true)).isClassic)
        }
        assertFalse(agColorsFor(resolveAgTheme(AgThemeMode.GLASS, true)).isClassic)
        assertFalse(agColorsFor(resolveAgTheme(AgThemeMode.GLASS, false)).isClassic)
    }

    @Test
    fun graphiteAndPaperAreProperlyContrastingSurfaces() {
        val graphite = agColorsFor(resolveAgTheme(AgThemeMode.GRAPHITE, true))
        val paper = agColorsFor(resolveAgTheme(AgThemeMode.PAPER, false))

        assertNotEquals("graphite and paper backgrounds must differ", graphite.background, paper.background)
        assertNotEquals("text must not blend into a dark background", graphite.text, graphite.background)
        assertNotEquals("text must not blend into a light background", paper.text, paper.background)

        // Dark theme: light text on a dark surface, with a comfortable gap.
        assertTrue(
            "graphite text must be lighter than its background",
            luminance(graphite.text) > luminance(graphite.background)
        )
        assertTrue(
            "graphite text contrast is too low",
            luminance(graphite.text) - luminance(graphite.background) > 0.35
        )

        // Light theme: dark text on a soft paper surface.
        assertTrue(
            "paper text must be darker than its background",
            luminance(paper.text) < luminance(paper.background)
        )
    }

    @Test
    fun oledUsesAPureBlackSurfaceWithReadableText() {
        val oled = agColorsFor(resolveAgTheme(AgThemeMode.OLED, true))
        assertEquals(0xFF000000.toInt(), oled.background.toArgb())
        assertTrue("oled text must be lighter than pure black", luminance(oled.text) > 0.5)
    }

    /** Rough sRGB relative luminance used only for contrast sanity checks. */
    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
