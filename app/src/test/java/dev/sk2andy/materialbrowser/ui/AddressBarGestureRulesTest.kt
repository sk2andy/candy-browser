package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressBarGestureRulesTest {
    @Test
    fun `down swipe does not act from address bar`() {
        assertEquals(
            AddressBarVerticalAction.None,
            AddressBarGestureRules.action(dragDistance = 56f, threshold = 56f),
        )
    }

    @Test
    fun `up swipe opens tabs`() {
        assertEquals(
            AddressBarVerticalAction.OpenTabs,
            AddressBarGestureRules.action(dragDistance = -56f, threshold = 56f),
        )
    }

    @Test
    fun `short vertical drag does nothing`() {
        assertEquals(
            AddressBarVerticalAction.None,
            AddressBarGestureRules.action(dragDistance = -55f, threshold = 56f),
        )
    }

    @Test
    fun `overview gesture uses deliberate travel distance`() {
        assertEquals(56f, AddressBarGestureRules.OPEN_TABS_THRESHOLD_DP, 0f)
        assertEquals(24f, AddressBarGestureRules.PARKED_OPEN_TABS_THRESHOLD_DP, 0f)
        assertEquals(320L, AddressBarGestureRules.PARKED_REPOSITION_LONG_PRESS_MILLIS)
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

    @Test
    fun `wide tab strip starts at 600 dp`() {
        assertEquals(false, AddressBarWideLayoutRules.usesTabStrip(599f))
        assertEquals(true, AddressBarWideLayoutRules.usesTabStrip(600f))
        assertEquals(true, AddressBarWideLayoutRules.usesTabStrip(840f))
        assertEquals(false, AddressBarWideLayoutRules.usesTabStrip(Float.NaN))
    }
}
