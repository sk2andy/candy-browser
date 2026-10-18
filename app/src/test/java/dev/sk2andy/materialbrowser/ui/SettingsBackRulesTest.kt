package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackRulesTest {
    @Test
    fun `link routing returns to browser settings`() {
        assertEquals(
            SettingsDestination.Browser,
            SettingsBackRules.parentDestination(SettingsDestination.LinkRouting),
        )
    }

    @Test
    fun `nested action pages return to gestures and controls`() {
        listOf(
            SettingsDestination.AddressBarLongPressActions,
            SettingsDestination.AddressBarActions,
            SettingsDestination.MenuActions,
            SettingsDestination.LinkPeekActions,
        ).forEach { destination ->
            assertEquals(
                SettingsDestination.Gestures,
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
            SettingsDestination.Tabs,
            SettingsDestination.Gestures,
            SettingsDestination.TabsAndGestures,
            SettingsDestination.Appearance,
            SettingsDestination.AppIcon,
            SettingsDestination.Browser,
            SettingsDestination.Player,
            SettingsDestination.Downloads,
            SettingsDestination.Userscripts,
            SettingsDestination.SiteCapsules,
            SettingsDestination.Sync,
            SettingsDestination.ProtectionAndData,
            SettingsDestination.HistoryAndData,
            SettingsDestination.DeveloperOptions,
            SettingsDestination.AboutLegal,
        ).forEach { destination ->
            assertEquals(SettingsDestination.Home, SettingsBackRules.parentDestination(destination))
        }
    }
}
