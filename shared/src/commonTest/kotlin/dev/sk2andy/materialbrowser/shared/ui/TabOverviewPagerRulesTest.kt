package dev.sk2andy.materialbrowser.shared.ui

import dev.sk2andy.materialbrowser.browser.BrowserTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabOverviewPagerRulesTest {
    @Test
    fun `stale pager index uses a stable fallback while tabs reconcile`() {
        val tabs = listOf(BrowserTab(id = "tab-one", lastAccessedAt = 0L))

        assertEquals("tab-page-1", TabOverviewPagerRules.key(tabs, page = 1))
        assertNull(TabOverviewPagerRules.tabAt(tabs, page = 1))
    }

    @Test
    fun `current pager index keeps the tab identity`() {
        val tab = BrowserTab(id = "tab-one", lastAccessedAt = 0L)

        assertEquals("tab-one", TabOverviewPagerRules.key(listOf(tab), page = 0))
        assertEquals(tab, TabOverviewPagerRules.tabAt(listOf(tab), page = 0))
    }
}
