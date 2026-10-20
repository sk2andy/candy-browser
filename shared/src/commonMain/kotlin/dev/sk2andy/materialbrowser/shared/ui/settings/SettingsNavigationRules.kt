package dev.sk2andy.materialbrowser.shared.ui.settings

import dev.sk2andy.materialbrowser.ui.SettingsDestination

/** The same hierarchy drives header Back, predictive previews and route motion. */
object SettingsNavigationRules {
    fun parentDestination(destination: SettingsDestination): SettingsDestination = when (destination) {
        SettingsDestination.MenuStyle,
        SettingsDestination.MenuContentLayout -> SettingsDestination.MenuActions
        SettingsDestination.TabOverview,
        SettingsDestination.TabStackFolder,
        SettingsDestination.TabLifecycle,
        SettingsDestination.ProfileOptions -> SettingsDestination.Tabs
        SettingsDestination.ClosedTabUndo -> SettingsDestination.TabLifecycle
        SettingsDestination.AppearanceColors,
        SettingsDestination.AppearanceSurfaces,
        SettingsDestination.AppearanceAddressBar,
        SettingsDestination.AppearanceWebsites -> SettingsDestination.Appearance
        SettingsDestination.BrowserStartup,
        SettingsDestination.BrowserFavorites,
        SettingsDestination.BrowserWebsites,
        SettingsDestination.BrowserLinks -> SettingsDestination.Browser
        SettingsDestination.LinkRouting,
        SettingsDestination.Translations -> SettingsDestination.BrowserWebsites
        SettingsDestination.ExternalLinkPreview -> SettingsDestination.BrowserLinks
        SettingsDestination.PlayerMode -> SettingsDestination.Player
        SettingsDestination.ToppingCatalog -> SettingsDestination.Userscripts
        SettingsDestination.AddressBarLongPressActions,
        SettingsDestination.AddressBarActions,
        SettingsDestination.MenuActions -> SettingsDestination.GestureAddressBar
        SettingsDestination.LinkLongPress,
        SettingsDestination.LinkPeekSwipe,
        SettingsDestination.LinkPeekActions -> SettingsDestination.GestureLinks
        SettingsDestination.GestureLinks,
        SettingsDestination.GestureAddressBar,
        SettingsDestination.GestureControls -> SettingsDestination.Gestures
        else -> SettingsDestination.Home
    }

    fun isBackNavigation(from: SettingsDestination, to: SettingsDestination): Boolean =
        to == SettingsDestination.Home || to == parentDestination(from) ||
            to == SettingsDestination.TabsAndGestures && from in setOf(
                SettingsDestination.TabOverview,
                SettingsDestination.AddressBarLongPressActions,
                SettingsDestination.AddressBarActions,
                SettingsDestination.MenuActions,
                SettingsDestination.LinkPeekActions,
            )
}
