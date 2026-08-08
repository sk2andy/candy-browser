package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarDockInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun edgeTabIsAccessibleAndRestoresOnce() {
        val restores = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                val progress = remember { mutableFloatStateOf(0f) }
                Box(Modifier.size(width = 52.dp, height = 48.dp)) {
                    AddressBarEdgeTab(
                        onRestore = restores::incrementAndGet,
                        onTabs = {},
                        overviewGestureEnabled = true,
                        overviewGestureProgress = progress,
                        onOverviewGestureProgress = { progress.floatValue = it },
                        onOverviewGestureStarted = {},
                        onOverviewGestureCancelled = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab)
            .assertHasClickAction()
            .assertWidthIsEqualTo(52.dp)
            .assertHeightIsEqualTo(48.dp)
            .performClick()

        assertEquals(1, restores.get())
    }

    @Test
    fun edgeTabKeepsOverviewSwipe() {
        val overviewOpens = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                val progress = remember { mutableFloatStateOf(0f) }
                Box(Modifier.size(width = 52.dp, height = 48.dp)) {
                    AddressBarEdgeTab(
                        onRestore = {},
                        onTabs = overviewOpens::incrementAndGet,
                        overviewGestureEnabled = true,
                        overviewGestureProgress = progress,
                        onOverviewGestureProgress = { progress.floatValue = it },
                        onOverviewGestureStarted = {},
                        onOverviewGestureCancelled = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab)
            .performTouchInput {
                swipe(
                    start = bottomCenter,
                    end = Offset(center.x, -bottomCenter.y),
                )
            }

        assertEquals(1, overviewOpens.get())
    }
}
