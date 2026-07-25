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
}
