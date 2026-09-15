package dev.sk2andy.materialbrowser.ui

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuConfigurationSection
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation
import dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuSettingsResources
import dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuSettingsPage as SharedBrowserMenuSettingsPage
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

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
    )
}

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
