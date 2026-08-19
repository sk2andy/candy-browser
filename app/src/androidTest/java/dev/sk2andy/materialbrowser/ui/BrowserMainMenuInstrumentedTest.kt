package dev.sk2andy.materialbrowser.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMainMenuInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun usesApprovedGroupsAndDismissesAfterAction() {
        val dismissals = AtomicInteger()
        val dockActions = AtomicInteger()
        val cookieChanges = AtomicInteger()
        val scrollChanges = AtomicInteger()
        val desktopViewChanges = AtomicInteger()
        val zoomChanges = AtomicInteger()
        val safeAreaChanges = AtomicInteger()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val configuration = LocalConfiguration.current
            val shortConfiguration = remember(configuration) {
                Configuration(configuration).apply { screenHeightDp = 450 }
            }
            CompositionLocalProvider(LocalConfiguration provides shortConfiguration) {
                MaterialBrowserTheme {
                    var expanded by remember { mutableStateOf(true) }
                    var cookieRemovalEnabled by remember { mutableStateOf(false) }
                    var forceVerticalScrolling by remember { mutableStateOf(false) }
                    var desktopView by remember { mutableStateOf(false) }
                    var forcePageZooming by remember { mutableStateOf(false) }
                    var forceSafeArea by remember { mutableStateOf(false) }
                    Box {
                        BrowserMainMenu(
                            expanded = expanded,
                            blurTarget = null,
                            onDismissRequest = {
                                if (expanded) dismissals.incrementAndGet()
                                expanded = false
                            },
                            pageSubtitle = "developer.android.com",
                            canGoBack = false,
                            canGoForward = false,
                            isLoading = false,
                            canToggleFavorite = true,
                            isFavorite = false,
                            canUsePageActions = true,
                            canOpenReader = true,
                            canToggleDomainMute = true,
                            isDomainMuted = false,
                            canToggleDesktopView = true,
                            isDesktopView = desktopView,
                            canToggleCookieBannerRemoval = true,
                            isCookieBannerRemovalEnabled = cookieRemovalEnabled,
                            canToggleForceVerticalScrolling = true,
                            isForceVerticalScrollingEnabled = forceVerticalScrolling,
                            canToggleForcePageZooming = true,
                            isForcePageZoomingEnabled = forcePageZooming,
                            canToggleForceSafeArea = true,
                            isForceSafeAreaEnabled = forceSafeArea,
                            canAddSiteCapsule = true,
                            canSnooze = true,
                            snoozedTabCount = 2,
                            onBack = {},
                            onForward = {},
                            onReloadOrStop = {},
                            onToggleFavorite = {},
                            onShare = {},
                            onOpenExternal = {},
                            onPrint = {},
                            onOpenReader = {},
                            onDomainMutedChange = {},
                            onDesktopViewChange = { enabled ->
                                desktopViewChanges.incrementAndGet()
                                desktopView = enabled
                            },
                            onCookieBannerRemovalEnabledChange = { enabled ->
                                cookieChanges.incrementAndGet()
                                cookieRemovalEnabled = enabled
                            },
                            onForceVerticalScrollingChange = { enabled ->
                                scrollChanges.incrementAndGet()
                                forceVerticalScrolling = enabled
                            },
                            onForcePageZoomingChange = { enabled ->
                                zoomChanges.incrementAndGet()
                                forcePageZooming = enabled
                            },
                            onForceSafeAreaChange = { enabled ->
                                safeAreaChanges.incrementAndGet()
                                forceSafeArea = enabled
                            },
                            onOpenCandyTrail = {},
                            onAddSiteCapsule = {},
                            onSummarize = {},
                            onSnooze = {},
                            onSnoozedTabs = {},
                            onDockAddressBar = dockActions::incrementAndGet,
                            onSettings = {},
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(200L)

        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertExists()
        val pageGroup = hasTestTag(BrowserMainMenuTestTags.PageGroup)
        composeRule.onNode(
            pageGroup and
                hasAnyDescendant(hasText(context.getString(R.string.reader_open_action))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_share))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_open_in_app))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_print))) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.CookieBannerRemoval)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.ForceVerticalScrolling)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.DesktopView)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.ForcePageZooming)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.ForceSafeArea)) and
                hasAnyDescendant(hasTestTag(DomainMuteMenuTestTags.Item)),
        ).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.action_mute_domain)).assertExists()
        val candyGroup = hasTestTag(BrowserMainMenuTestTags.CandyGroup)
        composeRule.onNode(
            candyGroup and
                hasAnyDescendant(hasText(context.getString(R.string.action_open_candy_trail))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_add_site_capsule))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_summarize))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_snooze_tab))),
        ).assertExists()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.CandyGroup)
            .onChildren()
            .assertCountEquals(4)
        composeRule.onNodeWithText(
            context.getString(R.string.browser_menu_browser_group),
        ).assertExists()
        composeRule.onNode(
            hasTestTag(BrowserMainMenuTestTags.BrowserGroup) and
                hasAnyDescendant(hasText(context.getString(R.string.snoozed_tabs_title))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_dock_address_bar))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_settings))),
        ).assertExists()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.BrowserGroup)
            .onChildren()
            .assertCountEquals(3)

        val menuHeight = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu)
            .fetchSemanticsNode().boundsInRoot.height
        val maximumMenuHeight = 450f * context.resources.displayMetrics.density * 0.8f
        assertTrue(menuHeight <= maximumMenuHeight + 1f)
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Settings)
            .assertIsNotDisplayed()
        repeat(3) {
            composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu)
                .performTouchInput {
                    swipe(
                        start = center + Offset(0f, 150f),
                        end = center + Offset(0f, -150f),
                        durationMillis = 1_000L,
                    )
                }
        }

        composeRule.onNodeWithTag(BrowserMainMenuTestTags.CookieBannerRemoval)
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.CookieBannerRemoval).assertIsOn()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForceVerticalScrolling)
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForceVerticalScrolling).assertIsOn()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DesktopView)
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DesktopView).assertIsOn()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForcePageZooming)
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForcePageZooming).assertIsOn()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForceSafeArea)
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForceSafeArea).assertIsOn()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertExists()
        assertEquals(1, cookieChanges.get())
        assertEquals(1, scrollChanges.get())
        assertEquals(1, desktopViewChanges.get())
        assertEquals(1, zoomChanges.get())
        assertEquals(1, safeAreaChanges.get())

        repeat(3) {
            composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu)
                .performTouchInput { swipeUp() }
        }
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Settings).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DockAddressBar)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, dismissals.get())
        assertEquals(1, dockActions.get())
        composeRule.mainClock.advanceTimeBy(
            BrowserMainMenuMotion.EXIT_DURATION_MILLIS.toLong() / 2L,
        )
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertExists()
        assertEquals(1, dockActions.get())
        composeRule.mainClock.advanceTimeBy(
            BrowserMainMenuMotion.EXIT_DURATION_MILLIS.toLong() / 2L + 32L,
        )
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
        assertEquals(1, dockActions.get())
    }

    @Test
    fun disablesReaderForUnsupportedPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserMainMenu(
                    expanded = true,
                    blurTarget = null,
                    onDismissRequest = {},
                    pageSubtitle = "New tab",
                    canGoBack = false,
                    canGoForward = false,
                    isLoading = false,
                    canToggleFavorite = false,
                    isFavorite = false,
                    canUsePageActions = false,
                    canOpenReader = false,
                    canToggleDomainMute = false,
                    isDomainMuted = false,
                    canToggleDesktopView = false,
                    isDesktopView = false,
                    canToggleCookieBannerRemoval = false,
                    isCookieBannerRemovalEnabled = false,
                    canToggleForceVerticalScrolling = false,
                    isForceVerticalScrollingEnabled = false,
                    canToggleForcePageZooming = false,
                    isForcePageZoomingEnabled = false,
                    canToggleForceSafeArea = false,
                    isForceSafeAreaEnabled = false,
                    canAddSiteCapsule = false,
                    canSnooze = false,
                    snoozedTabCount = 0,
                    onBack = {},
                    onForward = {},
                    onReloadOrStop = {},
                    onToggleFavorite = {},
                    onShare = {},
                    onOpenExternal = {},
                    onPrint = {},
                    onOpenReader = {},
                    onDomainMutedChange = {},
                    onDesktopViewChange = {},
                    onCookieBannerRemovalEnabledChange = {},
                    onForceVerticalScrollingChange = {},
                    onForcePageZoomingChange = {},
                    onForceSafeAreaChange = {},
                    onOpenCandyTrail = {},
                    onAddSiteCapsule = {},
                    onSummarize = {},
                    onSnooze = {},
                    onSnoozedTabs = {},
                    onDockAddressBar = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.reader_open_action),
        ).assertIsNotEnabled()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.CookieBannerRemoval)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForceVerticalScrolling)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DesktopView)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForcePageZooming)
            .assertDoesNotExist()
    }
}
