package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.TabStack
import dev.sk2andy.materialbrowser.browser.isFreshBlankTab

data class SnoozedTab(
    val tab: BrowserTab,
    val wakeAtMillis: Long,
    val createdAtMillis: Long,
)

data class SnoozeUndoToken(
    val tabId: String,
    val appliedSnoozedTab: SnoozedTab,
    val originalIndex: Int,
    val originalSelectedTabId: String,
    val selectedTabIdAfterSnooze: String,
    val replacementTabId: String?,
    val touchedTabBefore: BrowserTab?,
    val touchedTabAfter: BrowserTab?,
    val originalTabStack: TabStack? = null,
    val tabStackAfterSnooze: TabStack? = null,
)

enum class SnoozePreset {
    LaterToday,
    Tomorrow,
    NextWeek,
}

object SnoozeRules {
    fun canSnooze(tab: BrowserTab, wakeAtMillis: Long, nowMillis: Long): Boolean =
        !tab.isIncognito && wakeAtMillis > nowMillis
}

object SnoozeMutationRules {
    fun rescheduled(
        tabs: List<SnoozedTab>,
        tabId: String,
        wakeAtMillis: Long,
        nowMillis: Long,
    ): List<SnoozedTab>? {
        if (wakeAtMillis <= nowMillis || tabs.none { it.tab.id == tabId }) return null
        return tabs.map { snoozed ->
            if (snoozed.tab.id == tabId) snoozed.copy(wakeAtMillis = wakeAtMillis)
            else snoozed
        }.sortedWith(compareBy<SnoozedTab>({ it.wakeAtMillis }, { it.tab.id }))
    }

    fun deleted(tabs: List<SnoozedTab>, tabId: String): List<SnoozedTab>? =
        tabs.takeIf { list -> list.any { it.tab.id == tabId } }
            ?.filterNot { it.tab.id == tabId }
}

data class SnoozeUndoResult(
    val tabs: List<BrowserTab>,
    val selectedTabId: String,
    val snoozedTabs: List<SnoozedTab>,
    val restoredTab: BrowserTab,
    val removedReplacementTabId: String?,
)

object SnoozeUndoRules {
    fun undo(
        tabs: List<BrowserTab>,
        selectedTabId: String,
        snoozedTabs: List<SnoozedTab>,
        token: SnoozeUndoToken,
        maxTabs: Int,
    ): SnoozeUndoResult? {
        if (tabs.any { it.id == token.tabId }) return null
        val pending = snoozedTabs.firstOrNull { it == token.appliedSnoozedTab } ?: return null
        val updatedTabs = tabs.toMutableList()
        val replacementIndex = token.replacementTabId?.let { replacementId ->
            updatedTabs.indexOfFirst { it.id == replacementId && it.isFreshBlankTab }
                .takeIf { it >= 0 }
        }
        val removedReplacementId = replacementIndex?.let { index ->
            updatedTabs.removeAt(index).id
        }
        if (updatedTabs.size >= maxTabs) return null

        val touchedBefore = token.touchedTabBefore
        val touchedAfter = token.touchedTabAfter
        if (touchedBefore != null && touchedAfter != null) {
            val touchedIndex = updatedTabs.indexOfFirst { it == touchedAfter }
            if (touchedIndex >= 0) updatedTabs[touchedIndex] = touchedBefore
        }
        val restoredTab = pending.tab.copy(
            isIncognito = false,
            progress = 0,
            isLoading = false,
            canGoBack = false,
            canGoForward = false,
            blockedCount = 0,
            error = null,
        )
        updatedTabs.add(token.originalIndex.coerceIn(0, updatedTabs.size), restoredTab)
        val orderedTabs = TabPinningRules.orderedTabs(updatedTabs)
        val restoredSelection = if (
            token.originalSelectedTabId == token.tabId &&
            selectedTabId == token.selectedTabIdAfterSnooze
        ) {
            token.tabId
        } else {
            selectedTabId.takeIf { current -> orderedTabs.any { it.id == current } }
                ?: token.tabId
        }
        return SnoozeUndoResult(
            tabs = orderedTabs,
            selectedTabId = restoredSelection,
            snoozedTabs = snoozedTabs.filterNot { it.tab.id == token.tabId },
            restoredTab = restoredTab,
            removedReplacementTabId = removedReplacementId,
        )
    }
}

data class SnoozeRestoreResult(
    val tabs: List<BrowserTab>,
    val completedTabIds: Set<String>,
    val restoredTabIds: Set<String>,
)

data class SnoozeStartupSnapshot(
    val tabs: List<BrowserTab>,
    val snoozedTabs: List<SnoozedTab>,
)

object SnoozeRestoreRules {
    fun settleStartupRestore(
        originalTabs: List<BrowserTab>,
        originalSnoozedTabs: List<SnoozedTab>,
        restoredTabs: List<BrowserTab>,
        remainingSnoozedTabs: List<SnoozedTab>,
        snapshotPersisted: Boolean,
    ): SnoozeStartupSnapshot = if (snapshotPersisted) {
        SnoozeStartupSnapshot(restoredTabs, remainingSnoozedTabs)
    } else {
        SnoozeStartupSnapshot(originalTabs, originalSnoozedTabs)
    }

    fun restoreDue(
        tabs: List<BrowserTab>,
        snoozedTabs: List<SnoozedTab>,
        profiles: List<BrowserProfile>,
        activeProfileId: String,
        nowMillis: Long,
        maxTabs: Int,
    ): SnoozeRestoreResult {
        val restored = tabs.toMutableList()
        val completedIds = linkedSetOf<String>()
        val restoredIds = linkedSetOf<String>()
        val profileIds = profiles.mapTo(linkedSetOf(), BrowserProfile::id)
        val fallbackProfileId = activeProfileId.takeIf(profileIds::contains)
            ?: profiles.firstOrNull()?.id
            ?: return SnoozeRestoreResult(tabs, emptySet(), emptySet())

        snoozedTabs.asSequence()
            .filter { it.wakeAtMillis <= nowMillis }
            .sortedWith(compareBy<SnoozedTab>({ it.wakeAtMillis }, { it.tab.id }))
            .forEach { snoozed ->
                val tab = snoozed.tab
                when {
                    tab.isIncognito -> completedIds += tab.id
                    restored.any { it.id == tab.id } -> completedIds += tab.id
                    restored.size >= maxTabs -> Unit
                    else -> {
                        restored += tab.copy(
                            lastAccessedAt = nowMillis,
                            profileId = tab.profileId.takeIf(profileIds::contains)
                                ?: fallbackProfileId,
                            isIncognito = false,
                            progress = 0,
                            isLoading = false,
                            canGoBack = false,
                            canGoForward = false,
                            blockedCount = 0,
                            error = null,
                        )
                        completedIds += tab.id
                        restoredIds += tab.id
                    }
                }
            }

        return SnoozeRestoreResult(
            tabs = TabPinningRules.orderedTabs(restored),
            completedTabIds = completedIds,
            restoredTabIds = restoredIds,
        )
    }
}
