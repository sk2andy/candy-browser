package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.isFreshBlankTab

internal data class ClosedTabUndoToken(
    val tab: BrowserTab,
    val originalIndex: Int,
    val wasSelected: Boolean,
    val selectedTabIdAfterClose: String,
    val replacementTabId: String?,
    val closedAtMillis: Long,
)

internal data class ClosedTabUndoResult(
    val tabs: List<BrowserTab>,
    val selectedTabId: String,
    val removedReplacementTabId: String?,
)

internal object ClosedTabUndoRules {
    const val DURATION_MILLIS = 3_000L

    fun restore(
        tabs: List<BrowserTab>,
        selectedTabId: String,
        activeProfileId: String,
        token: ClosedTabUndoToken,
        nowMillis: Long,
    ): ClosedTabUndoResult? {
        if (nowMillis < token.closedAtMillis ||
            nowMillis - token.closedAtMillis >= DURATION_MILLIS ||
            token.tab.profileId != activeProfileId ||
            tabs.any { it.id == token.tab.id }
        ) return null
        val replacement = tabs.firstOrNull { it.id == token.replacementTabId }
            ?.takeIf { it.isFreshBlankTab && !it.isPinned && it.profileId == token.tab.profileId &&
                it.isIncognito == token.tab.isIncognito
            }
        val restoredTab = token.tab.copy(
            progress = 0,
            isLoading = false,
            canGoBack = false,
            canGoForward = false,
            blockedCount = 0,
            error = null,
            httpStatusCode = null,
            failureKind = null,
        )
        val restoredTabs = tabs.filterNot { it.id == replacement?.id }.toMutableList()
        restoredTabs.add(token.originalIndex.coerceIn(0, restoredTabs.size), restoredTab)
        val profileTabs = TabPinningRules.orderedTabs(
            restoredTabs.filter { it.profileId == activeProfileId },
        ).iterator()
        val orderedTabs = restoredTabs.map { tab ->
            if (tab.profileId == activeProfileId) profileTabs.next() else tab
        }
        val selection = if (
            token.wasSelected && selectedTabId == token.selectedTabIdAfterClose ||
            selectedTabId == replacement?.id
        ) restoredTab.id else selectedTabId
        return ClosedTabUndoResult(orderedTabs, selection, replacement?.id)
    }
}
