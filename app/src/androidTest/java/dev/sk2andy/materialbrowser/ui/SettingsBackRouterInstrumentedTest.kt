package dev.sk2andy.materialbrowser.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 34)
class SettingsBackRouterInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun subpageRevealsHomeAndCancellationRestoresSubpage() {
        var destination by mutableStateOf(SettingsDestination.Browser)
        var navigationCalls = 0
        showRouter(destination = { destination }) {
            destination = it
            navigationCalls++
        }
        assertPixel(Color.Cyan, top = true)

        startGesture(BackEventCompat.EDGE_LEFT)
        assertPixel(Color.Green, top = true)
        assertPixel(Color.Cyan, top = false)
        composeRule.runOnIdle {
            assertEquals(SettingsDestination.Browser, destination)
            assertEquals(0, navigationCalls)
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        composeRule.waitForIdle()
        assertPixel(Color.Cyan, top = true)

        startGesture(BackEventCompat.EDGE_RIGHT)
        assertPixel(Color.Green, top = true)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertPixel(Color.Green, top = true)
        composeRule.runOnIdle {
            assertEquals(SettingsDestination.Home, destination)
            assertEquals(1, navigationCalls)
            assertFalse(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        }
    }

    @Test
    fun transparentSubpageKeepsOpaqueSurfaceAboveParentPreview() {
        showRouter(
            destination = { SettingsDestination.Browser },
            transparentSubpage = true,
            onDestinationChanged = {},
        )
        assertPixel(Color.Yellow, top = true)

        startGesture(BackEventCompat.EDGE_LEFT)
        assertPixel(Color.Green, top = true)
        assertPixel(Color.Yellow, top = false)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        composeRule.waitForIdle()
        assertPixel(Color.Yellow, top = true)
    }

    @Test
    fun nestedSubpageRevealsDirectParentAndCommitNavigatesOnce() {
        var destination by mutableStateOf(SettingsDestination.LinkRouting)
        var navigationCalls = 0
        showRouter(destination = { destination }) {
            destination = it
            navigationCalls++
        }
        assertPixel(Color.Blue, top = true)

        startGesture(BackEventCompat.EDGE_RIGHT)
        assertPixel(Color.Cyan, top = true)
        assertPixel(Color.Blue, top = false)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertPixel(Color.Cyan, top = true)
        composeRule.runOnIdle {
            assertEquals(SettingsDestination.Browser, destination)
            assertEquals(1, navigationCalls)
        }

        startGesture(BackEventCompat.EDGE_LEFT)
        assertPixel(Color.Green, top = true)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertPixel(Color.Green, top = true)
        composeRule.runOnIdle {
            assertEquals(SettingsDestination.Home, destination)
            assertEquals(2, navigationCalls)
        }
    }

    private fun showRouter(
        destination: () -> SettingsDestination,
        transparentSubpage: Boolean = false,
        onDestinationChanged: (SettingsDestination) -> Unit,
    ) {
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(surface = Color.Yellow)) {
                Box(Modifier.fillMaxSize().testTag(CONTAINER_TAG).background(Color.Magenta)) {
                    SettingsBackRouter(
                        destination = destination(),
                        onDestinationChanged = onDestinationChanged,
                    ) { currentDestination ->
                        if (transparentSubpage && currentDestination == SettingsDestination.Browser) {
                            SettingsPage(title = "Browser", backContentDescription = "Back", onBack = {}) {}
                        } else {
                            val color = when (currentDestination) {
                                SettingsDestination.Home -> Color.Green
                                SettingsDestination.Browser -> Color.Cyan
                                else -> Color.Blue
                            }
                            Box(Modifier.fillMaxSize().background(color))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun startGesture(edge: Int) {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 500f, 0f, edge),
            )
        }
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(100f, 500f, 0.7f, edge),
            )
        }
        composeRule.waitForIdle()
    }

    private fun assertPixel(expected: Color, top: Boolean) {
        val pixels = composeRule.onNodeWithTag(CONTAINER_TAG).captureToImage().toPixelMap()
        val actual = pixels[pixels.width / 2, if (top) 0 else pixels.height / 2]
        assertEquals(expected.red, actual.red, 0.01f)
        assertEquals(expected.green, actual.green, 0.01f)
        assertEquals(expected.blue, actual.blue, 0.01f)
    }

    private companion object {
        const val CONTAINER_TAG = "settings_back_container"
    }
}
