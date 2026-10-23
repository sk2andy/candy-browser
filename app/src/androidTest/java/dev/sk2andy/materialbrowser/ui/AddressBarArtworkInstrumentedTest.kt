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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAddressBarStyle
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.shared.ui.settings.AppearanceSettingsSection
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarArtworkInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun artworkFollowsLiveThemeAndAddressStyleChoicesRemainUsable() {
        var settings by mutableStateOf(AppearanceSettings())
        var page = Color.Unspecified
        var ink = Color.Unspecified
        composeRule.setContent {
            MaterialBrowserTheme(settings = settings) {
                val colors = MaterialTheme.colorScheme
                SideEffect {
                    page = colors.surfaceContainerLow
                    ink = colors.primary
                }
                AppearanceSettingsPage(
                    settings = settings,
                    section = AppearanceSettingsSection.AddressBar,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "address-bar-artwork").apply { mkdirs() }
        for (theme in listOf(BrowserAppearanceMode.Light, BrowserAppearanceMode.Dark, BrowserAppearanceMode.Amoled)) {
            composeRule.runOnIdle { settings = settings.copy(appearanceMode = theme) }
            for ((style, label) in listOf(
                BrowserAddressBarStyle.Classic to R.string.address_bar_style_classic,
                BrowserAddressBarStyle.Segmented to R.string.address_bar_style_segmented,
            )) {
                composeRule.onNodeWithText(context.getString(label)).performScrollTo().performClick()
                composeRule.runOnIdle { assertEquals(style, settings.addressBarStyle) }
            }
            val artwork = composeRule.onNodeWithTag("settings_address_bar_artwork", useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed().assertHeightIsEqualTo(220.dp)
            val pixels = artwork.captureToImage().toPixelMap()
            val corners = listOf(
                0 to 0, pixels.width - 1 to 0,
                0 to pixels.height - 1, pixels.width - 1 to pixels.height - 1,
            )
            for ((x, y) in corners) {
                val border = pixels[x, y]
                assertTrue("Paper follows $theme", abs(border.red - page.red) < 0.02f &&
                    abs(border.green - page.green) < 0.02f && abs(border.blue - page.blue) < 0.02f)
            }
            val inkDistance = (1..39).minOf { x ->
                (1..21).minOf { y ->
                    val pixel = pixels[x * (pixels.width - 1) / 40, y * (pixels.height - 1) / 22]
                    abs(pixel.red - ink.red) + abs(pixel.green - ink.green) + abs(pixel.blue - ink.blue)
                }
            }
            assertTrue("Artwork retains primary-colored detail in $theme", inkDistance < 0.24f)
            composeRule.waitForIdle()
            composeRule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                File(directory, "${theme.name.lowercase()}.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
        }
    }
}
