package dev.agwidgets.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for [mergeStatus]: the PC document owns quotas, context and
 * timestamps; the phone-side ZCode check only replaces the `zcode` block when
 * it is at least as fresh as the PC one.
 */
class StatusMergeTest {

    private companion object {
        const val PC_UPDATED_AT = "2026-11-25T03:00:00+02:00"
        const val PC_UPDATED_AT_V2 = "2026-11-25T03:05:00+02:00"
        const val OLD_CHECKED_AT = "2026-11-25T03:00:00+02:00"
        const val FRESH_CHECKED_AT = "2026-11-25T03:10:00+02:00"

        const val PC_RAW =
            """{"updated_at": "2026-11-25T03:00:00+02:00", "antigravity": {"plan_tier": "Pro", "buckets": []}, "zcode": {"running": true, "coupon_state": "absent", "coupon_visible": false}}"""
    }

    private fun pcStatus(
        updatedAt: String = PC_UPDATED_AT,
        pcUpdatedAt: String? = null,
        zcode: ZCodeStatus? = ZCodeStatus(
            running = true,
            couponVisible = false,
            method = "api",
            checkedAt = OLD_CHECKED_AT,
        ),
        rawJson: String = PC_RAW,
    ) = AgStatus(
        updatedAt = updatedAt,
        planTier = "Pro",
        buckets = listOf(
            QuotaBucket(
                id = "gemini-5h",
                kind = "5h",
                remainingFraction = 0.66,
                resetTime = "2026-11-25T04:28:35Z",
            ),
        ),
        zcode = zcode,
        contextSummary = ContextSummary(
            conversationId = "conv-1",
            active = true,
            model = "gemini-4-neon-high",
            tokens = 377387,
            window = 524288,
            pct = 71.98,
            level = "red",
        ),
        rawJson = rawJson,
        pcUpdatedAt = pcUpdatedAt,
    )

    private fun directZCode(
        checkedAt: String = FRESH_CHECKED_AT,
        couponVisible: Boolean = true,
        planId: String = "pro-2500",
    ) = ZCodeStatus(
        running = true,
        couponVisible = couponVisible,
        planId = planId,
        matched = "500M",
        method = "api",
        authOk = true,
        remainingUnits = 250_000_000L,
        totalUnits = 500_000_000L,
        checkedAt = checkedAt,
    )

    @Test
    fun nothingToMerge() {
        assertNull(mergeStatus(null, null))
    }

    @Test
    fun phoneOnlyStatusStillRenders() {
        val direct = directZCode()

        val merged = mergeStatus(null, direct)!!

        assertTrue(merged.buckets.isEmpty())
        assertNull(merged.contextSummary)
        assertNull(merged.planTier)
        assertEquals(direct.checkedAt, merged.updatedAt)
        assertNull(merged.pcUpdatedAt)
        assertEquals(direct, merged.zcode)
        // Phone-only rawJson is the ZCode document itself, without a "zcode" wrapper.
        assertEquals("visible", JSONObject(merged.rawJson).getString("coupon_state"))
    }

    @Test
    fun pcOnlyStatusIsReturnedUnchanged() {
        val pc = pcStatus()

        assertSame(pc, mergeStatus(pc, null))
    }

    @Test
    fun freshPcZCodeIsNotOverwrittenByStaleDirect() {
        val pc = pcStatus(
            pcUpdatedAt = PC_UPDATED_AT_V2,
            zcode = ZCodeStatus(
                running = true,
                couponVisible = false,
                method = "api",
                checkedAt = FRESH_CHECKED_AT,
            ),
        )
        val direct = directZCode(checkedAt = OLD_CHECKED_AT)

        val merged = mergeStatus(pc, direct)!!

        assertSame(pc.zcode, merged.zcode)
        assertEquals(pc.updatedAt, merged.updatedAt)
        assertEquals(PC_UPDATED_AT, merged.updatedAt)
        assertEquals(PC_UPDATED_AT_V2, merged.pcUpdatedAt)
        assertEquals(pc.buckets, merged.buckets)
        assertEquals(
            "absent",
            JSONObject(merged.rawJson).getJSONObject("zcode").getString("coupon_state"),
        )
    }

