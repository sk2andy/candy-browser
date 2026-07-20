package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TabFocusHapticRulesTest {
    @Test
    fun adjacentPageProducesOneTick() {
        assertEquals(1, TabFocusHapticRules.crossedEntryCount(2, 3))
    }

    @Test
    fun forwardJumpProducesTickForEveryCrossedPage() {
        assertEquals(3, TabFocusHapticRules.crossedEntryCount(2, 5))
    }

    @Test
    fun backwardJumpProducesTickForEveryCrossedPage() {
        assertEquals(3, TabFocusHapticRules.crossedEntryCount(5, 2))
    }

    @Test
    fun unchangedPageProducesNoTick() {
        assertEquals(0, TabFocusHapticRules.crossedEntryCount(3, 3))
    }
}
