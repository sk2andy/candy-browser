package dev.sk2andy.materialbrowser.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.data.SnoozedTab
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnoozeScreensInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            controller = null
        }
    }

    @Test
    fun regularTabPresetSubmitsFutureTimeExactlyOnce() {
        val submitted = AtomicLong(0L)
        val calls = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                SnoozeTabSheet(
                    tab = BrowserTab("tab", 1L, title = "Example"),
                    onSnooze = { wakeAt ->
                        calls.incrementAndGet()
                        submitted.set(wakeAt)
                        true
                    },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SnoozeTestTags.Tomorrow)
            .assertIsEnabled()
            .performClick()

        assertEquals(1, calls.get())
        assertTrue(submitted.get() > System.currentTimeMillis())
    }

    @Test
    fun privateTabShowsPersistenceBoundaryAndDisablesChoices() {
        composeRule.setContent {
            MaterialBrowserTheme {
                SnoozeTabSheet(
                    tab = BrowserTab("private", 1L, isIncognito = true),
                    onSnooze = { false },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.snooze_unavailable_private))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SnoozeTestTags.LaterToday).assertIsNotEnabled()
        composeRule.onNodeWithTag(SnoozeTestTags.Custom).assertIsNotEnabled()
    }

    @Test
    fun tabActionsExposeSnoozeAsOptionBeforeOpeningPicker() {
        val snoozeCalls = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                TabActionsSheet(
                    tab = BrowserTab("tab", 1L, title = "Example"),
                    profiles = listOf(BrowserProfile("default", "🍬")),
                    isFavorite = false,
                    canToggleDomainMute = false,
                    isDomainMuted = false,
                    onToggleFavorite = {},
                    onOpenCandyTrail = {},
                    onTogglePinned = {},
                    onMoveToProfile = {},
                    onShare = {},
                    onOpenExternal = {},
                    onPrint = {},
                    onDomainMutedChange = {},
                    onAddSiteCapsule = {},
                    onSummarize = {},
                    onSnooze = { snoozeCalls.incrementAndGet() },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SnoozeTestTags.TabActions).assertIsDisplayed()
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Favorite).assertExists()
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Pin).assertExists()
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Trail).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.action_fork_tab))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.action_settings))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.action_back))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.action_forward))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.action_reload))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(SnoozeTestTags.TabActionsSnooze)
            .assertIsEnabled()
            .performClick()
        assertEquals(1, snoozeCalls.get())
    }

    @Test
    fun heroLongPressOpensActionsBeforeSnoozePicker() {
        verifyLongPressActionsFlow(TabOverviewMode.Hero)
    }

    @Test
    fun gridLongPressOpensActionsBeforeSnoozePicker() {
        verifyLongPressActionsFlow(TabOverviewMode.Grid)
    }

    @Test
    fun listLongPressOpensActionsBeforeSnoozePicker() {
        verifyLongPressActionsFlow(TabOverviewMode.List)
    }

    @Test
    fun managementExposesOpenDeleteAndProfileMetadata() {
        val openCalls = AtomicInteger()
        val deleteCalls = AtomicInteger()
        val backCalls = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                SnoozedTabsScreen(
                    snoozedTabs = listOf(
                        SnoozedTab(
                            tab = BrowserTab(
                                id = "saved",
                                lastAccessedAt = 1L,
                                profileId = "work",
                                title = "Saved page",
                                url = "https://example.com",
                            ),
                            wakeAtMillis = System.currentTimeMillis() + 60_000L,
                            createdAtMillis = 1L,
                        ),
                    ),
                    profiles = listOf(BrowserProfile("work", "💼")),
                    onBack = { backCalls.incrementAndGet() },
                    onReschedule = { _, _ -> true },
                    onOpenNow = {
                        openCalls.incrementAndGet()
                        true
                    },
                    onDelete = {
                        deleteCalls.incrementAndGet()
                        true
                    },
                )
            }
        }

        composeRule.onNodeWithTag(SnoozeTestTags.card("saved")).assertIsDisplayed()
        composeRule.onNodeWithText("💼").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_open_now)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_delete)).performClick()

        assertEquals(1, openCalls.get())
        assertEquals(1, backCalls.get())
        assertEquals(1, deleteCalls.get())
    }

    @Test
    fun snoozeFeedbackShowsUndoAndRunsActionOnce() {
        val hostState = SnackbarHostState()
        val undoCalls = AtomicInteger()
        lateinit var scope: CoroutineScope
        composeRule.setContent {
            scope = rememberCoroutineScope()
            MaterialBrowserTheme { SnackbarHost(hostState) }
        }

        composeRule.runOnIdle {
            scope.launch {
                showSnoozeUndoFeedback(
                    hostState = hostState,
                    message = context.getString(R.string.snooze_confirmation),
                    undoLabel = context.getString(R.string.action_undo),
                    onUndo = { undoCalls.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.snooze_confirmation))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_undo)).performClick()
        composeRule.waitUntil { undoCalls.get() == 1 }
        assertEquals(1, undoCalls.get())
    }

    private fun verifyLongPressActionsFlow(mode: TabOverviewMode) {
        lateinit var browserController: BrowserController
        lateinit var selectedTabId: String
        lateinit var tabId: String
        val favoriteTarget = AtomicReference<String?>(null)
        val snoozeTarget = AtomicReference<String?>(null)
        val backgroundUrl = "https://${mode.name.lowercase()}.background.example"
        composeRule.runOnIdle {
            browserController = BrowserController(composeRule.activity)
            browserController.updateTabOverviewMode(mode)
            controller = browserController
            selectedTabId = browserController.selectedTabId
            tabId = requireNotNull(browserController.createBackgroundTab(backgroundUrl))
        }
        composeRule.setContent {
            var snoozeTabId by remember { mutableStateOf<String?>(null) }
            val bottomBarTop = remember { mutableFloatStateOf(2_000f) }
            MaterialBrowserTheme {
                Box {
                    TabOverview(
                        controller = browserController,
                        visible = true,
                        bottomBarTopPx = bottomBarTop,
                        onClose = {},
                        onSelect = {},
                        onNewTab = {},
                        onNewTabButtonBounds = {},
                        destinationButtonVisible = false,
                        onEntryHeroStarted = {},
                        onEntryHeroCompleted = {},
                        candyTrailTabId = null,
                        candyTrailSourceBounds = null,
                        candyTrailBackProgress = 0f,
                        candyTrailBackEdgeSign = 1,
                        candyTrailPredictiveBackCommitted = false,
                        onOpenCandyTrail = { _, _ -> },
                        onCloseCandyTrail = {},
                        onToggleFavoriteTab = { targetId ->
                            favoriteTarget.set(targetId)
                            browserController.toggleFavorite(targetId)
                        },
                        onAddSiteCapsule = {},
                        onSnoozeTab = { targetId ->
                            snoozeTarget.set(targetId)
                            snoozeTabId = targetId
                        },
                    )
                    SnoozeTabSheet(
                        tab = snoozeTabId?.let { id ->
                            browserController.tabs.firstOrNull { it.id == id }
                        },
                        onSnooze = { true },
                        onDismiss = { snoozeTabId = null },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SnoozeTestTags.overviewTab(tabId))
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Favorite).assertExists()
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Pin).assertExists()
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Trail).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.action_fork_tab))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(DomainMuteMenuTestTags.Item).performClick()
        composeRule.runOnIdle {
            assertTrue(browserController.isDomainMuted(tabId))
            assertTrue(!browserController.isDomainMuted(selectedTabId))
        }
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Favorite).performClick()
        composeRule.runOnIdle {
            assertEquals(tabId, favoriteTarget.get())
            assertTrue(browserController.isFavorite(backgroundUrl))
            assertEquals(selectedTabId, browserController.selectedTabId)
        }

        composeRule.onNodeWithTag(SnoozeTestTags.overviewTab(tabId))
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.onNodeWithTag(SnoozeTestTags.TabActionsSnooze).assertExists()
        composeRule.onNodeWithTag(SnoozeTestTags.Sheet).assertDoesNotExist()
        composeRule.onNodeWithTag(SnoozeTestTags.TabActionsSnooze).performClick()
        assertEquals(tabId, snoozeTarget.get())
        composeRule.onNodeWithTag(SnoozeTestTags.TabActionsSnooze).assertDoesNotExist()
        composeRule.onNodeWithTag(SnoozeTestTags.Sheet).assertExists()
        composeRule.onNodeWithTag(SnoozeTestTags.Tomorrow).performClick()
        composeRule.onNodeWithTag(SnoozeTestTags.Sheet).assertDoesNotExist()
    }
}
