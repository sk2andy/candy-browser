package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.ui.settings.settingsSearchTarget
import dev.sk2andy.materialbrowser.browser.BrowserSessionResidencyRules
import dev.sk2andy.materialbrowser.browser.LinkLongPressAction
import dev.sk2andy.materialbrowser.browser.LinkPeekSwipeAction
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.browser.StatusBarScrollRules
import dev.sk2andy.materialbrowser.shared.browser.AddressBarLongPressAction
import dev.sk2andy.materialbrowser.shared.ui.settings.TabDismissResistanceSettings
import dev.sk2andy.materialbrowser.shared.ui.settings.TabOverviewSettings
import dev.sk2andy.materialbrowser.shared.ui.settings.TabOverviewSettingsStrings
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import kotlin.math.roundToInt

internal object TabSettingsTestTags {
    const val StackFolderMode = "tab_settings_stack_folder_mode"
    const val ResidentTabLimit = "tab_settings_resident_limit"
    const val ListStartsAtBottom = "tab_settings_list_starts_at_bottom"
    const val AutomaticSorting = "tab_settings_automatic_sorting"
    const val ClosedTabUndo = "tab_settings_closed_tab_undo"
    const val AddressBarDocking = "tab_settings_address_bar_docking"
    const val AddressBarSwipeMode = "tab_settings_address_bar_swipe_mode"
    const val AddressBarLongPressAction = "tab_settings_address_bar_long_press_action"
    const val LinkLongPressAction = "tab_settings_link_long_press_action"
    const val LinkPeekSwipeAction = "tab_settings_link_peek_swipe_action"
    const val StatusBarScrollEnabled = "tab_settings_status_bar_scroll_enabled"
    const val StatusBarScrollWidth = "tab_settings_status_bar_scroll_width"
}

internal enum class TabSettingsSection {
    All,
    Tabs,
    Gestures,
}

