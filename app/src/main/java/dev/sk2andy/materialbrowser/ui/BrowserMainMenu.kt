package dev.sk2andy.materialbrowser.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserInputDiagnostics
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuAction
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuCapabilities
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuItem
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuLabelKey
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuSection
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuState
import dev.sk2andy.materialbrowser.shared.browser.BrowserToppingMenuCommand
import dev.sk2andy.materialbrowser.shared.ui.BrowserMainMenuEffects
import dev.sk2andy.materialbrowser.shared.ui.BrowserMainMenuContainerRole
import dev.sk2andy.materialbrowser.shared.ui.BrowserMainMenuResources
import dev.sk2andy.materialbrowser.shared.ui.BrowserMainMenuStyle
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens
import dev.sk2andy.materialbrowser.shared.ui.BrowserMainMenu as SharedBrowserMainMenu

internal typealias BrowserMainMenuMotion =
    dev.sk2andy.materialbrowser.shared.ui.BrowserMainMenuMotion
internal typealias BrowserMainMenuTestTags =
    dev.sk2andy.materialbrowser.shared.ui.BrowserMainMenuTestTags
internal typealias DomainMuteMenuTestTags =
    dev.sk2andy.materialbrowser.shared.ui.DomainMuteMenuTestTags

private val AndroidBrowserMainMenuResources = object : BrowserMainMenuResources {
    @Composable
    override fun title(): String = stringResource(R.string.browser_menu_title)

    @Composable
    override fun sectionTitle(section: BrowserFeatureMenuSection): String = stringResource(
        when (section) {
            BrowserFeatureMenuSection.Page -> R.string.browser_menu_page_group
            BrowserFeatureMenuSection.Toppings -> R.string.browser_menu_toppings_group
            BrowserFeatureMenuSection.Browser -> R.string.browser_menu_browser_group
            BrowserFeatureMenuSection.Toolbar,
            BrowserFeatureMenuSection.Candy,
            -> R.string.browser_menu_title
        },
    )

    @Composable
    override fun label(item: BrowserFeatureMenuItem, toolbar: Boolean): String {
        item.dynamicLabel?.let { return it }
        if (toolbar && item.action == BrowserFeatureMenuAction.ToggleFavorite) {
            return stringResource(R.string.action_favorite)
        }
        if (item.action == BrowserFeatureMenuAction.ToggleDomainMute) {
            return stringResource(R.string.action_mute_domain)
        }
        return stringResource(item.labelKey.androidStringResource())
    }

    @Composable
    override fun accessibilityLabel(item: BrowserFeatureMenuItem): String? = when (item.action) {
        BrowserFeatureMenuAction.ToggleFavorite,
        BrowserFeatureMenuAction.TogglePinned,
        -> label(item)
        else -> null
    }

    @Composable
    override fun supportingText(
        item: BrowserFeatureMenuItem,
        snoozedTabCount: Int,
    ): String? = when (item.action) {
        BrowserFeatureMenuAction.ToggleCookieBannerRemoval -> stringResource(
            if (item.enabled) {
                R.string.privacy_cookie_banner_remove_description
            } else {
                R.string.privacy_cookie_banner_remove_unavailable
            },
        )
        BrowserFeatureMenuAction.ToggleForceVerticalScrolling ->
            stringResource(R.string.privacy_force_vertical_scrolling_description)
        BrowserFeatureMenuAction.ToggleForcePageZooming ->
            stringResource(R.string.privacy_force_page_zooming_description)
        BrowserFeatureMenuAction.ToggleForceSafeArea ->
            stringResource(R.string.compatibility_force_safe_area_description)
        BrowserFeatureMenuAction.ToggleAlwaysBlockPopups ->
            stringResource(R.string.action_always_block_popups_description)
        BrowserFeatureMenuAction.ToggleDesktopView ->
            stringResource(R.string.action_desktop_view_description)
        BrowserFeatureMenuAction.SnoozeTab -> if (item.enabled) {
            null
        } else {
            stringResource(R.string.snooze_unavailable_private)
        }
        BrowserFeatureMenuAction.OpenSnoozedTabs -> if (snoozedTabCount == 0) {
            stringResource(R.string.snoozed_tabs_settings_summary)
        } else {
            pluralStringResource(
                R.plurals.snoozed_tabs_settings_count,
                snoozedTabCount,
                snoozedTabCount,
            )
        }
        BrowserFeatureMenuAction.InvokeToppingCommand -> item.supportingText
        BrowserFeatureMenuAction.DuplicateTab ->
            stringResource(R.string.duplicate_tab_url_only_disclaimer)
        else -> null
    }

    @Composable
    override fun icon(item: BrowserFeatureMenuItem, modifier: Modifier) {
        Icon(
            painter = painterResource(item.androidDrawableResource()),
            contentDescription = null,
            modifier = modifier,
        )
    }

    @Composable
    override fun trailingIcon(modifier: Modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_symbol_chevron_right),
            contentDescription = null,
            modifier = modifier,
        )
    }
}