    @Test
    fun freshDirectZCodeWinsButKeepsPcTimestamps() {
        val pc = pcStatus(
            pcUpdatedAt = PC_UPDATED_AT_V2,
            zcode = ZCodeStatus(
                running = true,
                couponVisible = false,
                method = "api",
                checkedAt = OLD_CHECKED_AT,
            ),
        )
        val direct = directZCode(checkedAt = FRESH_CHECKED_AT)

        val merged = mergeStatus(pc, direct)!!

        assertSame(direct, merged.zcode)
        assertEquals(PC_UPDATED_AT, merged.updatedAt)
        assertEquals(PC_UPDATED_AT_V2, merged.pcUpdatedAt)
        assertEquals(pc.buckets, merged.buckets)
        assertEquals(pc.planTier, merged.planTier)
        assertSame(pc.contextSummary, merged.contextSummary)
    }

    @Test
    fun equalTimestampsLetDirectWin() {
        val pc = pcStatus(
            pcUpdatedAt = PC_UPDATED_AT_V2,
            zcode = ZCodeStatus(
                running = true,
                couponVisible = false,
                method = "api",
                checkedAt = FRESH_CHECKED_AT,
            ),
        )
        val direct = directZCode(checkedAt = FRESH_CHECKED_AT)

        val merged = mergeStatus(pc, direct)!!

        assertSame(direct, merged.zcode)
        assertEquals(PC_UPDATED_AT, merged.updatedAt)
        assertEquals(PC_UPDATED_AT_V2, merged.pcUpdatedAt)
    }

    @Test
    fun missingPcZCodeAcceptsDirectWithoutTouchingPcTimestamps() {
        val pc = pcStatus(pcUpdatedAt = PC_UPDATED_AT_V2, zcode = null)
        val direct = directZCode(checkedAt = FRESH_CHECKED_AT)

        val merged = mergeStatus(pc, direct)!!

        assertSame(direct, merged.zcode)
        assertEquals(PC_UPDATED_AT, merged.updatedAt)
        assertEquals(PC_UPDATED_AT_V2, merged.pcUpdatedAt)
        assertEquals(pc.buckets, merged.buckets)
    }

    @Test
    fun mergedRawJsonReflectsDirectZCodeButKeepsPcUpdatedAt() {
        val pc = pcStatus(
            pcUpdatedAt = PC_UPDATED_AT_V2,
            zcode = ZCodeStatus(
                running = true,
                couponVisible = false,
                method = "api",
                checkedAt = OLD_CHECKED_AT,
            ),
        )
        val direct = directZCode(checkedAt = FRESH_CHECKED_AT)

        val merged = mergeStatus(pc, direct)!!
        val raw = JSONObject(merged.rawJson)
        val rawZCode = raw.getJSONObject("zcode")

        assertEquals("visible", rawZCode.getString("coupon_state"))
        assertEquals("pro-2500", rawZCode.getString("plan_id"))
        assertEquals(FRESH_CHECKED_AT, rawZCode.getString("checked_at"))
        assertEquals(PC_UPDATED_AT, raw.getString("updated_at"))
        assertEquals(pc.updatedAt, merged.updatedAt)
    }

    @Test
    fun brokenPcRawJsonDoesNotBreakMerge() {
        val pc = pcStatus(
            pcUpdatedAt = PC_UPDATED_AT_V2,
            zcode = ZCodeStatus(
                running = true,
                couponVisible = false,
                method = "api",
                checkedAt = OLD_CHECKED_AT,
            ),
            rawJson = "not json",
        )
        val direct = directZCode(checkedAt = FRESH_CHECKED_AT)

        val merged = mergeStatus(pc, direct)!!

        assertEquals("not json", merged.rawJson)
        assertSame(direct, merged.zcode)
        assertEquals(PC_UPDATED_AT, merged.updatedAt)
        assertEquals(PC_UPDATED_AT_V2, merged.pcUpdatedAt)
        assertEquals(pc.buckets, merged.buckets)
    }

    @Test
    fun pcUpdatedAtIsNeverReplacedByDirectTime() {
        val pc = pcStatus(
            pcUpdatedAt = PC_UPDATED_AT_V2,
            zcode = ZCodeStatus(
                running = true,
                couponVisible = false,
                method = "api",
                checkedAt = OLD_CHECKED_AT,
            ),
        )
        val direct = directZCode(checkedAt = "2026-11-25T03:20:00+02:00")

        val merged = mergeStatus(pc, direct)!!

        assertEquals(PC_UPDATED_AT_V2, merged.pcUpdatedAt)
        assertEquals(PC_UPDATED_AT, merged.updatedAt)
        assertSame(direct, merged.zcode)
    }
}
