package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.ui.NewTabFavoritesTestTags
import dev.sk2andy.materialbrowser.ui.NewTabPage
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteFaviconLiveInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun liveYouTubeFavoriteUsesSharpFirstPartyIcon() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("candyLiveYouTube") == "true")
        val favorite = FavoriteEntry("https://www.youtube.com/", "YouTube", 1L)
        val store = FavoriteFaviconStore(composeRule.activity)
        val repository = FavoriteFaviconRepository.get(composeRule.activity)
        val small = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val showFavorite = mutableStateOf(true)
        var restored: Bitmap? = null
        try {
            assertTrue(repository.flush())
            assertTrue(store.save(favorite.url, small))
            repository.capture(favorite.url, bitmap = null, forceRefresh = true)
            assertTrue(repository.flush())
            restored = store.load(favorite.url)
            assertNotNull(restored)
            val bitmap = requireNotNull(restored)
            assertTrue("YouTube icon is ${bitmap.width}x${bitmap.height}", bitmap.minimumDimension() >= 128)
            composeRule.setContent {
                if (showFavorite.value) {
                    MaterialBrowserTheme {
                        NewTabPage(
                            favorites = listOf(favorite),
                            favicons = mapOf(favorite.url to bitmap),
                            incognito = false,
                            modeProgress = 0f,
                            revealOriginInRoot = Offset.Zero,
                            onSearch = {},
                            onFavorite = {},
                        )
                    }
                }
            }
            composeRule.onNodeWithTag(NewTabFavoritesTestTags.favicon(favorite.url), useUnmergedTree = true)
                .assertIsDisplayed()
            val directory = InstrumentationRegistry.getArguments().getString("candyScreenshotDirectory")
            if (directory != null) {
                composeRule.waitForIdle()
                val target = File(directory, "252-youtube-sharp-live.png")
                require(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
                File(directory, "252-youtube-icon-size.txt").writeText("${bitmap.width}x${bitmap.height}")
                val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                try {
                    target.outputStream().use { output ->
                        check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                } finally {
                    screenshot.recycle()
                }
            }
        } finally {
            composeRule.runOnIdle { showFavorite.value = false }
            composeRule.waitForIdle()
            small.recycle()
            restored?.recycle()
            store.prune(emptySet())
        }
    }
}
