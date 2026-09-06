package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserChromeGestureRulesTest {
    @Test
    fun `overview progress follows the Candy threshold`() {
        assertEquals(0.0, BrowserChromeGestureRules.overviewProgress(dragY = 12.0))
        assertEquals(0.5, BrowserChromeGestureRules.overviewProgress(dragY = -28.0))
        assertEquals(1.0, BrowserChromeGestureRules.overviewProgress(dragY = -80.0))
    }

    @Test
    fun `only a committed upward drag opens the overview`() {
        assertTrue(
            BrowserChromeGestureRules.shouldOpenOverview(
                dragX = 8.0,
                dragY = -56.0,
                touchSlop = 8.0,
            ),
        )
        assertFalse(
            BrowserChromeGestureRules.shouldOpenOverview(
                dragX = 60.0,
                dragY = -56.0,
                touchSlop = 8.0,
            ),
        )
        assertFalse(
            BrowserChromeGestureRules.shouldOpenOverview(
                dragX = 0.0,
                dragY = -55.0,
                touchSlop = 8.0,
            ),
        )
        assertFalse(
            BrowserChromeGestureRules.shouldOpenOverview(
                dragX = 0.0,
                dragY = -80.0,
                touchSlop = 8.0,
                isAddressEditing = true,
            ),
        )
    }

    @Test
    fun `tab switch uses Android distance fraction in either direction`() {
        assertEquals(
            BrowserTabSwitchTarget.Previous,
            BrowserTabSwitchGestureRules.target(
                dragX = 240.0,
                dragY = 10.0,
                velocityX = 0.0,
                viewportWidth = 1_000.0,
                hasPreviousTab = true,
                hasNextTab = true,
                isAddressEditing = false,
            ),
        )
        assertEquals(
            BrowserTabSwitchTarget.Next,
            BrowserTabSwitchGestureRules.target(
                dragX = -240.0,
                dragY = 10.0,
                velocityX = 0.0,
                viewportWidth = 1_000.0,
                hasPreviousTab = true,
                hasNextTab = true,
                isAddressEditing = false,
            ),
        )
    }

    @Test
    fun `matching fast fling switches after minimum travel`() {
        assertEquals(
            BrowserTabSwitchTarget.Next,
            BrowserTabSwitchGestureRules.target(
                dragX = -24.0,
                dragY = 0.0,
                velocityX = -900.0,
                viewportWidth = 1_000.0,
                hasPreviousTab = true,
                hasNextTab = true,
                isAddressEditing = false,
            ),
        )
        assertEquals(
            BrowserTabSwitchTarget.Stay,
            BrowserTabSwitchGestureRules.target(
                dragX = -24.0,
                dragY = 0.0,
                velocityX = 900.0,
                viewportWidth = 1_000.0,
                hasPreviousTab = true,
                hasNextTab = true,
                isAddressEditing = false,
            ),
        )
    }

    @Test
    fun `editing vertical motion and missing target suppress tab switch`() {
        val base = BrowserTabSwitchGestureRules.target(
            dragX = -300.0,
            dragY = 0.0,
            velocityX = -1_000.0,
            viewportWidth = 1_000.0,
            hasPreviousTab = true,
            hasNextTab = true,
            isAddressEditing = true,
        )
        val vertical = BrowserTabSwitchGestureRules.target(
            dragX = -300.0,
            dragY = -301.0,
            velocityX = -1_000.0,
            viewportWidth = 1_000.0,
            hasPreviousTab = true,
            hasNextTab = true,
            isAddressEditing = false,
        )
        val missing = BrowserTabSwitchGestureRules.target(
            dragX = -300.0,
            dragY = 0.0,
            velocityX = -1_000.0,
            viewportWidth = 1_000.0,
            hasPreviousTab = true,
            hasNextTab = false,
            isAddressEditing = false,
        )

        assertEquals(BrowserTabSwitchTarget.Stay, base)
        assertEquals(BrowserTabSwitchTarget.Stay, vertical)
        assertEquals(BrowserTabSwitchTarget.Stay, missing)
    }
}
