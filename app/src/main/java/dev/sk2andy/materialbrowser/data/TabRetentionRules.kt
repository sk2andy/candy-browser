package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab

internal object TabRetentionRules {
    fun expiredTabIds(
        tabs: List<BrowserTab>,
        selectedTabId: String?,
        lifetime: InactiveTabLifetime,
        nowMillis: Long,
    ): Set<String> {
        val maxAgeMillis = lifetime.maxAgeMillis ?: return emptySet()
        if (tabs.size <= 1) return emptySet()
        val protectedTabId = selectedTabId
            ?.takeIf { selected -> tabs.any { it.id == selected } }
            ?: tabs.maxByOrNull(BrowserTab::lastAccessedAt)?.id
        val cutoff = nowMillis - maxAgeMillis
        return tabs.asSequence()
            .filter { it.id != protectedTabId }
            .filter(TabDeletionRules::canDelete)
            .filter { it.lastAccessedAt > 0L && it.lastAccessedAt < cutoff }
            .map(BrowserTab::id)
            .toSet()
    }
}
