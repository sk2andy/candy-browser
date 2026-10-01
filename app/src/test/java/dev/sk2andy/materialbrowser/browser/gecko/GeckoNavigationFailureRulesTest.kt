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

    @Test
    fun `only HTTPS upgrade failure exposes HTTP exception`() {
        assertEquals(
            BrowserEngineFailureKind.HttpsOnly,
            GeckoNavigationFailureRules.kindForErrorCode(WebRequestError.ERROR_HTTPS_ONLY),
        )
        listOf(
            WebRequestError.ERROR_SECURITY_BAD_CERT,
            WebRequestError.ERROR_SECURITY_SSL,
            WebRequestError.ERROR_BAD_HSTS_CERT,
        ).forEach { errorCode ->
            assertEquals(
                BrowserEngineFailureKind.Other,
                GeckoNavigationFailureRules.kindForErrorCode(errorCode),
            )
        }
    }
}
