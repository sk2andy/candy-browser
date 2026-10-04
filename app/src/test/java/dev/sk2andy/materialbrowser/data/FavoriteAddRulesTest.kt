package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteAddRulesTest {
    @Test
    fun `valid entry trims title and falls back to host`() {
        assertEquals(
            "Example",
            FavoriteAddRules.entry(" https://example.com/path ", " Example ", 1L)?.title,
        )
        assertEquals(
            "example.com",
            FavoriteAddRules.entry("https://example.com/", "", 1L)?.title,
        )
    }

    @Test
    fun `invalid credentialed and oversized urls are rejected`() {
        listOf(
            "javascript:alert(1)",
            "https://user@example.com/",
            "https://example.com/a b",
            "not a url",
            "https://example.com/" + "a".repeat(8_192),
        ).forEach { url ->
            assertNull(FavoriteAddRules.entry(url, "Example", 1L))
        }
    }

    @Test
    fun `addition preserves folders and rejects canonical duplicates`() {
        val folder = FavoriteFolder("folder", "Folder")
        val existing = FavoriteEntry("https://example.com/", "Existing", 1L, parentFolderId = folder.id)
        val library = FavoriteLibrary(listOf(folder, existing))
        assertNull(
            FavoriteAddRules.add(
                library,
                FavoriteEntry("https://example.com:443/#fragment", "Duplicate", 2L),
            ),
        )
        val entry = FavoriteEntry("https://other.example/", "Other", 2L)
        assertEquals(listOf(entry, folder, existing), FavoriteAddRules.add(library, entry)?.entries)
    }

    @Test
    fun `full library rejects addition without dropping existing favorites`() {
        val library = FavoriteLibrary(
            (1..100).map { index ->
                FavoriteEntry("https://example.com/$index", "Entry $index", 1L)
            },
        )
        assertNull(FavoriteAddRules.add(library, FavoriteEntry("https://other.example/", "Other", 2L)))
        assertEquals(100, library.favorites.size)
    }
}
