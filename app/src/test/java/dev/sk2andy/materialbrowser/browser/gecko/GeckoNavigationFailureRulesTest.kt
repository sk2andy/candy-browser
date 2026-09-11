package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineFailureKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.geckoview.WebRequestError

class GeckoNavigationFailureRulesTest {
    @Test
    fun `offline error stays distinct from unknown host`() {
        assertEquals(
            BrowserEngineFailureKind.Offline,
            GeckoNavigationFailureRules.kindForErrorCode(WebRequestError.ERROR_OFFLINE),
        )
        assertEquals(
            BrowserEngineFailureKind.UnknownHost,
            GeckoNavigationFailureRules.kindForErrorCode(WebRequestError.ERROR_UNKNOWN_HOST),
        )
    }

    @Test
    fun `other transport errors remain generic`() {
        assertEquals(
            BrowserEngineFailureKind.Other,
            GeckoNavigationFailureRules.kindForErrorCode(WebRequestError.ERROR_NET_TIMEOUT),
        )
    }
}
