package dev.sk2andy.materialbrowser.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetContentLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetToolbarRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuConfigurationSection
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayoutRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuPresentation
import dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuSettingsResources
import dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuSettingsPage as SharedBrowserMenuSettingsPage
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

internal object BrowserMenuPresentationSettingsTestTags {
    const val Presentation = "browser_menu_settings_presentation"
    const val ContentLayout = "browser_menu_settings_content_layout"

    fun toolbarSlot(index: Int): String = "browser_menu_settings_toolbar_slot_$index"
}

private object AndroidBrowserMenuSettingsResources : BrowserMenuSettingsResources {
    @Composable
    override fun title(): String = stringResource(R.string.settings_menu_actions_title)

    @Composable
    override fun back(): String = stringResource(R.string.action_back)

    @Composable
    override fun intro(): String = stringResource(R.string.settings_menu_actions_intro)

    @Composable
    override fun sectionTitle(section: BrowserMenuConfigurationSection): String = stringResource(
        when (section) {
            BrowserMenuConfigurationSection.Toolbar -> R.string.settings_menu_section_toolbar
            BrowserMenuConfigurationSection.Page -> R.string.browser_menu_page_group
            BrowserMenuConfigurationSection.Toppings -> R.string.browser_menu_toppings_group
            BrowserMenuConfigurationSection.Extensions -> R.string.settings_menu_section_extensions
            BrowserMenuConfigurationSection.Candy -> R.string.settings_menu_section_candy
            BrowserMenuConfigurationSection.Browser -> R.string.browser_menu_browser_group
            BrowserMenuConfigurationSection.TabSwitcher -> R.string.settings_menu_section_tab_switcher
        },
    )

    @Composable
    override fun entryLabel(entry: BrowserMenuEntry): String = stringResource(entry.labelResource())

    @Composable
    override fun locationLabel(location: BrowserMenuLocation): String = stringResource(
        when (location) {
            BrowserMenuLocation.Nowhere -> R.string.settings_menu_location_nowhere
            BrowserMenuLocation.Tab -> R.string.settings_menu_location_tab
            BrowserMenuLocation.TabSwitcher -> R.string.settings_menu_location_tab_switcher
            BrowserMenuLocation.Both -> R.string.settings_menu_location_both
        },
    )
}

@Composable
internal fun BrowserMenuSettingsPage(
    layout: BrowserMenuLayout,
    onLocationChanged: (BrowserMenuEntry, BrowserMenuLocation) -> Unit,
    onBack: () -> Unit,
    onPresentationChanged: (BrowserMenuPresentation) -> Unit = {},
    onBottomSheetContentLayoutChanged: (BrowserMenuBottomSheetContentLayout) -> Unit = {},
    onBottomSheetToolbarEntriesChanged: (List<BrowserMenuEntry?>) -> Unit = {},
) {
    SharedBrowserMenuSettingsPage(
        layout = layout,
        resources = AndroidBrowserMenuSettingsResources,
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
        availableEntries = BrowserMenuEntry.entries.filterNot { entry ->
            entry == BrowserMenuEntry.OpenFirefoxExtensions
        },
        onLocationChanged = onLocationChanged,
        onBack = onBack,
        presentationSettings = {
            BrowserMenuPresentationSettings(
                layout = layout,
                onPresentationChanged = onPresentationChanged,
                onBottomSheetContentLayoutChanged = onBottomSheetContentLayoutChanged,
                onBottomSheetToolbarEntriesChanged = onBottomSheetToolbarEntriesChanged,
                onLocationChanged = onLocationChanged,
            )
        },
    )
}

