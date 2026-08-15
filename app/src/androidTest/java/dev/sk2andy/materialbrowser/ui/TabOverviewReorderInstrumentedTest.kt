package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabOverviewReorderInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            controller = null
            clearSession()
        }
    }

    @Test
    fun heroTabsReorderAfterLongPressDrag() = verifyReorder(TabOverviewMode.Hero)

    @Test
    fun gridTabsReorderAfterLongPressDrag() = verifyReorder(TabOverviewMode.Grid)

    @Test
    fun listTabsReorderAfterLongPressDrag() = verifyReorder(TabOverviewMode.List)

    @Test
    fun quickReleaseAfterLongPressStillCommits() = verifyReorder(
        mode = TabOverviewMode.Hero,
        moveDurationMillis = 80L,
    )

    @Test
    fun heroCardMatchesAndroidSwitcherProportions() {
        lateinit var browserController: BrowserController
        lateinit var tabId: String
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
            tabId = browserController.selectedTabId
            browserController.updateTabOverviewMode(TabOverviewMode.Hero)
        }
        setOverviewContent(browserController)
        composeRule.waitForIdle()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val cardBounds = composeRule
            .onNodeWithTag(SnoozeTestTags.overviewTab(tabId))
            .fetchSemanticsNode()
            .boundsInRoot
        val density = composeRule.activity.resources.displayMetrics.density
        val expectedWidth = (rootBounds.width * 0.70f)
            .coerceIn(244f * density, 360f * density)

        assertEquals(expectedWidth, cardBounds.width, 8f)
        assertEquals(0.45f, cardBounds.width / cardBounds.height, 0.001f)
        assertTrue(cardBounds.top >= rootBounds.top)
        assertTrue(cardBounds.bottom <= rootBounds.bottom)
    }

    private fun verifyReorder(
        mode: TabOverviewMode,
        moveDurationMillis: Long = 240L,
    ) {
        lateinit var browserController: BrowserController
        lateinit var sourceTabId: String
        lateinit var destinationTabId: String
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
            sourceTabId = requireNotNull(
                browserController.createBackgroundTab("https://source-${mode.wireValue}.example"),
            )
            destinationTabId = requireNotNull(
                browserController.createBackgroundTab("https://target-${mode.wireValue}.example"),
            )
            browserController.selectTab(sourceTabId)
            browserController.updateTabOverviewMode(mode)
        }
        setOverviewContent(browserController)
        composeRule.waitForIdle()

        val sourceNode = composeRule.onNodeWithTag(SnoozeTestTags.overviewTab(sourceTabId))
        val sourceBounds = sourceNode.fetchSemanticsNode().boundsInRoot
        val destinationBounds = composeRule
            .onNodeWithTag(SnoozeTestTags.overviewTab(destinationTabId))
            .fetchSemanticsNode()
            .boundsInRoot
        val destinationInSource = Offset(
            x = destinationBounds.center.x - sourceBounds.left,
            y = destinationBounds.center.y - sourceBounds.top,
        )
        sourceNode.performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveTo(destinationInSource, delayMillis = moveDurationMillis)
            up()
        }

        composeRule.waitUntil(timeoutMillis = 12_000L) {
            browserController.activeTabs.indexOfFirst { it.id == sourceTabId } ==
                browserController.activeTabs.indexOfFirst { it.id == destinationTabId } + 1
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf(destinationTabId, sourceTabId),
                browserController.activeTabs
                    .filter { it.id == sourceTabId || it.id == destinationTabId }
                    .map(BrowserTab::id),
            )
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
    }

    private fun clearSession() {
        composeRule.activity
            .getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
