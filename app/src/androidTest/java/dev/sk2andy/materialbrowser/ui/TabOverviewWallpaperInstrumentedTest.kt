package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.ProfileWallpaper
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabOverviewWallpaperInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var bitmap: Bitmap? = null

    @After
    fun tearDown() {
        bitmap?.recycle()
    }

    @Test
    fun wallpaperFillsCompleteOverviewBackground() {
        bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }
        val wallpaper = ProfileWallpaperRuntime(
            bitmap = requireNotNull(bitmap).asImageBitmap(),
            wallpaper = ProfileWallpaper(),
        )
        composeRule.setContent {
            MaterialTheme {
                TabOverviewBackground(
                    wallpaper = wallpaper,
                    modifier = Modifier.size(120.dp),
                )
            }
        }

        val image = composeRule
            .onNodeWithTag(TabOverviewChromeTestTags.Background)
            .captureToImage()
        val pixels = image.toPixelMap()
        val center = pixels[pixels.width / 2, pixels.height / 2]

        assertTrue(center.red > 0.35f)
        assertTrue(center.green < 0.08f)
        assertTrue(center.blue < 0.08f)
    }
}
