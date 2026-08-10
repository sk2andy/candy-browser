package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab

internal object TabReorderingRules {
    fun canMove(tabs: List<BrowserTab>, tabId: String): Boolean =
        destinationRange(tabs, tabId)?.let { range -> range.first != range.last } == true

    fun destinationRange(
        tabs: List<BrowserTab>,
        tabId: String,
    ): IntRange? {
        val sourceIndex = tabs.indexOfFirst { it.id == tabId }
        if (sourceIndex < 0) return null
        val pinnedCount = tabs.count(BrowserTab::isPinned)
        return if (tabs[sourceIndex].isPinned) {
            0 until pinnedCount
        } else {
            pinnedCount until tabs.size
        }
    }

    fun clampedDestinationIndex(
        tabs: List<BrowserTab>,
        tabId: String,
        requestedIndex: Int,
    ): Int? {
        val range = destinationRange(tabs, tabId) ?: return null
        if (range.isEmpty()) return null
        return requestedIndex.coerceIn(range.first, range.last)
    }

    fun move(
        tabs: List<BrowserTab>,
        tabId: String,
        requestedIndex: Int,
    ): List<BrowserTab> {
        val sourceIndex = tabs.indexOfFirst { it.id == tabId }
        val destinationIndex = clampedDestinationIndex(tabs, tabId, requestedIndex)
            ?: return tabs
        if (sourceIndex == destinationIndex) return tabs

        return tabs.toMutableList().apply {
            val tab = removeAt(sourceIndex)
            add(destinationIndex, tab)
        }
    }
}
