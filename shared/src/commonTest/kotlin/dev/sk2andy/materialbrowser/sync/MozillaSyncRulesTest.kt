package dev.sk2andy.materialbrowser.sync

import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.SyncProvider
import dev.sk2andy.materialbrowser.browser.SyncedProfileRuntimeRules
import dev.sk2andy.materialbrowser.browser.isSynced
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MozillaSyncRulesTest {
    @Test
    fun `remote device has provider specific profile identity`() {
        val profile = MozillaSyncRules.profile(MozillaSyncDevice("desktop", "Laptop", emptyList()))

        assertEquals("mozilla:desktop", profile.id)
        assertEquals("desktop", profile.syncedDeviceId)
        assertEquals("Laptop", profile.syncedDisplayName)
        assertEquals(SyncProvider.Mozilla, profile.syncProvider)
        assertTrue(profile.isSynced)
        assertNull(SyncedProfileRuntimeRules.deviceId(profile))
    }

    @Test
    fun `publishes only eligible tabs from the bound local profile`() {
        val tab = BrowserTab(
            id = "normal",
            lastAccessedAt = 42,
            profileId = "work",
            title = "Page",
            url = "https://example.com/",
            syncCandyId = "candy-local-tab",
        )
        val tabs = listOf(
            tab,
            tab.copy(id = "private", isIncognito = true),
            tab.copy(id = "other", profileId = "synced:desktop"),
            tab.copy(id = "popup"),
            tab.copy(id = "blank", url = "about:blank"),
            tab.copy(id = "file", url = "file:///secret"),
        )

        assertEquals(
            listOf(MozillaSyncTab("Page", "https://example.com/", 42)),
            MozillaSyncRules.localTabs(tabs, "work", setOf("popup")),
        )
    }
}
