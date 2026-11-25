package dev.agwidgets.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing contract for [AgStatus.fromJson] and [ZCodeStatus.fromJson].
 *
 * Every document here is pure JSON: no Android runtime, no network, no clock.
 */
class StatusParsingTest {

    private fun status(json: String): AgStatus =
        AgStatus.fromJson(json.trimIndent()) ?: error("Status document must parse: $json")

    // ---------------------------------------------------------------------
    // Full contract document
    // ---------------------------------------------------------------------

    @Test
    fun parsesFullContractDocument() {
        val parsed = status(
            """
            {
              "updated_at": "2026-11-25T03:15:25+02:00",
              "pc_updated_at": "2026-11-25T03:15:20+02:00",
              "antigravity": {
                "plan_tier": "Pro",
                "buckets": [
                  {"id": "gemini-5h", "kind": "5h", "remaining_fraction": 0.668,
                   "reset_time": "2026-11-25T04:28:35Z"},
                  {"id": "3p-weekly", "kind": "weekly", "remaining_fraction": 0.5,
                   "reset_time": "2026-12-01T23:28:35Z"}
                ]
              },
              "context_summary": {
                "conversation_id": "conv-1",
                "active": true,
                "model": "gemini-4-neon-high",
                "tokens": 377387,
                "window": 524288,
                "pct": 71.98,
                "level": "red",
                "warn_tokens": 209715,
                "risk_tokens": 340787,
                "yellow_pct": 40.0,
                "red_pct": 65.0,
                "window_source": "api"
              },
              "zcode": {
                "running": true,
                "coupon_state": "visible",
                "coupon_visible": true,
                "matched": "Забрать 500M",
                "plan_id": "pro-2500",
                "method": "api",
                "auth_ok": true,
                "network_error": false,
                "remaining_units": 250000000,
                "total_units": 500000000,
                "ends_at": "2026-12-01T23:28:35Z",
                "plan_name": "Pro 2500M",
                "checked_at": "2026-11-25T03:15:00+02:00"
              }
            }
            """,
        )

        assertEquals("2026-11-25T03:15:25+02:00", parsed.updatedAt)
        assertEquals("2026-11-25T03:15:20+02:00", parsed.pcUpdatedAt)
        assertEquals("Pro", parsed.planTier)
        assertNull(parsed.antigravityError)

        assertEquals(2, parsed.buckets.size)
        assertEquals("gemini-5h", parsed.buckets[0].id)
        assertEquals("5h", parsed.buckets[0].kind)
        assertEquals(0.668, parsed.buckets[0].remainingFraction!!, 0.000001)
        assertEquals("2026-11-25T04:28:35Z", parsed.buckets[0].resetTime)
        assertEquals("3p-weekly", parsed.buckets[1].id)
        assertEquals("weekly", parsed.buckets[1].kind)
        assertEquals(0.5, parsed.buckets[1].remainingFraction!!, 0.000001)
        assertEquals("2026-12-01T23:28:35Z", parsed.buckets[1].resetTime)

        val zcode = parsed.zcode!!
        assertTrue(zcode.running)
        assertTrue(zcode.couponVisible)
        assertTrue(zcode.isCouponVisible)
        assertEquals(ZCodeStatus.COUPON_VISIBLE, zcode.couponState)
        assertEquals("Забрать 500M", zcode.matched)
        assertEquals("pro-2500", zcode.planId)
        assertEquals("api", zcode.method)
        assertEquals(true, zcode.authOk)
        assertFalse(zcode.networkError)
        assertEquals(250_000_000L, zcode.remainingUnits)
        assertEquals(500_000_000L, zcode.totalUnits)
        assertEquals("2026-12-01T23:28:35Z", zcode.endsAt)
        assertEquals("Pro 2500M", zcode.planName)
        assertEquals("2026-11-25T03:15:00+02:00", zcode.checkedAt)

        val context = parsed.contextSummary!!
        assertEquals("conv-1", context.conversationId)
        assertTrue(context.active)
        assertEquals("gemini-4-neon-high", context.model)
        assertEquals(377387, context.tokens)
        assertEquals(524288, context.window)
        assertEquals(71.98, context.pct!!, 0.000001)
        assertEquals("red", context.level)
        assertEquals(209715, context.warnTokens)
        assertEquals(340787, context.riskTokens)
        assertEquals(40.0, context.yellowPct!!, 0.000001)
        assertEquals(65.0, context.redPct!!, 0.000001)
        assertEquals("api", context.windowSource)
    }

    // ---------------------------------------------------------------------
    // Non-finite and wrongly typed numbers
    // ---------------------------------------------------------------------

