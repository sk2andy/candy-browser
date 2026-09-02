package dev.sk2andy.materialbrowser.sync

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

object SyncTabRules {
    const val MAX_TABS = 1_000
    const val MAX_TITLE_LENGTH = 4_096
    const val MAX_URL_LENGTH = 16_384

    fun isValidCandyId(value: String): Boolean = value.matches(IDENTIFIER)

    fun outboundTab(tab: SyncTab, isPrivate: Boolean): SyncTab? {
        if (isPrivate) return null
        return normalizeTab(tab)
    }

    fun normalizeSnapshot(snapshot: SyncTabSnapshot): SyncTabSnapshot? {
        if (snapshot.tabs.size > MAX_TABS) return null
        val ids = mutableSetOf<String>()
        val tabs = snapshot.tabs.mapNotNull(::normalizeTab)
        if (tabs.size != snapshot.tabs.size || tabs.any { !ids.add(it.candyId) }) return null
        return snapshot.copy(
            tabs = tabs.sortedWith(compareBy<SyncTab>({ it.windowId }, { it.index }, { it.candyId })),
        )
    }

    fun apply(profile: SyncProfile, mutation: SyncPendingMutation): SyncMutationResult {
        if (mutation.targetDeviceId != profile.deviceId) return SyncMutationResult.InvalidTab
        return when (mutation) {
            is SyncPendingMutation.Open -> if (mutation.isPrivate) {
                SyncMutationResult.InvalidTab
            } else {
                open(profile, mutation.tab)
            }
            is SyncPendingMutation.Navigate -> navigate(profile, mutation)
            is SyncPendingMutation.Close -> close(profile, mutation.candyId)
            is SyncPendingMutation.Reorder -> reorder(profile, mutation.orderedCandyIds)
            is SyncPendingMutation.SetPinned -> setPinned(profile, mutation)
        }
    }

    private fun open(profile: SyncProfile, tab: SyncTab): SyncMutationResult {
        val safeTab = normalizeTab(tab) ?: return SyncMutationResult.InvalidTab
        if (profile.tabs.any { it.candyId == safeTab.candyId }) return SyncMutationResult.AlreadyApplied
        if (profile.tabs.size >= MAX_TABS) return SyncMutationResult.InvalidTab
        val existing = if (safeTab.active) {
            profile.tabs.map { current ->
                current.copy(active = current.active && current.windowId != safeTab.windowId)
            }
        } else {
            profile.tabs
        }
        return SyncMutationResult.Applied(profile.copy(tabs = normalizeIndices(existing + safeTab)))
    }

    private fun navigate(
        profile: SyncProfile,
        mutation: SyncPendingMutation.Navigate,
    ): SyncMutationResult {
        val index = profile.tabs.indexOfFirst { it.candyId == mutation.candyId }
        if (index < 0) return SyncMutationResult.MissingTab
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(mutation.url) ?: return SyncMutationResult.InvalidTab
        if (safeUrl.length > MAX_URL_LENGTH) return SyncMutationResult.InvalidTab
        val updated = profile.tabs[index].copy(
            title = mutation.title.take(MAX_TITLE_LENGTH),
            url = safeUrl,
        )
        if (updated == profile.tabs[index]) return SyncMutationResult.AlreadyApplied
        return SyncMutationResult.Applied(
            profile.copy(tabs = profile.tabs.toMutableList().also { it[index] = updated }),
        )
    }

    private fun close(profile: SyncProfile, candyId: String): SyncMutationResult {
        val removed = profile.tabs.firstOrNull { it.candyId == candyId }
            ?: return SyncMutationResult.AlreadyApplied
        var remaining = profile.tabs.filterNot { it.candyId == candyId }
        if (removed.active && remaining.none { it.windowId == removed.windowId && it.active }) {
            val replacement = remaining
                .filter { it.windowId == removed.windowId }
                .minWithOrNull(compareBy<SyncTab>({ kotlin.math.abs(it.index - removed.index) }, { it.index }))
            if (replacement != null) {
                remaining = remaining.map { tab ->
                    if (tab.candyId == replacement.candyId) tab.copy(active = true) else tab
                }
            }
        }
        return SyncMutationResult.Applied(
            profile.copy(tabs = normalizeIndices(remaining)),
        )
    }

    private fun reorder(profile: SyncProfile, orderedCandyIds: List<String>): SyncMutationResult {
        if (orderedCandyIds.size != profile.tabs.size || orderedCandyIds.toSet().size != orderedCandyIds.size) {
            return SyncMutationResult.InvalidTab
        }
        val byId = profile.tabs.associateBy(SyncTab::candyId)
        if (orderedCandyIds.toSet() != byId.keys) return SyncMutationResult.InvalidTab
        val reordered = orderedCandyIds.mapIndexed { index, candyId ->
            requireNotNull(byId[candyId]).copy(index = index)
        }
        return if (reordered == profile.tabs) {
            SyncMutationResult.AlreadyApplied
        } else {
            SyncMutationResult.Applied(profile.copy(tabs = reordered))
        }
    }

    private fun setPinned(
        profile: SyncProfile,
        mutation: SyncPendingMutation.SetPinned,
    ): SyncMutationResult {
        val index = profile.tabs.indexOfFirst { it.candyId == mutation.candyId }
        if (index < 0) return SyncMutationResult.MissingTab
        if (profile.tabs[index].pinned == mutation.pinned) return SyncMutationResult.AlreadyApplied
        val tabs = profile.tabs.toMutableList()
        tabs[index] = tabs[index].copy(pinned = mutation.pinned)
        return SyncMutationResult.Applied(profile.copy(tabs = tabs))
    }

    private fun normalizeTab(tab: SyncTab): SyncTab? {
        if (!isValidCandyId(tab.candyId) || tab.windowId < 0 || tab.index < 0) return null
        if (tab.groupId != null && tab.groupId < 0) return null
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(tab.url) ?: return null
        if (safeUrl.length > MAX_URL_LENGTH) return null
        return tab.copy(title = tab.title.take(MAX_TITLE_LENGTH), url = safeUrl)
    }

    private fun normalizeIndices(tabs: List<SyncTab>): List<SyncTab> = tabs
        .groupBy(SyncTab::windowId)
        .toSortedMap()
        .flatMap { (_, windowTabs) ->
            windowTabs.sortedWith(compareBy<SyncTab>({ it.index }, { it.candyId }))
                .mapIndexed { index, tab -> tab.copy(index = index) }
        }

    private val IDENTIFIER = Regex("[A-Za-z0-9._:-]{1,128}")
}
