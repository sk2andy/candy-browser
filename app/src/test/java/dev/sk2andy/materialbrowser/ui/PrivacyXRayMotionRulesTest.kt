package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyXRayMotionRulesTest {
    @Test
    fun `count direction distinguishes increases decreases and unchanged values`() {
        assertEquals(
            PrivacyCountDirection.Increasing,
            PrivacyXRayMotionRules.countDirection(previousCount = 3, currentCount = 4),
        )
        assertEquals(
            PrivacyCountDirection.Decreasing,
            PrivacyXRayMotionRules.countDirection(previousCount = 4, currentCount = 3),
        )
        assertEquals(
            PrivacyCountDirection.Unchanged,
            PrivacyXRayMotionRules.countDirection(previousCount = 3, currentCount = 3),
        )
    }

    @Test
    fun `new blocked requests wait for batch window after cooldown`() {
        assertEquals(
            PrivacyXRayMotionRules.BADGE_BATCH_WINDOW_MILLIS,
            PrivacyXRayMotionRules.badgePulseDelayMillis(
                previousCount = 6,
                currentCount = 9,
                elapsedSinceLastPulseMillis = Long.MAX_VALUE,
            ),
        )
    }

    @Test
    fun `busy burst waits for remaining pulse cooldown`() {
        assertEquals(
            540L,
            PrivacyXRayMotionRules.badgePulseDelayMillis(
                previousCount = 9,
                currentCount = 12,
                elapsedSinceLastPulseMillis = 100L,
            ),
        )
    }

    @Test
    fun `unchanged and decreasing counts never pulse`() {
        assertNull(
            PrivacyXRayMotionRules.badgePulseDelayMillis(
                previousCount = 7,
                currentCount = 7,
                elapsedSinceLastPulseMillis = Long.MAX_VALUE,
            ),
        )
        assertNull(
            PrivacyXRayMotionRules.badgePulseDelayMillis(
                previousCount = 7,
                currentCount = 0,
                elapsedSinceLastPulseMillis = Long.MAX_VALUE,
            ),
        )
    }

    @Test
    fun `reset during batch window cancels pending pulse`() {
        assertFalse(
            PrivacyXRayMotionRules.shouldRunBatchedPulse(
                triggerCount = 8,
                currentCount = 0,
            ),
        )
        assertTrue(
            PrivacyXRayMotionRules.shouldRunBatchedPulse(
                triggerCount = 8,
                currentCount = 12,
            ),
        )
    }

    @Test
    fun `category fractions remain bounded for incomplete or inconsistent snapshots`() {
        assertEquals(0f, PrivacyXRayMotionRules.categoryFraction(-1, 10), 0f)
        assertEquals(0f, PrivacyXRayMotionRules.categoryFraction(2, 0), 0f)
        assertEquals(0.25f, PrivacyXRayMotionRules.categoryFraction(2, 8), 0f)
        assertEquals(1f, PrivacyXRayMotionRules.categoryFraction(12, 8), 0f)
    }
}
