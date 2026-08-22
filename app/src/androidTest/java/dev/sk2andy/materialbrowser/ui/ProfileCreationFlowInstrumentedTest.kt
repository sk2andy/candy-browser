package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileCreationFlowInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.mainClock.autoAdvance = true
        composeRule.runOnIdle {
            controller?.destroy()
            controller = null
            clearSession()
        }
    }

    @Test
    fun addButtonCreatesProfileDuringOverviewEntry() {
        lateinit var browserController: BrowserController
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        composeRule.mainClock.autoAdvance = false
        try {
            setOverviewContent(browserController)
            composeRule.mainClock.advanceTimeBy(96L)
            composeRule.onNodeWithTag(ProfileSwitcherTestTags.Add)
                .assertIsEnabled()
                .performTouchInput { click() }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProfileCreationTestTags.Sheet).assertExists()
        composeRule.onNodeWithText("💼").performClick()
        composeRule.onNodeWithTag(ProfileCreationTestTags.CreateButton).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.profiles.size == 2
        }
        composeRule.runOnIdle {
            assertEquals("💼", browserController.profiles.last().emoji)
        }
    }

    private fun setOverviewContent(browserController: BrowserController) {
        composeRule.setContent {
            val bottomBarTop = remember { mutableFloatStateOf(2_000f) }
            MaterialBrowserTheme {
                TabOverview(
                    controller = browserController,
                    visible = true,
                    bottomBarTopPx = bottomBarTop,
                    onClose = {},
                    onSelect = {},
                    onNewTab = {},
                    destinationChromeVisible = true,
                    onEntryHeroStarted = {},
                    onEntryHeroCompleted = {},
                    onExitHeroVisibilityChanged = {},
                    candyTrailTabId = null,
                    candyTrailSourceBounds = null,
                    candyTrailBackProgress = 0f,
                    candyTrailBackEdgeSign = 1,
                    candyTrailPredictiveBackCommitted = false,
                    onOpenCandyTrail = { _, _ -> },
                    onCloseCandyTrail = {},
                    onToggleFavoriteTab = {},
                    onAddSiteCapsule = {},
                    onSnoozeTab = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun clearSession() {
        composeRule.activity
            .getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
