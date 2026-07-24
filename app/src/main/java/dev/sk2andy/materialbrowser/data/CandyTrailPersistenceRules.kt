package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab

object CandyTrailPersistenceRules {
    fun canPersist(tab: BrowserTab): Boolean = !tab.isIncognito

    fun persistentTabIds(tabs: List<BrowserTab>): Set<String> = tabs.asSequence()
        .filter(::canPersist)
        .mapTo(linkedSetOf(), BrowserTab::id)
}
