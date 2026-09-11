package dev.sk2andy.materialbrowser.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteBookmarkImportStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun importMergesWithLatestPersistedFavorites() {
        val firstOwner = BrowserSessionStore(context)
        val importingOwner = BrowserSessionStore(context)
        val existing = favorite("https://existing.example/", "Existing", 1L)
        val imported = favorite("https://imported.example/", "Imported", 2L)
        assertTrue(firstOwner.saveFavoritesCommitted(listOf(existing)))

        val result = importingOwner.mergeImportedFavoritesCommitted(listOf(imported))

        assertEquals(1, result?.importedCount)
        assertEquals(listOf(imported, existing), importingOwner.loadFavorites())
    }

    @Test
    fun staleOwnerCannotOverwriteNewerFavoriteCommit() {
        val staleOwner = BrowserSessionStore(context)
        val currentOwner = BrowserSessionStore(context)
        val original = favorite("https://original.example/", "Original", 1L)
        val newer = favorite("https://newer.example/", "Newer", 2L)
        assertTrue(staleOwner.saveFavoritesCommitted(listOf(original)))
        assertTrue(currentOwner.saveFavoritesCommitted(listOf(newer, original)))

        val saved = staleOwner.saveFavoritesCommitted(
            favorites = emptyList(),
            expectedCurrent = listOf(original),
        )

        assertFalse(saved)
        assertEquals(listOf(newer, original), staleOwner.loadFavorites())
    }

    private fun favorite(url: String, title: String, addedAt: Long) = FavoriteEntry(
        url = url,
        title = title,
        addedAt = addedAt,
    )
}
