package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsNavigationRules

internal object SettingsBackRules {
    fun parentDestination(destination: SettingsDestination): SettingsDestination =
        SettingsNavigationRules.parentDestination(destination)
}
