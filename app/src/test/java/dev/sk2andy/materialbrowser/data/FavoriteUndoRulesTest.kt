package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteUndoRulesTest {
    @Test
    fun `undo add restores exact previous list including bounded entry`() {
        val previous = (0 until BrowsingLibraryRules.MAX_FAVORITES).map(::favorite)
        val added = favorite(101)
        val applied = BrowsingLibraryRules.toggleFavorite(previous, added)
        val mutation = FavoriteMutation(
            before = previous,
            applied = applied,
            added = true,
            revision = 4,
        )

        val restored = FavoriteUndoRules.restore(applied, 4, mutation)

        assertEquals(previous, restored)
    }

    @Test
    fun `undo remove restores favorite at original position`() {
        val previous = listOf(favorite(1), favorite(2), favorite(3))
        val applied = BrowsingLibraryRules.toggleFavorite(previous, favorite(2))
        val mutation = FavoriteMutation(
            before = previous,
            applied = applied,
            added = false,
            revision = 8,
        )

        val restored = FavoriteUndoRules.restore(applied, 8, mutation)

        assertEquals(previous, restored)
    }

    @Test
    fun `stale undo cannot overwrite newer favorite mutation`() {
        val previous = listOf(favorite(1))
        val applied = listOf(favorite(2), favorite(1))
        val mutation = FavoriteMutation(previous, applied, added = true, revision = 2)

        assertNull(FavoriteUndoRules.restore(applied, 3, mutation))
        assertNull(FavoriteUndoRules.restore(listOf(favorite(3)) + applied, 2, mutation))
    }

    private fun favorite(index: Int) = FavoriteEntry(
        url = "https://example$index.com/",
        title = "Example $index",
        addedAt = index.toLong(),
    )
}
