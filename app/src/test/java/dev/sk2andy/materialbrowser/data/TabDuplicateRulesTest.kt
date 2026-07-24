package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabDuplicateRulesTest {
    @Test
    fun `canonical duplicate ignores fragment and default port`() {
        val tabs = listOf(
            tab("selected", "https://Example.com:443/page#one", accessed = 1),
            tab("duplicate", "https://example.com/page#two", accessed = 2),
        )

        assertEquals(listOf("duplicate"), TabDuplicateRules.tabIdsToClose(tabs, "selected"))
    }

    @Test
    fun `selected and pinned tabs are never closed`() {
        val tabs = listOf(
            tab("selected", "https://example.com", accessed = 1),
            tab("pinned", "https://example.com", accessed = 2, pinned = true),
            tab("duplicate", "https://example.com", accessed = 3),
        )

        assertEquals(listOf("duplicate"), TabDuplicateRules.tabIdsToClose(tabs, "selected"))
    }

    @Test
    fun `most recently used unprotected duplicate remains`() {
        val tabs = listOf(
            tab("selected", "https://selected.example", accessed = 1),
            tab("old", "https://duplicate.example", accessed = 2),
            tab("new", "https://duplicate.example", accessed = 3),
        )

        assertEquals(listOf("old"), TabDuplicateRules.tabIdsToClose(tabs, "selected"))
    }

    @Test
    fun `regular and incognito duplicates stay isolated`() {
        val tabs = listOf(
            tab("selected", "https://selected.example", accessed = 1),
            tab("regular", "https://example.com", accessed = 2),
            tab("private", "https://example.com", accessed = 3, incognito = true),
        )

        assertTrue(TabDuplicateRules.tabIdsToClose(tabs, "selected").isEmpty())
    }

    @Test
    fun `duplicates in different profiles stay isolated`() {
        val tabs = listOf(
            tab("selected", "https://selected.example", accessed = 1),
            tab("home", "https://example.com", accessed = 2, profileId = "home"),
            tab("work", "https://example.com", accessed = 3, profileId = "work"),
        )

        assertTrue(TabDuplicateRules.tabIdsToClose(tabs, "selected").isEmpty())
    }

    @Test
    fun `blank invalid and unique tabs are ignored`() {
        val tabs = listOf(
            tab("selected", BLANK_URL, accessed = 1),
            tab("blank", BLANK_URL, accessed = 2),
            tab("file-a", "file:///tmp/a", accessed = 3),
            tab("file-b", "file:///tmp/a", accessed = 4),
            tab("unique", "https://unique.example", accessed = 5),
        )

        assertTrue(TabDuplicateRules.tabIdsToClose(tabs, "selected").isEmpty())
    }

    @Test
    fun `query and scheme remain part of duplicate key`() {
        val tabs = listOf(
            tab("selected", "https://selected.example", 1),
            tab("http", "http://example.com/page?q=one", 2),
            tab("https", "https://example.com/page?q=one", 3),
            tab("query", "http://example.com/page?q=two", 4),
        )

        assertTrue(TabDuplicateRules.tabIdsToClose(tabs, "selected").isEmpty())
    }

    @Test
    fun `multiple groups close in tab order and stable tie keeps first`() {
        val tabs = listOf(
            tab("selected", "https://selected.example", 1),
            tab("a-first", "https://a.example", 5),
            tab("b-first", "https://b.example", 7),
            tab("a-second", "https://a.example", 5),
            tab("b-new", "https://b.example", 8),
        )

        assertEquals(
            listOf("b-first", "a-second"),
            TabDuplicateRules.tabIdsToClose(tabs, "selected"),
        )
    }

    @Test
    fun `all pinned duplicate group closes nothing`() {
        val tabs = listOf(
            tab("selected", "https://selected.example", 1),
            tab("pin-a", "https://a.example", 2, pinned = true),
            tab("pin-b", "https://a.example", 3, pinned = true),
        )

        assertTrue(TabDuplicateRules.tabIdsToClose(tabs, "selected").isEmpty())
    }

    private fun tab(
        id: String,
        url: String,
        accessed: Long,
        pinned: Boolean = false,
        incognito: Boolean = false,
        profileId: String = "home",
    ) = BrowserTab(
        id = id,
        url = url,
        lastAccessedAt = accessed,
        isPinned = pinned,
        isIncognito = incognito,
        profileId = profileId,
    )
}
