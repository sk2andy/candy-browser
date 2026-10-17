package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab

/** Shared privacy boundary for platform reminder content; native schedulers own delivery limits. */
object SnoozeNotificationRules {
    fun eligibleTabs(
        tabs: List<BrowserTab>,
        restoredTabIds: Set<String>,
        profiles: List<BrowserProfile>,
    ): List<BrowserTab> {
        val protectedProfileIds = profiles.asSequence()
            .filter { it.protection != null }
            .mapTo(hashSetOf(), BrowserProfile::id)
        return tabs.filter { tab ->
            tab.id in restoredTabIds && !tab.isIncognito && tab.profileId !in protectedProfileIds
        }
    }

    fun eligibleSnoozedTabs(
        tabs: List<SnoozedTab>,
        profiles: List<BrowserProfile>,
    ): List<SnoozedTab> {
        val eligibleIds = eligibleTabs(
            tabs = tabs.map(SnoozedTab::tab),
            restoredTabIds = tabs.mapTo(hashSetOf()) { it.tab.id },
            profiles = profiles,
        ).mapTo(hashSetOf(), BrowserTab::id)
        return tabs.filter { it.tab.id in eligibleIds }
    }
}
