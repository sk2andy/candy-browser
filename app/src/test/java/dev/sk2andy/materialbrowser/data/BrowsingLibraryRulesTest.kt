package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowsingLibraryRulesTest {
    @Test
    fun `history deduplicates canonical URL and keeps latest title`() {
        val old = HistoryEntry("https://Example.com:443/page#old", "Old", 10)
        val latest = HistoryEntry("https://example.com/page#new", "Latest", 20)

        val history = BrowsingLibraryRules.addHistory(listOf(old), latest)

        assertEquals(listOf(latest), history)
    }

    @Test
    fun `history rejects blank and non web URLs`() {
        val history = listOf(HistoryEntry("https://example.com/", "Example", 10))

        assertEquals(
            history,
            BrowsingLibraryRules.addHistory(history, HistoryEntry("about:blank", "Blank", 20)),
        )
        assertEquals(
            history,
            BrowsingLibraryRules.addHistory(history, HistoryEntry("file:///secret", "File", 20)),
        )
    }

    @Test
    fun `history limit retains most recently visited entries`() {
        val history = (1L..5L).map { index ->
            HistoryEntry("https://example.com/$index", "Page $index", index)
        }

        val updated = BrowsingLibraryRules.addHistory(
            current = history,
            entry = HistoryEntry("https://example.com/latest", "Latest", 10),
            limit = 3,
        )

        assertEquals(listOf(10L, 5L, 4L), updated.map(HistoryEntry::lastVisitedAt))
    }

    @Test
    fun `suggestions rank host prefix before title and content matches`() {
        val history = listOf(
            HistoryEntry("https://news.example/", "Daily", 10),
            HistoryEntry("https://example.com/google", "Article", 30),
            HistoryEntry("https://other.example/", "Google Account", 20),
            HistoryEntry("https://google.dev/", "Android", 1),
        )

        val suggestions = BrowsingLibraryRules.suggestions(history, "google", limit = 3)

        assertEquals(
            listOf("https://google.dev/", "https://other.example/", "https://example.com/google"),
            suggestions.map(HistoryEntry::url),
        )
    }

    @Test
    fun `empty query returns recent history`() {
        val history = listOf(
            HistoryEntry("https://old.example/", "Old", 1),
            HistoryEntry("https://new.example/", "New", 3),
            HistoryEntry("https://middle.example/", "Middle", 2),
        )

        val suggestions = BrowsingLibraryRules.suggestions(history, "  ", limit = 2)

        assertEquals(listOf("New", "Middle"), suggestions.map(HistoryEntry::title))
    }

    @Test
    fun `address suggestions mark and prioritize multiple matching open tabs`() {
        val tabs = listOf(
            browserTab(id = "current", url = BLANK_URL, lastAccessedAt = 30),
            browserTab(
                id = "news-tab",
                url = "https://news.example/article#comments",
                title = "Example News",
                lastAccessedAt = 20,
            ),
            browserTab(
                id = "docs-tab",
                url = "https://docs.example/guide",
                title = "Example Docs",
                lastAccessedAt = 10,
            ),
        )
        val history = listOf(
            HistoryEntry("https://other.example/archive", "Example Archive", 100),
            HistoryEntry("https://news.example/article", "Old News Title", 5),
        )

        val suggestions = BrowsingLibraryRules.addressSuggestions(
            history = history,
            tabs = tabs,
            selectedTabId = "current",
            isIncognito = false,
            query = "example",
            limit = 6,
        )

        assertEquals(listOf("news-tab", "docs-tab", null), suggestions.map { it.openTabId })
        assertEquals("Example News", suggestions.first().title)
    }

    @Test
    fun `address suggestions use most recent canonical tab and exclude current tab`() {
        val tabs = listOf(
            browserTab(
                id = "current",
                url = "https://current.example/",
                title = "Current",
                lastAccessedAt = 30,
            ),
            browserTab(
                id = "older",
                url = "https://Example.com:443/page#old",
                title = "Older",
                lastAccessedAt = 10,
            ),
            browserTab(
                id = "newer",
                url = "https://example.com/page#new",
                title = "Newer",
                lastAccessedAt = 20,
            ),
        )

        val suggestions = BrowsingLibraryRules.addressSuggestions(
            history = emptyList(),
            tabs = tabs,
            selectedTabId = "current",
            isIncognito = false,
            query = "example.com/page",
            limit = 6,
        )

        assertEquals(listOf("newer"), suggestions.map { it.openTabId })
        assertFalse(suggestions.any { it.openTabId == "current" })
    }

    @Test
    fun `address suggestions isolate regular and incognito tabs`() {
        val tabs = listOf(
            browserTab(id = "current-private", url = BLANK_URL, isIncognito = true),
            browserTab(
                id = "private-match",
                url = "https://private.example/",
                title = "Private",
                isIncognito = true,
            ),
            browserTab(
                id = "regular-match",
                url = "https://regular.example/",
                title = "Regular",
            ),
        )

        val suggestions = BrowsingLibraryRules.addressSuggestions(
            history = listOf(HistoryEntry("https://history.example/", "History", 50)),
            tabs = tabs,
            selectedTabId = "current-private",
            isIncognito = true,
            query = "example",
            limit = 6,
        )

        assertEquals(listOf("private-match"), suggestions.map { it.openTabId })
        assertFalse(suggestions.any { it.url.contains("regular") || it.url.contains("history") })
    }

    @Test
    fun `regular address suggestions never reveal incognito tabs`() {
        val tabs = listOf(
            browserTab(id = "current-regular", url = BLANK_URL),
            browserTab(
                id = "regular-match",
                url = "https://regular.example/",
                title = "Regular",
            ),
            browserTab(
                id = "private-match",
                url = "https://private.example/",
                title = "Private",
                isIncognito = true,
            ),
        )

        val suggestions = BrowsingLibraryRules.addressSuggestions(
            history = emptyList(),
            tabs = tabs,
            selectedTabId = "current-regular",
            isIncognito = false,
            query = "example",
            limit = 6,
        )

        assertEquals(listOf("regular-match"), suggestions.map { it.openTabId })
        assertFalse(suggestions.any { it.url.contains("private") })
    }

    @Test
    fun `domain completion prioritizes open tabs then favorites then history`() {
        val completion = BrowsingLibraryRules.domainCompletion(
            history = listOf(HistoryEntry("https://github-history.example/", "History", 30)),
            favorites = listOf(FavoriteEntry("https://github-favorite.example/", "Favorite", 20)),
            tabs = listOf(
                browserTab(id = "current", url = BLANK_URL),
                browserTab(id = "open", url = "https://www.github.com/project"),
            ),
            selectedTabId = "current",
            isIncognito = false,
            query = "git",
        )

        assertEquals("github.com", completion)
    }

    @Test
    fun `private domain completion never reveals regular browsing library`() {
        val completion = BrowsingLibraryRules.domainCompletion(
            history = listOf(HistoryEntry("https://secret-history.example/", "History", 30)),
            favorites = listOf(FavoriteEntry("https://secret-favorite.example/", "Favorite", 20)),
            tabs = listOf(
                browserTab(id = "current", url = BLANK_URL, isIncognito = true),
                browserTab(
                    id = "private",
                    url = "https://secret-private.example/",
                    isIncognito = true,
                ),
                browserTab(id = "regular", url = "https://secret-regular.example/"),
            ),
            selectedTabId = "current",
            isIncognito = true,
            query = "secret",
        )

        assertEquals("secret-private.example", completion)
    }

    @Test
    fun `domain completion rejects path whitespace and completed hosts`() {
        val history = listOf(HistoryEntry("https://github.com/", "GitHub", 30))

        assertEquals(
            null,
            BrowsingLibraryRules.domainCompletion(
                history,
                emptyList(),
                emptyList(),
                "current",
                false,
                "git hub",
            ),
        )
        assertEquals(
            null,
            BrowsingLibraryRules.domainCompletion(
                history,
                emptyList(),
                emptyList(),
                "current",
                false,
                "github.com",
            ),
        )
    }

    @Test
    fun `favorite toggle adds then removes canonical URL`() {
        val favorite = FavoriteEntry("https://Example.com:443/#one", "Example", 10)
        val added = BrowsingLibraryRules.toggleFavorite(emptyList(), favorite)

        assertTrue(BrowsingLibraryRules.isFavorite(added, "https://example.com/#two"))

        val removed = BrowsingLibraryRules.toggleFavorite(
            added,
            FavoriteEntry("https://example.com/", "Renamed", 20),
        )

        assertTrue(removed.isEmpty())
        assertFalse(BrowsingLibraryRules.isFavorite(removed, favorite.url))
    }

    private fun browserTab(
        id: String,
        url: String,
        title: String = "",
        lastAccessedAt: Long = 1,
        isIncognito: Boolean = false,
    ) = BrowserTab(
        id = id,
        lastAccessedAt = lastAccessedAt,
        isIncognito = isIncognito,
        title = title,
        url = url,
    )
}
