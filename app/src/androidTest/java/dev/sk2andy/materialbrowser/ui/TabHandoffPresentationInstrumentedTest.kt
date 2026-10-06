package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabHandoffPresentationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun consecutiveRestorationStartsOpaqueWhileSwitcherSnapshotKeepsColor() {
        val bitmap = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.RED)
        }
        val initial = TabHandoff(
            tab = BrowserTab(
                id = "restore",
                lastAccessedAt = 1L,
                title = "Restore",
                url = "https://example.com",
            ),
            preview = bitmap,
            favicon = null,
            previewTopInsetPx = 0,
        )
        val handoff = mutableStateOf(initial)
        val bottom = mutableFloatStateOf(Float.NaN)
        lateinit var alpha: Animatable<Float, AnimationVector1D>
        composeRule.setContent {
            MaterialBrowserTheme {
                val current = handoff.value
                val currentAlpha = rememberTabHandoffAlpha(current)
                SideEffect { alpha = currentAlpha }
                Box(Modifier.fillMaxSize().background(Color.Yellow)) {
                    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = currentAlpha.value }) {
                        FullscreenTabPreviewContent(
                            tab = current.tab,
                            preview = current.preview,
                            favicon = null,
                            rootHeightPx = composeRule.activity.window.decorView.height.toFloat(),
                            previewTopInsetPx = 0,
                            bottomBarTopPx = bottom,
                            favorites = emptyList(),
                            previewSaturation = TabHandoffRules.previewSaturation(current, currentAlpha.value),
                        )
                    }
                }
            }
        }
        assertCenterPixel { red > 0.95f && green < 0.05f && blue < 0.05f }
        composeRule.runOnIdle { runBlocking { alpha.snapTo(0f) } }
        assertCenterPixel { red > 0.95f && green > 0.95f && blue < 0.05f }

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            handoff.value = initial.copy(isRestoring = true, visualIdentity = Any())
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertCenterPixel { red in 0.18f..0.25f && green == red && blue == red }
        composeRule.runOnIdle {
            assertEquals(1f, alpha.value, 0f)
            assertEquals(AndroidColor.RED, bitmap.getPixel(32, 64))
        }
        composeRule.mainClock.autoAdvance = true
    }

    private fun assertCenterPixel(predicate: Color.() -> Boolean) {
        val image = composeRule.onRoot().captureToImage()
        val color = image.toPixelMap()[image.width / 2, image.height / 2]
        assertTrue("Unexpected preview pixel: $color", color.predicate())
    }
}
