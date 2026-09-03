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
