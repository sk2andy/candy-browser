package dev.sk2andy.materialbrowser.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 34)
class SettingsBackRouterInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun parentScrollPositionSurvivesAChildVisitAndSystemBack() {
        var destination by mutableStateOf(SettingsDestination.Gestures)
        composeRule.setContent {
            MaterialTheme {
                SettingsBackRouter(destination, { destination = it }) { current ->
                    SettingsPage(title = current.name, backContentDescription = "Back", onBack = {
                        destination = SettingsBackRules.parentDestination(current)
                    }) {
                        if (current == SettingsDestination.Gestures) {
                            repeat(20) { index -> Text("Parent row $index", Modifier.height(56.dp)) }
                            Button(onClick = { destination = SettingsDestination.GestureControls },
                                modifier = Modifier.testTag("open_scrolled_child")) { Text("Gesture controls") }
                        } else {
                            Text("Child settings")
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("open_scrolled_child").performScrollTo()
        val originalScroll = composeRule.onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(originalScroll > 0f)
        composeRule.onNodeWithTag("open_scrolled_child").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(SettingsDestination.Gestures, destination)
        val restoredScroll = composeRule.onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(originalScroll, restoredScroll, 1f)
    }

    @Test
    fun scrolledParentIsPreservedDuringPredictivePreviewCancellationAndCommit() {
        var destination by mutableStateOf(SettingsDestination.Gestures)
        showScrolledParent(destination = { destination }, onDestinationChanged = { destination = it })
        composeRule.onNodeWithTag("open_scrolled_preview_child").performScrollTo()
        val originalScroll = parentScroll()
        assertTrue(originalScroll > 0f)
        assertPixel(Color.Green, top = true)
        composeRule.onNodeWithTag("open_scrolled_preview_child").performClick()
        composeRule.waitForIdle()
        assertPixel(Color.Blue, top = true)

        startGesture(BackEventCompat.EDGE_LEFT)
        assertPixel(Color.Green, top = true)
        assertPixel(Color.Blue, top = false)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        composeRule.waitForIdle()
        assertEquals(SettingsDestination.GestureControls, destination)
        assertPixel(Color.Blue, top = true)

        startGesture(BackEventCompat.EDGE_RIGHT)
        assertPixel(Color.Green, top = true)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(SettingsDestination.Gestures, destination)
        assertPixel(Color.Green, top = true)
        assertEquals(originalScroll, parentScroll(), 1f)
    }

    @Test
    fun rapidRouteChangesAndPredictiveCommitsKeepOneSavedParentState() {
        var destination by mutableStateOf(SettingsDestination.Gestures)
        showScrolledParent(destination = { destination }, onDestinationChanged = { destination = it })
        composeRule.onNodeWithTag("open_scrolled_preview_child").performScrollTo()
        val originalScroll = parentScroll()
        composeRule.mainClock.autoAdvance = false

        repeat(3) {
            // Keep multiple outgoing routes alive, then start Back before the transition settles.
            composeRule.runOnIdle { destination = SettingsDestination.GestureControls }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle { destination = SettingsDestination.GestureLinks }
            composeRule.mainClock.advanceTimeByFrame()
            startGesture(BackEventCompat.EDGE_LEFT)
            composeRule.mainClock.advanceTimeBy(1_000L)
            composeRule.waitForIdle()
            assertPixel(Color.Green, top = true)
            composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            composeRule.mainClock.advanceTimeByFrame()
            // The next iteration interrupts the return animation as well.
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
        assertEquals(SettingsDestination.Gestures, destination)
        assertPixel(Color.Green, top = true)
        assertEquals(originalScroll, parentScroll(), 1f)
    }

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
        assertPixel(Color.Red, top = true)
        assertPixel(Color.Blue, top = false)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertPixel(Color.Red, top = true)
        composeRule.runOnIdle {
            assertEquals(SettingsDestination.BrowserWebsites, destination)
            assertEquals(1, navigationCalls)
        }

        startGesture(BackEventCompat.EDGE_LEFT)
        assertPixel(Color.Cyan, top = true)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertPixel(Color.Cyan, top = true)
        composeRule.runOnIdle {
            assertEquals(SettingsDestination.Browser, destination)
            assertEquals(2, navigationCalls)
        }

        startGesture(BackEventCompat.EDGE_RIGHT)
        assertPixel(Color.Green, top = true)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertPixel(Color.Green, top = true)
        composeRule.runOnIdle {
            assertEquals(SettingsDestination.Home, destination)
            assertEquals(3, navigationCalls)
        }
    }

    private fun showRouter(
        destination: () -> SettingsDestination,
        transparentSubpage: Boolean = false,
        onDestinationChanged: (SettingsDestination) -> Unit,
    ) {
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(surface = Color.Yellow, surfaceContainerLow = Color.Yellow)) {
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
                                SettingsDestination.BrowserWebsites -> Color.Red
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

    private fun showScrolledParent(
        destination: () -> SettingsDestination,
        onDestinationChanged: (SettingsDestination) -> Unit,
    ) {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag(CONTAINER_TAG)) {
                    SettingsBackRouter(destination(), onDestinationChanged) { page ->
                        if (page == SettingsDestination.Gestures) {
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                    .testTag("scrolled_preview_parent"),
                            ) {
                                // Top and saved viewport have distinct colors: a fresh preview
                                // scroll state is red even though the saved parent should be green.
                                Box(Modifier.fillMaxWidth().height(2_000.dp).background(Color.Red))
                                Box(Modifier.fillMaxWidth().height(2_000.dp).background(Color.Green))
                                Button(
                                    onClick = { onDestinationChanged(SettingsDestination.GestureControls) },
                                    modifier = Modifier.fillMaxWidth().testTag("open_scrolled_preview_child"),
                                ) { Text("Open child") }
                            }
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.Blue))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun parentScroll(): Float =
        composeRule.onNodeWithTag("scrolled_preview_parent").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()

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
