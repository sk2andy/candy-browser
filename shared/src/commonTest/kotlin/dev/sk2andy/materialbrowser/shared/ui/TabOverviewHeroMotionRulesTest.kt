package dev.sk2andy.materialbrowser.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabOverviewHeroMotionRulesTest {
    @Test
    fun `entry starts as browser viewport and finishes as overview card`() {
        assertTrue(TabOverviewHeroRules.isHeroVisible(hasTargetBounds = true, progress = 0f))
        assertFalse(
            TabOverviewHeroRules.isCardVisible(
                isInitialCard = true,
                progress = 0f,
            ),
        )

        assertFalse(TabOverviewHeroRules.isHeroVisible(hasTargetBounds = true, progress = 1f))
        assertTrue(
            TabOverviewHeroRules.isCardVisible(
                isInitialCard = true,
                progress = 1f,
            ),
        )
        assertEquals(1f, TabOverviewHeroRules.backgroundAlpha(1f, isExiting = false))
    }

    @Test
    fun `exit reverses card fraction back to browser viewport`() {
        assertEquals(
            1f,
            TabOverviewHeroRules.targetFraction(
                entryProgress = 1f,
                exitProgress = 0f,
                isExiting = true,
            ),
        )
        assertEquals(
            0f,
            TabOverviewHeroRules.targetFraction(
                entryProgress = 1f,
                exitProgress = 1f,
                isExiting = true,
            ),
        )
        assertEquals(
            0f,
            TabOverviewHeroRules.contentAlpha(exitProgress = 1f, isExiting = true),
        )
    }

    @Test
    fun `entry and exit durations stay nonzero and intentionally asymmetric`() {
        assertTrue(TabOverviewHeroRules.ENTRY_DURATION_MILLIS > 0)
        assertTrue(TabOverviewHeroRules.EXIT_DURATION_MILLIS > 0)
        assertTrue(
            TabOverviewHeroRules.EXIT_DURATION_MILLIS >
                TabOverviewHeroRules.ENTRY_DURATION_MILLIS,
        )
    }
}
