package dev.agwidgets

import dev.agwidgets.data.*
import dev.agwidgets.ui.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.round

/**
 * Golden fixtures shared with the Python collector and the JS widget
 * (`tests/fixtures`). Every case is replayed against the real main-code
 * parsers and formatters on a plain JVM, so a drift between the three
 * implementations fails here instead of shipping a divergent UI.
 */
class GoldenFixturesTest {

    private lateinit var originalLocale: Locale
    private lateinit var fixtures: File

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        // Numbers must always use a dot, whatever the device locale is.
        Locale.setDefault(Locale("ru", "RU"))
        fixtures = fixturesDir()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    // --- fixture loading --------------------------------------------------

    private fun fixturesDir(): File {
        val base = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            "../../tests/fixtures",
            "../tests/fixtures",
            "tests/fixtures",
            "../../../tests/fixtures",
        ).map { File(base, it) }
        return candidates.firstOrNull { it.isDirectory }
            ?: run {
                fail(
                    "Fixture directory not found from ${base.absolutePath}. Looked in:\n" +
                        candidates.joinToString("\n") { it.absolutePath }
                )
                error("unreachable")
            }
    }

    private fun fixture(name: String): JSONObject {
        val file = File(fixtures, name)
        if (!file.isFile) fail("Fixture file not found: ${file.absolutePath}")
        return JSONObject(file.readText())
    }

    // --- zcode_cases.json -------------------------------------------------

    @Test
    fun balanceCasesMatchParser() {
        val cases = fixture("zcode_cases.json").getJSONArray("balance_cases")
        assertTrue("balance_cases must not be empty", cases.length() > 0)

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val label = case.getString("name")
            val expected = case.getJSONObject("expected")

            val result = parseBalanceResponse(
                JSONObject(case.getJSONObject("payload").toString()),
                200,
                fallbackNowSec = case.getDouble("now_sec"),
            )

            if (expected.isNull("remaining_units")) {
                assertNull("$label: remaining_units", result.remainingUnits)
            } else {
                assertEquals(
                    "$label: remaining_units",
                    expected.getLong("remaining_units"),
                    result.remainingUnits,
                )
            }
            if (expected.isNull("total_units")) {
                assertNull("$label: total_units", result.totalUnits)
            } else {
                assertEquals("$label: total_units", expected.getLong("total_units"), result.totalUnits)
            }
            assertEquals("$label: plan_name", expected.getString("plan_name"), result.planName)
            if (expected.isNull("ends_at")) {
                assertNull("$label: ends_at", result.endsAt)
            } else {
                assertEquals("$label: ends_at", expected.getString("ends_at"), result.endsAt)
            }
            assertNull("$label: errorKind", result.errorKind)
        }
    }

    @Test
    fun touchCasesMatchParser() {
        val cases = fixture("zcode_cases.json").getJSONArray("touch_cases")
        assertTrue("touch_cases must not be empty", cases.length() > 0)

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val label = case.getString("name")
            val expected = case.getJSONObject("expected")

            val result = parseTouchResponse(JSONObject(case.getJSONObject("payload").toString()), 200)

            assertEquals("$label: coupon_state", expected.getString("coupon_state"), result.couponState)
            assertEquals("$label: plan_id", expected.getString("plan_id"), result.planId)
            assertEquals("$label: matched", expected.getString("matched"), result.matched)
        }
    }

    // --- format_cases.json ------------------------------------------------

    @Test
    fun windowCasesMatchFormatter() {
        val cases = fixture("format_cases.json").getJSONArray("window_cases")
        assertTrue("window_cases must not be empty", cases.length() > 0)

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val input = case.getInt("input")
            assertEquals("formatWindow($input)", case.getString("expected"), formatWindow(input))
        }
    }

    @Test
    fun tokenCasesMatchFormatterUnderRussianLocale() {
        val cases = fixture("format_cases.json").getJSONArray("token_cases")
        assertTrue("token_cases must not be empty", cases.length() > 0)
        // setUp() already switched the default locale to ru_RU: "1.2M" here
        // proves the decimal separator is a dot and never a comma.
        assertEquals("ru", Locale.getDefault().language)

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val input = case.getInt("input")
            assertEquals("formatK($input) under ru_RU", case.getString("expected"), formatK(input))
        }
    }

    @Test
    fun contextLevelCasesMatchThresholds() {
        val cases = fixture("format_cases.json").getJSONArray("context_level_cases")
        assertTrue("context_level_cases must not be empty", cases.length() > 0)

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val label = "context level case #$index"
            val yellow = case.getDouble("yellow_pct")
            val red = case.getDouble("red_pct")

            if (case.isNull("window")) {
                assertNull("$label: no window means no pct", contextLevel(null, null, yellow, red))
                assertEquals("$label: unknown level", "unknown", contextLevel(0.0, "unknown", yellow, red))
                continue
            }

            val tokens = case.getInt("tokens")
            val window = case.getInt("window")
            val pct = round((tokens.toDouble() / window * 100.0) * 100.0) / 100.0

            assertEquals("$label: pct", case.getDouble("expected_pct"), pct, 1e-9)
            assertEquals(
                "$label: level",
                case.getString("expected_level"),
                contextLevel(pct, null, yellow, red),
            )
        }
    }

    @Test
    fun oauthUrlCasesMatchAllowlist() {
        val cases = fixture("format_cases.json").getJSONArray("oauth_url_cases")
        assertTrue("oauth_url_cases must not be empty", cases.length() > 0)

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val url = case.getString("url")
            val trusted = case.getBoolean("trusted")
            assertEquals("isTrustedAuthorizeUrl($url)", trusted, isTrustedAuthorizeUrl(url) != null)
        }
    }

    // --- status_cases.json ------------------------------------------------

    private fun statusCase(key: String): AgStatus {
        val raw = fixture("status_cases.json").getJSONObject(key).toString()
        return AgStatus.fromJson(raw) ?: error("$key must parse")
    }

    @Test
    fun validV2StatusParses() {
        val status = statusCase("valid_v2")

        val zcode = status.zcode ?: error("valid_v2: zcode must parse")
        assertEquals("unknown", zcode.couponState)
        assertTrue(zcode.isCouponUnknown)
        assertFalse(zcode.couponVisible)

        assertEquals(2, status.buckets.size)
        val threeP = status.buckets.firstOrNull { it.id == "3p-5h" }
        assertNotNull("valid_v2: 3p-5h bucket", threeP)
        assertEquals("1970-01-01T00:00:00Z", threeP!!.resetTime)

        assertEquals("2026-11-25T11:58:00+02:00", status.pcUpdatedAt)

        val context = status.contextSummary ?: error("valid_v2: context_summary must parse")
        assertEquals(20.11, context.pct!!, 1e-9)
        assertEquals("green", context.level)
        assertEquals(40.0, context.yellowPct!!, 1e-9)
        assertEquals(65.0, context.redPct!!, 1e-9)
        assertEquals("default", context.windowSource)
    }

    @Test
    fun validV1StatusParses() {
        val status = statusCase("valid_v1")

        val zcode = status.zcode ?: error("valid_v1: zcode must parse")
        // Legacy `coupon_visible: false` without `coupon_state` maps to absent.
        assertEquals("absent", zcode.couponState)

        assertEquals("2026-11-20T09:00:00+03:00", status.updatedAt)
        assertEquals(status.updatedAt, status.pcUpdatedAt)
        assertEquals("unknown", status.contextSummary?.level)
    }

    @Test
    fun invalidEmptyStatusIsRejected() {
        assertEquals("{}", fixture("status_cases.json").getJSONObject("invalid_empty").toString())
        assertNull(AgStatus.fromJson("{}"))
    }
}
