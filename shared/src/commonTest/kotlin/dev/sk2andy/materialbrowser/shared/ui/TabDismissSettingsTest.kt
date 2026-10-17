package dev.sk2andy.materialbrowser.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabDismissSettingsTest {
    @Test
    fun `default preference preserves production dismissal threshold`() {
        assertEquals(
            TabDismissPhysics.DEFAULT_RESISTANCE_FRACTION,
            TabDismissPhysics.resistanceFraction(TabDismissPhysics.DEFAULT_RESISTANCE_PERCENT),
        )
    }

    @Test
    fun `stored values outside control range stay inside supported physics`() {
        assertEquals(10, TabDismissPhysics.resistancePercent(Int.MIN_VALUE))
        assertEquals(90, TabDismissPhysics.resistancePercent(Int.MAX_VALUE))
        assertEquals(0.1f, TabDismissPhysics.resistanceFraction(-1))
        assertEquals(0.9f, TabDismissPhysics.resistanceFraction(101))
    }

    @Test
    fun `selected resistance changes when same drag clears resistance`() {
        assertTrue(TabDismissPhysics.hasClearedResistance(
            rawDistance = 60f,
            dismissThreshold = 100f,
            resistanceFraction = TabDismissPhysics.resistanceFraction(30),
        ))
        assertFalse(TabDismissPhysics.hasClearedResistance(
            rawDistance = 60f,
            dismissThreshold = 100f,
            resistanceFraction = TabDismissPhysics.resistanceFraction(80),
        ))
    }
}