    @Test
    fun remainingFractionTurnsNonNumericAndNonFiniteValuesIntoNull() {
        val parsed = status(
            """
            {
              "antigravity": {
                "buckets": [
                  {"id": "nan-string", "kind": "5h", "remaining_fraction": "NaN",
                   "reset_time": "2026-11-25T04:28:35Z"},
                  {"id": "missing", "kind": "5h"},
                  {"id": "json-null", "kind": "5h", "remaining_fraction": null},
                  {"id": "garbage-string", "kind": "weekly", "remaining_fraction": "foo"},
                  {"id": "object-value", "kind": "weekly", "remaining_fraction": {"value": 1}},
                  {"id": "array-value", "kind": "weekly", "remaining_fraction": [1]},
                  {"id": "finite-number", "kind": "5h", "remaining_fraction": 0.668}
                ]
              }
            }
            """,
        )

        assertEquals(7, parsed.buckets.size)
        assertNull(parsed.buckets[0].remainingFraction)
        assertEquals("nan-string", parsed.buckets[0].id)
        assertEquals("2026-11-25T04:28:35Z", parsed.buckets[0].resetTime)
        assertNull(parsed.buckets[1].remainingFraction)
        assertNull(parsed.buckets[2].remainingFraction)
        assertNull(parsed.buckets[3].remainingFraction)
        assertNull(parsed.buckets[4].remainingFraction)
        assertNull(parsed.buckets[5].remainingFraction)
        assertEquals(0.668, parsed.buckets[6].remainingFraction!!, 0.000001)
    }

    @Test
    fun pctRejectsNaNAndInfinityButKeepsFiniteValues() {
        fun pctOf(value: String): Double? = status(
            """{"context_summary": {"model": "m", "tokens": 1, "window": 100, "pct": $value}}""",
        ).contextSummary!!.pct

        assertNull(pctOf("\"NaN\""))
        assertNull(pctOf("\"Infinity\""))
        assertNull(pctOf("\"-Infinity\""))
        assertNull(pctOf("\"foo\""))
        assertEquals(71.98, pctOf("71.98")!!, 0.000001)
    }

    @Test
    fun tokensRejectBooleansAndGarbageButAcceptNumbers() {
        fun tokensOf(value: String): Int? = status(
            """{"context_summary": {"model": "m", "tokens": $value}}""",
        ).contextSummary!!.tokens

        assertNull(tokensOf("\"abc\""))
        assertNull(tokensOf("true"))
        assertNull(tokensOf("false"))
        assertNull(tokensOf("null"))
        assertEquals(1234, tokensOf("1234")!!)
        assertEquals(1234, tokensOf("\"1234\"")!!)
    }

    @Test
    fun windowMustBePositive() {
        fun windowOf(value: String): Int? = status(
            """{"context_summary": {"model": "m", "tokens": 1, "window": $value}}""",
        ).contextSummary!!.window

        assertNull(windowOf("0"))
        assertNull(windowOf("-1"))
        assertNull(windowOf("true"))
        assertNull(windowOf("\"abc\""))
        assertEquals(524288, windowOf("524288")!!)
    }

    @Test
    fun zcodeUnitsUseStrictParsing() {
        fun zcodeOf(fragment: String): ZCodeStatus = status(
            """{"zcode": {"running": true, "coupon_state": "visible", $fragment}}""",
        ).zcode!!

        assertEquals(250_000_000L, zcodeOf("\"remaining_units\": \"250000000\"").remainingUnits)
        assertEquals(250_000_000L, zcodeOf("\"remaining_units\": 250000000").remainingUnits)
        assertNull(zcodeOf("\"remaining_units\": \"abc\"").remainingUnits)
        assertNull(zcodeOf("\"remaining_units\": true").remainingUnits)
        assertEquals(500_000_000L, zcodeOf("\"total_units\": \"500000000\"").totalUnits)
        assertNull(zcodeOf("\"total_units\": true").totalUnits)
        assertNull(zcodeOf("\"total_units\": \"NaN\"").totalUnits)
    }

    // ---------------------------------------------------------------------
    // Document markers: a random ntfy message must not wipe the cache
    // ---------------------------------------------------------------------

