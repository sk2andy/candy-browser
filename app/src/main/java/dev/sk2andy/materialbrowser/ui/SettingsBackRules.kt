package dev.sk2andy.materialbrowser.ui

internal object SettingsBackRules {
    fun parentDestination(destination: SettingsDestination): SettingsDestination = when (destination) {
        SettingsDestination.LinkRouting -> SettingsDestination.Browser
        SettingsDestination.ToppingCatalog -> SettingsDestination.Userscripts
        SettingsDestination.AppIcon -> SettingsDestination.Home
        SettingsDestination.AddressBarLongPressActions,
        SettingsDestination.AddressBarActions,
        SettingsDestination.MenuActions,
        SettingsDestination.LinkPeekActions -> SettingsDestination.Gestures
        else -> SettingsDestination.Home
    }
}
