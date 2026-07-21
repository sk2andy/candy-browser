package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TabReorderMotionTest {
    @Test
    fun `pin target starts at old position while preceding tabs shift right`() {
        val deltas = TabReorderMotion.indexDeltas(
            oldOrder = listOf("one", "two", "target"),
            newOrder = listOf("target", "one", "two"),
        )

        assertEquals(2, deltas["target"])
        assertEquals(-1, deltas["one"])
        assertEquals(-1, deltas["two"])
        assertEquals(200f, TabReorderMotion.translationX(2, 100f, 0f), 0.001f)
        assertEquals(0f, TabReorderMotion.translationX(2, 100f, 1f), 0.001f)
    }

    @Test
    fun `unpin target moves right behind remaining pinned tabs`() {
        val deltas = TabReorderMotion.indexDeltas(
            oldOrder = listOf("target", "pin", "one"),
            newOrder = listOf("pin", "target", "one"),
        )

        assertEquals(-1, deltas["target"])
        assertEquals(1, deltas["pin"])
        assertEquals(0, deltas["one"])
    }
}
