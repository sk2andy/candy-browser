package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

internal object GeckoBootstrapRecoveryRules {
    fun hasPoisonedAddress(url: String): Boolean =
        CandyPrivacyHostContract.isBootstrapDocumentUrl(url)

    fun resetPoisonedTab(tab: BrowserTab): BrowserTab =
        if (hasPoisonedAddress(tab.url)) {
            tab.copy(url = BLANK_URL, title = "")
        } else {
            tab
        }

    fun recoverUrl(trail: CandyTrail): String? {
        trail.nodes.firstOrNull { node -> node.id == trail.currentNodeId }
            ?.url
            ?.let(BrowserUriPolicy::normalizeHttpUrl)
            ?.let { return it }
        return trail.nodes
            .sortedWith(compareByDescending<CandyTrailNode> {
                it.visitedAt
            }.thenBy { it.id })
            .firstNotNullOfOrNull { node -> BrowserUriPolicy.normalizeHttpUrl(node.url) }
    }
}
