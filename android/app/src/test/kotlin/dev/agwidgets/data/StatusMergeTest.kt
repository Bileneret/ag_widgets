package dev.agwidgets.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StatusMergeTest {

    private fun pcStatus(updatedAt: String = "2026-11-25T03:00:00+02:00") = AgStatus(
        updatedAt = updatedAt,
        planTier = "Pro",
        buckets = listOf(QuotaBucket("gemini-5h", "5h", 0.66, "2026-11-25T04:28:35Z")),
        zcode = ZCodeStatus(running = true, couponVisible = false, method = "api"),
        contextSummary = ContextSummary(
            conversationId = "conv-1",
            active = true,
            model = "gemini-4-neon-high",
            tokens = 377387,
            window = 524288,
            pct = 71.98,
            level = "red",
        ),
        rawJson = "{}",
    )

    @Test
    fun nothingToMerge() {
        assertNull(mergeStatus(null, null))
    }

    @Test
    fun phoneOnlyStatusStillRenders() {
        val direct = ZCodeStatus(
            running = true,
            couponVisible = true,
            method = "api",
            authOk = true,
            checkedAt = "2026-11-25T03:10:00+02:00",
        )
        val merged = mergeStatus(null, direct)!!
        assertEquals(emptyList<QuotaBucket>(), merged.buckets)
        assertNull(merged.contextSummary)
        assertEquals("2026-11-25T03:10:00+02:00", merged.updatedAt)
        assertEquals(direct, merged.zcode)
    }

    @Test
    fun directZCodeOverridesPcZCodeButKeepsQuotas() {
        val pc = pcStatus()
        val direct = ZCodeStatus(
            running = true,
            couponVisible = true,
            matched = "500M",
            method = "api",
            authOk = true,
            remainingUnits = 250_000_000,
            totalUnits = 500_000_000,
            checkedAt = "2026-11-25T03:10:00+02:00",
        )
        val merged = mergeStatus(pc, direct)!!
        assertEquals(pc.buckets, merged.buckets)
        assertSame(pc.contextSummary, merged.contextSummary)
        assertEquals(direct, merged.zcode)
        assertEquals("2026-11-25T03:10:00+02:00", merged.updatedAt)
    }

    @Test
    fun pcOnlyStatusIsReturnedUnchanged() {
        val pc = pcStatus()
        assertSame(pc, mergeStatus(pc, null))
    }
}
