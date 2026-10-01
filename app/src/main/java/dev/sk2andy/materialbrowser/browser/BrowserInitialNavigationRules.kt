package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType

internal object BrowserInitialNavigationRules {
    fun shouldIgnoreInitialBlank(
        currentUrl: String?,
        pendingUrl: String?,
        eventUrl: String?,
        eventType: BrowserEngineEventType,
    ): Boolean = eventUrl == BLANK_URL &&
        BrowserUriPolicy.normalizeHttpUrl(currentUrl) != null &&
        BrowserUriPolicy.normalizeHttpUrl(pendingUrl) != null &&
        (eventType == BrowserEngineEventType.NavigationStarted ||
            eventType == BrowserEngineEventType.NavigationCommitted ||
            eventType == BrowserEngineEventType.StateChanged)
}
