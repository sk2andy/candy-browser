package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabOverviewHeroRulesTest {
    @Test
    fun `hero waits for target bounds`() {
        assertFalse(TabOverviewHeroRules.canStart(hasTargetBounds = false))
        assertTrue(TabOverviewHeroRules.canStart(hasTargetBounds = true))
    }

    @Test
    fun `initial card stays hidden while hero is visible`() {
        assertTrue(TabOverviewHeroRules.isHeroVisible(hasTargetBounds = true, progress = 0.5f))
        assertFalse(TabOverviewHeroRules.isCardVisible(isInitialCard = true, progress = 0.5f))
    }

    @Test
    fun `initial card replaces hero at completion`() {
        assertFalse(TabOverviewHeroRules.isHeroVisible(hasTargetBounds = true, progress = 0.995f))
        assertTrue(TabOverviewHeroRules.isCardVisible(isInitialCard = true, progress = 0.995f))
    }

    @Test
    fun `neighbor cards remain visible`() {
        assertTrue(TabOverviewHeroRules.isCardVisible(isInitialCard = false, progress = 0f))
    }

    @Test
    fun `exit target card is replaced by hero immediately`() {
        assertFalse(
            TabOverviewHeroRules.isCardVisible(
                isInitialCard = true,
                progress = 1f,
                isExitTarget = true,
            ),
        )
    }

    @Test
    fun `background remains opaque throughout exit`() {
        assertEquals(
            0.4f,
            TabOverviewHeroRules.backgroundAlpha(entryProgress = 0.4f, isExiting = false),
            0f,
        )
        assertEquals(
            1f,
            TabOverviewHeroRules.backgroundAlpha(entryProgress = 0.4f, isExiting = true),
            0f,
        )
    }

    @Test
    fun `overview content clears early during exit`() {
        assertEquals(
            1f,
            TabOverviewHeroRules.contentAlpha(
                exitProgress = 0f,
                isExiting = true,
            ),
            0f,
        )
        assertEquals(
            0f,
            TabOverviewHeroRules.contentAlpha(
                exitProgress = 0.25f,
                isExiting = true,
            ),
            0f,
        )
    }

    @Test
    fun `neighbor tabs enter late`() {
        assertEquals(
            0f,
            TabOverviewHeroRules.neighborAlpha(entryProgress = 0.55f),
            0f,
        )
        assertEquals(
            1f,
            TabOverviewHeroRules.neighborAlpha(entryProgress = 1f),
            0f,
        )
    }
}
