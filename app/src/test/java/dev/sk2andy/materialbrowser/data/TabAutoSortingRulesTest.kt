package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Test

class TabAutoSortingRulesTest {
    @Test
    fun `orders oldest access first and newest access last`() {
        val tabs = listOf(
            tab("newest", lastAccessedAt = 30L),
            tab("oldest", lastAccessedAt = 10L),
            tab("middle", lastAccessedAt = 20L),
        )

        val ordered = TabAutoSortingRules.orderedTabs(tabs, selectedTabId = "newest")

        assertEquals(listOf("oldest", "middle", "newest"), ordered.map(BrowserTab::id))
    }

    @Test
    fun `keeps pins before regular tabs while sorting both groups`() {
        val tabs = listOf(
            tab("regular-new", lastAccessedAt = 40L),
            tab("pin-new", lastAccessedAt = 30L, pinned = true),
            tab("regular-old", lastAccessedAt = 10L),
            tab("pin-old", lastAccessedAt = 20L, pinned = true),
        )

        val ordered = TabAutoSortingRules.orderedTabs(tabs, selectedTabId = "regular-new")

        assertEquals(
            listOf("pin-old", "pin-new", "regular-old", "regular-new"),
            ordered.map(BrowserTab::id),
        )
    }

    @Test
    fun `places selected tab last in its group when access times tie`() {
        val tabs = listOf(
            tab("selected", lastAccessedAt = 10L),
            tab("other", lastAccessedAt = 10L),
        )

        val ordered = TabAutoSortingRules.orderedTabs(tabs, selectedTabId = "selected")

        assertEquals(listOf("other", "selected"), ordered.map(BrowserTab::id))
    }

    private fun tab(
        id: String,
        lastAccessedAt: Long,
        pinned: Boolean = false,
    ) = BrowserTab(
        id = id,
        lastAccessedAt = lastAccessedAt,
        isPinned = pinned,
    )
}
