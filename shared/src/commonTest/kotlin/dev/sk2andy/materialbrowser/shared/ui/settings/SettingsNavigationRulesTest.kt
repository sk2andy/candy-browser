package dev.sk2andy.materialbrowser.shared.ui.settings

import dev.sk2andy.materialbrowser.ui.SettingsDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsNavigationRulesTest {
    @Test
    fun selectionPagesReturnToTheirOwningSettings() {
        assertEquals(SettingsDestination.MenuActions, SettingsNavigationRules.parentDestination(SettingsDestination.MenuStyle))
        assertEquals(SettingsDestination.MenuActions, SettingsNavigationRules.parentDestination(SettingsDestination.MenuContentLayout))
        assertEquals(SettingsDestination.Tabs, SettingsNavigationRules.parentDestination(SettingsDestination.TabOverview))
    }

    @Test
    fun expandedPagesReturnToTheirOwnersWithReverseMotion() {
        val parents = mapOf(
            SettingsDestination.TabStackFolder to SettingsDestination.Tabs,
            SettingsDestination.ClosedTabUndo to SettingsDestination.Tabs,
            SettingsDestination.AppearanceColors to SettingsDestination.Appearance,
            SettingsDestination.AppearanceSurfaces to SettingsDestination.Appearance,
            SettingsDestination.AppearanceAddressBar to SettingsDestination.Appearance,
            SettingsDestination.ExternalLinkPreview to SettingsDestination.BrowserLinks,
            SettingsDestination.PlayerMode to SettingsDestination.Player,
            SettingsDestination.LinkLongPress to SettingsDestination.Gestures,
            SettingsDestination.LinkPeekSwipe to SettingsDestination.Gestures,
            SettingsDestination.LinkPeekActions to SettingsDestination.Gestures,
            SettingsDestination.AddressBarActions to SettingsDestination.Gestures,
            SettingsDestination.AddressBarLongPressActions to SettingsDestination.Gestures,
            SettingsDestination.MenuActions to SettingsDestination.Gestures,
        )
        parents.forEach { (child, parent) ->
            assertEquals(parent, SettingsNavigationRules.parentDestination(child))
            assertTrue(SettingsNavigationRules.isBackNavigation(child, parent))
            assertFalse(SettingsNavigationRules.isBackNavigation(parent, child))
        }
    }

    @Test
    fun everyDestinationHasAnAcyclicPathHome() {
        SettingsDestination.entries.forEach { start ->
            val visited = mutableSetOf<SettingsDestination>()
            var destination = start
            while (destination != SettingsDestination.Home) {
                assertTrue(visited.add(destination), "Cycle from $start at $destination")
                val parent = SettingsNavigationRules.parentDestination(destination)
                assertTrue(SettingsNavigationRules.isBackNavigation(destination, parent))
                destination = parent
            }
            assertEquals(SettingsDestination.Home, destination)
        }
    }

    @Test
    fun legacyCombinedControlsRetainBackMotion() {
        assertTrue(SettingsNavigationRules.isBackNavigation(
            SettingsDestination.MenuActions, SettingsDestination.TabsAndGestures,
        ))
        assertTrue(SettingsNavigationRules.isBackNavigation(
            SettingsDestination.TabOverview, SettingsDestination.TabsAndGestures,
        ))
        assertFalse(SettingsNavigationRules.isBackNavigation(
            SettingsDestination.Appearance, SettingsDestination.AppearanceSurfaces,
        ))
    }
}
