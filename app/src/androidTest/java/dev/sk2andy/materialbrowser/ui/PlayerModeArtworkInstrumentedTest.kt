package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerMode
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerModeArtworkInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectionsShowDistinctArtworkInSameSlotAndFollowLiveTheme() {
        var mode by mutableStateOf(InlineMediaPlayerMode.Disabled)
        var appearance by mutableStateOf(BrowserAppearanceMode.Light)
        var page = Color.Unspecified
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings(appearanceMode = appearance)) {
                val color = MaterialTheme.colorScheme.surfaceContainerLow
                SideEffect { page = color }
                PlayerModeSettingsPage(mode, true, onModeChanged = { mode = it }, onBack = {})
            }
        }
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "player-mode-artwork",
        ).apply { mkdirs() }
        var width: Int? = null
        for (theme in listOf(BrowserAppearanceMode.Light, BrowserAppearanceMode.Dark, BrowserAppearanceMode.Amoled)) {
            composeRule.runOnIdle { appearance = theme }
            val signatures = mutableSetOf<List<Int>>()
            for (candidate in InlineMediaPlayerMode.entries) {
                composeRule.onNodeWithTag("player_mode_${candidate.stableId}").performScrollTo().performClick()
                composeRule.runOnIdle { assertEquals(candidate, mode) }
                val node = composeRule.onNodeWithTag(
                    "settings_player_mode_artwork_${candidate.name.lowercase()}",
                    useUnmergedTree = true,
                )
                    .performScrollTo().assertIsDisplayed().assertHeightIsEqualTo(240.dp)
                val image = node.captureToImage()
                if (width == null) width = image.width
                assertEquals(width, image.width)
                val pixels = image.toPixelMap()
                for ((x, y) in listOf(0 to 0, pixels.width - 1 to 0, 0 to pixels.height - 1)) {
                    val border = pixels[x, y]
                    assertTrue("Paper follows $theme", abs(border.red - page.red) < 0.02f &&
                        abs(border.green - page.green) < 0.02f && abs(border.blue - page.blue) < 0.02f)
                }
                val signature = (1..19).flatMap { x ->
                    (1..11).map { y -> pixels[x * (pixels.width - 1) / 20, y * (pixels.height - 1) / 12].hashCode() }
                }
                assertTrue("Each mode has distinct artwork: $candidate", signatures.add(signature))
                composeRule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                    File(directory, "${theme.name.lowercase()}_${candidate.stableId}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        }
    }
}
