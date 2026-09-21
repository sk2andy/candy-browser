package dev.sk2andy.materialbrowser.data

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteLibraryStoreInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    @After
    fun tearDown() {
        activityRule.scenario.onActivity(::clear)
    }

    @Test
    fun legacyArrayLoadsAsRootFavoritesInSourceOrder() {
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(
                    "favorites",
                    """[
                        {"url":"https://first.example/","title":"First","addedAt":1},
                        {"url":"https://second.example/","title":"Second","addedAt":2}
                    ]""".trimIndent(),
                )
                .commit()

            val library = BrowserSessionStore(activity).loadFavoriteLibrary()

            assertEquals(listOf("First", "Second"), library.favorites.map(FavoriteEntry::title))
            assertTrue(library.entries.all { it.parentFolderId == null })
        }
    }

    @Test
    fun malformedRowDoesNotDiscardValidLegacyAndFolderEntries() {
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(
                    "favorites",
                    """[
                        {"url":"https://legacy.example/","title":"Legacy","addedAt":1},
                        {"type":"favorite","id":"broken","title":"Broken"},
                        {"type":"folder","id":"folder","title":"Folder"},
                        {"type":"favorite","id":"nested","url":"https://nested.example/","title":"Nested","addedAt":2,"parentFolderId":"folder"}
                    ]""".trimIndent(),
                )
                .commit()

            val library = BrowserSessionStore(activity).loadFavoriteLibrary()

            assertEquals(listOf("Legacy", "Folder"), BrowsingFavoritesRules.children(library, null)
                .map {
                    when (it) {
                        is FavoriteEntry -> it.title
                        is FavoriteFolder -> it.title
                    }
                })
            assertEquals(listOf("Nested"), BrowsingFavoritesRules.children(library, "folder")
                .filterIsInstance<FavoriteEntry>()
                .map(FavoriteEntry::title))
        }
    }

    @Test
    fun recursiveFoldersRoundTripWithCustomAndEmojiIcons() {
        activityRule.scenario.onActivity { activity ->
            val library = FavoriteLibrary(
                entries = listOf(
                    FavoriteFolder("root", "Root", icon = FavoriteFolderIcon.Emoji("📚")),
                    FavoriteFolder("nested", "Nested", "root", FavoriteFolderIcon.Custom),
                    FavoriteEntry(
                        url = "https://saved.example/",
                        title = "Saved",
                        addedAt = 1L,
                        id = "saved",
                        parentFolderId = "nested",
                    ),
                ),
            )
            val store = BrowserSessionStore(activity)

            store.saveFavoriteLibrary(library)

            assertEquals(library, store.loadFavoriteLibrary())
        }
    }

    @Test
    fun committedWriteRejectsStaleRecursiveSnapshot() {
        activityRule.scenario.onActivity { activity ->
            val store = BrowserSessionStore(activity)
            val initial = FavoriteLibrary(entries = listOf(FavoriteFolder("root", "Root")))
            val current = FavoriteLibrary(
                entries = initial.entries + FavoriteFolder("nested", "Nested", "root"),
            )
            store.saveFavoriteLibrary(initial)
            store.saveFavoriteLibrary(current)

            val saved = store.saveFavoriteLibraryCommitted(
                library = FavoriteLibrary(),
                expectedCurrent = initial,
            )

            assertFalse(saved)
            assertEquals(current, store.loadFavoriteLibrary())
        }
    }

    @Test
    fun importPrependsRootEntriesWithoutFlatteningExistingFolders() {
        activityRule.scenario.onActivity { activity ->
            val store = BrowserSessionStore(activity)
            val existing = FavoriteLibrary(
                entries = listOf(
                    FavoriteFolder("folder", "Folder"),
                    FavoriteEntry(
                        url = "https://nested.example/",
                        title = "Nested",
                        addedAt = 1L,
                        id = "nested",
                        parentFolderId = "folder",
                    ),
                ),
            )
            store.saveFavoriteLibrary(existing)

            val result = store.mergeImportedFavoritesCommitted(
                listOf(FavoriteEntry("https://imported.example/", "Imported", 2L)),
            )
            val loaded = store.loadFavoriteLibrary()

            assertEquals(1, result?.importedCount)
            assertEquals(
                listOf("Imported", "Folder"),
                BrowsingFavoritesRules.children(loaded, null).map {
                    when (it) {
                        is FavoriteEntry -> it.title
                        is FavoriteFolder -> it.title
                    }
                },
            )
            assertEquals(listOf("Nested"), loaded.favorites.filter { it.parentFolderId == "folder" }
                .map(FavoriteEntry::title))
        }
    }

    private fun clear(activity: ComponentActivity) {
        activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