    @Test
    fun documentsWithoutStatusMarkerAreRejected() {
        assertNull(AgStatus.fromJson("{}"))
        assertNull(AgStatus.fromJson("""{"foo": 1}"""))
        assertNull(AgStatus.fromJson("""{"saved_at": "2026-11-25T03:15:25+02:00"}"""))
        assertNull(AgStatus.fromJson("not a json object"))

        assertNotNull(AgStatus.fromJson("""{"updated_at": "2026-11-25T03:15:25+02:00"}"""))
        assertNotNull(AgStatus.fromJson("""{"antigravity": {}}"""))
        assertNotNull(AgStatus.fromJson("""{"zcode": {}}"""))
        assertNotNull(AgStatus.fromJson("""{"context_summary": {}}"""))
    }

    // ---------------------------------------------------------------------
    // v2 fields
    // ---------------------------------------------------------------------

    @Test
    fun readsPcUpdatedAtAndAntigravityError() {
        val parsed = status(
            """
            {
              "updated_at": "2026-11-25T03:15:25+02:00",
              "pc_updated_at": "2026-11-25T03:14:00+02:00",
              "antigravity": {
                "plan_tier": "Pro",
                "error": "quota endpoint timed out",
                "buckets": []
              }
            }
            """,
        )

        assertEquals("2026-11-25T03:14:00+02:00", parsed.pcUpdatedAt)
        assertEquals("2026-11-25T03:15:25+02:00", parsed.updatedAt)
        assertEquals("Pro", parsed.planTier)
        assertEquals("quota endpoint timed out", parsed.antigravityError)
    }

    @Test
    fun pcUpdatedAtFallsBackToUpdatedAtWhenAbsent() {
        val parsed = status("""{"updated_at": "2026-11-25T03:15:25+02:00"}""")

        assertEquals("2026-11-25T03:15:25+02:00", parsed.updatedAt)
        assertEquals("2026-11-25T03:15:25+02:00", parsed.pcUpdatedAt)
        assertTrue(parsed.buckets.isEmpty())
        assertNull(parsed.zcode)
        assertNull(parsed.contextSummary)
    }

    @Test
    fun readsContextThresholdsAndWindowSource() {
        val parsed = status(
            """
            {
              "context_summary": {
                "model": "m",
                "tokens": 10,
                "window": 100,
                "yellow_pct": 35.5,
                "red_pct": 70.25,
                "window_source": "logs"
              }
            }
            """,
        )

        val context = parsed.contextSummary!!
        assertEquals(35.5, context.yellowPct!!, 0.000001)
        assertEquals(70.25, context.redPct!!, 0.000001)
        assertEquals("logs", context.windowSource)
    }

    // ---------------------------------------------------------------------
    // Tri-state coupon: visible / absent / unknown
    // ---------------------------------------------------------------------

    @Test
    fun unknownCouponStateIsNotTreatedAsAbsent() {
        val zcode = status(
            """
            {
              "zcode": {
                "running": false,
                "coupon_state": "unknown",
                "coupon_visible": null,
                "error_kind": "network",
                "network_error": true,
                "checked_at": "2026-11-25T03:00:00+02:00"
              }
            }
            """,
        ).zcode!!

        assertTrue(zcode.isCouponUnknown)
        assertFalse(zcode.isCouponVisible)
        assertFalse(zcode.couponVisible)
        assertEquals(ZCodeStatus.COUPON_UNKNOWN, zcode.couponState)
        assertEquals("network", zcode.errorKind)
        assertTrue(zcode.networkError)
    }

    @Test
    fun explicitCouponStateWinsOverLegacyFlag() {
        val absent = status(
            """{"zcode": {"running": true, "coupon_state": "absent", "coupon_visible": false}}""",
        ).zcode!!
        assertEquals(ZCodeStatus.COUPON_ABSENT, absent.couponState)
        assertFalse(absent.isCouponUnknown)
        assertFalse(absent.isCouponVisible)
        assertFalse(absent.couponVisible)

        val visible = status(
            """{"zcode": {"running": true, "coupon_state": "visible"}}""",
        ).zcode!!
        assertEquals(ZCodeStatus.COUPON_VISIBLE, visible.couponState)
        assertTrue(visible.isCouponVisible)
        assertTrue(visible.couponVisible)

        val unknown = status(
            """{"zcode": {"coupon_state": "unknown", "coupon_visible": false}}""",
        ).zcode!!
        assertTrue(unknown.isCouponUnknown)
        assertFalse(unknown.couponVisible)
    }

    @Test
    fun legacyCouponFlagMapsToVisibleOrAbsent() {
        val absent = status(
            """{"zcode": {"running": true, "coupon_visible": false, "method": "api"}}""",
        ).zcode!!
        assertEquals(ZCodeStatus.COUPON_ABSENT, absent.couponState)
        assertFalse(absent.isCouponUnknown)
        assertFalse(absent.couponVisible)

        val visible = status(
            """{"zcode": {"running": true, "coupon_visible": true}}""",
        ).zcode!!
        assertEquals(ZCodeStatus.COUPON_VISIBLE, visible.couponState)
        assertTrue(visible.isCouponVisible)
        assertTrue(visible.couponVisible)
    }

