package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.data.BrowserMemorySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class GeckoHistoryCacheRulesTest {
    @Test
    fun `default expiry allows at most five nominal minutes across three generations`() {
        assertEquals(200, GeckoHistoryCacheRules.contentViewerTimeoutSeconds(BrowserMemorySettings()))
    }

    @Test
    fun `short expiry accounts for timer generations`() {
        assertEquals(
            40,
            GeckoHistoryCacheRules.contentViewerTimeoutSeconds(
                BrowserMemorySettings(historyCacheLifetimeMinutes = 1),
            ),
        )
    }

    @Test
    fun `untrusted lifetime stays within developer setting bounds`() {
        assertEquals(
            40,
            GeckoHistoryCacheRules.contentViewerTimeoutSeconds(
                BrowserMemorySettings(historyCacheLifetimeMinutes = Int.MIN_VALUE),
            ),
        )
        assertEquals(
            2_400,
            GeckoHistoryCacheRules.contentViewerTimeoutSeconds(
                BrowserMemorySettings(historyCacheLifetimeMinutes = Int.MAX_VALUE),
            ),
        )
    }
}
