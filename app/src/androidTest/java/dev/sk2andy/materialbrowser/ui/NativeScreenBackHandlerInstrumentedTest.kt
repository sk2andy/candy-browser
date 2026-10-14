package dev.sk2andy.materialbrowser.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 34)
class NativeScreenBackHandlerInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cancellationRestoresSurfaceWithoutNavigating() {
        lateinit var state: NativeScreenBackState
        var backCalls = 0
        composeRule.setContent {
            state = rememberNativeScreenBackState()
            NativeScreenBackHandler(state = state, onBack = { backCalls++ })
            Box(
                Modifier
                    .size(300.dp)
                    .testTag("native_back_surface")
                    .background(Color.Magenta),
            ) {
                Box(
                    Modifier
                        .size(250.dp)
                        .predictiveBackSurface(state.progress.value, state.swipeEdgeSign)
                        .background(Color.Cyan),
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue(topCenterPixel().green > 0.9f)

        startGesture(progress = 0.6f, edge = BackEventCompat.EDGE_RIGHT)
        composeRule.runOnIdle {
            assertEquals(0.6f, state.progress.value, 0.001f)
            assertEquals(-1, state.swipeEdgeSign)
            assertEquals(0, backCalls)
        }
        assertTrue(topCenterPixel().green < 0.1f)

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(0f, state.progress.value, 0.001f)
            assertEquals(0, backCalls)
        }
        assertTrue(topCenterPixel().green > 0.9f)
    }

    @Test
    fun commitUsesLatestCallbackOnceAndResetsForNestedNavigation() {
        lateinit var state: NativeScreenBackState
        var destination by mutableIntStateOf(1)
        var navigatedFrom = 0
        var backCalls = 0
        composeRule.setContent {
            state = rememberNativeScreenBackState()
            val currentDestination = destination
            NativeScreenBackHandler(state = state) {
                navigatedFrom = currentDestination
                backCalls++
            }
        }
        composeRule.waitForIdle()
        startGesture(progress = 0.5f)
        composeRule.runOnIdle { destination = 2 }
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(2, navigatedFrom)
            assertEquals(1, backCalls)
            assertEquals(0f, state.progress.value, 0.001f)
        }
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(2, backCalls) }
    }

    @Test
    fun dismissalRetainsShrinkThroughExitTransition() {
        lateinit var state: NativeScreenBackState
        var backCalls = 0
        composeRule.setContent {
            state = rememberNativeScreenBackState()
            NativeScreenBackHandler(state = state, retainProgressOnBack = true) { backCalls++ }
        }
        composeRule.waitForIdle()
        startGesture(progress = 0.4f)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, backCalls)
            assertEquals(1f, state.progress.value, 0.001f)
        }
    }

    @Test
    fun snoozedTabsCancellationAndCommitKeepDismissalSeparate() {
        var backCalls = 0
        composeRule.setContent {
            SnoozedTabsScreen(
                snoozedTabs = emptyList(),
                profiles = emptyList(),
                onBack = { backCalls++ },
                onReschedule = { _, _ -> true },
                onOpenNow = { true },
                onDelete = { true },
            )
        }
        composeRule.waitForIdle()
        startGesture(progress = 0.7f)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(0, backCalls) }
        startGesture(progress = 0.7f)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test
    fun readerCancellationKeepsSessionAndCommitDismissesOnce() {
        var backCalls = 0
        val repository = ReaderLibraryRepository.get(composeRule.activity)
        composeRule.setContent {
            ReaderStudioScreen(
                result = null,
                sourceUrl = "https://example.com/article",
                isPrivate = true,
                repository = repository,
                onRetry = {},
                onDismiss = { backCalls++ },
                onOpenOriginal = {},
                onOpenLink = {},
            )
        }
        composeRule.waitForIdle()
        startGesture(progress = 0.5f)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(0, backCalls) }
        startGesture(progress = 0.5f)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test
    fun downloadsDelegateSystemBackAndKeepToolbarAction() {
        var backCalls = 0
        composeRule.setContent {
            DownloadsScreen(
                downloads = emptyList(),
                onClearFinished = {},
                onOpenDownload = {},
                onBack = { backCalls++ },
            )
        }
        composeRule.runOnIdle {
            assertFalse(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        }
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back))
            .performClick()
        composeRule.runOnIdle { assertEquals(1, backCalls) }
    }

    private fun startGesture(progress: Float, edge: Int = BackEventCompat.EDGE_LEFT) {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 500f, 0f, edge),
            )
        }
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(100f, 500f, progress, edge),
            )
        }
        composeRule.waitForIdle()
    }

    private fun topCenterPixel(): Color {
        val pixels = composeRule.onNodeWithTag("native_back_surface").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, 0]
    }
}
