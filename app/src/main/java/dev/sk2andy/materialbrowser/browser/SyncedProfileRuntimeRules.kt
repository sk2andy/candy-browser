package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncTab

data class SyncedTabNavigation(
    val runtimeTabId: String,
    val url: String,
)

data class SyncedTabReconciliation(
    val tabs: List<BrowserTab>,
    val removedRuntimeTabIds: Set<String>,
    val navigations: List<SyncedTabNavigation>,
)

object SyncedProfileRuntimeRules {
    private const val PROFILE_PREFIX = "synced:"

    fun profileId(deviceId: String): String = "$PROFILE_PREFIX$deviceId"

    fun deviceId(profile: BrowserProfile?): String? = profile
        ?.takeIf(BrowserProfile::isSynced)
        ?.syncedDeviceId

    fun runtimeProfile(
        profile: SyncProfile,
        iconEmoji: String,
        selectedTabId: String? = null,
    ): BrowserProfile = BrowserProfile(
        id = profileId(profile.deviceId),
        emoji = iconEmoji,
        selectedTabId = selectedTabId,
        isolationEnabled = false,
        syncedDeviceId = profile.deviceId,
        syncedDisplayName = profile.displayName,
        syncedIconCatalogId = profile.icon.catalogId,
        syncedIconEmoji = iconEmoji,
        syncedIconAccentHue = profile.icon.accentHue,
    )

    fun reconcile(
        profile: SyncProfile,
        existingTabs: List<BrowserTab>,
        nowMillis: Long,
        maxTabs: Int = MAX_TABS,
        locallyPendingCandyIds: Set<String> = emptySet(),
    ): SyncedTabReconciliation {
        require(maxTabs >= 0)
        val runtimeProfileId = profileId(profile.deviceId)
        val existingByCandyId = existingTabs
            .filter { it.profileId == runtimeProfileId && it.syncCandyId != null }
            .associateBy { requireNotNull(it.syncCandyId) }
        val visibleRemoteTabs = profile.tabs
            .sortedWith(compareBy<SyncTab>({ it.windowId }, { it.index }, { it.candyId }))
            .take(maxTabs)
        val remoteIds = visibleRemoteTabs.mapTo(linkedSetOf(), SyncTab::candyId)
        val remoteTabs = visibleRemoteTabs
            .map { remote ->
                val existing = existingByCandyId[remote.candyId]
                if (existing == null) {
                    BrowserTab(
                        id = runtimeTabId(profile.deviceId, remote.candyId),
                        lastAccessedAt = nowMillis,
                        profileId = runtimeProfileId,
                        isPinned = remote.pinned,
                        title = remote.title,
                        url = remote.url,
                        isLoading = remote.url != BLANK_URL,
                        syncCandyId = remote.candyId,
                    )
                } else {
                    existing.copy(
                        profileId = runtimeProfileId,
                        isIncognito = false,
                        isPinned = remote.pinned,
                        title = remote.title,
                        url = remote.url,
                        syncCandyId = remote.candyId,
                    )
                }
            }
        val retainedLocalTabs = existingByCandyId
            .filter { (candyId, tab) ->
                candyId !in remoteIds &&
                    (tab.url == BLANK_URL || candyId in locallyPendingCandyIds)
            }
            .values
            .sortedBy(BrowserTab::lastAccessedAt)
        val ordered = (remoteTabs + retainedLocalTabs).take(maxTabs)
        val retainedIds = retainedLocalTabs.mapTo(hashSetOf()) { requireNotNull(it.syncCandyId) }
        return SyncedTabReconciliation(
            tabs = ordered,
            removedRuntimeTabIds = existingByCandyId
                .filterKeys { it !in remoteIds && it !in retainedIds }
                .values
                .mapTo(linkedSetOf(), BrowserTab::id),
            navigations = ordered.mapNotNull { reconciled ->
                val previous = existingByCandyId[reconciled.syncCandyId]
                reconciled.takeIf { previous != null && previous.url != it.url }
                    ?.let { SyncedTabNavigation(it.id, it.url) }
            },
        )
    }

    fun reconcileLinkedProfile(
        profile: SyncProfile,
        localProfileId: String,
        existingTabs: List<BrowserTab>,
        nowMillis: Long,
        maxTabs: Int = MAX_TABS,
        locallyPendingCandyIds: Set<String> = emptySet(),
    ): SyncedTabReconciliation {
        require(maxTabs >= 0)
        val localTabs = existingTabs.filter { it.profileId == localProfileId }
        val existingByCandyId = localTabs
            .filter { !it.isIncognito && it.syncCandyId != null }
            .associateBy { requireNotNull(it.syncCandyId) }
        val orderedRemoteTabs = profile.tabs
            .sortedWith(compareBy<SyncTab>({ it.windowId }, { it.index }, { it.candyId }))
        val remoteIds = orderedRemoteTabs.mapTo(linkedSetOf(), SyncTab::candyId)
        val retainedLocalTabs = localTabs.filter { tab ->
            val candyId = tab.syncCandyId
            tab.isIncognito ||
                candyId == null ||
                candyId !in remoteIds && (
                    tab.url == BLANK_URL ||
                        BrowserUriPolicy.normalizeHttpUrl(tab.url) == null ||
                        candyId in locallyPendingCandyIds
                    )
        }.take(maxTabs)
        val visibleRemoteTabs = orderedRemoteTabs.take(
            (maxTabs - retainedLocalTabs.size).coerceAtLeast(0),
        )
        val remoteTabs = visibleRemoteTabs.map { remote ->
            val existing = existingByCandyId[remote.candyId]
            if (existing == null) {
                BrowserTab(
                    id = runtimeTabId(profile.deviceId, remote.candyId),
                    lastAccessedAt = nowMillis,
                    profileId = localProfileId,
                    isPinned = remote.pinned,
                    title = remote.title,
                    url = remote.url,
                    isLoading = remote.url != BLANK_URL,
                    syncCandyId = remote.candyId,
                )
            } else {
                existing.copy(
                    profileId = localProfileId,
                    isIncognito = false,
                    isPinned = remote.pinned,
                    title = remote.title,
                    url = remote.url,
                )
            }
        }
        val ordered = (remoteTabs + retainedLocalTabs)
            .distinctBy(BrowserTab::id)
        val retainedRuntimeIds = ordered.mapTo(hashSetOf(), BrowserTab::id)
        return SyncedTabReconciliation(
            tabs = ordered,
            removedRuntimeTabIds = localTabs
                .filterNot { it.id in retainedRuntimeIds }
                .mapTo(linkedSetOf(), BrowserTab::id),
            navigations = remoteTabs.mapNotNull { reconciled ->
                val previous = existingByCandyId[reconciled.syncCandyId]
                reconciled.takeIf { previous != null && previous.url != it.url }
                    ?.let { SyncedTabNavigation(it.id, it.url) }
            },
        )
    }

    fun outboundTab(
        tab: BrowserTab,
        index: Int,
        selectedTabId: String,
    ): SyncTab? {
        if (tab.isIncognito) return null
        val candyId = tab.syncCandyId ?: return null
        val url = BrowserUriPolicy.normalizeHttpUrl(tab.url) ?: return null
        return SyncTab(
            candyId = candyId,
            windowId = 0,
            index = index,
            groupId = null,
            active = tab.id == selectedTabId,
            pinned = tab.isPinned,
            title = tab.title,
            url = url,
        )
    }

    private fun runtimeTabId(deviceId: String, candyId: String): String =
        "sync-tab:$deviceId:$candyId"
}
