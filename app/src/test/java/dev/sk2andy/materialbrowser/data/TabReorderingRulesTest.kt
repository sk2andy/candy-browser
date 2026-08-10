package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabReorderingRulesTest {
    @Test
    fun `tab needs a sibling in its pin group to move`() {
        assertFalse(TabReorderingRules.canMove(listOf(tab("pin", true), tab("regular")), "pin"))
        assertFalse(TabReorderingRules.canMove(listOf(tab("pin", true), tab("regular")), "regular"))
        assertTrue(
            TabReorderingRules.canMove(
                listOf(tab("pin", true), tab("regular-a"), tab("regular-b")),
                "regular-a",
            ),
        )
    }

    @Test
    fun `pinned tab can move within pinned group`() {
        val tabs = listOf(tab("pin-a", true), tab("pin-b", true), tab("regular"))

        val reordered = TabReorderingRules.move(tabs, "pin-b", 0)

        assertEquals(listOf("pin-b", "pin-a", "regular"), reordered.map(BrowserTab::id))
    }

    @Test
    fun `pinned tab cannot move into regular group`() {
        val tabs = listOf(tab("pin-a", true), tab("pin-b", true), tab("regular"))

        val reordered = TabReorderingRules.move(tabs, "pin-a", 2)

        assertEquals(listOf("pin-b", "pin-a", "regular"), reordered.map(BrowserTab::id))
    }

    @Test
    fun `regular tab cannot move into pinned group`() {
        val tabs = listOf(tab("pin", true), tab("regular-a"), tab("regular-b"))

        val reordered = TabReorderingRules.move(tabs, "regular-b", 0)

        assertEquals(listOf("pin", "regular-b", "regular-a"), reordered.map(BrowserTab::id))
    }

    @Test
    fun `regular tab can move to end of regular group`() {
        val tabs = listOf(tab("pin", true), tab("regular-a"), tab("regular-b"))

        val reordered = TabReorderingRules.move(tabs, "regular-a", Int.MAX_VALUE)

        assertEquals(listOf("pin", "regular-b", "regular-a"), reordered.map(BrowserTab::id))
    }

    @Test
    fun `unknown tab leaves order unchanged`() {
        val tabs = listOf(tab("one"), tab("two"))

        assertEquals(tabs, TabReorderingRules.move(tabs, "missing", 0))
    }

    private fun tab(id: String, pinned: Boolean = false) = BrowserTab(
        id = id,
        lastAccessedAt = 1L,
        isPinned = pinned,
    )
}
