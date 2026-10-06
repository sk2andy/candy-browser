package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.data.BrowserMemorySettings

internal object GeckoHistoryCacheRules {
    fun contentViewerTimeoutSeconds(settings: BrowserMemorySettings): Int {
        val lifetimeSeconds = settings.normalized().historyCacheLifetimeMinutes * 60
        // Gecko expires three generations, with each timer interval equal to half this pref.
        // Scale to the longest nominal lifetime; busy or suspended processes can delay delivery.
        return lifetimeSeconds * 2 / 3
    }
}
