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
        protectedRuntimeTabIds: Set<String> = emptySet(),
    ): SyncedTabReconciliation {
        require(maxTabs >= 0)
        val runtimeProfileId = profileId(profile.deviceId)
        val existingProfileTabs = existingTabs.filter { it.profileId == runtimeProfileId }
        val protectedTabs = existingProfileTabs.filter { it.id in protectedRuntimeTabIds }
        val protectedCandyIds = protectedTabs.mapNotNullTo(hashSetOf(), BrowserTab::syncCandyId)
        val existingByCandyId = existingProfileTabs
            .filter { it.syncCandyId != null }
            .associateBy { requireNotNull(it.syncCandyId) }
        val orderedRemoteTabs = profile.tabs
            .sortedWith(compareBy<SyncTab>({ it.windowId }, { it.index }, { it.candyId }))
        val remoteIds = orderedRemoteTabs.mapTo(linkedSetOf(), SyncTab::candyId)
        val retainableLocalTabs = existingProfileTabs.filter { tab ->
            val candyId = tab.syncCandyId
            tab.id in protectedRuntimeTabIds ||
                candyId != null && candyId !in remoteIds && (
                    tab.url == BLANK_URL || candyId in locallyPendingCandyIds
                )
        }
        val retainedLocalTabs = (
            existingProfileTabs.filter { it.id in protectedRuntimeTabIds } + retainableLocalTabs
            ).distinctBy(BrowserTab::id).take(maxTabs)
        val visibleRemoteTabs = orderedRemoteTabs
            .filterNot { it.candyId in protectedCandyIds }
            .take((maxTabs - retainedLocalTabs.size).coerceAtLeast(0))
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
        val ordered = (remoteTabs + retainedLocalTabs).distinctBy(BrowserTab::id)
        val retainedRuntimeIds = ordered.mapTo(hashSetOf(), BrowserTab::id)
        return SyncedTabReconciliation(
            tabs = ordered,
            removedRuntimeTabIds = existingProfileTabs
                .filterNot { it.id in retainedRuntimeIds }
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
        protectedRuntimeTabIds: Set<String> = emptySet(),
    ): SyncedTabReconciliation {
        require(maxTabs >= 0)
        val localTabs = existingTabs.filter { it.profileId == localProfileId }
        val existingByCandyId = localTabs
            .filter { !it.isIncognito && it.syncCandyId != null }
            .associateBy { requireNotNull(it.syncCandyId) }
        val orderedRemoteTabs = profile.tabs
            .sortedWith(compareBy<SyncTab>({ it.windowId }, { it.index }, { it.candyId }))
        val remoteIds = orderedRemoteTabs.mapTo(linkedSetOf(), SyncTab::candyId)
        val protectedCandyIds = localTabs.asSequence()
            .filter { it.id in protectedRuntimeTabIds }
            .mapNotNull(BrowserTab::syncCandyId)
            .toSet()
        val retainableLocalTabs = localTabs.filter { tab ->
            val candyId = tab.syncCandyId
            tab.id in protectedRuntimeTabIds ||
                tab.isIncognito ||
                candyId == null ||
                candyId !in remoteIds && (
                    tab.url == BLANK_URL ||
                        BrowserUriPolicy.normalizeHttpUrl(tab.url) == null ||
                        candyId in locallyPendingCandyIds
                )
        }
        val retainedLocalTabs = (
            localTabs.filter { it.id in protectedRuntimeTabIds } + retainableLocalTabs
            ).distinctBy(BrowserTab::id).take(maxTabs)
        val visibleRemoteTabs = orderedRemoteTabs
            .filterNot { it.candyId in protectedCandyIds }
            .take((maxTabs - retainedLocalTabs.size).coerceAtLeast(0))
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
