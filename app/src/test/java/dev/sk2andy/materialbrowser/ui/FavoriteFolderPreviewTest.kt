package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.FavoriteFolder
import dev.sk2andy.materialbrowser.data.FavoriteLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteFolderPreviewTest {
    @Test
    fun `folder preview follows nested sibling order and takes four favorites`() {
        val root = FavoriteFolder("root", "Root")
        val nested = FavoriteFolder("nested", "Nested", "root")
        val first = favorite("first", "root")
        val second = favorite("second", "nested")
        val third = favorite("third", "nested")
        val fourth = favorite("fourth", "root")
        val fifth = favorite("fifth", "root")
        val source = FavoriteLibrary(listOf(root, first, nested, fourth, second, third, fifth))
        assertEquals(listOf(first, second, third, fourth), newTabFolderPreviewFavorites(source, root.id))
        assertEquals(emptyList<FavoriteEntry>(), newTabFolderPreviewFavorites(source, "missing"))
    }

    private fun favorite(name: String, parent: String) = FavoriteEntry(
        url = "https://$name.example/",
        title = name,
        addedAt = 1,
        parentFolderId = parent,
    )
}
