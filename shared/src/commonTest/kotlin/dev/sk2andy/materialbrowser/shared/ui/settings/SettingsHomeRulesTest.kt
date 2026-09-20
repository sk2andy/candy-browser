package dev.sk2andy.materialbrowser.shared.ui.settings

import dev.sk2andy.materialbrowser.ui.SettingsDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsHomeRulesTest {
    @Test
    fun androidHomeGroupsDestinationsByTask() {
        val items = SettingsHomeRules.items(hasFirefoxExtensions = true)

        assertEquals(
            listOf(
                SettingsDestination.Search,
                SettingsDestination.TabsAndGestures,
                SettingsDestination.Browser,
                SettingsDestination.Downloads,
                SettingsDestination.Appearance,
                SettingsDestination.SiteCapsules,
                SettingsDestination.Userscripts,
                null,
                SettingsDestination.ProtectionAndData,
                SettingsDestination.Sync,
                SettingsDestination.AboutLegal,
            ),
            items.map(SettingsHomeItem::destination),
        )
        assertEquals(
            listOf(
                SettingsHomeGroup.Browsing,
                SettingsHomeGroup.Personalization,
                SettingsHomeGroup.PrivacyData,
                SettingsHomeGroup.About,
            ),
            items.map(SettingsHomeItem::group).distinct(),
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
