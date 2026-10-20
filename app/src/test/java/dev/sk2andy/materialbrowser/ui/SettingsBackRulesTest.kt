package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackRulesTest {
    @Test
    fun `selection pages return to the menu and tab settings`() {
        assertEquals(SettingsDestination.MenuActions, SettingsBackRules.parentDestination(SettingsDestination.MenuStyle))
        assertEquals(SettingsDestination.MenuActions, SettingsBackRules.parentDestination(SettingsDestination.MenuContentLayout))
        assertEquals(SettingsDestination.Tabs, SettingsBackRules.parentDestination(SettingsDestination.TabOverview))
    }
    @Test
    fun `link routing returns to website behavior`() {
        assertEquals(
            SettingsDestination.BrowserWebsites,
            SettingsBackRules.parentDestination(SettingsDestination.LinkRouting),
        )
    }

    @Test
    fun `nested action pages return to their focused section`() {
        listOf(
            SettingsDestination.AddressBarLongPressActions,
            SettingsDestination.AddressBarActions,
            SettingsDestination.MenuActions,
        ).forEach { destination ->
            assertEquals(
                SettingsDestination.GestureAddressBar,
                SettingsBackRules.parentDestination(destination),
            )
        }
        listOf(SettingsDestination.LinkLongPress, SettingsDestination.LinkPeekSwipe, SettingsDestination.LinkPeekActions)
            .forEach { assertEquals(SettingsDestination.GestureLinks, SettingsBackRules.parentDestination(it)) }
        assertEquals(SettingsDestination.TabLifecycle, SettingsBackRules.parentDestination(SettingsDestination.ClosedTabUndo))
        assertEquals(SettingsDestination.BrowserWebsites, SettingsBackRules.parentDestination(SettingsDestination.Translations))
        listOf(SettingsDestination.TabLifecycle, SettingsDestination.ProfileOptions)
            .forEach { assertEquals(SettingsDestination.Tabs, SettingsBackRules.parentDestination(it)) }
        listOf(SettingsDestination.GestureLinks, SettingsDestination.GestureAddressBar, SettingsDestination.GestureControls)
            .forEach { assertEquals(SettingsDestination.Gestures, SettingsBackRules.parentDestination(it)) }
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
    @Test
    fun `appearance details return to theme and browser details return to browser`() {
        listOf(
            SettingsDestination.AppearanceSurfaces,
            SettingsDestination.AppearanceAddressBar,
            SettingsDestination.AppearanceWebsites,
        ).forEach { destination ->
            assertEquals(SettingsDestination.Appearance, SettingsBackRules.parentDestination(destination))
        }
        listOf(
            SettingsDestination.BrowserStartup,
            SettingsDestination.BrowserFavorites,
            SettingsDestination.BrowserWebsites,
            SettingsDestination.BrowserLinks,
        ).forEach { destination ->
            assertEquals(SettingsDestination.Browser, SettingsBackRules.parentDestination(destination))
        }
    }

}
