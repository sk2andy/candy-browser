package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteLibraryRulesTest {
    @Test
    fun `normalization preserves valid recursive hierarchy and sibling order`() {
        val library = FavoriteLibrary(
            entries = listOf(
                folder("work", "Work"),
                folder("android", "Android", "work"),
                favorite("one", "android"),
                favorite("two", "android"),
                favorite("root"),
            ),
        )

        val normalized = BrowsingFavoritesRules.normalizeLibrary(library)

        assertEquals(listOf("work", "root"), idsAt(normalized, null))
        assertEquals(listOf("android"), idsAt(normalized, "work"))
        assertEquals(listOf("one", "two"), idsAt(normalized, "android"))
    }

    @Test
    fun `normalization promotes missing and cyclic folder parents to root`() {
        val normalized = BrowsingFavoritesRules.normalizeLibrary(
            FavoriteLibrary(
                entries = listOf(
                    folder("a", "A", "b"),
                    folder("b", "B", "a"),
                    folder("missing", "Missing", "gone"),
                    favorite("child", "gone"),
                ),
            ),
        )

        assertEquals(listOf("a", "b", "missing", "child"), idsAt(normalized, null))
        assertTrue(normalized.entries.all { it.parentFolderId == null })
    }

    @Test
    fun `move rejects a folder into itself or its descendant`() {
        val library = FavoriteLibrary(
            entries = listOf(folder("parent", "Parent"), folder("child", "Child", "parent")),
        )

        assertEquals(library, BrowsingFavoritesRules.move(library, "parent", "parent"))
        assertEquals(library, BrowsingFavoritesRules.move(library, "parent", "child"))
    }

    @Test
    fun `move rejects a folder into an arbitrary-depth descendant`() {
        val library = FavoriteLibrary(
            entries = listOf(
                folder("parent", "Parent"),
                folder("child", "Child", "parent"),
                folder("grandchild", "Grandchild", "child"),
            ),
        )

        assertEquals(
            library,
            BrowsingFavoritesRules.move(library, "parent", "grandchild"),
        )
    }

    @Test
    fun `reorder affects siblings only`() {
        val library = FavoriteLibrary(
            entries = listOf(
                folder("folder", "Folder"),
                favorite("first"),
                favorite("second"),
                favorite("nested", "folder"),
            ),
        )

        val reordered = BrowsingFavoritesRules.reorder(library, "second", 0)

        assertEquals(listOf("second", "folder", "first"), idsAt(reordered, null))
        assertEquals(listOf("nested"), idsAt(reordered, "folder"))
    }

    @Test
    fun `normalization globally deduplicates URLs and retains legacy stable identifier`() {
        val first = FavoriteEntry("https://example.com", "One", 1L)
        val duplicate = FavoriteEntry("https://example.com/", "Two", 2L, id = "second")

        val normalized = BrowsingFavoritesRules.normalizeLibrary(
            FavoriteLibrary(entries = listOf(first, duplicate)),
        )

        assertEquals(listOf(first.id), normalized.favorites.map(FavoriteEntry::id))
        assertFalse(first.id.isBlank())
    }

    private fun folder(id: String, title: String, parentFolderId: String? = null) = FavoriteFolder(
        id = id,
        title = title,
        parentFolderId = parentFolderId,
    )

    private fun favorite(id: String, parentFolderId: String? = null) = FavoriteEntry(
        url = "https://$id.example/",
        title = id,
        addedAt = 1L,
        id = id,
        parentFolderId = parentFolderId,
    )

    private fun idsAt(library: FavoriteLibrary, parentFolderId: String?) =
        BrowsingFavoritesRules.children(library, parentFolderId).map(FavoriteLibraryEntry::id)
}
