package dev.sk2andy.materialbrowser.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.FavoritesActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.FavoriteFolder
import dev.sk2andy.materialbrowser.data.FavoriteFolderIcon
import dev.sk2andy.materialbrowser.data.FavoriteFolderIconStore
import dev.sk2andy.materialbrowser.data.FavoriteLibrary
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesActivityInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun managerUploadCommitsCustomIconAndSurvivesProviderRemovalAndActivityRestart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = BrowserSessionStore(context)
        val previous = store.loadFavoriteLibrary()
        val folder = FavoriteFolder(UUID.randomUUID().toString(), "Travel")
        val resolver = context.contentResolver
        val sourceUri = requireNotNull(
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "favorite-icon-${folder.id}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                },
            ),
        )
        val iconStore = FavoriteFolderIconStore(context)
        val pickerLaunches = AtomicInteger()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_GET_CONTENT || intent.type != "image/*") return null
                pickerLaunches.incrementAndGet()
                return Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().setData(sourceUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }
        }
        var sourceRemoved = false
        instrumentation.addMonitor(monitor)
        try {
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.CYAN)
            }
            try {
                requireNotNull(resolver.openOutputStream(sourceUri)).use { output ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
            } finally {
                bitmap.recycle()
            }
            assertTrue(store.saveFavoriteLibraryCommitted(FavoriteLibrary(listOf(folder))))
            ActivityScenario.launch(FavoritesActivity::class.java).use { scenario ->
                composeRule.onNodeWithTag("favorites_actions:${folder.id}").performClick()
                composeRule.onNodeWithText(context.getString(R.string.favorites_folder_icon)).performClick()
                composeRule.onNodeWithText(context.getString(R.string.favorites_upload_icon)).performClick()
                composeRule.waitUntil(5_000) {
                    store.loadFavoriteLibrary().folders.singleOrNull()?.icon == FavoriteFolderIcon.Custom
                }
                assertEquals(1, pickerLaunches.get())
                sourceRemoved = resolver.delete(sourceUri, null, null) == 1
                assertTrue(sourceRemoved)
                scenario.recreate()
                composeRule.onNodeWithTag("favorites_folder:${folder.id}").assertIsDisplayed()
                assertEquals(
                    folder.copy(icon = FavoriteFolderIcon.Custom),
                    store.loadFavoriteLibrary().folders.single(),
                )
                val restored = iconStore.load(folder.id)
                assertNotNull(restored)
                try {
                    assertEquals(Color.CYAN, restored!!.getPixel(0, 0))
                } finally {
                    restored?.recycle()
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            if (!sourceRemoved) resolver.delete(sourceUri, null, null)
            iconStore.delete(folder.id)
            store.saveFavoriteLibraryCommitted(previous)
        }
    }

    @Test
    fun nestedDeleteAndUndoPersistFullLibraryIncludingSiblingOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = BrowserSessionStore(context)
        val previous = store.loadFavoriteLibrary()
        val parent = FavoriteFolder("activity-parent", "Parent")
        val child = FavoriteFolder("activity-child", "Child", parentFolderId = parent.id)
        val first = FavoriteEntry("https://first.example/", "First", 1, parentFolderId = child.id)
        val second = FavoriteEntry("https://second.example/", "Second", 2, parentFolderId = child.id)
        val source = FavoriteLibrary(listOf(parent, child, first, second))
        assertTrue(store.saveFavoriteLibraryCommitted(source))
        try {
            ActivityScenario.launch(FavoritesActivity::class.java).use {
                composeRule.onNodeWithTag("favorites_folder:${parent.id}").performClick()
                composeRule.onNodeWithTag("favorites_folder:${child.id}").performClick()
                composeRule.onNodeWithTag(FavoritesScreenTestTags.delete(first.url)).performClick()
                composeRule.waitUntil(5_000) { store.loadFavoriteLibrary().favorites.size == 1 }
                assertEquals(listOf(parent, child, second), store.loadFavoriteLibrary().entries)
                composeRule.onNodeWithText(context.getString(R.string.action_undo)).performClick()
                composeRule.waitUntil(5_000) { store.loadFavoriteLibrary() == source }
                assertEquals(source, store.loadFavoriteLibrary())
            }
        } finally {
            store.saveFavoriteLibraryCommitted(previous)
        }
    }
}
