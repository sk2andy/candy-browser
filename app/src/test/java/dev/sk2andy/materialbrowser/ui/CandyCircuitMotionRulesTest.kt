package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyCircuitMotionRulesTest {
    @Test
    fun `closed tile stays visible before dissolving`() {
        assertEquals(1f, CandyCircuitMotionRules.outgoingAlpha(0.30f), 0.001f)
        assertEquals(0f, CandyCircuitMotionRules.outgoingAlpha(0.56f), 0.001f)
        assertTrue(CandyCircuitMotionRules.outgoingScale(0.15f) > 1f)
    }

    @Test
    fun `refill tiles enter in stable staggered order`() {
        val first = CandyCircuitMotionRules.incomingProgress(
            progress = 0.56f,
            order = 0,
            count = 4,
        )
        val last = CandyCircuitMotionRules.incomingProgress(
            progress = 0.56f,
            order = 3,
            count = 4,
        )

        assertTrue(first > last)
        assertEquals(0f, CandyCircuitMotionRules.incomingProgress(0f, 0, 4), 0.001f)
        assertEquals(1f, CandyCircuitMotionRules.incomingProgress(1f, 3, 4), 0.001f)
    }

    @Test
    fun `refill settles at final size and position`() {
        assertTrue(CandyCircuitMotionRules.incomingScale(0.5f) > 1f)
        assertEquals(1f, CandyCircuitMotionRules.incomingScale(1f), 0.001f)
        assertEquals(0f, CandyCircuitMotionRules.incomingOffsetFraction(1f), 0.001f)
    }
}