private class AndroidBrowserMainMenuEffects(
    private val backdropSource: CandyChromeBackdropSource?,
) : BrowserMainMenuEffects {
    override val style = BrowserMainMenuStyle(
        toolbarLabelFontSize = 12.sp,
        rowMinHeight = 48.dp,
        rowLabelFontSize = 16.sp,
        rowSupportingTextFontSize = 12.sp,
        useExpressiveToggleButtons = true,
    )

    @Composable
    override fun menuSurface(
        modifier: Modifier,
        shape: Shape,
        content: @Composable () -> Unit,
    ) {
        val tokens = browserChromeSurfaceTokens()
        CandyChromeSurface(
            backdropSource = backdropSource,
            tokens = tokens,
            modifier = modifier,
            shape = shape,
            blurCornerRadius = tokens.largeCornerRadius,
            content = content,
        )
    }

    @Composable
    override fun containerColor(
        color: Color,
        frostedAlpha: Float,
        role: BrowserMainMenuContainerRole,
    ): Color =
        browserChromeColor(color, frostedAlpha)

    override fun popupState(expanded: Boolean, visible: Boolean) {
        BrowserInputDiagnostics.popupState(expanded, visible)
    }
}

@Composable
internal fun BrowserMainMenu(
    expanded: Boolean,
    backdropSource: CandyChromeBackdropSource?,
    onDismissRequest: () -> Unit,
    pageSubtitle: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    canToggleFavorite: Boolean,
    isFavorite: Boolean,
    isPinned: Boolean,
    canUsePageActions: Boolean,
    canUseDocumentActions: Boolean = canUsePageActions,
    canOpenReader: Boolean,
    canTranslatePage: Boolean,
    canToggleDomainMute: Boolean,
    isDomainMuted: Boolean,
    canToggleAlwaysBlockPopups: Boolean,
    isAlwaysBlockPopupsEnabled: Boolean,
    canToggleDesktopView: Boolean,
    isDesktopView: Boolean,
    canToggleCookieBannerRemoval: Boolean,
    isCookieBannerRemovalEnabled: Boolean,
    canToggleForceVerticalScrolling: Boolean,
    isForceVerticalScrollingEnabled: Boolean,
    canToggleForcePageZooming: Boolean,
    isForcePageZoomingEnabled: Boolean,
    canToggleForceSafeArea: Boolean,
    isForceSafeAreaEnabled: Boolean,
    canAddSiteCapsule: Boolean,
    canSnooze: Boolean,
    snoozedTabCount: Int,
    overflowAddressBarActions: List<AddressBarAction> = emptyList(),
    canCloseTab: Boolean = true,
    userScriptMenuCommands: List<UserScriptMenuCommand> = emptyList(),
    onTabs: () -> Unit = {},
    onNewTab: () -> Unit = {},
    onDuplicateTab: () -> Unit = {},
    onCloseTab: () -> Unit = {},
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onPrint: () -> Unit,
    onOpenReader: () -> Unit,
    onTranslate: () -> Unit,
    onFindInPage: () -> Unit = {},
    onDomainMutedChange: (Boolean) -> Unit,
    onAlwaysBlockPopupsChange: (Boolean) -> Unit,
    onDesktopViewChange: (Boolean) -> Unit,
    onCookieBannerRemovalEnabledChange: (Boolean) -> Unit,
    onForceVerticalScrollingChange: (Boolean) -> Unit,
    onForcePageZoomingChange: (Boolean) -> Unit,
    onForceSafeAreaChange: (Boolean) -> Unit,
    onOpenCandyTrail: () -> Unit,
    onAddSiteCapsule: () -> Unit,
    onSummarize: () -> Unit,
    onSnooze: () -> Unit,
    onSnoozedTabs: () -> Unit,
    onUserScriptMenuCommand: (UserScriptMenuCommand) -> Unit = {},
    canDockAddressBar: Boolean = true,
    onDockAddressBar: () -> Unit,
    onParkAddressBarRight: () -> Unit = {},
    onFavorites: () -> Unit = {},
    onDownloads: () -> Unit = {},
    onHistory: () -> Unit,
    onOpenFirefoxExtensions: (() -> Unit)? = null,
    onSettings: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val menuState = BrowserFeatureMenuState(
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        isLoading = isLoading,
        hasPage = canUsePageActions,
        canCloseTab = canCloseTab,
        canToggleFavorite = canToggleFavorite,
        isFavorite = isFavorite,
        isPinned = isPinned,
        canOpenReader = canOpenReader,
        canTranslatePage = canTranslatePage,
        canUseDocumentActions = canUseDocumentActions,
        canToggleCookieBannerRemoval = canToggleCookieBannerRemoval,
        isCookieBannerRemovalEnabled = isCookieBannerRemovalEnabled,
        canToggleForceVerticalScrolling = canToggleForceVerticalScrolling,
        isForceVerticalScrollingEnabled = isForceVerticalScrollingEnabled,
        canToggleForcePageZooming = canToggleForcePageZooming,
        isForcePageZoomingEnabled = isForcePageZoomingEnabled,
        canToggleForceSafeArea = canToggleForceSafeArea,
        isForceSafeAreaEnabled = isForceSafeAreaEnabled,
        canToggleAlwaysBlockPopups = canToggleAlwaysBlockPopups,
        isAlwaysBlockPopupsEnabled = isAlwaysBlockPopupsEnabled,
        canToggleDesktopView = canToggleDesktopView,
        isDesktopView = isDesktopView,
        canToggleDomainMute = canToggleDomainMute,
        isDomainMuted = isDomainMuted,
        canAddSiteCapsule = canAddSiteCapsule,
        canSnooze = canSnooze,
        canDockAddressBar = canDockAddressBar,
        overflowPageActions = overflowAddressBarActions.mapNotNull(AddressBarAction::sharedMenuAction),
        toppingCommands = userScriptMenuCommands.map(UserScriptMenuCommand::sharedMenuCommand),
    )
    val items = BrowserFeatureMenuRules.items(
        state = menuState,
        capabilities = BrowserFeatureMenuCapabilities(
            supportsFirefoxExtensions = onOpenFirefoxExtensions != null,
        ),
    )
    SharedBrowserMainMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        pageSubtitle = pageSubtitle,
        items = items,
        snoozedTabCount = snoozedTabCount,
        screenSize = DpSize(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp),
        resources = AndroidBrowserMainMenuResources,
        effects = rememberAndroidBrowserMainMenuEffects(backdropSource),
        onAction = { item ->
            when (item.action) {
                BrowserFeatureMenuAction.Back -> onBack()
                BrowserFeatureMenuAction.Forward -> onForward()
                BrowserFeatureMenuAction.Reload,
                BrowserFeatureMenuAction.Stop,
                -> onReloadOrStop()
                BrowserFeatureMenuAction.ToggleFavorite -> onToggleFavorite()
                BrowserFeatureMenuAction.TogglePinned -> onTogglePinned()
                BrowserFeatureMenuAction.ShowTabs -> onTabs()
                BrowserFeatureMenuAction.NewTab -> onNewTab()
                BrowserFeatureMenuAction.DuplicateTab -> onDuplicateTab()
                BrowserFeatureMenuAction.CloseTab -> onCloseTab()
                BrowserFeatureMenuAction.ParkAddressBarRight -> onParkAddressBarRight()
                BrowserFeatureMenuAction.OpenReader -> onOpenReader()
                BrowserFeatureMenuAction.TranslatePage -> onTranslate()
                BrowserFeatureMenuAction.FindInPage -> onFindInPage()
                BrowserFeatureMenuAction.Share -> onShare()
                BrowserFeatureMenuAction.OpenExternal -> onOpenExternal()
                BrowserFeatureMenuAction.Print -> onPrint()
                BrowserFeatureMenuAction.ToggleCookieBannerRemoval ->
                    onCookieBannerRemovalEnabledChange(item.checked != true)
                BrowserFeatureMenuAction.ToggleForceVerticalScrolling ->
                    onForceVerticalScrollingChange(item.checked != true)
                BrowserFeatureMenuAction.ToggleForcePageZooming ->
                    onForcePageZoomingChange(item.checked != true)
                BrowserFeatureMenuAction.ToggleForceSafeArea ->
                    onForceSafeAreaChange(item.checked != true)
                BrowserFeatureMenuAction.ToggleAlwaysBlockPopups ->
                    onAlwaysBlockPopupsChange(item.checked != true)
                BrowserFeatureMenuAction.ToggleDesktopView ->
                    onDesktopViewChange(item.checked != true)
                BrowserFeatureMenuAction.ToggleDomainMute ->
                    onDomainMutedChange(item.checked != true)
                BrowserFeatureMenuAction.OpenCandyTrail -> onOpenCandyTrail()
                BrowserFeatureMenuAction.AddSiteCapsule -> onAddSiteCapsule()
                BrowserFeatureMenuAction.Summarize -> onSummarize()
                BrowserFeatureMenuAction.SnoozeTab -> onSnooze()
                BrowserFeatureMenuAction.DockAddressBar -> onDockAddressBar()
                BrowserFeatureMenuAction.OpenSnoozedTabs -> onSnoozedTabs()
                BrowserFeatureMenuAction.OpenFavorites -> onFavorites()
                BrowserFeatureMenuAction.OpenDownloads -> onDownloads()
                BrowserFeatureMenuAction.OpenHistory -> onHistory()
                BrowserFeatureMenuAction.OpenFirefoxExtensions ->
                    onOpenFirefoxExtensions?.invoke()
                BrowserFeatureMenuAction.OpenSettings -> onSettings()
                BrowserFeatureMenuAction.InvokeToppingCommand -> {
                    userScriptMenuCommands.firstOrNull { command ->
                        command.scriptId == item.toppingScriptId &&
                            command.commandId == item.toppingCommandId
                    }?.let(onUserScriptMenuCommand)
                }
            }
        },
    )
}

