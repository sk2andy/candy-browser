package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab

internal object TabPinningRules {
    fun orderedTabs(tabs: List<BrowserTab>): List<BrowserTab> =
        tabs.filter(BrowserTab::isPinned) + tabs.filterNot(BrowserTab::isPinned)

    fun withPinnedState(
        tabs: List<BrowserTab>,
        tabId: String,
        isPinned: Boolean,
    ): List<BrowserTab> {
        val target = tabs.firstOrNull { it.id == tabId } ?: return tabs
        if (target.isPinned == isPinned) return tabs

        val updatedTarget = target.copy(isPinned = isPinned)
        val remainingTabs = tabs.filterNot { it.id == tabId }
        if (isPinned) return listOf(updatedTarget) + remainingTabs

        val firstUnpinnedIndex = remainingTabs.indexOfFirst { !it.isPinned }
            .takeIf { it >= 0 }
            ?: remainingTabs.size
        return buildList(tabs.size) {
            addAll(remainingTabs.take(firstUnpinnedIndex))
            add(updatedTarget)
            addAll(remainingTabs.drop(firstUnpinnedIndex))
        }
    }
}
