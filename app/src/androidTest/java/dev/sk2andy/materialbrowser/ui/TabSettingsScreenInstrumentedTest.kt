package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assert
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.sk2andy.materialbrowser.shared.ui.settings.TabOverviewSelectionPage
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.LinkLongPressAction
import dev.sk2andy.materialbrowser.browser.LinkPeekSwipeAction
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TabSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun statusBarWidthLabelsRemainLegibleAtDoubleFontScaleOnCompactPage() {
        var enabled by mutableStateOf(false)
        var width by mutableStateOf(43)
        val saves = mutableListOf<Int>()
        val previews = mutableListOf<Pair<Int, Boolean>>()
        composeRule.setContent {
            MaterialBrowserTheme {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    Box(Modifier.width(320.dp)) {
                        TabsAndGesturesSettingsPage(
                            inactiveTabLifetime = InactiveTabLifetime.Never,
                            residentTabLimit = 10,
                            tabOverviewMode = TabOverviewMode.Grid,
                            tabStackFolderMode = TabOverviewMode.Grid,
                            tabListStartsAtBottom = false,
                            automaticTabSortingEnabled = false,
                            dismissResistancePercent = 40,
                            profilesEnabled = true,
                            isAddressBarDockingEnabled = true,
                            onInactiveTabLifetimeChanged = {},
                            onResidentTabLimitChanged = {},
                            onTabOverviewModeChanged = {},
                            onTabStackFolderModeChanged = {},
                            onTabListStartsAtBottomChanged = {},
                            onAutomaticTabSortingEnabledChanged = {},
                            onDismissResistancePercentChanged = {},
                            onProfilesEnabledChanged = {},
                            onAddressBarDockingEnabledChanged = {},
                            onAddressBarActions = {},
                            section = TabSettingsSection.GestureControls,
                            isStatusBarScrollEnabled = enabled,
                            statusBarScrollTargetWidthPercent = width,
                            onStatusBarScrollEnabledChanged = { enabled = it },
                            onStatusBarScrollTargetWidthPercentChanged = { width = it; saves += it },
                            onStatusBarScrollTargetWidthPreview = { value, finished -> previews += value to finished },
                            onBack = {},
                        )
                    }
                }
            }
        }
        val labels = listOf(
            context.getString(R.string.settings_status_bar_scroll_width_title),
            context.getString(R.string.settings_status_bar_scroll_width_summary, 43),
        )
        labels.forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(label, useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.size)
            val result = layouts.single()
            assertTrue(label, result.lineCount > 0)
            // Full-width stacked labels must wrap by words, never one letter per line.
            assertTrue(label, result.lineCount <= label.split(" ").size)
            assertFalse(label, result.didOverflowHeight)
            (0 until result.lineCount).forEach { line ->
                assertTrue(label, result.getLineLeft(line) >= -1f)
                assertTrue(label, result.getLineRight(line) <= result.size.width + 1f)
                assertTrue(label, result.getLineTop(line) >= -1f)
                assertTrue(label, result.getLineBottom(line) <= result.size.height + 1f)
                assertFalse(label, result.isLineEllipsized(line))
            }
            assertEquals(label.length, result.getLineEnd(result.lineCount - 1))
        }
        composeRule.onNodeWithTag(TabSettingsTestTags.StatusBarScrollWidth)
            .performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        composeRule.runOnIdle {
            assertEquals(43, width)
            assertTrue(saves.isEmpty())
            assertTrue(previews.isEmpty())
        }
    }

    @Test
    fun statusBarWidthRemainsVisibleAndRetainedWhileDisabled() {
        var enabled by mutableStateOf(false)
        var width by mutableStateOf(43)
        val saves = mutableListOf<Int>()
        val previews = mutableListOf<Pair<Int, Boolean>>()
        composeRule.setContent {
            MaterialBrowserTheme {
                TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = TabOverviewMode.Grid,
                    tabStackFolderMode = TabOverviewMode.Grid,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isAddressBarDockingEnabled = true,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabStackFolderModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onAddressBarDockingEnabledChanged = {},
                    onAddressBarActions = {},
                    section = TabSettingsSection.GestureControls,
                    isStatusBarScrollEnabled = enabled,
                    statusBarScrollTargetWidthPercent = width,
                    onStatusBarScrollEnabledChanged = { enabled = it },
                    onStatusBarScrollTargetWidthPercentChanged = { width = it; saves += it },
                    onStatusBarScrollTargetWidthPreview = { value, finished -> previews += value to finished },
                    onBack = {},
                )
            }
        }
        val slider = composeRule.onNodeWithTag(TabSettingsTestTags.StatusBarScrollWidth)
        slider.performScrollTo().assertIsNotEnabled()
            .assertContentDescriptionEquals(context.getString(R.string.settings_status_bar_scroll_width_title))
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(R.string.settings_status_bar_scroll_width_summary, 43),
            ))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        composeRule.runOnIdle {
            assertEquals(43, width)
            assertTrue(saves.isEmpty())
            assertTrue(previews.isEmpty())
        }
        composeRule.onNodeWithTag(TabSettingsTestTags.StatusBarScrollEnabled)
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        slider.performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        composeRule.runOnIdle {
            assertEquals(45, width)
            assertEquals(listOf(45), saves)
            assertEquals(listOf(45 to false, 45 to true), previews)
        }
        composeRule.onNodeWithTag(TabSettingsTestTags.StatusBarScrollEnabled)
            .performScrollTo().performClick()
        slider.performScrollTo().assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(R.string.settings_status_bar_scroll_width_summary, 45),
            ))
    }

    @Test
    fun closedTabUndoSettingReflectsStateAndInvokesCallback() {
        var enabled by mutableStateOf(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                ClosedTabUndoSettingsPage(
                    enabled = enabled,
                    onEnabledChanged = { enabled = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(TabSettingsTestTags.ClosedTabUndo)
            .performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(enabled) }
        composeRule.onNodeWithTag(TabSettingsTestTags.ClosedTabUndo).performClick()
        composeRule.runOnIdle { assertFalse(enabled) }
    }

    @Test
    fun bottomStartIsAvailableForGridAndListOnly() {
        var mode by mutableStateOf(TabOverviewMode.Grid)
        var showOverview by mutableStateOf(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                if (showOverview) TabOverviewSelectionPage(
                    mode = mode,
                    strings = androidTabOverviewSettingsStrings(),
                    backContentDescription = stringResource(R.string.action_back),
                    onBack = { showOverview = false },
                    onModeChanged = { mode = it },
                ) else TabsAndGesturesSettingsPage(
                    onTabOverview = { showOverview = true },
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = mode,
                    tabStackFolderMode = TabOverviewMode.Grid,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isAddressBarDockingEnabled = true,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = { mode = it },
                    onTabStackFolderModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onAddressBarDockingEnabledChanged = {},
                    onAddressBarActions = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(TabSettingsTestTags.ListStartsAtBottom).assertIsEnabled()
        composeRule.onNode(
            hasText(context.getString(R.string.settings_tab_overview_mode)) and
                hasText(context.getString(R.string.tab_overview_mode_grid)),
        )
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.tab_overview_mode_list))
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        composeRule.onNodeWithTag(TabSettingsTestTags.ListStartsAtBottom).assertIsEnabled()
        composeRule.onNodeWithText(context.getString(R.string.tab_overview_mode_list))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.tab_overview_mode_hero))
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        composeRule.onNodeWithTag(TabSettingsTestTags.ListStartsAtBottom).assertIsNotEnabled()
    }

    @Test
    fun actionEditorEntriesInvokeCallbacks() {
        val linkPeekOpened = AtomicBoolean()
        val addressBarLongPressOpened = AtomicBoolean()
        composeRule.setContent {
            MaterialBrowserTheme {
                TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = TabOverviewMode.Grid,
                    tabStackFolderMode = TabOverviewMode.Grid,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isAddressBarDockingEnabled = true,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabStackFolderModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onAddressBarDockingEnabledChanged = {},
                    onAddressBarLongPressActions = { addressBarLongPressOpened.set(true) },
                    onLinkPeekActions = { linkPeekOpened.set(true) },
                    onAddressBarActions = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.settings_link_peek_actions_title))
            .performScrollTo()
            .performClick()

        assertTrue(linkPeekOpened.get())
        composeRule.onNodeWithText(
            context.getString(R.string.settings_address_bar_long_press_title),
        )
            .performScrollTo()
            .performClick()

        assertTrue(addressBarLongPressOpened.get())
    }

    @Test
    fun linkLongPressChoiceShowsAllActionsAndUpdatesSetting() {
        var action by mutableStateOf(LinkLongPressAction.LinkPeek)
        var showChoice by mutableStateOf(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                if (showChoice) LinkLongPressSettingsPage(
                    action = action,
                    onActionChanged = { action = it },
                    onBack = { showChoice = false },
                ) else TabsAndGesturesSettingsPage(
                    onLinkLongPress = { showChoice = true },
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = TabOverviewMode.Grid,
                    tabStackFolderMode = TabOverviewMode.Grid,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isAddressBarDockingEnabled = true,
                    linkLongPressAction = action,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabStackFolderModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onAddressBarDockingEnabledChanged = {},
                    onLinkLongPressActionChanged = { action = it },
                    onAddressBarActions = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(TabSettingsTestTags.LinkLongPressAction)
            .performScrollTo()
            .performClick()

        val actions = listOf(
            LinkLongPressAction.LinkPeek to R.string.link_peek_title,
            LinkLongPressAction.CopyLink to R.string.external_link_preview_copy_link,
            LinkLongPressAction.Share to R.string.action_share,
            LinkLongPressAction.DownloadLink to R.string.action_download_link,
            LinkLongPressAction.OpenInNewTabInBackground to
                R.string.action_open_link_background_tab,
            LinkLongPressAction.OpenInNewTabInForeground to
                R.string.action_open_link_foreground_tab,
            LinkLongPressAction.OpenInPrivateTabInBackground to
                R.string.action_open_link_private_background_tab,
            LinkLongPressAction.OpenInPrivateTabInForeground to
                R.string.action_open_link_private_foreground_tab,
        )
        actions.forEach { (candidate, label) ->
            composeRule.onNode(
                hasText(context.getString(label)) and
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.Selected,
                        candidate == LinkLongPressAction.LinkPeek,
                    ),
            ).assertExists()
        }
        composeRule.onNodeWithText(
            context.getString(R.string.external_link_preview_copy_link),
        ).performScrollTo().performClick()

        assertEquals(LinkLongPressAction.CopyLink, action)
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back))
            .performScrollTo().performClick()
        composeRule.onNodeWithTag(TabSettingsTestTags.LinkPeekSwipeAction)
            .performScrollTo()
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.settings_link_peek_swipe_action))
            .assertIsNotEnabled()
    }

    @Test
    fun linkPeekSwipeChoiceShowsAllActionsAndUpdatesSetting() {
        var action by mutableStateOf(LinkPeekSwipeAction.OpenBackground)
        var showChoice by mutableStateOf(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                if (showChoice) LinkPeekSwipeSettingsPage(
                    action = action,
                    enabled = true,
                    onActionChanged = { action = it },
                    onBack = { showChoice = false },
                ) else TabsAndGesturesSettingsPage(
                    onLinkPeekSwipe = { showChoice = true },
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = TabOverviewMode.Grid,
                    tabStackFolderMode = TabOverviewMode.Grid,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isAddressBarDockingEnabled = true,
                    linkPeekSwipeAction = action,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabStackFolderModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onAddressBarDockingEnabledChanged = {},
                    onLinkPeekSwipeActionChanged = { action = it },
                    onAddressBarActions = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(TabSettingsTestTags.LinkPeekSwipeAction)
            .performScrollTo()
            .performClick()

        val actions = listOf(
            LinkPeekSwipeAction.OpenBackground to R.string.action_open_link_background_tab,
            LinkPeekSwipeAction.OpenForeground to R.string.action_open_link_foreground_tab,
            LinkPeekSwipeAction.OpenNewStack to R.string.action_open_link_new_stack,
            LinkPeekSwipeAction.OpenPrivate to R.string.action_open_link_private_foreground_tab,
            LinkPeekSwipeAction.Copy to R.string.external_link_preview_copy_link,
            LinkPeekSwipeAction.Share to R.string.action_share,
            LinkPeekSwipeAction.Favorite to R.string.action_add_favorite,
            LinkPeekSwipeAction.Snooze to R.string.action_snooze_tab,
            LinkPeekSwipeAction.ReaderLater to R.string.reader_save_offline,
        )
        actions.forEach { (candidate, label) ->
            composeRule.onNode(
                hasText(context.getString(label)) and
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.Selected,
                        candidate == LinkPeekSwipeAction.OpenBackground,
                    ),
            ).assertExists()
        }
        composeRule.onNodeWithText(context.getString(R.string.action_open_link_new_stack))
            .performScrollTo().performClick()

        assertEquals(LinkPeekSwipeAction.OpenNewStack, action)
    }

    @Test
    fun residentTabSliderUpdatesDisplayedLimit() {
        composeRule.setContent {
            MaterialBrowserTheme {
                TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = TabOverviewMode.Grid,
                    tabStackFolderMode = TabOverviewMode.Grid,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isAddressBarDockingEnabled = true,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabStackFolderModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onAddressBarDockingEnabledChanged = {},
                    onAddressBarActions = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.resources.getQuantityString(
                R.plurals.settings_resident_tab_limit_summary,
                10,
                10,
            ),
        ).assertExists()
        composeRule.onNodeWithTag(TabSettingsTestTags.ResidentTabLimit)
            .assertContentDescriptionEquals(context.getString(R.string.settings_resident_tab_limit))
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.resources.getQuantityString(R.plurals.settings_resident_tab_limit_summary, 10, 10),
            ))
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(15f)
            }
        composeRule.onNodeWithText(
            context.resources.getQuantityString(
                R.plurals.settings_resident_tab_limit_summary,
                15,
                15,
            ),
        ).assertExists()
    }

    @Test
    fun tabOrderingOptionsExposeModeAndCallbacks() {
        val listStartsAtBottom = AtomicBoolean()
        val automaticSorting = AtomicBoolean()
        val overviewMode = AtomicReference<TabOverviewMode?>()
        val stackFolderMode = AtomicReference<TabOverviewMode?>()
        var currentOverviewMode by mutableStateOf(TabOverviewMode.Hero)
        var currentStackFolderMode by mutableStateOf(TabOverviewMode.Grid)
        var showOverview by mutableStateOf(false)
        var showFolder by mutableStateOf(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                if (showOverview) TabOverviewSelectionPage(
                    mode = currentOverviewMode,
                    strings = androidTabOverviewSettingsStrings(),
                    backContentDescription = stringResource(R.string.action_back),
                    onBack = { showOverview = false },
                    onModeChanged = { currentOverviewMode = it; overviewMode.set(it) },
                ) else if (showFolder) TabOverviewSelectionPage(
                    mode = currentStackFolderMode,
                    strings = androidTabOverviewSettingsStrings().copy(overviewMode = stringResource(R.string.settings_tab_stack_folder_mode)),
                    backContentDescription = stringResource(R.string.action_back),
                    onBack = { showFolder = false },
                    onModeChanged = { currentStackFolderMode = it; stackFolderMode.set(it) },
                    testTagPrefix = "tab_stack_folder_option",
                ) else TabsAndGesturesSettingsPage(
                    onTabStackFolder = { showFolder = true },
                    onTabOverview = { showOverview = true },
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = currentOverviewMode,
                    tabStackFolderMode = currentStackFolderMode,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isAddressBarDockingEnabled = true,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = { mode ->
                        currentOverviewMode = mode
                        overviewMode.set(mode)
                    },
                    onTabStackFolderModeChanged = { mode ->
                        currentStackFolderMode = mode
                        stackFolderMode.set(mode)
                    },
                    onTabListStartsAtBottomChanged = listStartsAtBottom::set,
                    onAutomaticTabSortingEnabledChanged = automaticSorting::set,
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onAddressBarDockingEnabledChanged = {},
                    onAddressBarActions = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.tab_overview_mode_hero))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.tab_overview_mode_list))
            .assertExists()
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.tab_overview_mode_grid))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.tab_overview_mode_hero))
            .assertExists()
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        composeRule.onNodeWithTag(TabSettingsTestTags.ListStartsAtBottom)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(TabSettingsTestTags.AutomaticSorting)
            .assertIsEnabled()
            .performClick()

        assertTrue(automaticSorting.get())
        assertTrue(listStartsAtBottom.get())
        assertTrue(overviewMode.get() == TabOverviewMode.List)
        assertTrue(stackFolderMode.get() == TabOverviewMode.Hero)
    }

    @Test
    fun addressBarSwipeModeReflectsStateAndInvokesCallback() {
        var enabled by mutableStateOf(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = TabOverviewMode.Grid,
                    tabStackFolderMode = TabOverviewMode.Grid,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isAddressBarDockingEnabled = true,
                    isAddressBarSwipeModeEnabled = enabled,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabStackFolderModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onAddressBarDockingEnabledChanged = {},
                    onAddressBarSwipeModeEnabledChanged = { enabled = it },
                    onAddressBarActions = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(TabSettingsTestTags.AddressBarSwipeMode)
            .performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(enabled) }
        composeRule.onNodeWithTag(TabSettingsTestTags.AddressBarSwipeMode).performClick()
        composeRule.runOnIdle { assertFalse(enabled) }
    }

    @Test
    fun addressBarDockingSwitchCanDisableParking() {
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            MaterialBrowserTheme {
                TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = TabOverviewMode.Grid,
                    tabStackFolderMode = TabOverviewMode.Grid,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isAddressBarDockingEnabled = enabled,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabStackFolderModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onAddressBarDockingEnabledChanged = { enabled = it },
                    onAddressBarActions = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(TabSettingsTestTags.AddressBarDocking)
            .performScrollTo()
            .performClick()

        assertFalse(enabled)
    }
}
