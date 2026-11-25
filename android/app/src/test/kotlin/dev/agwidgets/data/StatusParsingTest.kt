package dev.agwidgets.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusParsingTest {

    @Test
    fun parsesFullStatusWithContextWindowAndNetworkFlag() {
        val raw = """
            {
              "updated_at": "2026-11-25T03:15:25+02:00",
              "antigravity": {
                "plan_tier": "Pro",
                "buckets": [
                  {"id": "gemini-5h", "kind": "5h", "remaining_fraction": 0.668, "reset_time": "2026-11-25T04:28:35Z"},
                  {"id": "3p-weekly", "kind": "weekly", "remaining_fraction": 0.5, "reset_time": "2026-12-01T23:28:35Z"}
                ]
              },
              "zcode": {
                "running": true,
                "coupon_visible": true,
                "matched": "Забрать 500M",
                "method": "api",
                "auth_ok": true,
                "network_error": false,
                "remaining_units": 250000000,
                "total_units": 500000000,
                "ends_at": "2026-12-01T23:28:35Z"
              },
              "context_summary": {
                "model": "gemini-4-neon-high",
                "tokens": 377387,
                "window": 524288,
                "pct": 71.98,
                "level": "red",
                "warn_tokens": 209715,
                "risk_tokens": 340787
              }
            }
        """.trimIndent()

        val status = AgStatus.fromJson(raw)!!
        assertEquals("Pro", status.planTier)
        assertEquals(2, status.buckets.size)
        assertEquals(0.668, status.buckets[0].remainingFraction!!, 0.0001)
        assertEquals("gemini-5h", status.buckets[0].id)
        assertEquals("weekly", status.buckets[1].kind)

        val z = status.zcode!!
        assertTrue(z.couponVisible)
        assertEquals("Забрать 500M", z.matched)
        assertFalse(z.networkError)
        assertEquals(250_000_000L, z.remainingUnits)
        assertEquals(500_000_000L, z.totalUnits)

        val ctx = status.contextSummary!!
        assertEquals("gemini-4-neon-high", ctx.model)
        assertEquals(377387, ctx.tokens)
        assertEquals(524288, ctx.window)
        assertEquals(71.98, ctx.pct!!, 0.001)
        assertEquals("red", ctx.level)
        assertEquals(209715, ctx.warnTokens)
        assertEquals(340787, ctx.riskTokens)
    }

    @Test
    fun parsesLegacyStatusWithoutNewFields() {
        val raw = """
            {
              "antigravity": {"plan_tier": "Pro", "buckets": []},
              "zcode": {"running": true, "coupon_visible": false, "method": "api"},
              "context_summary": {"model": "gemini-4-neon-high", "tokens": 1000, "level": "green"}
            }
        """.trimIndent()

        val status = AgStatus.fromJson(raw)!!
        assertFalse(status.zcode!!.networkError)
        val context = status.contextSummary!!
        assertNull(context.window)
        assertNull(context.pct)
        assertEquals(1000, context.tokens)
    }

    @Test
    fun rejectsInvalidJson() {
        assertNull(AgStatus.fromJson("not a json object"))
    }

    @Test
    fun zcodeRoundTripKeepsNetworkError() {
        val original = ZCodeStatus(
            running = true,
            couponVisible = false,
            method = "api",
            authOk = null,
            networkError = true,
            checkedAt = "2026-11-25T03:00:00Z",
        )
        val parsed = ZCodeStatus.fromJson(JSONObject(original.toJson().toString()))
        assertTrue(parsed.networkError)
        assertNull(parsed.authOk)
        assertEquals("2026-11-25T03:00:00Z", parsed.checkedAt)
    }
}
