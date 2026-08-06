package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressBarGestureRulesTest {
    @Test
    fun `down swipe does not act from address bar`() {
        assertEquals(
            AddressBarVerticalAction.None,
            AddressBarGestureRules.action(dragDistance = 120f, threshold = 120f),
        )
    }

    @Test
    fun `up swipe opens tabs`() {
        assertEquals(
            AddressBarVerticalAction.OpenTabs,
            AddressBarGestureRules.action(dragDistance = -120f, threshold = 120f),
        )
    }

    @Test
    fun `short vertical drag does nothing`() {
        assertEquals(
            AddressBarVerticalAction.None,
            AddressBarGestureRules.action(dragDistance = -119f, threshold = 120f),
        )
    }

    @Test
    fun `overview gesture uses deliberate travel distance`() {
        assertEquals(120f, AddressBarGestureRules.OPEN_TABS_THRESHOLD_DP, 0f)
    }

    @Test
    fun `tab switch distance matches viewport fraction`() {
        assertEquals(
            false,
            AddressBarTabSwitchRules.hasReachedDistance(
                dragDistance = 239f,
                viewportWidth = 1_000f,
            ),
        )
        assertEquals(
            true,
            AddressBarTabSwitchRules.hasReachedDistance(
                dragDistance = 240f,
                viewportWidth = 1_000f,
            ),
        )
    }
}
