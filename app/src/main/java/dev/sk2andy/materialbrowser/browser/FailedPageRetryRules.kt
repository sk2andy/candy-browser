package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands

internal object FailedPageRetryRules {
    fun commandFor(
        tab: BrowserTab,
    ): BrowserEngineCommand? {
        if (tab.isLoading) return null
        val retryUrl = BrowserUriPolicy.normalizeHttpUrl(tab.url) ?: return null
        if (tab.error == null && tab.httpStatusCode == null) return null
        return BrowserEngineCommands.retryFailedPage(retryUrl)
    }
}