@Composable
private fun rememberAndroidBrowserMainMenuEffects(
    backdropSource: CandyChromeBackdropSource?,
): BrowserMainMenuEffects = androidx.compose.runtime.remember(backdropSource) {
    AndroidBrowserMainMenuEffects(backdropSource)
}

private fun AddressBarAction.sharedMenuAction(): BrowserFeatureMenuAction? = when (this) {
    AddressBarAction.Tabs -> BrowserFeatureMenuAction.ShowTabs
    AddressBarAction.NewTab -> BrowserFeatureMenuAction.NewTab
    AddressBarAction.CloseTab -> BrowserFeatureMenuAction.CloseTab
    AddressBarAction.ParkRight -> BrowserFeatureMenuAction.ParkAddressBarRight
    else -> null
}

private fun UserScriptMenuCommand.sharedMenuCommand() = BrowserToppingMenuCommand(
    scriptId = scriptId,
    commandId = commandId,
    caption = caption,
    scriptName = scriptName,
)

@StringRes
private fun BrowserFeatureMenuLabelKey.androidStringResource(): Int = when (this) {
    BrowserFeatureMenuLabelKey.Back -> R.string.action_back
    BrowserFeatureMenuLabelKey.Forward -> R.string.action_forward
    BrowserFeatureMenuLabelKey.Reload -> R.string.action_reload
    BrowserFeatureMenuLabelKey.StopLoading -> R.string.action_stop_loading
    BrowserFeatureMenuLabelKey.AddFavorite -> R.string.action_add_favorite
    BrowserFeatureMenuLabelKey.RemoveFavorite -> R.string.action_remove_favorite
    BrowserFeatureMenuLabelKey.PinTab -> R.string.action_pin_tab
    BrowserFeatureMenuLabelKey.UnpinTab -> R.string.action_remove_pin
    BrowserFeatureMenuLabelKey.Tabs -> R.string.address_bar_action_tabs
    BrowserFeatureMenuLabelKey.NewTab -> R.string.cd_new_tab
    BrowserFeatureMenuLabelKey.DuplicateTab -> R.string.action_duplicate_tab
    BrowserFeatureMenuLabelKey.CloseTab -> R.string.cd_close_tab
    BrowserFeatureMenuLabelKey.ParkAddressBarRight -> R.string.action_park_address_pill_right
    BrowserFeatureMenuLabelKey.Reader -> R.string.reader_open_action
    BrowserFeatureMenuLabelKey.Translate -> R.string.action_translate_page
    BrowserFeatureMenuLabelKey.FindInPage -> R.string.action_find_in_page
    BrowserFeatureMenuLabelKey.Share -> R.string.action_share
    BrowserFeatureMenuLabelKey.OpenExternal -> R.string.action_open_in_app
    BrowserFeatureMenuLabelKey.Print -> R.string.action_print
    BrowserFeatureMenuLabelKey.CookieBannerRemoval -> R.string.privacy_cookie_banner_remove
    BrowserFeatureMenuLabelKey.ForceVerticalScrolling -> R.string.privacy_force_vertical_scrolling
    BrowserFeatureMenuLabelKey.ForcePageZooming -> R.string.privacy_force_page_zooming
    BrowserFeatureMenuLabelKey.ForceSafeArea -> R.string.compatibility_force_safe_area
    BrowserFeatureMenuLabelKey.AlwaysBlockPopups -> R.string.action_always_block_popups
    BrowserFeatureMenuLabelKey.DesktopView -> R.string.action_desktop_view
    BrowserFeatureMenuLabelKey.MuteDomain,
    BrowserFeatureMenuLabelKey.UnmuteDomain,
    -> R.string.action_mute_domain
    BrowserFeatureMenuLabelKey.CandyTrail -> R.string.action_open_candy_trail
    BrowserFeatureMenuLabelKey.AddSiteCapsule -> R.string.action_add_site_capsule
    BrowserFeatureMenuLabelKey.Summarize -> R.string.action_summarize
    BrowserFeatureMenuLabelKey.SnoozeTab -> R.string.action_snooze_tab
    BrowserFeatureMenuLabelKey.DockAddressBar -> R.string.action_dock_address_bar
    BrowserFeatureMenuLabelKey.SnoozedTabs -> R.string.snoozed_tabs_title
    BrowserFeatureMenuLabelKey.Favorites -> R.string.favorites_title
    BrowserFeatureMenuLabelKey.Downloads -> R.string.downloads_title
    BrowserFeatureMenuLabelKey.History -> R.string.action_history
    BrowserFeatureMenuLabelKey.Settings -> R.string.action_settings
    BrowserFeatureMenuLabelKey.FirefoxExtensions -> R.string.gecko_extensions_title
    BrowserFeatureMenuLabelKey.ToppingsCommand -> R.string.browser_menu_toppings_group
}

