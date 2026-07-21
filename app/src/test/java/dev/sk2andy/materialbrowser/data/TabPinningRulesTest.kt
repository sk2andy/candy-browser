package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TabPinningRulesTest {
    @Test
    fun `pinning moves tab to front and preserves remaining order`() {
        val updated = TabPinningRules.withPinnedState(
            tabs = listOf(tab("pinned", isPinned = true), tab("one"), tab("two")),
            tabId = "two",
            isPinned = true,
        )

        assertEquals(listOf("two", "pinned", "one"), updated.map(BrowserTab::id))
        assertTrue(updated.first().isPinned)
    }

    @Test
    fun `unpinning moves tab behind remaining pinned tabs`() {
        val updated = TabPinningRules.withPinnedState(
            tabs = listOf(
                tab("new-pin", isPinned = true),
                tab("old-pin", isPinned = true),
                tab("one"),
                tab("two"),
            ),
            tabId = "new-pin",
            isPinned = false,
        )

        assertEquals(listOf("old-pin", "new-pin", "one", "two"), updated.map(BrowserTab::id))
        assertFalse(updated[1].isPinned)
    }

    @Test
    fun `removing only pin changes state even when order stays unchanged`() {
        val updated = TabPinningRules.withPinnedState(
            tabs = listOf(tab("only-pin", isPinned = true), tab("regular")),
            tabId = "only-pin",
            isPinned = false,
        )

        assertEquals(listOf("only-pin", "regular"), updated.map(BrowserTab::id))
        assertFalse(updated.first().isPinned)
    }

    @Test
    fun `ordering repairs legacy mixed order stably`() {
        val ordered = TabPinningRules.orderedTabs(
            listOf(tab("one"), tab("pin-a", true), tab("two"), tab("pin-b", true)),
        )

        assertEquals(listOf("pin-a", "pin-b", "one", "two"), ordered.map(BrowserTab::id))
    }

    @Test
    fun `missing tab and unchanged state keep original list`() {
        val tabs = listOf(tab("one"), tab("pin", true))

        assertSame(tabs, TabPinningRules.withPinnedState(tabs, "missing", true))
        assertSame(tabs, TabPinningRules.withPinnedState(tabs, "pin", true))
    }

    private fun tab(id: String, isPinned: Boolean = false) = BrowserTab(
        id = id,
        lastAccessedAt = 1L,
        isPinned = isPinned,
    )
}
