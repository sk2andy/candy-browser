package dev.sk2andy.materialbrowser.sync

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.SyncProvider
import dev.sk2andy.materialbrowser.shared.browser.BrowserUrlRules

object MozillaSyncRules {
    fun profileId(deviceId: String): String = "mozilla:$deviceId"

    fun profile(device: MozillaSyncDevice): BrowserProfile = BrowserProfile(
        id = profileId(device.id),
        emoji = "🦊",
        syncedDeviceId = device.id,
        syncedDisplayName = device.displayName,
        syncProvider = SyncProvider.Mozilla,
    )

    fun localTabs(
        tabs: List<BrowserTab>,
        profileId: String,
        excludedTabIds: Set<String>,
    ): List<MozillaSyncTab> = tabs.mapNotNull { tab ->
        if (tab.profileId != profileId || tab.isIncognito || tab.id in excludedTabIds) {
            return@mapNotNull null
        }
        val url = BrowserUrlRules.normalizeHttpUrl(tab.url)?.value ?: return@mapNotNull null
        MozillaSyncTab(title = tab.title, url = url, lastUsedMillis = tab.lastAccessedAt)
    }
}
