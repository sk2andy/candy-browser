package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserMomentumRecoveryRulesTest {
    @Test
    fun progressInExpectedDirectionResetsStallCount() {
        val observation = observe(
            previousScrollY = 100,
            currentScrollY = 101,
            direction = 1,
            stalledFrames = 2,
        )

        assertEquals(0, observation.stalledFrames)
        assertEquals(BrowserMomentumWatchdogDecision.Continue, observation.decision)
    }

    @Test
    fun threeConsecutiveFramesWithoutProgressRecover() {
        val observation = observe(stalledFrames = 2)

        assertEquals(3, observation.stalledFrames)
        assertEquals(BrowserMomentumWatchdogDecision.Recover, observation.decision)
    }

    @Test
    fun movementInOldDirectionCountsAsStall() {
        val observation = observe(
            previousScrollY = 100,
            currentScrollY = 99,
            direction = 1,
            stalledFrames = 1,
        )

        assertEquals(2, observation.stalledFrames)
        assertEquals(BrowserMomentumWatchdogDecision.Continue, observation.decision)
    }

    @Test
    fun slowReferenceFlingStopsWithoutRecovery() {
        val observation = observe(
            stalledFrames = 2,
            shadowVelocity = 199f,
            minimumRecoveryVelocity = 200f,
        )

        assertEquals(2, observation.stalledFrames)
        assertEquals(BrowserMomentumWatchdogDecision.Stop, observation.decision)
    }

    @Test
    fun finishedReferenceFlingStopsWithoutRecovery() {
        val observation = observe(
            stalledFrames = 2,
            shadowRunning = false,
        )

        assertEquals(2, observation.stalledFrames)
        assertEquals(BrowserMomentumWatchdogDecision.Stop, observation.decision)
    }

    private fun observe(
        previousScrollY: Int = 100,
        currentScrollY: Int = 100,
        direction: Int = 1,
        stalledFrames: Int = 0,
        shadowRunning: Boolean = true,
        shadowVelocity: Float = 1_000f,
        minimumRecoveryVelocity: Float = 200f,
    ): BrowserMomentumWatchdogObservation = BrowserMomentumRecoveryRules.observe(
        previousScrollY = previousScrollY,
        currentScrollY = currentScrollY,
        direction = direction,
        stalledFrames = stalledFrames,
        shadowRunning = shadowRunning,
        shadowVelocity = shadowVelocity,
        minimumRecoveryVelocity = minimumRecoveryVelocity,
        requiredStalledFrames = 3,
    )
}
