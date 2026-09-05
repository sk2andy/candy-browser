package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab

internal object TabDeletionRules {
    fun canDelete(tab: BrowserTab): Boolean = !tab.isPinned

    fun deletableTabIds(tabs: List<BrowserTab>): Set<String> = tabs
        .asSequence()
        .filter(::canDelete)
        .mapTo(linkedSetOf(), BrowserTab::id)
}
