package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowsingFavoritesRulesTest {
    @Test
    fun `search matches title host and URL ignoring case`() {
        val titleMatch = favorite(
            url = "https://example.com/guide",
            title = "Kotlin Guide",
            addedAt = 1,
        )
        val hostMatch = favorite(
            url = "https://candy.example/article",
            title = "Article",
            addedAt = 2,
        )
        val urlMatch = favorite(
            url = "https://example.org/special-path",
            title = "Reference",
            addedAt = 3,
        )
        val favorites = listOf(titleMatch, hostMatch, urlMatch)

        assertEquals(listOf(titleMatch), BrowsingFavoritesRules.visibleEntries(favorites, "KOTLIN"))
        assertEquals(listOf(hostMatch), BrowsingFavoritesRules.visibleEntries(favorites, "candy"))
        assertEquals(listOf(urlMatch), BrowsingFavoritesRules.visibleEntries(favorites, "special"))
    }

    @Test
    fun `empty query preserves stored order and removes invalid duplicates`() {
        val older = favorite("https://older.example/", "Older", 10)
        val newer = favorite("https://newer.example/", "Newer", 20)
        val duplicate = favorite("https://newer.example:443/#fragment", "Duplicate", 30)
        val invalid = favorite("not a web URL", "Invalid", 30)
        val credentials = favorite("https://user:secret@private.example/", "Credentials", 40)

        assertEquals(
            listOf(older, newer),
            BrowsingFavoritesRules.visibleEntries(
                listOf(older, invalid, credentials, newer, duplicate),
                "",
            ),
        )
    }

    @Test
    fun `visible favorites are bounded to the library limit`() {
        val favorites = List(BrowsingLibraryRules.MAX_FAVORITES + 5) { index ->
            favorite("https://example$index.test/", "Favorite $index", index.toLong())
        }

        assertEquals(
            BrowsingLibraryRules.MAX_FAVORITES,
            BrowsingFavoritesRules.visibleEntries(favorites, "").size,
        )
    }

    @Test
    fun `remove uses canonical URL identity`() {
        val target = favorite("https://Example.com:443/#one", "Target", 10)
        val other = favorite("https://other.example/", "Other", 20)

        assertEquals(
            listOf(other),
            BrowsingFavoritesRules.remove(
                favorites = listOf(target, other),
                entry = favorite("https://example.com/", "Stale title", 0),
            ),
        )
    }

    private fun favorite(url: String, title: String, addedAt: Long) = FavoriteEntry(
        url = url,
        title = title,
        addedAt = addedAt,
    )
}