    @Test
    fun legacyNetworkOrAuthErrorMapsToUnknownCoupon() {
        val network = status(
            """{"zcode": {"running": false, "network_error": true}}""",
        ).zcode!!
        assertEquals(ZCodeStatus.COUPON_UNKNOWN, network.couponState)
        assertTrue(network.isCouponUnknown)
        assertFalse(network.couponVisible)

        val auth = status(
            """{"zcode": {"running": false, "error_kind": "auth"}}""",
        ).zcode!!
        assertEquals(ZCodeStatus.COUPON_UNKNOWN, auth.couponState)
        assertTrue(auth.isCouponUnknown)
        assertEquals("auth", auth.errorKind)
    }

    @Test
    fun toJsonWritesNullCouponVisibleForUnknownState() {
        val unknown = ZCodeStatus(
            running = false,
            couponVisible = false,
            couponState = ZCodeStatus.COUPON_UNKNOWN,
            networkError = true,
            errorKind = "network",
        ).toJson()
        assertEquals(ZCodeStatus.COUPON_UNKNOWN, unknown.getString("coupon_state"))
        assertTrue(unknown.has("coupon_visible"))
        assertTrue(unknown.isNull("coupon_visible"))

        val visible = ZCodeStatus(running = true, couponVisible = true).toJson()
        assertEquals(ZCodeStatus.COUPON_VISIBLE, visible.getString("coupon_state"))
        assertTrue(visible.getBoolean("coupon_visible"))

        val absent = ZCodeStatus(running = true, couponVisible = false).toJson()
        assertEquals(ZCodeStatus.COUPON_ABSENT, absent.getString("coupon_state"))
        assertFalse(absent.getBoolean("coupon_visible"))
    }

    @Test
    fun zcodeRoundTripKeepsCouponStateAndErrorDetails() {
        val original = ZCodeStatus(
            running = false,
            couponVisible = false,
            couponState = ZCodeStatus.COUPON_UNKNOWN,
            matched = "",
            planId = "pro-2500",
            method = "api",
            authOk = null,
            networkError = true,
            errorKind = "network",
            remainingUnits = 250_000_000L,
            totalUnits = 500_000_000L,
            endsAt = "2026-12-01T23:28:35Z",
            planName = "Pro 2500M",
            checkedAt = "2026-11-25T03:00:00+02:00",
        )

        val parsed = ZCodeStatus.fromJson(JSONObject(original.toJson().toString()))

        assertEquals(original, parsed)
        assertEquals(ZCodeStatus.COUPON_UNKNOWN, parsed.couponState)
        assertTrue(parsed.isCouponUnknown)
        assertEquals("network", parsed.errorKind)
        assertEquals("Pro 2500M", parsed.planName)
        assertEquals("2026-12-01T23:28:35Z", parsed.endsAt)
        assertEquals("2026-11-25T03:00:00+02:00", parsed.checkedAt)
        assertTrue(parsed.networkError)
    }

    // ---------------------------------------------------------------------
    // Legacy document without any v2 fields
    // ---------------------------------------------------------------------

    @Test
    fun parsesLegacyStatusWithoutNewFields() {
        val parsed = status(
            """
            {
              "antigravity": {"plan_tier": "Pro", "buckets": []},
              "zcode": {"running": true, "coupon_visible": false, "method": "api"},
              "context_summary": {"model": "gemini-4-neon-high", "tokens": 1000, "level": "green"}
            }
            """,
        )

        assertNull(parsed.updatedAt)
        assertNull(parsed.pcUpdatedAt)
        assertNull(parsed.antigravityError)

        val zcode = parsed.zcode!!
        assertFalse(zcode.networkError)
        assertNull(zcode.errorKind)
        assertEquals(ZCodeStatus.COUPON_ABSENT, zcode.couponState)

        val context = parsed.contextSummary!!
        assertNull(context.window)
        assertNull(context.pct)
        assertNull(context.yellowPct)
        assertNull(context.redPct)
        assertNull(context.windowSource)
        assertEquals(1000, context.tokens)
        assertEquals("green", context.level)
    }

    @Test
    fun rejectsInvalidJson() {
        assertNull(AgStatus.fromJson("not a json object"))
        assertNull(AgStatus.fromJson(""))
        assertNull(AgStatus.fromJson("[]"))
    }
}
