package dev.sk2andy.materialbrowser.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.data.SnoozedTab
import dev.sk2andy.materialbrowser.data.TabOverviewMode
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
        composeRule.mainClock.autoAdvance = true
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
                SnoozeTabDialog(
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

        composeRule.onNode(isDialog()).assertExists()
        composeRule.onNodeWithTag(SnoozeTestTags.PresetGroup).assertIsDisplayed()
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
                SnoozeTabDialog(
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
            val configuration = LocalConfiguration.current
            val shortConfiguration = remember(configuration) {
                Configuration(configuration).apply { screenHeightDp = 600 }
            }
            CompositionLocalProvider(LocalConfiguration provides shortConfiguration) {
                MaterialBrowserTheme {
                    TestTabActionsMenu(
                        tab = BrowserTab("tab", 1L, title = "Example"),
                        onSnooze = { snoozeCalls.incrementAndGet() },
                    )
                }
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
            .assertIsNotDisplayed()
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        assertEquals(1, snoozeCalls.get())
    }

    @Test
    fun tabActionsPinFitsAndTogglesOnNarrowScreens() {
        val pinCalls = AtomicInteger()
        composeRule.setContent {
            val configuration = LocalConfiguration.current
            val narrowConfiguration = remember(configuration) {
                Configuration(configuration).apply { screenWidthDp = 320 }
            }
            CompositionLocalProvider(LocalConfiguration provides narrowConfiguration) {
                MaterialBrowserTheme {
                    TestTabActionsMenu(
                        tab = BrowserTab("tab", 1L, title = "Example"),
                        onTogglePinned = { pinCalls.incrementAndGet() },
                    )
                }
            }
        }

        val menuBounds = composeRule
            .onNodeWithTag(SnoozeTestTags.TabActions)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val favoriteBounds = composeRule
            .onNodeWithTag(TabActionsMenuTestTags.Favorite)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val pinNode = composeRule
            .onNodeWithTag(TabActionsMenuTestTags.Pin)
            .assertIsDisplayed()
        val pinBounds = pinNode.fetchSemanticsNode().boundsInRoot

        assertTrue(favoriteBounds.left >= menuBounds.left)
        assertTrue(pinBounds.right <= menuBounds.right)
        assertTrue(pinBounds.left >= favoriteBounds.right)
        pinNode.performClick()
        assertEquals(1, pinCalls.get())
    }

    @Test
    fun tabActionsMenuKeepsOutgoingContentUntilExitMotionCompletes() {
        val menuTab = mutableStateOf<BrowserTab?>(null)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialBrowserTheme {
                TestTabActionsMenu(tab = menuTab.value)
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.runOnIdle {
            menuTab.value = BrowserTab("tab", 1L, title = "Example")
        }
        composeRule.mainClock.advanceTimeBy(
            TabActionsMenuMotion.ENTER_DURATION_MILLIS.toLong() + 32L,
        )
        composeRule.onNodeWithTag(SnoozeTestTags.TabActions).assertExists()

        composeRule.runOnIdle { menuTab.value = null }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(SnoozeTestTags.TabActions).assertExists()

        composeRule.mainClock.advanceTimeBy(
            TabActionsMenuMotion.EXIT_DURATION_MILLIS.toLong() + 32L,
        )
        composeRule.onNodeWithTag(SnoozeTestTags.TabActions).assertDoesNotExist()
    }

    @Test
    fun favoriteActionKeepsPresentedStateDuringVisibleExit() {
        val menuTab = mutableStateOf<BrowserTab?>(
            BrowserTab("tab", 1L, title = "Example", url = "https://example.com"),
        )
        val isFavorite = mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialBrowserTheme {
                TestTabActionsMenu(
                    tab = menuTab.value,
                    isFavorite = isFavorite.value,
                    onToggleFavorite = {
                        menuTab.value = null
                        isFavorite.value = true
                    },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(
            TabActionsMenuMotion.ENTER_DURATION_MILLIS.toLong() + 32L,
        )

        composeRule.onNodeWithTag(TabActionsMenuTestTags.Favorite)
            .assertIsNotSelected()
            .performClick()
        composeRule.mainClock.advanceTimeBy(
            TabActionsMenuMotion.EXIT_DURATION_MILLIS.toLong() / 2L,
        )

        composeRule.onNodeWithTag(SnoozeTestTags.TabActions).assertExists()
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Favorite).assertIsNotSelected()
        composeRule.runOnIdle { assertTrue(isFavorite.value) }

        composeRule.mainClock.advanceTimeBy(
            TabActionsMenuMotion.EXIT_DURATION_MILLIS.toLong() / 2L + 32L,
        )
        composeRule.onNodeWithTag(SnoozeTestTags.TabActions).assertDoesNotExist()
    }

    @Test
    fun heroOverflowOpensActionsAboveChromeBeforeSnoozePicker() {
        verifyOverflowActionsFlow(TabOverviewMode.Hero)
    }

    @Test
    fun gridOverflowOpensActionsAboveChromeBeforeSnoozePicker() {
        verifyOverflowActionsFlow(TabOverviewMode.Grid)
    }

    @Test
    fun listOverflowOpensActionsAboveChromeBeforeSnoozePicker() {
        verifyOverflowActionsFlow(TabOverviewMode.List)
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

    private fun verifyOverflowActionsFlow(mode: TabOverviewMode) {
        lateinit var browserController: BrowserController
        lateinit var tabId: String
        val favoriteTarget = AtomicReference<String?>(null)
        val snoozeTarget = AtomicReference<String?>(null)
        val backgroundUrl = "https://background-${mode.name.lowercase()}.com"
        composeRule.runOnIdle {
            browserController = BrowserController(composeRule.activity)
            browserController.updateTabOverviewMode(mode)
            controller = browserController
            tabId = requireNotNull(browserController.createBackgroundTab(backgroundUrl))
            browserController.selectTab(tabId)
            browserController.setDomainMuted(tabId, false)
            if (browserController.isFavorite(backgroundUrl)) {
                browserController.toggleFavorite(tabId)
            }
        }
        composeRule.setContent {
            var snoozeTabId by remember { mutableStateOf<String?>(null) }
            val bottomBarTop = remember { mutableFloatStateOf(2_000f) }
            MaterialBrowserTheme(
                settings = AppearanceSettings(surfaceStyle = BrowserSurfaceStyle.Frosted),
            ) {
                Box {
                    TabOverview(
                        controller = browserController,
                        visible = true,
                        bottomBarTopPx = bottomBarTop,
                        onClose = {},
                        onSelect = {},
                        onNewTab = {},
                        onOpenSettings = {},
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
                    SnoozeTabDialog(
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
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        val chromeBounds = composeRule.onNodeWithTag(TabOverviewChromeTestTags.Bar)
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag(TabOverviewChromeTestTags.More).performClick()
        composeRule.onNodeWithTag(BrowserChromeSurfaceTestTags.BackdropBlur)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Favorite).assertExists()
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Pin).assertExists()
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Trail).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.action_fork_tab))
            .assertDoesNotExist()
        val menuBounds = composeRule.onNodeWithTag(SnoozeTestTags.TabActions)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(menuBounds.bottom <= chromeBounds.top)
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Pin).performClick()
        composeRule.runOnIdle {
            assertTrue(browserController.activeTabs.first { it.id == tabId }.isPinned)
        }
        composeRule.onNodeWithTag(TabOverviewChromeTestTags.More).performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_remove_pin)).assertExists()
        composeRule.onNodeWithTag(DomainMuteMenuTestTags.Item).performClick()
        composeRule.runOnIdle {
            assertTrue(browserController.isDomainMuted(tabId))
        }
        composeRule.onNodeWithTag(TabActionsMenuTestTags.Favorite).performClick()
        composeRule.runOnIdle {
            assertEquals(tabId, favoriteTarget.get())
            assertTrue(browserController.isFavorite(backgroundUrl))
            assertEquals(tabId, browserController.selectedTabId)
        }

        composeRule.onNodeWithTag(TabOverviewChromeTestTags.More).performClick()
        composeRule.onNodeWithTag(SnoozeTestTags.TabActionsSnooze).assertExists()
        composeRule.onNodeWithTag(SnoozeTestTags.Dialog).assertDoesNotExist()
        composeRule.onNodeWithTag(SnoozeTestTags.TabActionsSnooze).performClick()
        assertEquals(tabId, snoozeTarget.get())
        composeRule.onNodeWithTag(SnoozeTestTags.TabActionsSnooze).assertDoesNotExist()
        composeRule.onNodeWithTag(SnoozeTestTags.Dialog).assertExists()
        composeRule.onNodeWithTag(SnoozeTestTags.Tomorrow).performClick()
        composeRule.onNodeWithTag(SnoozeTestTags.Dialog).assertDoesNotExist()
    }

    @Composable
    private fun TestTabActionsMenu(
        tab: BrowserTab?,
        isFavorite: Boolean = false,
        onToggleFavorite: () -> Unit = {},
        onTogglePinned: () -> Unit = {},
        onSnooze: () -> Unit = {},
    ) {
        TabActionsFloatingMenu(
            tab = tab,
            profiles = listOf(BrowserProfile("default", "🍬")),
            isFavorite = isFavorite,
            canToggleDomainMute = false,
            isDomainMuted = false,
            onToggleFavorite = onToggleFavorite,
            onOpenCandyTrail = {},
            onTogglePinned = onTogglePinned,
            onMoveToProfile = {},
            onShare = {},
            onOpenExternal = {},
            onPrint = {},
            onDomainMutedChange = {},
            onAddSiteCapsule = {},
            onSummarize = {},
            onSnooze = onSnooze,
            onDismiss = {},
        )
    }
}
