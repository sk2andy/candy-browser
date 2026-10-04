package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FederatedLoginRulesTest {
    @Test
    fun `detects cross site Google Identity Services SDK`() {
        assertEquals(
            FederatedLoginProvider.Google,
            FederatedLoginRules.providerForSubresource(
                requestUrl = "https://accounts.google.com/gsi/client",
                pageUrl = "https://www.example.com/login",
            ),
        )
        assertEquals(
            FederatedLoginProvider.Google,
            FederatedLoginRules.providerForSubresource(
                requestUrl = "https://accounts.google.com/gsi/fedcm.json?client_id=secret",
                pageUrl = "https://example.net/login",
            ),
        )
        assertEquals(
            FederatedLoginProvider.Google,
            FederatedLoginRules.providerForSubresource(
                requestUrl = "https://accounts.google.com/gsi/client",
                pageUrl = "http://127.0.0.1/login",
            ),
        )
    }

    @Test
    fun `rejects first party lookalike and generic oauth requests`() {
        assertNull(
            FederatedLoginRules.providerForSubresource(
                requestUrl = "https://accounts.google.com/gsi/client",
                pageUrl = "https://accounts.google.com/",
            ),
        )
        assertNull(
            FederatedLoginRules.providerForSubresource(
                requestUrl = "https://accounts.google.com.example/gsi/client",
                pageUrl = "https://example.net/",
            ),
        )
        assertNull(
            FederatedLoginRules.providerForSubresource(
                requestUrl = "https://login.example/oauth/client.js",
                pageUrl = "https://example.net/",
            ),
        )
        assertNull(
            FederatedLoginRules.providerForSubresource(
                requestUrl = "file:///accounts.google.com/gsi/client",
                pageUrl = "https://example.net/",
            ),
        )
        assertNull(
            FederatedLoginRules.providerForSubresource(
                requestUrl = "http://accounts.google.com/gsi/client",
                pageUrl = "https://example.net/",
            ),
        )
    }

    @Test
    fun `allows only known Google authentication navigation paths`() {
        assertTrue(FederatedLoginRules.isProviderNavigation("https://accounts.google.com/gsi/select"))
        assertTrue(
            FederatedLoginRules.isProviderNavigation(
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=secret",
            ),
        )
        assertFalse(FederatedLoginRules.isProviderNavigation("https://accounts.google.com/"))
        assertFalse(
            FederatedLoginRules.isProviderNavigation(
                "http://accounts.google.com/o/oauth2/v2/auth",
            ),
        )
        assertFalse(
            FederatedLoginRules.isProviderNavigation(
                "https://accounts.google.com.example/o/oauth2/v2/auth",
            ),
        )
    }

    @Test
    fun `preserves user opened secure Google authentication popups`() {
        listOf(
            "https://accounts.google.com/gsi/select?client_id=fixture",
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=fixture",
            "https://accounts.google.com/signin/oauth/consent",
        ).forEach { url ->
            assertTrue(
                url,
                FederatedLoginRules.shouldPreservePopupNavigation(
                    url = url,
                    target = BrowserEngineNavigationTarget.New,
                    hasUserGesture = true,
                    isNativePopup = false,
                ),
            )
        }
    }

    @Test
    fun `provider page includes account redirects but rejects insecure and lookalike hosts`() {
        assertTrue(FederatedLoginRules.isProviderPage("https://accounts.google.com/v3/signin/identifier"))
        assertTrue(FederatedLoginRules.isProviderPage("https://accounts.google.com/ServiceLogin"))
        assertFalse(FederatedLoginRules.isProviderPage("http://accounts.google.com/ServiceLogin"))
        assertFalse(FederatedLoginRules.isProviderPage("https://accounts.google.com.example/ServiceLogin"))
        assertFalse(FederatedLoginRules.isProviderPage("https://example.com/ServiceLogin"))
        assertFalse(FederatedLoginRules.isProviderPage("https://accounts.google.com/invalid path"))
    }

    @Test
    fun `preserves provider completion within accepted native popup without another gesture`() {
        assertTrue(
            FederatedLoginRules.shouldPreservePopupNavigation(
                url = "https://accounts.google.com/gsi/transform",
                target = BrowserEngineNavigationTarget.Current,
                hasUserGesture = false,
                isNativePopup = true,
            ),
        )
    }

    @Test
    fun `rejects insecure lookalike unrelated and malformed popup destinations`() {
        listOf(
            "http://accounts.google.com/gsi/select",
            "https://accounts.google.com.example/gsi/select",
            "https://accounts.google.com/",
            "https://login.example/oauth/authorize",
            "javascript:window.close()",
            "https://accounts.google.com/gsi/invalid path",
        ).forEach { url ->
            assertFalse(
                url,
                FederatedLoginRules.shouldPreservePopupNavigation(
                    url = url,
                    target = BrowserEngineNavigationTarget.New,
                    hasUserGesture = true,
                    isNativePopup = false,
                ),
            )
            assertFalse(
                url,
                FederatedLoginRules.shouldPreservePopupNavigation(
                    url = url,
                    target = BrowserEngineNavigationTarget.Current,
                    hasUserGesture = false,
                    isNativePopup = true,
                ),
            )
        }
    }

    @Test
    fun `requires gesture for new popup and native identity for current navigation`() {
        val url = "https://accounts.google.com/gsi/select"
        listOf(false, true).forEach { isNativePopup ->
            assertFalse(
                FederatedLoginRules.shouldPreservePopupNavigation(
                    url = url,
                    target = BrowserEngineNavigationTarget.New,
                    hasUserGesture = false,
                    isNativePopup = isNativePopup,
                ),
            )
            assertFalse(
                FederatedLoginRules.shouldPreservePopupNavigation(
                    url = url,
                    target = BrowserEngineNavigationTarget.None,
                    hasUserGesture = true,
                    isNativePopup = isNativePopup,
                ),
            )
        }
        assertFalse(
            FederatedLoginRules.shouldPreservePopupNavigation(
                url = url,
                target = BrowserEngineNavigationTarget.Current,
                hasUserGesture = true,
                isNativePopup = false,
            ),
        )
    }

    @Test
    fun `compatible user agent removes WebView identity but keeps mobile Chrome`() {
        val webViewUserAgent = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Build/AP4A; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
            "Chrome/138.0.0.0 Mobile Safari/537.36"

        val compatible = FederatedLoginRules.compatibleUserAgent(webViewUserAgent)

        assertEquals(
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Build/AP4A) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/138.0.0.0 Mobile Safari/537.36",
            compatible,
        )
        assertEquals(compatible, FederatedLoginRules.compatibleUserAgent(compatible))
    }
}
