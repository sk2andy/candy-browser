package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import java.time.LocalDate
import java.time.ZoneId
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
    fun `same canonical URL is retained once per profile`() {
        val personal = HistoryEntry(
            "https://example.com/page#personal",
            "Personal",
            10,
            profileId = "personal",
        )
        val work = HistoryEntry(
            "https://Example.com:443/page#work",
            "Work",
            20,
            profileId = "work",
        )

        val history = BrowsingLibraryRules.addHistory(listOf(personal), work)

        assertEquals(listOf(work, personal), history)
    }

    @Test
    fun `visible history unions selected profiles and matches title host or URL`() {
        val history = listOf(
            HistoryEntry("https://personal.example/", "Home", 10, "personal"),
            HistoryEntry("https://work.example/docs", "Guide", 30, "work"),
            HistoryEntry("https://other.example/", "Work notes", 20, "other"),
        )

        assertEquals(
            listOf("work", "personal"),
            BrowsingHistoryRules.visibleEntries(
                history = history,
                selectedProfileIds = setOf("personal", "work"),
                query = "example",
            ).map(HistoryEntry::profileId),
        )
        assertEquals(
            listOf("https://work.example/docs"),
            BrowsingHistoryRules.visibleEntries(
                history = history,
                selectedProfileIds = setOf("work"),
                query = "guide",
            ).map(HistoryEntry::url),
        )
    }

    @Test
    fun `history sections use local dates and newest day first`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val older = LocalDate.of(2026, 8, 25).atTime(23, 30).atZone(berlin).toInstant()
        val newer = LocalDate.of(2026, 8, 26).atTime(0, 30).atZone(berlin).toInstant()

        val sections = BrowsingHistoryRules.sections(
            entries = listOf(
                HistoryEntry("https://old.example/", "Old", older.toEpochMilli()),
                HistoryEntry("https://new.example/", "New", newer.toEpochMilli()),
            ),
            zoneId = berlin,
        )

        assertEquals(
            listOf(LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 25)),
            sections.map(HistoryDaySection::date),
        )
    }

    @Test
    fun `history deletion removes only selected visit identity`() {
        val personal = HistoryEntry("https://example.com/", "Personal", 10, "personal")
        val work = HistoryEntry("https://example.com/", "Work", 10, "work")

        assertEquals(
            listOf(work),
            BrowsingHistoryRules.removeEntries(listOf(personal, work), listOf(personal)),
        )
    }

    @Test
    fun `history range deletion intersects profiles and inclusive local days`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val since = LocalDate.of(2026, 3, 28)
        val until = LocalDate.of(2026, 3, 29)
        val request = HistoryClearRequest(
            profileIds = setOf("personal"),
            sinceInclusiveMillis = since.atStartOfDay(berlin).toInstant().toEpochMilli(),
            untilExclusiveMillis = until.plusDays(1)
                .atStartOfDay(berlin)
                .toInstant()
                .toEpochMilli(),
        )
        val before = HistoryEntry(
            "https://before.example/",
            "Before",
            since.minusDays(1).atTime(23, 59).atZone(berlin).toInstant().toEpochMilli(),
            "personal",
        )
        val sinceBoundary = HistoryEntry(
            "https://since.example/",
            "Since",
            since.atStartOfDay(berlin).toInstant().toEpochMilli(),
            "personal",
        )
        val untilBoundary = HistoryEntry(
            "https://until.example/",
            "Until",
            until.atTime(23, 59).atZone(berlin).toInstant().toEpochMilli(),
            "personal",
        )
        val atExclusiveBoundary = HistoryEntry(
            "https://after.example/",
            "After",
            until.plusDays(1).atStartOfDay(berlin).toInstant().toEpochMilli(),
            "personal",
        )
        val otherProfile = HistoryEntry(
            "https://work.example/",
            "Work",
            since.atTime(12, 0).atZone(berlin).toInstant().toEpochMilli(),
            "work",
        )

        assertEquals(
            listOf(before, atExclusiveBoundary, otherProfile),
            BrowsingHistoryRules.removeRange(
                listOf(
                    before,
                    sinceBoundary,
                    untilBoundary,
                    atExclusiveBoundary,
                    otherProfile,
                ),
                request,
            ),
        )
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
    fun `suggestions show canonical reload history only once`() {
        val history = listOf(
            HistoryEntry("https://Example.com:443/page#first", "First load", 1),
            HistoryEntry("https://example.com/page#second", "Latest reload", 3),
            HistoryEntry("https://other.example/", "Other", 2),
        )

        val suggestions = BrowsingLibraryRules.suggestions(history, query = "", limit = 8)

        assertEquals(listOf("Latest reload", "Other"), suggestions.map(HistoryEntry::title))
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