@Composable
internal fun TabsAndGesturesSettingsPage(
    inactiveTabLifetime: InactiveTabLifetime,
    residentTabLimit: Int,
    tabOverviewMode: TabOverviewMode,
    tabStackFolderMode: TabOverviewMode,
    tabListStartsAtBottom: Boolean,
    automaticTabSortingEnabled: Boolean,
    isClosedTabUndoEnabled: Boolean = false,
    dismissResistancePercent: Int,
    profilesEnabled: Boolean,
    isAddressBarDockingEnabled: Boolean,
    isAddressBarSwipeModeEnabled: Boolean = false,
    addressBarLongPressAction: AddressBarLongPressAction = AddressBarLongPressAction.Default,
    linkLongPressAction: LinkLongPressAction = LinkLongPressAction.LinkPeek,
    linkPeekSwipeAction: LinkPeekSwipeAction = LinkPeekSwipeAction.OpenBackground,
    isStatusBarScrollEnabled: Boolean = false,
    statusBarScrollTargetWidthPercent: Int = StatusBarScrollRules.DEFAULT_TARGET_WIDTH_PERCENT,
    onInactiveTabLifetimeChanged: (InactiveTabLifetime) -> Unit,
    onResidentTabLimitChanged: (Int) -> Unit,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    onTabStackFolderModeChanged: (TabOverviewMode) -> Unit,
    onTabListStartsAtBottomChanged: (Boolean) -> Unit,
    onAutomaticTabSortingEnabledChanged: (Boolean) -> Unit,
    onClosedTabUndoEnabledChanged: (Boolean) -> Unit = {},
    onDismissResistancePercentChanged: (Int) -> Unit,
    onProfilesEnabledChanged: (Boolean) -> Unit,
    onAddressBarDockingEnabledChanged: (Boolean) -> Unit,
    onAddressBarSwipeModeEnabledChanged: (Boolean) -> Unit = {},
    onAddressBarLongPressActions: () -> Unit = {},
    onLinkLongPressActionChanged: (LinkLongPressAction) -> Unit = {},
    onLinkPeekSwipeActionChanged: (LinkPeekSwipeAction) -> Unit = {},
    onStatusBarScrollEnabledChanged: (Boolean) -> Unit = {},
    onStatusBarScrollTargetWidthPercentChanged: (Int) -> Unit = {},
    onStatusBarScrollTargetWidthPreview: (widthPercent: Int, finished: Boolean) -> Unit = { _, _ -> },
    onOpenStatusBarScrollAccessibilitySettings: () -> Unit = {},
    onLinkPeekActions: () -> Unit = {},
    onAddressBarActions: () -> Unit,
    onMenuActions: () -> Unit = {},
    onTabOverview: () -> Unit = {},
    onTabStackFolder: () -> Unit = {},
    onClosedTabUndo: () -> Unit = {},
    onLinkLongPress: () -> Unit = {},
    onLinkPeekSwipe: () -> Unit = {},
    onBack: () -> Unit,
    section: TabSettingsSection = TabSettingsSection.All,
) {
    var lifetimeMenuExpanded by remember { mutableStateOf(false) }
    var residentLimit by remember(residentTabLimit) {
        mutableFloatStateOf(residentTabLimit.toFloat())
    }
    var statusBarScrollWidth by remember(statusBarScrollTargetWidthPercent) {
        mutableFloatStateOf(statusBarScrollTargetWidthPercent.toFloat())
    }
    var showStatusBarScrollAccessibilityDialog by remember { mutableStateOf(false) }
    SettingsPage(
        title = stringResource(
            when (section) {
                TabSettingsSection.All -> R.string.settings_tabs_gestures_title
                TabSettingsSection.Tabs -> R.string.settings_home_tabs_title
                TabSettingsSection.Gestures -> R.string.settings_home_gestures_title
            },
        ),
        onBack = onBack,
    ) {
        if (section != TabSettingsSection.Gestures) {
            SettingsSectionTitle(stringResource(R.string.settings_tab_overview_mode))
            SettingsPreferenceGroup {
                TabOverviewSettings(
                    mode = tabOverviewMode,
                    listStartsAtBottom = tabListStartsAtBottom,
                    strings = androidTabOverviewSettingsStrings(),
                    containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
                    onOpenMode = onTabOverview,
                    onListStartsAtBottomChanged = onTabListStartsAtBottomChanged,
                    listStartsAtBottomModifier = Modifier.testTag(
                        TabSettingsTestTags.ListStartsAtBottom,
                    ),
                )
                SettingsPreferenceDivider()
                dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSelectionLink(
                    title = stringResource(R.string.settings_tab_stack_folder_mode),
                    value = tabStackFolderMode.displayName(), onClick = onTabStackFolder, enabled = true,
                    modifier = Modifier.testTag(TabSettingsTestTags.StackFolderMode),
                )
                SettingsPreferenceDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_automatic_tab_sorting_title),
                    subtitle = stringResource(R.string.settings_automatic_tab_sorting_subtitle),
                    checked = automaticTabSortingEnabled,
                    onCheckedChange = onAutomaticTabSortingEnabledChanged,
                    modifier = Modifier.testTag(TabSettingsTestTags.AutomaticSorting),
                )
            }
            SettingsPageSpacer()
            SettingsSectionTitle(stringResource(R.string.settings_group_tab_lifecycle))
            SettingsPreferenceGroup {
                dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSelectionLink(
                    title = stringResource(R.string.settings_closed_tab_undo_title),
                    value = stringResource(R.string.settings_closed_tab_undo_summary),
                    onClick = onClosedTabUndo,
                    modifier = Modifier.testTag(TabSettingsTestTags.ClosedTabUndo),
                )
                SettingsPageSpacer()
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .settingsSearchTarget(stringResource(R.string.settings_resident_tab_limit)),
                    shape = dev.sk2andy.materialbrowser.shared.ui.settings.settingsRowShape(),
                    color = dev.sk2andy.materialbrowser.shared.ui.settings.settingsRowColor(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        val residentTitle = stringResource(R.string.settings_resident_tab_limit)
                        val residentValue = pluralStringResource(
                            R.plurals.settings_resident_tab_limit_summary,
                            residentLimit.roundToInt(),
                            residentLimit.roundToInt(),
                        )
                        Text(
                            residentTitle,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            residentValue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = residentLimit,
                            onValueChange = { residentLimit = it },
                            onValueChangeFinished = {
                                onResidentTabLimitChanged(residentLimit.roundToInt())
                            },
                            modifier = Modifier.testTag(TabSettingsTestTags.ResidentTabLimit).semantics {
                                contentDescription = residentTitle
                                stateDescription = residentValue
                            },
                            valueRange = BrowserSessionResidencyRules.MIN_LIMIT.toFloat()..
                                BrowserSessionResidencyRules.MAX_LIMIT.toFloat(),
                            steps = BrowserSessionResidencyRules.MAX_LIMIT -
                                BrowserSessionResidencyRules.MIN_LIMIT - 1,
                        )
                    }
                }
                SettingsPageSpacer()
                Box {
                    SettingsChoice(
                        title = stringResource(R.string.settings_auto_close_tabs),
                        value = inactiveTabLifetime.displayName(),
                        expanded = lifetimeMenuExpanded,
                        onClick = { lifetimeMenuExpanded = true },
                    )
                    SettingsDropdown(
                        expanded = lifetimeMenuExpanded,
                        onDismissRequest = { lifetimeMenuExpanded = false },
                    ) {
                        InactiveTabLifetime.entries.forEach { lifetime ->
                            SettingsDropdownItem(
                                label = lifetime.displayName(),
                                selected = lifetime == inactiveTabLifetime,
                                onClick = {
                                    lifetimeMenuExpanded = false
                                    onInactiveTabLifetimeChanged(lifetime)
                                },
                            )
                        }
                    }
                }
            }
            SettingsPageSpacer()
            SettingsSectionTitle(stringResource(R.string.profile_actions_title))
            SettingsPreferenceGroup {
                SettingsSwitch(
                    title = stringResource(R.string.settings_profiles_title),
                    subtitle = stringResource(R.string.settings_profiles_subtitle),
                    checked = profilesEnabled,
                    onCheckedChange = onProfilesEnabledChanged,
                    modifier = Modifier,
                )
                SettingsPreferenceDivider()
                GuestBubbleSettings(profilesEnabled = profilesEnabled)
            }
        }
        if (section != TabSettingsSection.Tabs) {
            SettingsSectionTitle(stringResource(R.string.settings_group_link_interactions))
            SettingsPreferenceGroup {
                dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSelectionLink(
                    title = stringResource(R.string.settings_link_long_press_action),
                    value = stringResource(linkLongPressAction.labelRes()), onClick = onLinkLongPress, enabled = true,
                    modifier = Modifier.testTag(TabSettingsTestTags.LinkLongPressAction),
                )
                run {
                    SettingsPreferenceDivider()
                    dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSelectionLink(
                    title = stringResource(R.string.settings_link_peek_swipe_action),
                    value = stringResource(linkPeekSwipeAction.labelRes()), onClick = onLinkPeekSwipe, enabled = linkLongPressAction == LinkLongPressAction.LinkPeek,
                    modifier = Modifier.testTag(TabSettingsTestTags.LinkPeekSwipeAction),
                )
                    Text(
                        text = stringResource(R.string.settings_link_peek_swipe_action_summary),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsPreferenceDivider()
                SettingsLink(
                    icon = ImageVector.vectorResource(R.drawable.ic_symbol_open_in_new),
                    title = stringResource(R.string.settings_link_peek_actions_title),
                    subtitle = stringResource(R.string.settings_link_peek_actions_summary),
                    onClick = onLinkPeekActions,
                )
            }
            SettingsPageSpacer()
            SettingsSectionTitle(stringResource(R.string.settings_address_bar_actions_title))
            SettingsPreferenceGroup {
                Box(modifier = Modifier.testTag(TabSettingsTestTags.AddressBarLongPressAction)) {
                    SettingsLink(
                        icon = ImageVector.vectorResource(R.drawable.ic_symbol_route),
                        title = stringResource(R.string.settings_address_bar_long_press_title),
                        subtitle = stringResource(addressBarLongPressAction.labelRes()),
                        onClick = onAddressBarLongPressActions,
                    )
                }
                SettingsPreferenceDivider()
                SettingsLink(
                    icon = ImageVector.vectorResource(R.drawable.ic_switch_to_tab),
                    title = stringResource(R.string.settings_address_bar_actions_title),
                    subtitle = stringResource(R.string.settings_address_bar_actions_summary),
                    onClick = onAddressBarActions,
                )
                SettingsPreferenceDivider()
                SettingsLink(
                    icon = ImageVector.vectorResource(R.drawable.ic_visibility),
                    title = stringResource(R.string.settings_menu_actions_title),
                    subtitle = stringResource(R.string.settings_menu_actions_summary),
                    onClick = onMenuActions,
                )
            }
            SettingsPageSpacer()
            SettingsSectionTitle(stringResource(R.string.settings_section_gestures))
            SettingsPreferenceGroup {
                SettingsSwitch(
                    title = stringResource(R.string.settings_address_bar_docking_title),
                    subtitle = stringResource(R.string.settings_address_bar_docking_subtitle),
                    checked = isAddressBarDockingEnabled,
                    onCheckedChange = onAddressBarDockingEnabledChanged,
                    modifier = Modifier.testTag(TabSettingsTestTags.AddressBarDocking),
                )
                SettingsPreferenceDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_address_bar_swipe_mode_title),
                    subtitle = stringResource(R.string.settings_address_bar_swipe_mode_summary),
                    checked = isAddressBarSwipeModeEnabled,
                    onCheckedChange = onAddressBarSwipeModeEnabledChanged,
                    modifier = Modifier.testTag(TabSettingsTestTags.AddressBarSwipeMode),
                )
                SettingsPreferenceDivider()
                TabDismissResistanceSettings(
                    valuePercent = dismissResistancePercent,
                    title = stringResource(R.string.settings_tab_dismiss_resistance),
                    summary = { value ->
                        stringResource(R.string.settings_tab_dismiss_resistance_summary, value)
                    },
                    containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
                    enabled = true,
                    onValueChanged = onDismissResistancePercentChanged,
                )
            }
            SettingsPageSpacer()
            SettingsPreferenceGroup {
                SettingsSwitch(
                    title = stringResource(R.string.settings_status_bar_scroll_title),
                    subtitle = stringResource(R.string.settings_status_bar_scroll_summary),
                    checked = isStatusBarScrollEnabled,
                    onCheckedChange = { enabled ->
                        onStatusBarScrollEnabledChanged(enabled)
                        if (enabled) showStatusBarScrollAccessibilityDialog = true
                    },
                    modifier = Modifier.testTag(TabSettingsTestTags.StatusBarScrollEnabled),
                )
                SettingsPreferenceDivider()
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .settingsSearchTarget(stringResource(R.string.settings_status_bar_scroll_width_title))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    val widthTitle = stringResource(R.string.settings_status_bar_scroll_width_title)
                    val widthDescription = stringResource(
                        R.string.settings_status_bar_scroll_width_summary,
                        statusBarScrollWidth.roundToInt(),
                    )
                    Text(
                        widthTitle,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        widthDescription,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Slider(
                        enabled = isStatusBarScrollEnabled,
                        value = statusBarScrollWidth,
                        onValueChange = { value ->
                            if (isStatusBarScrollEnabled) {
                                statusBarScrollWidth = value
                                onStatusBarScrollTargetWidthPreview(value.roundToInt(), false)
                            }
                        },
                        onValueChangeFinished = {
                            if (isStatusBarScrollEnabled) {
                                val widthPercent = statusBarScrollWidth.roundToInt()
                                onStatusBarScrollTargetWidthPercentChanged(widthPercent)
                                onStatusBarScrollTargetWidthPreview(widthPercent, true)
                            }
                        },
                        modifier = Modifier.testTag(TabSettingsTestTags.StatusBarScrollWidth).semantics {
                            contentDescription = widthTitle
                            stateDescription = widthDescription
                        },
                        valueRange = StatusBarScrollRules.MIN_TARGET_WIDTH_PERCENT.toFloat()..
                            StatusBarScrollRules.MAX_TARGET_WIDTH_PERCENT.toFloat(),
                        steps = StatusBarScrollRules.MAX_TARGET_WIDTH_PERCENT -
                            StatusBarScrollRules.MIN_TARGET_WIDTH_PERCENT - 1,
                    )
                }
            }
        }
    }
    if (showStatusBarScrollAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showStatusBarScrollAccessibilityDialog = false },
            title = {
                Text(stringResource(R.string.settings_status_bar_scroll_accessibility_dialog_title))
            },
            text = {
                Text(stringResource(R.string.settings_status_bar_scroll_accessibility_dialog_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStatusBarScrollAccessibilityDialog = false
                        onOpenStatusBarScrollAccessibilitySettings()
                    },
                ) {
                    Text(stringResource(R.string.settings_status_bar_scroll_accessibility_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStatusBarScrollAccessibilityDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

internal fun LinkLongPressAction.labelRes(): Int = when (this) {
    LinkLongPressAction.LinkPeek -> R.string.link_peek_title
    LinkLongPressAction.CopyLink -> R.string.external_link_preview_copy_link
    LinkLongPressAction.Share -> R.string.action_share
    LinkLongPressAction.DownloadLink -> R.string.action_download_link
    LinkLongPressAction.OpenInNewTabInBackground -> R.string.action_open_link_background_tab
    LinkLongPressAction.OpenInNewTabInForeground -> R.string.action_open_link_foreground_tab
    LinkLongPressAction.OpenInPrivateTabInBackground ->
        R.string.action_open_link_private_background_tab
    LinkLongPressAction.OpenInPrivateTabInForeground ->
        R.string.action_open_link_private_foreground_tab
}

internal fun LinkPeekSwipeAction.labelRes(): Int = when (this) {
    LinkPeekSwipeAction.OpenBackground -> R.string.action_open_link_background_tab
    LinkPeekSwipeAction.OpenForeground -> R.string.action_open_link_foreground_tab
    LinkPeekSwipeAction.OpenNewStack -> R.string.action_open_link_new_stack
    LinkPeekSwipeAction.OpenPrivate -> R.string.action_open_link_private_foreground_tab
    LinkPeekSwipeAction.Copy -> R.string.external_link_preview_copy_link
    LinkPeekSwipeAction.Share -> R.string.action_share
    LinkPeekSwipeAction.Favorite -> R.string.action_add_favorite
    LinkPeekSwipeAction.Snooze -> R.string.action_snooze_tab
    LinkPeekSwipeAction.ReaderLater -> R.string.reader_save_offline
}

@Composable
internal fun androidTabOverviewSettingsStrings() = TabOverviewSettingsStrings(
    overviewMode = stringResource(R.string.settings_tab_overview_mode),
    modeNames = TabOverviewMode.entries.associateWith { it.displayName() },
    listStartsAtBottom = stringResource(R.string.settings_tab_list_starts_at_bottom_title),
    listStartsAtBottomSummary = stringResource(R.string.settings_tab_list_starts_at_bottom_subtitle),
)