@Composable
private fun BrowserMenuPresentationSettings(
    layout: BrowserMenuLayout,
    onPresentationChanged: (BrowserMenuPresentation) -> Unit,
    onBottomSheetContentLayoutChanged: (BrowserMenuBottomSheetContentLayout) -> Unit,
    onBottomSheetToolbarEntriesChanged: (List<BrowserMenuEntry?>) -> Unit,
    onLocationChanged: (BrowserMenuEntry, BrowserMenuLocation) -> Unit,
) {
    var presentationExpanded by remember { mutableStateOf(false) }
    var contentLayoutExpanded by remember { mutableStateOf(false) }
    Box {
        SettingsChoice(
            title = stringResource(R.string.settings_menu_presentation_title),
            value = layout.presentation.displayName(),
            expanded = presentationExpanded,
            onClick = { presentationExpanded = true },
            modifier = Modifier.testTag(BrowserMenuPresentationSettingsTestTags.Presentation),
        )
        SettingsDropdown(
            expanded = presentationExpanded,
            onDismissRequest = { presentationExpanded = false },
        ) {
            BrowserMenuPresentation.entries.forEach { presentation ->
                SettingsDropdownItem(
                    label = presentation.displayName(),
                    selected = presentation == layout.presentation,
                    onClick = {
                        presentationExpanded = false
                        if (presentation != layout.presentation) onPresentationChanged(presentation)
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Box {
        SettingsChoice(
            title = stringResource(R.string.settings_menu_bottom_sheet_layout_title),
            value = layout.bottomSheetContentLayout.displayName(),
            expanded = contentLayoutExpanded,
            onClick = { contentLayoutExpanded = true },
            modifier = Modifier.testTag(BrowserMenuPresentationSettingsTestTags.ContentLayout),
        )
        SettingsDropdown(
            expanded = contentLayoutExpanded,
            onDismissRequest = { contentLayoutExpanded = false },
        ) {
            BrowserMenuBottomSheetContentLayout.entries.forEach { contentLayout ->
                SettingsDropdownItem(
                    label = contentLayout.displayName(),
                    selected = contentLayout == layout.bottomSheetContentLayout,
                    onClick = {
                        contentLayoutExpanded = false
                        if (contentLayout != layout.bottomSheetContentLayout) {
                            onBottomSheetContentLayoutChanged(contentLayout)
                        }
                    },
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.settings_menu_presentation_summary),
        modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    if (layout.presentation == BrowserMenuPresentation.BottomSheet) {
        BrowserMenuBottomSheetToolbarSettings(
            layout = layout,
            onEntriesChanged = onBottomSheetToolbarEntriesChanged,
            onLocationChanged = onLocationChanged,
        )
    }
}

@Composable
private fun BrowserMenuBottomSheetToolbarSettings(
    layout: BrowserMenuLayout,
    onEntriesChanged: (List<BrowserMenuEntry?>) -> Unit,
    onLocationChanged: (BrowserMenuEntry, BrowserMenuLocation) -> Unit,
) {
    var expandedSlot by remember { mutableStateOf<Int?>(null) }
    val entries = BrowserMenuBottomSheetToolbarRules.normalize(layout.bottomSheetToolbarEntries)
    val availableEntries = BrowserMenuBottomSheetToolbarRules.availableEntries().filterNot { entry ->
        entry == BrowserMenuEntry.OpenFirefoxExtensions
    }
    SettingsSectionTitle(stringResource(R.string.settings_menu_bottom_sheet_toolbar_title))
    Spacer(Modifier.height(8.dp))
    entries.forEachIndexed { index, currentEntry ->
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_menu_bottom_sheet_toolbar_slot, index + 1),
                value = currentEntry?.let { entry -> stringResource(entry.labelResource()) }
                    ?: stringResource(R.string.settings_menu_bottom_sheet_toolbar_empty),
                expanded = expandedSlot == index,
                onClick = { expandedSlot = index },
                modifier = Modifier.testTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(index)),
            )
            SettingsDropdown(
                expanded = expandedSlot == index,
                onDismissRequest = { expandedSlot = null },
            ) {
                (listOf(null) + availableEntries).forEach { entry ->
                    SettingsDropdownItem(
                        label = entry?.let { stringResource(it.labelResource()) }
                            ?: stringResource(R.string.settings_menu_bottom_sheet_toolbar_empty),
                        selected = entry == currentEntry,
                        onClick = {
                            expandedSlot = null
                            if (entry != null) {
                                when (BrowserMenuLayoutRules.location(layout, entry)) {
                                    BrowserMenuLocation.Nowhere -> onLocationChanged(entry, BrowserMenuLocation.Tab)
                                    BrowserMenuLocation.TabSwitcher -> onLocationChanged(entry, BrowserMenuLocation.Both)
                                    BrowserMenuLocation.Tab,
                                    BrowserMenuLocation.Both,
                                    -> Unit
                                }
                            }
                            if (entry != currentEntry) {
                                onEntriesChanged(
                                    BrowserMenuBottomSheetToolbarRules.update(layout, index, entry)
                                        .bottomSheetToolbarEntries,
                                )
                            }
                        },
                    )
                }
            }
        }
        if (index != entries.lastIndex) Spacer(Modifier.height(4.dp))
    }
    Text(
        text = stringResource(R.string.settings_menu_bottom_sheet_toolbar_summary),
        modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun BrowserMenuPresentation.displayName(): String = stringResource(
    when (this) {
        BrowserMenuPresentation.Floating -> R.string.settings_menu_presentation_floating
        BrowserMenuPresentation.BottomSheet -> R.string.settings_menu_presentation_bottom_sheet
    },
)

@Composable
private fun BrowserMenuBottomSheetContentLayout.displayName(): String = stringResource(
    when (this) {
        BrowserMenuBottomSheetContentLayout.Scroll -> R.string.settings_menu_bottom_sheet_layout_scroll
        BrowserMenuBottomSheetContentLayout.SwipePages -> R.string.settings_menu_bottom_sheet_layout_pages
    },
)

@StringRes
private fun BrowserMenuEntry.labelResource(): Int = when (this) {
    BrowserMenuEntry.Back -> R.string.action_back
    BrowserMenuEntry.Forward -> R.string.action_forward
    BrowserMenuEntry.Reload -> R.string.action_reload
    BrowserMenuEntry.Favorite -> R.string.action_favorite
    BrowserMenuEntry.Pin -> R.string.action_pin_tab
    BrowserMenuEntry.ShowTabs -> R.string.address_bar_action_tabs
    BrowserMenuEntry.NewTab -> R.string.cd_new_tab
    BrowserMenuEntry.CloseTab -> R.string.cd_close_tab
    BrowserMenuEntry.DuplicateTab -> R.string.action_duplicate_tab
    BrowserMenuEntry.Reader -> R.string.reader_open_action
    BrowserMenuEntry.Translate -> R.string.action_translate_page
    BrowserMenuEntry.FindInPage -> R.string.action_find_in_page
    BrowserMenuEntry.Share -> R.string.action_share
    BrowserMenuEntry.OpenExternal -> R.string.action_open_in_app
    BrowserMenuEntry.Print -> R.string.action_print
    BrowserMenuEntry.CookieBannerRemoval -> R.string.privacy_cookie_banner_remove
    BrowserMenuEntry.ForceVerticalScrolling -> R.string.privacy_force_vertical_scrolling
    BrowserMenuEntry.ForcePageZooming -> R.string.privacy_force_page_zooming
    BrowserMenuEntry.ForceSafeArea -> R.string.compatibility_force_safe_area
    BrowserMenuEntry.AlwaysBlockPopups -> R.string.action_always_block_popups
    BrowserMenuEntry.DesktopView -> R.string.action_desktop_view
    BrowserMenuEntry.DomainMute -> R.string.action_mute_domain
    BrowserMenuEntry.ToppingCommands -> R.string.settings_menu_entry_topping_commands
    BrowserMenuEntry.FirefoxPageActions -> R.string.settings_menu_entry_firefox_page_actions
    BrowserMenuEntry.CandyTrail -> R.string.action_open_candy_trail
    BrowserMenuEntry.AddSiteCapsule -> R.string.action_add_site_capsule
    BrowserMenuEntry.Summarize -> R.string.action_summarize
    BrowserMenuEntry.Snooze -> R.string.action_snooze_tab
    BrowserMenuEntry.AddressBarDocking -> R.string.action_dock_address_bar
    BrowserMenuEntry.OpenSnoozedTabs -> R.string.snoozed_tabs_title
    BrowserMenuEntry.OpenFavorites -> R.string.favorites_title
    BrowserMenuEntry.OpenDownloads -> R.string.downloads_title
    BrowserMenuEntry.OpenHistory -> R.string.action_history
    BrowserMenuEntry.OpenFirefoxExtensions -> R.string.gecko_extensions_title
    BrowserMenuEntry.OpenSettings -> R.string.action_settings
    BrowserMenuEntry.MoveToProfile -> R.string.action_move_tab_to_profile
    BrowserMenuEntry.TabStacks -> R.string.tab_stacks_title
    BrowserMenuEntry.CloseAllTabs -> R.string.action_close_all_tabs
}
