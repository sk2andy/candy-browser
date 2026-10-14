package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackRulesTest {
    @Test
    fun `nested action pages return to tabs and gestures`() {
        listOf(
            SettingsDestination.AddressBarLongPressActions,
            SettingsDestination.AddressBarActions,
            SettingsDestination.MenuActions,
            SettingsDestination.LinkPeekActions,
        ).forEach { destination ->
            assertEquals(
                SettingsDestination.TabsAndGestures,
                SettingsBackRules.parentDestination(destination),
            )
        }
    }

    @Test
    fun `topping catalog returns to userscripts`() {
        assertEquals(
            SettingsDestination.Userscripts,
            SettingsBackRules.parentDestination(SettingsDestination.ToppingCatalog),
        )
    }

    @Test
    fun `top level pages return home`() {
        listOf(
            SettingsDestination.Search,
            SettingsDestination.TabsAndGestures,
            SettingsDestination.Appearance,
            SettingsDestination.Browser,
            SettingsDestination.Player,
            SettingsDestination.Downloads,
            SettingsDestination.Userscripts,
            SettingsDestination.SiteCapsules,
            SettingsDestination.Sync,
            SettingsDestination.ProtectionAndData,
            SettingsDestination.DeveloperOptions,
            SettingsDestination.AboutLegal,
        ).forEach { destination ->
            assertEquals(SettingsDestination.Home, SettingsBackRules.parentDestination(destination))
        }
    }
}
