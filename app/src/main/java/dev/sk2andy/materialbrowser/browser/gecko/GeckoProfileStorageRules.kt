package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.BrowserTab

/** Gecko sessions use the stable Candy profile ID as their storage context. */
internal object GeckoProfileStorageRules {
    /**
     * Gecko's null context is the shared default jar. Only explicitly isolated profiles get a
     * named jar; private mode remains separately partitioned by Gecko and receives its own stable
     * context so private data cannot accidentally join another profile's private session.
     */
    fun contextId(profileId: String, isolationEnabled: Boolean, isPrivate: Boolean): String? =
        when {
            isPrivate -> "private:$profileId"
            isolationEnabled -> profileId
            else -> null
        }

    fun requiresContextDeletion(isolationEnabled: Boolean): Boolean = isolationEnabled

    fun affectedTabIds(tabs: List<BrowserTab>, profileId: String): Set<String> =
        tabs.filter { it.profileId == profileId }.mapTo(linkedSetOf(), BrowserTab::id)

    fun contextChanged(before: BrowserTab, after: BrowserTab): Boolean =
        before.profileId != after.profileId || before.isIncognito != after.isIncognito

    fun privacyStorageKey(tab: BrowserTab): String =
        if (tab.isIncognito) "private:${tab.profileId}" else tab.profileId

    fun movedTabs(
        tabs: List<BrowserTab>,
        sourceProfileId: String,
        targetProfileId: String,
    ): List<BrowserTab> = tabs.map { tab ->
        if (tab.profileId == sourceProfileId) tab.copy(profileId = targetProfileId) else tab
    }
}
