package dev.sk2andy.materialbrowser.shared.ui.settings

import dev.sk2andy.materialbrowser.ui.SettingsDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsHomeRulesTest {
    @Test
    fun androidHomeKeepsExistingDestinationOrder() {
        val items = SettingsHomeRules.items(hasFirefoxExtensions = true)

        assertEquals(
            listOf(
                SettingsDestination.Search,
                SettingsDestination.Sync,
                SettingsDestination.TabsAndGestures,
                SettingsDestination.Appearance,
                SettingsDestination.Browser,
                SettingsDestination.Downloads,
                SettingsDestination.Userscripts,
                null,
                SettingsDestination.SiteCapsules,
                SettingsDestination.ProtectionAndData,
                SettingsDestination.AboutLegal,
            ),
            items.map(SettingsHomeItem::destination),
        )
        assertTrue(items.single { it.destination == null }.isFirefoxExtensionsAction)
    }

    @Test
    fun unsupportedPlatformOmitsFirefoxExtensionAction() {
        val items = SettingsHomeRules.items(hasFirefoxExtensions = false)

        assertFalse(items.any(SettingsHomeItem::isFirefoxExtensionsAction))
        assertNull(items.firstOrNull { it.destination == null })
    }

    @Test
    fun developerOptionsAppearOnlyAfterUnlock() {
        assertFalse(
            SettingsHomeRules.items(hasFirefoxExtensions = false)
                .any { it.destination == SettingsDestination.DeveloperOptions },
        )
        val unlocked = SettingsHomeRules.items(
            hasFirefoxExtensions = false,
            hasDeveloperOptions = true,
        )

        assertEquals(
            SettingsDestination.DeveloperOptions,
            unlocked[unlocked.lastIndex - 1].destination,
        )
        assertEquals(SettingsDestination.AboutLegal, unlocked.last().destination)
    }

    @Test
    fun iosSharedSettingsExposeImplementedDestinations() {
        assertTrue(CandySettingsRouteRules.isEnabled(SettingsDestination.Userscripts))
        assertTrue(CandySettingsRouteRules.isEnabled(SettingsDestination.Appearance))
        assertTrue(CandySettingsRouteRules.isEnabled(SettingsDestination.Search))
        assertFalse(CandySettingsRouteRules.isEnabled(SettingsDestination.Downloads))
    }
}
