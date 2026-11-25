package dev.agwidgets.ui

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Parity tests for the pure-JVM formatters shared with the Python collector and
 * the JS widget (contract §2.4). Expectations are pinned to the actual
 * behaviour of Format.kt; no Android APIs are involved.
 */
class FormatParityTest {

    private val nowMs = 1_766_000_000_000L

    private lateinit var originalLocale: Locale

    @Before
    fun useRussianDefaultLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale("ru", "RU"))
    }

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalLocale)
    }

    // --- formatWindow -----------------------------------------------------

    @Test
    fun formatWindowUsesKilobytesBelowOneMegabyte() {
        assertEquals("512", formatWindow(512))
        assertEquals("1023", formatWindow(1_023))
        assertEquals("1K", formatWindow(1_024))
        assertEquals("512K", formatWindow(524_288))
        assertEquals("128K", formatWindow(131_072))
    }

    @Test
    fun formatWindowUsesMegabytesAtAndAboveOneMegabyte() {
        assertEquals("1M", formatWindow(1_048_576))
        assertEquals("1M", formatWindow(1_000_000))
        assertEquals("2M", formatWindow(2_097_152))
    }

    @Test
    fun formatWindowNonPositiveIsDash() {
        assertEquals("—", formatWindow(0))
        assertEquals("—", formatWindow(-1))
        assertEquals("—", formatWindow(-1_048_576))
    }

    // --- formatK / formatUnits -------------------------------------------

    @Test
    fun formatKMatchesContractScale() {
        assertEquals("1M", formatK(999_600))
        assertEquals("1M", formatK(1_000_000))
        assertEquals("1.2M", formatK(1_200_000))
        assertEquals("524K", formatK(524_288))
        assertEquals("999", formatK(999))
        assertEquals("1K", formatK(1_000))
        assertEquals("0", formatK(0))
    }

    @Test
    fun formatUnitsMatchesContractScale() {
        assertEquals("500M", formatUnits(500_000_000L))
        assertEquals("300M", formatUnits(300_000_000L))
        assertEquals("1B", formatUnits(1_000_000_000L))
        assertEquals("1.5B", formatUnits(1_500_000_000L))
        assertEquals("1K", formatUnits(1_000L))
        assertEquals("1M", formatUnits(999_600L))
        assertEquals("0", formatUnits(0L))
        assertEquals("999", formatUnits(999L))
    }

    @Test
    fun formattingKeepsUsDecimalPointUnderRussianLocale() {
        assertEquals("ru", Locale.getDefault().language)
        assertEquals("1.2M", formatK(1_200_000))
        assertEquals("2M", formatWindow(2_097_152))
        assertEquals("1.5B", formatUnits(1_500_000_000L))
    }

    // --- countdowns -------------------------------------------------------

    @Test
    fun countdownEpochZeroIsSuppressed() {
        assertEquals("—", formatCountdownShort("1970-01-01T00:00:00Z", nowMs))
        assertEquals("", formatCountdown("1970-01-01T00:00:00Z", nowMs))
    }

    @Test
    fun countdownNullInputIsEmptyOrNull() {
        assertNull(formatCountdownShort(null, nowMs))
        assertEquals("", formatCountdown(null, nowMs))
    }

    @Test
    fun countdownGarbageInputIsEmptyOrNull() {
        assertNull(formatCountdownShort("garbage", nowMs))
        assertEquals("", formatCountdown("garbage", nowMs))
    }

    @Test
    fun countdownPastInstantIsResetNow() {
        val past = "2020-01-01T00:00:00Z"
        assertEquals("сброс сейчас", formatCountdownShort(past, nowMs))
        assertEquals("сброс сейчас", formatCountdown(past, nowMs))
    }

    @Test
    fun countdownFutureInstantShowsDays() {
        val inTwoDays = Instant.ofEpochMilli(nowMs).plus(2, ChronoUnit.DAYS).toString()
        val shortRaw = formatCountdownShort(inTwoDays, nowMs)
        assertNotNull(shortRaw)
        val short = requireNotNull(shortRaw)
        val long = formatCountdown(inTwoDays, nowMs)
        assertTrue("short countdown should mention days: $short", short.contains("д"))
        assertFalse("short countdown must not say reset-now: $short", short.contains("сейчас"))
        assertTrue("long countdown should mention days: $long", long.contains("д"))
        assertFalse("long countdown must not say reset-now: $long", long.contains("сейчас"))
    }

    // --- parseInstant -----------------------------------------------------

    @Test
    fun parseInstantTreatsOffsetAndUtcAsSameInstant() {
        val withOffset = parseInstant("2026-11-25T10:00:00+02:00")
        val utc = parseInstant("2026-11-25T08:00:00Z")
        assertNotNull(withOffset)
        assertNotNull(utc)
        assertEquals(Instant.parse("2026-11-25T08:00:00Z"), withOffset)
        assertEquals(utc, withOffset)
    }

    @Test
    fun parseInstantRejectsNullBlankAndGarbage() {
        assertNull(parseInstant(null))
        assertNull(parseInstant(""))
        assertNull(parseInstant("   "))
        assertNull(parseInstant("garbage"))
        assertNull(parseInstant("2026-13-45T99:99:99Z"))
    }

    // --- contextLevel -----------------------------------------------------

    @Test
    fun contextLevelUnknownWireLevelIsNeverGreen() {
        assertEquals("unknown", contextLevel(70.0, "unknown"))
        assertEquals("unknown", contextLevel(0.0, "unknown"))
    }

    @Test
    fun contextLevelUsesDefaultThresholds() {
        assertEquals("green", contextLevel(10.0, null))
        assertEquals("green", contextLevel(39.99, null))
        assertEquals("yellow", contextLevel(40.0, null))
        assertEquals("yellow", contextLevel(50.0, null))
        assertEquals("red", contextLevel(65.0, null))
        assertEquals("red", contextLevel(70.0, null))
    }

    @Test
    fun contextLevelHonoursCustomThresholds() {
        assertEquals("green", contextLevel(29.0, null, 30.0, 60.0))
        assertEquals("yellow", contextLevel(30.0, null, 30.0, 60.0))
        assertEquals("yellow", contextLevel(50.0, null, 30.0, 60.0))
        assertEquals("red", contextLevel(60.0, null, 30.0, 60.0))
    }

    @Test
    fun contextLevelInvalidThresholdsFallBackToDefaults() {
        // red (40) <= yellow (50) is invalid, so the 40/65 defaults apply.
        assertEquals("yellow", contextLevel(45.0, null, 50.0, 40.0))
        assertEquals("green", contextLevel(39.0, null, 50.0, 40.0))
        assertEquals("red", contextLevel(65.0, null, 50.0, 40.0))
    }

    @Test
    fun contextLevelNullAndNaNPercentAreNull() {
        assertNull(contextLevel(null, null))
        assertNull(contextLevel(Double.NaN, null))
        assertNull(contextLevel(Double.POSITIVE_INFINITY, null))
    }

    @Test
    fun contextLevelExplicitWireLevelsPassThrough() {
        assertEquals("green", contextLevel(99.0, "green"))
        assertEquals("yellow", contextLevel(0.0, "yellow"))
        assertEquals("red", contextLevel(10.0, "red"))
        assertEquals("unknown", contextLevel(Double.NaN, "unknown"))
    }

    // --- prettyModel ------------------------------------------------------

    @Test
    fun prettyModelStripsEffortSuffixAndDotsVersion() {
        assertEquals("Claude Opus 5.5", prettyModel("claude-opus-5-5-high"))
        assertEquals("Gemini 4 Neon", prettyModel("gemini-4-neon-high"))
        assertEquals("Glm 5", prettyModel("glm-5"))
    }

    // --- formatDateShort --------------------------------------------------

    @Test
    fun formatDateShortMatchesDayMonthPattern() {
        val formatted = formatDateShort("2026-12-01T00:00:00Z")
        assertTrue("expected DD.MM, got: $formatted", formatted.matches(Regex("""\d{2}\.\d{2}""")))
        assertNotEquals("expected a real date, not a dash", "—", formatted)
    }

    @Test
    fun formatDateShortGarbageIsDash() {
        assertEquals("—", formatDateShort("garbage"))
        assertEquals("—", formatDateShort(""))
    }
}
