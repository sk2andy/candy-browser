package dev.sk2andy.materialbrowser.ui

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetContentLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetToolbarRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuConfigurationSection
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMenuBottomSheetInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyPageActionsAndControlsShowSettingsHintAndButtonNavigates() {
        val settingsOpens = AtomicInteger()
        showMenu(
            contentLayout = BrowserMenuBottomSheetContentLayout.SwipePages,
            menuLayout = emptySectionLayout(emptyPageActions = true).copy(
                bottomSheetContentLayout = BrowserMenuBottomSheetContentLayout.SwipePages,
            ),
            onOpenMenuSettings = { settingsOpens.incrementAndGet() },
        )
        openMenu()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.browser_menu_page_actions))
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.browser_menu_empty_section_message))
            .assertIsDisplayed()
        captureScreenshot("empty-page-actions-hint")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.browser_menu_empty_section_action))
            .performClick()
        assertEquals(1, settingsOpens.get())
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_menu_actions_title))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back)).performClick()
        composeRule.onNodeWithContentDescription("Open bottom sheet menu").assertIsDisplayed()
    }

    @Test
    fun emptyPageControlsShowSettingsHintAndButtonNavigates() {
        val settingsOpens = AtomicInteger()
        showMenu(
            contentLayout = BrowserMenuBottomSheetContentLayout.SwipePages,
            menuLayout = emptySectionLayout(emptyPageControls = true).copy(
                bottomSheetContentLayout = BrowserMenuBottomSheetContentLayout.SwipePages,
            ),
            onOpenMenuSettings = { settingsOpens.incrementAndGet() },
        )
        openMenu()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.browser_menu_page_controls))
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.browser_menu_empty_section_message))
            .assertIsDisplayed()
        captureScreenshot("empty-page-controls-hint")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.browser_menu_empty_section_action))
            .performClick()
        assertEquals(1, settingsOpens.get())
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_menu_actions_title))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back)).performClick()
        composeRule.onNodeWithContentDescription("Open bottom sheet menu").assertIsDisplayed()
    }

    @Test
    fun bothEmptySectionsRenderOneSettingsHint() {
        val settingsOpens = AtomicInteger()
        showMenu(
            menuLayout = emptySectionLayout(emptyPageActions = true, emptyPageControls = true),
            onOpenMenuSettings = { settingsOpens.incrementAndGet() },
        )
        openMenu()
        composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.browser_menu_empty_section_message))
            .assertCountEquals(1)
        composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.browser_menu_empty_section_action))
            .assertCountEquals(1)
        captureScreenshot("both-empty-sections-hint")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.browser_menu_empty_section_action))
            .performClick()
        assertEquals(1, settingsOpens.get())
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_menu_actions_title))
            .assertIsDisplayed()
    }

    @Test
    fun nonEmptyMenuSectionsRemainUnchanged() {
        showMenu()
        openMenu()
        composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.browser_menu_empty_section_message))
            .assertCountEquals(0)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.browser_menu_page_actions))
            .assertIsDisplayed()
    }

    private fun emptySectionLayout(
        emptyPageActions: Boolean = false,
        emptyPageControls: Boolean = false,
    ): BrowserMenuLayout {
        val toggleEntries = setOf(
            BrowserMenuEntry.CookieBannerRemoval,
            BrowserMenuEntry.ForceVerticalScrolling,
            BrowserMenuEntry.ForcePageZooming,
            BrowserMenuEntry.ForceSafeArea,
            BrowserMenuEntry.AlwaysBlockPopups,
            BrowserMenuEntry.DesktopView,
            BrowserMenuEntry.DomainMute,
        )
        val shortcutEntries = setOf(
            BrowserMenuEntry.OpenDownloads,
            BrowserMenuEntry.Reader,
            BrowserMenuEntry.OpenFavorites,
            BrowserMenuEntry.OpenHistory,
            BrowserMenuEntry.OpenSettings,
        )
        return BrowserMenuLayout(
            locations = BrowserMenuEntry.entries.associateWith { entry ->
                if (
                    (emptyPageActions && entry.section != BrowserMenuConfigurationSection.Toolbar &&
                        entry !in toggleEntries && entry !in shortcutEntries) ||
                    (emptyPageControls && entry in toggleEntries)
                ) BrowserMenuLocation.Nowhere else entry.defaultLocation
            },
        )
    }

    @Test
    fun scrollKeepsAddressShortcutsAndPageActionsFixed() {
        showMenu()
        openMenu()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(R.string.downloads_title)).assertIsDisplayed()
        captureScreenshot("portrait")
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val scrimBounds = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Scrim)
            .fetchSemanticsNode().boundsInRoot
        val sheetBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("scrim covers screen height", device.displayHeight.toFloat(), scrimBounds.height, 1f)
        assertEquals("sheet reaches lower window edge", scrimBounds.bottom, sheetBounds.bottom, 1f)
        val fixedTags = listOf(
            BrowserMenuBottomSheetTestTags.Handle,
            BrowserMenuBottomSheetTestTags.AddressBar,
            BrowserMenuBottomSheetTestTags.Shortcuts,
            BrowserMenuBottomSheetTestTags.Footer,
        )
        val fixedBounds = fixedTags.associateWith { tag ->
            val node = composeRule.onNodeWithTag(tag)
            assertTrue("$tag displayed", node.isDisplayed())
            node.fetchSemanticsNode().boundsInRoot
        }
        val handleBounds = fixedBounds.getValue(BrowserMenuBottomSheetTestTags.Handle)
        val addressBounds = fixedBounds.getValue(BrowserMenuBottomSheetTestTags.AddressBar)
        val shortcutsBounds = fixedBounds.getValue(BrowserMenuBottomSheetTestTags.Shortcuts)
        val footerBounds = fixedBounds.getValue(BrowserMenuBottomSheetTestTags.Footer)
        val contentBounds = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Content)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(handleBounds.bottom <= addressBounds.top + 1f)
        assertTrue(addressBounds.bottom <= shortcutsBounds.top + 1f)
        assertTrue(shortcutsBounds.bottom <= contentBounds.top + 1f)
        assertTrue(contentBounds.bottom <= footerBounds.top + 1f)

        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForceSafeArea)
            .performScrollTo().assertIsDisplayed()

        fixedTags.forEach { tag ->
            val after = composeRule.onNodeWithTag(tag).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val before = fixedBounds.getValue(tag)
            assertEquals("$tag top", before.top, after.top, 1f)
            assertEquals("$tag bottom", before.bottom, after.bottom, 1f)
        }
        listOf(
            BrowserMainMenuTestTags.Downloads,
            BrowserMainMenuTestTags.Favorites,
            BrowserMainMenuTestTags.Settings,
            BrowserMainMenuTestTags.Favorite,
            BrowserMainMenuTestTags.Pin,
        ).forEach { tag -> composeRule.onNodeWithTag(tag).assertIsDisplayed() }
    }

    @Test
    fun footerIconsAlignWithShortcutColumns() {
        showMenu()
        openMenu()
        listOf(
            BrowserMainMenuTestTags.Downloads to hasContentDescription(
                composeRule.activity.getString(R.string.action_back),
            ),
            BrowserMainMenuTestTags.Settings to hasTestTag(BrowserMainMenuTestTags.Pin),
        ).forEach { (shortcutTag, footerMatcher) ->
            val shortcut = composeRule.onNodeWithTag(shortcutTag)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val footer = composeRule.onNode(
                footerMatcher and hasAnyAncestor(hasTestTag(BrowserMenuBottomSheetTestTags.Footer)),
            ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("$shortcutTag column center", shortcut.center.x, footer.center.x, 1f)
        }
    }

    @Test
    fun configuredFooterCommandsRunOnceWithoutDuplicateRowsOrShortcuts() {
        val shares = AtomicInteger()
        val finds = AtomicInteger()
        val newTabs = AtomicInteger()
        val downloads = AtomicInteger()
        showMenu(
            toolbarEntries = listOf(
                BrowserMenuEntry.Share,
                BrowserMenuEntry.FindInPage,
                BrowserMenuEntry.NewTab,
                BrowserMenuEntry.OpenDownloads,
                BrowserMenuEntry.Pin,
            ),
            onShare = { shares.incrementAndGet() },
            onFindInPage = { finds.incrementAndGet() },
            onNewTab = { newTabs.incrementAndGet() },
            onDownloads = { downloads.incrementAndGet() },
        )
        listOf(
            R.string.action_share to shares,
            R.string.action_find_in_page to finds,
            R.string.cd_new_tab to newTabs,
            R.string.downloads_title to downloads,
        ).forEach { (labelResource, count) ->
            openMenu()
            val label = composeRule.activity.getString(labelResource)
            composeRule.onAllNodesWithText(label).assertCountEquals(0)
            composeRule.onAllNodes(
                hasContentDescription(label) and
                    hasAnyAncestor(hasTestTag(BrowserMenuBottomSheetTestTags.Footer)),
            ).assertCountEquals(1)
            if (labelResource == R.string.action_share) captureScreenshot("configured_footer")
            composeRule.onNode(
                hasContentDescription(label) and
                    hasAnyAncestor(hasTestTag(BrowserMenuBottomSheetTestTags.Footer)),
            ).assertIsDisplayed().performClick()
            composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
            assertEquals(1, count.get())
        }
    }

    @Test
    fun configuredFooterToggleKeepsSheetOpenAndReflectsCheckedState() {
        val desktopChanges = AtomicInteger()
        showMenu(
            toolbarEntries = listOf(
                BrowserMenuEntry.Back,
                BrowserMenuEntry.DesktopView,
                BrowserMenuEntry.Reload,
                BrowserMenuEntry.Favorite,
                BrowserMenuEntry.Pin,
            ),
            onDesktopChanged = { desktopChanges.incrementAndGet() },
        )
        openMenu()
        composeRule.onAllNodes(hasTestTag(BrowserMainMenuTestTags.DesktopView)).assertCountEquals(1)
        composeRule.onNode(
            hasTestTag(BrowserMainMenuTestTags.DesktopView) and
                hasAnyAncestor(hasTestTag(BrowserMenuBottomSheetTestTags.Footer)),
        ).assertIsDisplayed().assertIsOff().performClick().assertIsOn()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertIsDisplayed()
        assertEquals(1, desktopChanges.get())
        captureScreenshot("configured_footer_checked")
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DesktopView).performClick().assertIsOff()
        assertEquals(2, desktopChanges.get())
    }

    @Test
    fun emptyFooterSlotsKeepRemainingIconsAlignedWithShortcutColumns() {
        showMenu(
            toolbarEntries = listOf(
                BrowserMenuEntry.Back,
                null,
                BrowserMenuEntry.Reload,
                null,
                BrowserMenuEntry.Pin,
            ),
        )
        openMenu()
        listOf(
            BrowserMainMenuTestTags.Downloads to R.string.action_back,
            BrowserMainMenuTestTags.Favorites to R.string.action_reload,
            BrowserMainMenuTestTags.Settings to R.string.action_pin_tab,
        ).forEach { (shortcutTag, footerLabel) ->
            val shortcut = composeRule.onNodeWithTag(shortcutTag)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val footer = composeRule.onNode(
                hasContentDescription(composeRule.activity.getString(footerLabel)) and
                    hasAnyAncestor(hasTestTag(BrowserMenuBottomSheetTestTags.Footer)),
            ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("$shortcutTag column center", shortcut.center.x, footer.center.x, 1f)
        }
        composeRule.onAllNodes(
            hasContentDescription(composeRule.activity.getString(R.string.action_forward)) and
                hasAnyAncestor(hasTestTag(BrowserMenuBottomSheetTestTags.Footer)),
        ).assertCountEquals(0)
        composeRule.onAllNodes(
            hasTestTag(BrowserMainMenuTestTags.Favorite) and
                hasAnyAncestor(hasTestTag(BrowserMenuBottomSheetTestTags.Footer)),
        ).assertCountEquals(0)
    }

    @Test
    fun configuredTabCommandsRemainAvailableWithoutAddressBarOverflow() {
        val tabs = AtomicInteger()
        val newTabs = AtomicInteger()
        val closes = AtomicInteger()
        showMenu(
            toolbarEntries = listOf(
                BrowserMenuEntry.ShowTabs,
                BrowserMenuEntry.NewTab,
                BrowserMenuEntry.CloseTab,
                null,
                null,
            ),
            onTabs = { tabs.incrementAndGet() },
            onNewTab = { newTabs.incrementAndGet() },
            onCloseTab = { closes.incrementAndGet() },
        )
        listOf(
            R.string.address_bar_action_tabs to tabs,
            R.string.cd_new_tab to newTabs,
            R.string.cd_close_tab to closes,
        ).forEach { (labelResource, count) ->
            openMenu()
            composeRule.onNode(
                hasContentDescription(composeRule.activity.getString(labelResource)) and
                    hasAnyAncestor(hasTestTag(BrowserMenuBottomSheetTestTags.Footer)),
            ).assertIsDisplayed().performClick()
            composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
            assertEquals(1, count.get())
        }
    }

    @Test
    fun swipePagesSeparateActionsAndControlsAndKeepToggleOpen() {
        val desktopChanges = AtomicInteger()
        showMenu(
            contentLayout = BrowserMenuBottomSheetContentLayout.SwipePages,
            onDesktopChanged = { desktopChanges.incrementAndGet() },
        )
        openMenu()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DuplicateTab).assertIsDisplayed()
        val footerBefore = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Footer)
            .fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Pager)
            .performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DesktopView)
            .performScrollTo().assertIsDisplayed().assertIsOff().performClick()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DesktopView).assertIsOn()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertIsDisplayed()
        assertEquals(1, desktopChanges.get())
        val footerAfter = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Footer)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(footerBefore.top, footerAfter.top, 1f)
        assertEquals(footerBefore.bottom, footerAfter.bottom, 1f)

        composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Pager)
            .performTouchInput { swipeRight() }
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DuplicateTab).assertIsDisplayed()
    }

    @Test
    fun duplicateClosesSheetAndReopeningStartsWithActions() {
        val duplicates = AtomicInteger()
        showMenu(
            contentLayout = BrowserMenuBottomSheetContentLayout.SwipePages,
            onDuplicate = { duplicates.incrementAndGet() },
        )
        openMenu()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DuplicateTab).performClick()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
        assertEquals(1, duplicates.get())

        openMenu()
        composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Pager)
            .performTouchInput { swipeLeft() }
        dismissThroughScrim()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
        openMenu()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DuplicateTab).assertIsDisplayed()
    }

    @Test
    fun feedsUnreadChipTracksCountAndOpensDestination() {
        val opens = AtomicInteger()
        val count = mutableStateOf(3)
        showMenu(
            unreadFeedCount = { count.value },
            onOpenFeeds = { opens.incrementAndGet() },
            onOpenLibrary = {},
        )
        openMenu()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Feeds).performScrollTo()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.browser_menu_feed_unread_count, 3))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Library).assertExists()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Feeds).performClick()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
        assertEquals(1, opens.get())

        composeRule.runOnIdle { count.value = 0 }
        openMenu()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Feeds).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.browser_menu_feed_unread_count, 0))
            .assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.browser_menu_feed_unread_count, 3))
            .assertDoesNotExist()
    }

    @Test
    fun downloadsShortcutClosesSheetAndRunsAction() {
        val downloads = AtomicInteger()
        showMenu(onDownloads = { downloads.incrementAndGet() })
        openMenu()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Downloads).performClick()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
        assertEquals(1, downloads.get())
    }

    @Test
    fun systemBackDismissesSheet() {
        showMenu()
        openMenu()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
    }

    @Test
    fun tappingScrimAboveSheetDismissesMenu() {
        showMenu()
        openMenu()
        dismissThroughScrim()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
    }

    @Test
    fun landscapeKeepsHeaderFooterAndSwipeControlsReachable() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val desktopChanges = AtomicInteger()
        try {
            device.setOrientationLeft()
            composeRule.waitUntil(5_000) {
                composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            showMenu(
                contentLayout = BrowserMenuBottomSheetContentLayout.SwipePages,
                onDesktopChanged = { desktopChanges.incrementAndGet() },
            )
            openMenu()
            val addressBounds = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.AddressBar)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val footerBounds = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Footer)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val pagerBounds = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Pager)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(pagerBounds.height > 0f)
            assertTrue(addressBounds.bottom <= pagerBounds.top + 1f)
            assertTrue(pagerBounds.bottom <= footerBounds.top + 1f)
            composeRule.onNodeWithTag(BrowserMainMenuTestTags.Favorite).assertIsDisplayed()
            composeRule.onNodeWithTag(BrowserMainMenuTestTags.Pin).assertIsDisplayed()
            composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Pager)
                .performTouchInput { swipeLeft() }
            composeRule.onNodeWithTag(BrowserMainMenuTestTags.DesktopView)
                .performScrollTo().assertIsDisplayed().performClick().assertIsOn()
            assertEquals(1, desktopChanges.get())
            composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertIsDisplayed()
            captureScreenshot("landscape")
        } finally {
            device.setOrientationNatural()
        }
    }

    @Test
    fun addressActionClosesSheetBeforeOpeningEditor() {
        val addressClicks = AtomicInteger()
        showMenu(onAddressClick = { addressClicks.incrementAndGet() })
        openMenu()
        composeRule.onNodeWithText("example.test").performClick()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
        assertEquals(1, addressClicks.get())
    }

    @Test
    fun heldHandleDragMovesSheetAndShortDragReturnsToExpandedPosition() {
        showMenu(anchoredAddressBar = true)
        openMenu()
        val before = addressBounds()
        val travel = collapsedAddressBounds().top - before.top
        val handle = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Handle)
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, travel * 0.2f), delayMillis = 600L)
        }
        composeRule.waitForIdle()

        val held = addressBounds()
        assertTrue("address follows held drag", held.top - before.top > travel * 0.15f)
        assertTrue("address stops at finger", held.top - before.top < travel * 0.22f)
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertIsDisplayed()
        captureScreenshot("drag_short_held")
        handle.performTouchInput {
            advanceEventTime(200L)
            up()
        }
        composeRule.waitForIdle()

        assertEquals("short drag returns to expanded address position", before.top, addressBounds().top, 1f)
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertIsDisplayed()
    }

    @Test
    fun upwardHandleDragKeepsExpandedSheetPosition() {
        showMenu(anchoredAddressBar = true)
        openMenu()
        val before = addressBounds()
        val handle = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Handle)
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, -160f), delayMillis = 600L)
        }
        composeRule.waitForIdle()
        assertEquals("upward drag cannot move above expanded position", before.top, addressBounds().top, 1f)
        handle.performTouchInput {
            advanceEventTime(200L)
            up()
        }
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertIsDisplayed()
    }

    @Test
    fun draggingNearAddressAnchorFadesSheetKeepsAddressOpaqueAndDismissesOnRelease() {
        showMenu(anchoredAddressBar = true)
        openMenu()
        val before = addressBounds()
        val anchor = collapsedAddressBounds()
        val travel = anchor.top - before.top
        val handle = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Handle)
        val expandedHandleRed = redAtHandleSide()
        val expandedAddressRed = redAtAddressBottom()
        captureScreenshot("drag_expanded")
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, travel * 0.95f), delayMillis = 1_000L)
        }
        composeRule.waitForIdle()

        val held = addressBounds()
        assertTrue("sheet reaches original address anchor", anchor.top - held.top < travel * 0.1f)
        assertTrue("sheet cannot move below original address anchor", held.top <= anchor.top + 1f)
        assertTrue("expanded sheet has opaque surface", expandedHandleRed > 150)
        assertTrue("sheet fades while near original address anchor", redAtHandleSide() < 100)
        assertEquals(
            "address remains opaque during fade",
            expandedAddressRed.toFloat(),
            redAtAddressBottom().toFloat(),
            35f,
        )
        composeRule.onNodeWithText("example.test").assertIsDisplayed()
        captureScreenshot("drag_near_anchor_held")
        handle.performTouchInput {
            advanceEventTime(200L)
            up()
        }
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()

        openMenu()
        assertEquals("reopening resets drag displacement", before.top, addressBounds().top, 1f)
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DuplicateTab).assertIsDisplayed()
    }

    @Test
    fun handleDragPastCollapsedPositionStopsAtOriginalAddressBounds() {
        showMenu(anchoredAddressBar = true)
        openMenu()
        val anchor = collapsedAddressBounds()
        val travel = anchor.top - addressBounds().top
        val handle = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Handle)
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, travel * 1.2f), delayMillis = 1_000L)
        }
        composeRule.waitForIdle()

        val collapsed = addressBounds()
        assertEquals("collapsed address top matches original", anchor.top, collapsed.top, 1f)
        assertEquals("collapsed address left matches original", anchor.left, collapsed.left, 1f)
        assertEquals("collapsed address right matches original", anchor.right, collapsed.right, 1f)
        val scrimBounds = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Scrim)
            .fetchSemanticsNode().boundsInRoot
        val pageColor = screenshotColorAt(scrimBounds.center)
        val handleColor = colorAtHandleSide()
        assertEquals(
            "collapsed handle red matches page",
            AndroidColor.red(pageColor).toFloat(),
            AndroidColor.red(handleColor).toFloat(),
            4f,
        )
        assertEquals(
            "collapsed handle green matches page",
            AndroidColor.green(pageColor).toFloat(),
            AndroidColor.green(handleColor).toFloat(),
            4f,
        )
        assertEquals(
            "collapsed handle blue matches page",
            AndroidColor.blue(pageColor).toFloat(),
            AndroidColor.blue(handleColor).toFloat(),
            4f,
        )
        captureScreenshot("drag_fully_collapsed_held")
        handle.performTouchInput {
            advanceEventTime(200L)
            up()
        }
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
    }

    @Test
    fun systemBackDuringHeldDragStaysDismissedAfterFingerRelease() {
        showMenu(anchoredAddressBar = true)
        openMenu()
        val travel = collapsedAddressBounds().top - addressBounds().top
        val handle = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Handle)
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, travel * 0.2f), delayMillis = 600L)
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        try {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            composeRule.waitForIdle()
            handle.performTouchInput {
                advanceEventTime(200L)
                up()
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()

        openMenu()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DuplicateTab).assertIsDisplayed()
    }

    private fun addressBounds(): Rect =
        composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.AddressBar)
            .fetchSemanticsNode().boundsInRoot

    private fun collapsedAddressBounds(): Rect =
        composeRule.onNodeWithTag("Collapsed bottom sheet address anchor")
            .fetchSemanticsNode().boundsInWindow

    private fun redAtHandleSide(): Int = AndroidColor.red(colorAtHandleSide())

    private fun colorAtHandleSide(): Int {
        val bounds = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Handle)
            .fetchSemanticsNode().boundsInRoot
        return screenshotColorAt(Offset(bounds.left + bounds.width * 0.25f, bounds.center.y))
    }

    private fun redAtAddressBottom(): Int {
        val bounds = addressBounds()
        return screenshotRedAt(Offset(bounds.left + bounds.width * 0.3f, bounds.bottom - 12f))
    }

    private fun screenshotRedAt(position: Offset): Int = AndroidColor.red(screenshotColorAt(position))

    private fun screenshotColorAt(position: Offset): Int {
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        return try {
            bitmap.getPixel(position.x.toInt(), position.y.toInt())
        } finally {
            bitmap.recycle()
        }
    }

    private fun openMenu() {
        composeRule.onNodeWithContentDescription("Open bottom sheet menu").performClick()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertIsDisplayed()
    }

    private fun dismissThroughScrim() {
        val sheetBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu)
            .fetchSemanticsNode().boundsInRoot
        val scrimBounds = composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Scrim)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(sheetBounds.top > scrimBounds.top)
        composeRule.onNodeWithTag(BrowserMenuBottomSheetTestTags.Scrim).performTouchInput {
            click(Offset(center.x, (sheetBounds.top - scrimBounds.top) / 2f))
        }
    }

    private fun captureScreenshot(name: String) {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("screencap -p /sdcard/Download/candy_menu_sheet_$name.png")
    }

    private fun showMenu(
        contentLayout: BrowserMenuBottomSheetContentLayout = BrowserMenuBottomSheetContentLayout.Scroll,
        toolbarEntries: List<BrowserMenuEntry?> = BrowserMenuBottomSheetToolbarRules.DefaultEntries,
        anchoredAddressBar: Boolean = false,
        onDesktopChanged: () -> Unit = {},
        onDuplicate: () -> Unit = {},
        onDownloads: () -> Unit = {},
        onOpenFeeds: (() -> Unit)? = null,
        onOpenLibrary: (() -> Unit)? = null,
        unreadFeedCount: () -> Int = { 0 },
        onShare: () -> Unit = {},
        onFindInPage: () -> Unit = {},
        onTabs: () -> Unit = {},
        onNewTab: () -> Unit = {},
        onCloseTab: () -> Unit = {},
        onAddressClick: () -> Unit = {},
        menuLayout: BrowserMenuLayout? = null,
        onOpenMenuSettings: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialBrowserTheme {
                var expanded by remember { mutableStateOf(false) }
                var desktopView by remember { mutableStateOf(false) }
                var menuSettingsVisible by remember { mutableStateOf(false) }
                var anchorBounds by remember { mutableStateOf<Rect?>(null) }
                Box(Modifier.fillMaxSize().background(Color(0xFF1565C0))) {
                    if (anchoredAddressBar) {
                        Box(
                            Modifier.align(Alignment.BottomCenter).padding(16.dp)
                                .fillMaxWidth().height(68.dp)
                                .onGloballyPositioned { anchorBounds = it.boundsInWindow() }
                                .testTag("Collapsed bottom sheet address anchor"),
                        )
                    }
                    if (menuSettingsVisible) {
                        BrowserMenuSettingsPage(
                            layout = menuLayout ?: BrowserMenuLayout(
                                bottomSheetContentLayout = contentLayout,
                                bottomSheetToolbarEntries = toolbarEntries,
                            ),
                            onLocationChanged = { _, _ -> },
                            onBack = { menuSettingsVisible = false },
                        )
                    } else Box(Modifier.align(Alignment.BottomEnd).size(48.dp)) {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.MoreVert, "Open bottom sheet menu")
                        }
                        BrowserMainMenu(
                            expanded = expanded,
                            backdropSource = null,
                            onDismissRequest = { expanded = false },
                            pageSubtitle = "example.test",
                            canGoBack = true,
                            canGoForward = true,
                            isLoading = false,
                            canToggleFavorite = true,
                            isFavorite = false,
                            isPinned = false,
                            canUsePageActions = true,
                            canOpenReader = true,
                            canTranslatePage = true,
                            canToggleDomainMute = true,
                            isDomainMuted = false,
                            canToggleAlwaysBlockPopups = true,
                            isAlwaysBlockPopupsEnabled = false,
                            canToggleDesktopView = true,
                            isDesktopView = desktopView,
                            canToggleCookieBannerRemoval = true,
                            isCookieBannerRemovalEnabled = false,
                            canToggleForceVerticalScrolling = true,
                            isForceVerticalScrollingEnabled = false,
                            canToggleForcePageZooming = true,
                            isForcePageZoomingEnabled = false,
                            canToggleForceSafeArea = true,
                            isForceSafeAreaEnabled = false,
                            canAddSiteCapsule = true,
                            canSnooze = true,
                            snoozedTabCount = 0,
                            onBack = {},
                            onForward = {},
                            onReloadOrStop = {},
                            onToggleFavorite = {},
                            onTogglePinned = {},
                            onShare = onShare,
                            onFindInPage = onFindInPage,
                            onTabs = onTabs,
                            onNewTab = onNewTab,
                            onCloseTab = onCloseTab,
                            onOpenExternal = {},
                            onPrint = {},
                            onOpenReader = {},
                            onOpenFeeds = onOpenFeeds,
                            onOpenLibrary = onOpenLibrary,
                            unreadFeedCount = unreadFeedCount(),
                            onTranslate = {},
                            onDomainMutedChange = {},
                            onAlwaysBlockPopupsChange = {},
                            onDesktopViewChange = { enabled ->
                                desktopView = enabled
                                onDesktopChanged()
                            },
                            onCookieBannerRemovalEnabledChange = {},
                            onForceVerticalScrollingChange = {},
                            onForcePageZoomingChange = {},
                            onForceSafeAreaChange = {},
                            onOpenCandyTrail = {},
                            onAddSiteCapsule = {},
                            onSummarize = {},
                            onSnooze = {},
                            onSnoozedTabs = {},
                            onDuplicateTab = onDuplicate,
                            onDockAddressBar = {},
                            onDownloads = onDownloads,
                            onHistory = {},
                            menuLayout = menuLayout ?: BrowserMenuLayout(
                                bottomSheetContentLayout = contentLayout,
                                bottomSheetToolbarEntries = toolbarEntries,
                            ),
                            bottomSheetAddressBar = { commit ->
                                BrowserMenuSheetAddressBar(
                                    displayText = "example.test",
                                    actionLayout = AddressBarActionLayout.Default,
                                    actionState = AddressBarActionState(
                                        tabCount = 2,
                                        isLoading = false,
                                        canGoBack = true,
                                        canGoForward = true,
                                        canToggleFavorite = true,
                                        isFavorite = false,
                                        isPinned = false,
                                        canToggleDesktopView = true,
                                        isDesktopView = desktopView,
                                        canToggleForceVerticalScrolling = true,
                                        isForceVerticalScrollingEnabled = false,
                                        canUsePageActions = true,
                                        canOpenReader = true,
                                        canCloseTab = true,
                                        canParkRight = true,
                                        newTabPulseScale = 1f,
                                    ),
                                    actionCallbacks = AddressBarActionCallbacks(
                                        onTabs = {},
                                        onToggleFavorite = {},
                                        onTogglePinned = {},
                                        onDesktopViewChange = {},
                                        onForceVerticalScrollingChange = {},
                                        onReaderStudio = {},
                                        onFindInPage = {},
                                        onShare = {},
                                        onPrint = {},
                                        onNewTab = {},
                                        onHome = {},
                                        onReloadOrStop = {},
                                        onCloseTab = {},
                                        onBack = {},
                                        onForward = {},
                                        onParkRight = {},
                                        onNewTabButtonBounds = {},
                                    ),
                                    commit = commit,
                                    onAddress = onAddressClick,
                                    onDismissRequest = { expanded = false },
                                    connectionContent = {},
                                )
                            },
                            bottomSheetAnchorBounds = anchorBounds,
                            onSettings = {},
                            onOpenMenuSettings = {
                                menuSettingsVisible = true
                                onOpenMenuSettings()
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
