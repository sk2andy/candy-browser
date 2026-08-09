package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
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
        val snoozedTabOpens = AtomicInteger()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialBrowserTheme {
                var expanded by remember { mutableStateOf(true) }
                Box {
                    BrowserMainMenu(
                        expanded = expanded,
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
                        onOpenCandyTrail = {},
                        onAddSiteCapsule = {},
                        onSummarize = {},
                        onSnooze = {},
                        onSnoozedTabs = snoozedTabOpens::incrementAndGet,
                        onSettings = {},
                    )
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
                hasAnyDescendant(hasText(context.getString(R.string.action_settings))),
        ).assertExists()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.BrowserGroup)
            .onChildren()
            .assertCountEquals(2)
        composeRule.onNodeWithText(
            context.getString(R.string.snoozed_tabs_title),
        ).performClick()

        assertEquals(1, dismissals.get())
        assertEquals(1, snoozedTabOpens.get())
        composeRule.mainClock.advanceTimeBy(
            BrowserMainMenuMotion.EXIT_DURATION_MILLIS.toLong() / 2L,
        )
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertExists()
        assertEquals(1, snoozedTabOpens.get())
        composeRule.mainClock.advanceTimeBy(
            BrowserMainMenuMotion.EXIT_DURATION_MILLIS.toLong() / 2L + 32L,
        )
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
        assertEquals(1, snoozedTabOpens.get())
    }

    @Test
    fun disablesReaderForUnsupportedPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserMainMenu(
                    expanded = true,
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
                    onOpenCandyTrail = {},
                    onAddSiteCapsule = {},
                    onSummarize = {},
                    onSnooze = {},
                    onSnoozedTabs = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.reader_open_action),
        ).assertIsNotEnabled()
    }
}