@DrawableRes
internal fun BrowserFeatureMenuItem.androidDrawableResource(): Int = when (action) {
    BrowserFeatureMenuAction.Back -> R.drawable.ic_symbol_arrow_back
    BrowserFeatureMenuAction.Forward -> R.drawable.ic_symbol_arrow_forward
    BrowserFeatureMenuAction.Reload -> R.drawable.ic_symbol_refresh
    BrowserFeatureMenuAction.Stop -> R.drawable.ic_symbol_close
    BrowserFeatureMenuAction.ToggleFavorite -> if (checked == true) {
        R.drawable.ic_symbol_favorite_filled
    } else {
        R.drawable.ic_symbol_favorite
    }
    BrowserFeatureMenuAction.TogglePinned -> R.drawable.ic_push_pin
    BrowserFeatureMenuAction.ShowTabs -> R.drawable.ic_switch_to_tab
    BrowserFeatureMenuAction.NewTab -> R.drawable.ic_symbol_add
    BrowserFeatureMenuAction.DuplicateTab -> R.drawable.ic_content_copy
    BrowserFeatureMenuAction.CloseTab -> R.drawable.ic_symbol_close
    BrowserFeatureMenuAction.ParkAddressBarRight -> R.drawable.ic_symbol_chevron_physical_right
    BrowserFeatureMenuAction.OpenReader -> R.drawable.ic_reader_align_start
    BrowserFeatureMenuAction.TranslatePage -> R.drawable.ic_symbol_translate
    BrowserFeatureMenuAction.FindInPage -> R.drawable.ic_symbol_find_in_page
    BrowserFeatureMenuAction.Share -> R.drawable.ic_symbol_share
    BrowserFeatureMenuAction.OpenExternal -> R.drawable.ic_symbol_open_in_new
    BrowserFeatureMenuAction.Print -> R.drawable.ic_symbol_print
    BrowserFeatureMenuAction.ToggleDomainMute -> R.drawable.ic_symbol_volume_off
    BrowserFeatureMenuAction.OpenCandyTrail -> R.drawable.ic_symbol_route
    BrowserFeatureMenuAction.AddSiteCapsule -> R.drawable.ic_symbol_add_to_home_screen
    BrowserFeatureMenuAction.Summarize -> R.drawable.ic_symbol_auto_awesome
    BrowserFeatureMenuAction.SnoozeTab,
    BrowserFeatureMenuAction.OpenSnoozedTabs,
    -> R.drawable.ic_snooze
    BrowserFeatureMenuAction.DockAddressBar -> R.drawable.ic_symbol_chevron_right
    BrowserFeatureMenuAction.OpenFavorites -> R.drawable.ic_symbol_favorite
    BrowserFeatureMenuAction.OpenDownloads -> R.drawable.ic_reader_download
    BrowserFeatureMenuAction.OpenHistory -> R.drawable.ic_history
    BrowserFeatureMenuAction.OpenSettings -> R.drawable.ic_symbol_settings
    BrowserFeatureMenuAction.OpenFirefoxExtensions,
    BrowserFeatureMenuAction.InvokeToppingCommand,
    -> R.drawable.ic_symbol_extension
    BrowserFeatureMenuAction.ToggleCookieBannerRemoval -> R.drawable.ic_symbol_cookie
    BrowserFeatureMenuAction.ToggleForceVerticalScrolling ->
        R.drawable.ic_symbol_vertical_scroll
    BrowserFeatureMenuAction.ToggleForcePageZooming -> R.drawable.ic_symbol_zoom_in
    BrowserFeatureMenuAction.ToggleForceSafeArea -> R.drawable.ic_symbol_fit_screen
    BrowserFeatureMenuAction.ToggleAlwaysBlockPopups -> R.drawable.ic_symbol_block
    BrowserFeatureMenuAction.ToggleDesktopView -> R.drawable.ic_symbol_desktop
}
