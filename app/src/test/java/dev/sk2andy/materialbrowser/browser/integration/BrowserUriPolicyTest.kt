package dev.sk2andy.materialbrowser.browser.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUriPolicyTest {
    @Test
    fun acceptsHttpAndHttpsUrlsWithAuthorities() {
        assertEquals("https://example.com/path?q=1", BrowserUriPolicy.normalizeHttpUrl("https://example.com/path?q=1"))
        assertEquals("http://localhost:8080", BrowserUriPolicy.normalizeHttpUrl(" http://localhost:8080 "))
        assertEquals("https://bücher.example", BrowserUriPolicy.normalizeHttpUrl("https://bücher.example"))
        assertEquals("bücher.example", BrowserUriPolicy.displayHttpHost("https://bücher.example/path"))
        assertEquals("example.com", BrowserUriPolicy.displayHttpHost("https://www.example.com/path"))
    }

    @Test
    fun rejectsNonWebAndMalformedUrls() {
        assertNull(BrowserUriPolicy.normalizeHttpUrl("javascript:alert(1)"))
        assertNull(BrowserUriPolicy.normalizeHttpUrl("https:///missing-host"))
        assertNull(BrowserUriPolicy.normalizeHttpUrl("https://example.com/line\nbreak"))
        assertNull(BrowserUriPolicy.normalizeHttpUrl("https://user:secret@example.com/private"))
        assertNull(BrowserUriPolicy.normalizeHttpUrl("data:text/html,unsafe"))
        assertEquals("", BrowserUriPolicy.displayHttpHost("javascript:alert(1)"))
    }

    @Test
    fun externalSchemePolicyRejectsInternalSchemes() {
        assertTrue(BrowserUriPolicy.canOpenExternally("mailto"))
        assertTrue(BrowserUriPolicy.canOpenExternally("custom-app"))
        assertFalse(BrowserUriPolicy.canOpenExternally("https"))
        assertFalse(BrowserUriPolicy.canOpenExternally("file"))
        assertFalse(BrowserUriPolicy.canOpenExternally("javascript"))
    }

    @Test
    fun `normalizes safe explicit external addresses`() {
        assertEquals(
            "candy-app://callback?code=redacted",
            BrowserUriPolicy.normalizeExternalUri(" candy-app://callback?code=redacted "),
        )
        assertEquals(
            "mailto:test@example.com",
            BrowserUriPolicy.normalizeExternalUri("mailto:test@example.com"),
        )
        assertNull(BrowserUriPolicy.normalizeExternalUri("https://example.com"))
        assertNull(BrowserUriPolicy.normalizeExternalUri("javascript:alert(1)"))
        assertNull(BrowserUriPolicy.normalizeExternalUri("file:///data/local/private"))
        assertNull(BrowserUriPolicy.normalizeExternalUri("custom app://open"))
    }

    @Test
    fun `external navigation accepts user driven web and special scheme links`() {
        assertTrue(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "https",
                isForMainFrame = true,
                hasGesture = true,
                isRedirect = false,
            ),
        )
        assertTrue(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "folo",
                isForMainFrame = true,
                hasGesture = true,
                isRedirect = false,
            ),
        )
    }

    @Test
    fun `passive web navigation stays in current webview`() {
        listOf("http", "https", "HTTPS").forEach { scheme ->
            assertFalse(
                ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                    scheme = scheme,
                    isForMainFrame = true,
                    hasGesture = false,
                    isRedirect = false,
                ),
            )
        }
    }

    @Test
    fun `external navigation rejects missing scheme`() {
        assertFalse(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = null,
                isForMainFrame = true,
                hasGesture = true,
                isRedirect = false,
            ),
        )
    }

    @Test
    fun `external navigation rejects passive special scheme redirects without a grant`() {
        assertFalse(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "folo",
                isForMainFrame = true,
                hasGesture = false,
                isRedirect = true,
            ),
        )
        assertFalse(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "intent",
                isForMainFrame = true,
                hasGesture = false,
                isRedirect = true,
            ),
        )
    }

    @Test
    fun `external navigation accepts special scheme redirect with user grant`() {
        assertTrue(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "folo",
                isForMainFrame = true,
                hasGesture = false,
                isRedirect = true,
                hasUserNavigationGrant = true,
            ),
        )
    }

    @Test
    fun `external navigation accepts recent user grant for script redirect`() {
        assertTrue(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "candy-app",
                isForMainFrame = true,
                hasGesture = false,
                isRedirect = false,
                hasUserNavigationGrant = true,
            ),
        )
    }

    @Test
    fun `web redirect accepts recent user navigation grant only`() {
        assertTrue(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "https",
                isForMainFrame = true,
                hasGesture = false,
                isRedirect = true,
                hasUserNavigationGrant = true,
            ),
        )
        assertFalse(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "https",
                isForMainFrame = true,
                hasGesture = false,
                isRedirect = false,
                hasUserNavigationGrant = true,
            ),
        )
    }

    @Test
    fun `external navigation grant expires at deterministic boundary`() {
        assertTrue(
            ExternalNavigationPolicy.isUserNavigationGrantActive(
                expirationElapsedRealtime = 15_000L,
                nowElapsedRealtime = 15_000L,
            ),
        )
        assertFalse(
            ExternalNavigationPolicy.isUserNavigationGrantActive(
                expirationElapsedRealtime = 15_000L,
                nowElapsedRealtime = 15_001L,
            ),
        )
        assertFalse(
            ExternalNavigationPolicy.isUserNavigationGrantActive(
                expirationElapsedRealtime = null,
                nowElapsedRealtime = 1L,
            ),
        )
    }

    @Test
    fun `external navigation grant ignores stale terminal callback`() {
        val initialUrl = "https://example.com/start"
        val redirectUrl = "https://example.com/redirected"
        val started = requireNotNull(
            ExternalNavigationGrantRules.start(
                url = initialUrl,
                nowElapsedRealtime = 1_000L,
            ),
        )
        val redirected = requireNotNull(
            ExternalNavigationGrantRules.followRedirect(
                grant = started,
                url = redirectUrl,
                isForMainFrame = true,
                isRedirect = true,
                nowElapsedRealtime = 1_001L,
            ),
        )

        assertFalse(
            ExternalNavigationGrantRules.shouldClearForMainFrameCallback(
                grant = redirected,
                callbackUrl = initialUrl,
                currentWebViewUrl = redirectUrl,
                nowElapsedRealtime = 1_002L,
            ),
        )
        assertTrue(
            ExternalNavigationGrantRules.shouldClearForMainFrameCallback(
                grant = redirected,
                callbackUrl = redirectUrl,
                currentWebViewUrl = redirectUrl,
                nowElapsedRealtime = 1_002L,
            ),
        )
    }

    @Test
    fun `APK downloads accept taps and their authorized redirect`() {
        assertTrue(
            ApkDownloadNavigationRules.shouldRoute(
                url = "https://github.com/example/app/releases/latest/download/App.apk",
                isForMainFrame = true,
                hasGesture = true,
                isRedirect = false,
            ),
        )
        assertTrue(
            ApkDownloadNavigationRules.shouldRoute(
                url = "https://github.com/example/app/releases/download/v1/App.APK",
                isForMainFrame = true,
                hasGesture = false,
                isRedirect = true,
                hasUserNavigationGrant = true,
            ),
        )
    }

    @Test
    fun `APK downloads reject passive untrusted and subframe navigation`() {
        assertFalse(
            ApkDownloadNavigationRules.shouldRoute(
                url = "https://example.com/App.apk",
                isForMainFrame = true,
                hasGesture = false,
                isRedirect = true,
            ),
        )
        assertFalse(
            ApkDownloadNavigationRules.shouldRoute(
                url = "https://example.com/App.apk",
                isForMainFrame = false,
                hasGesture = true,
                isRedirect = false,
            ),
        )
        assertFalse(
            ApkDownloadNavigationRules.shouldRoute(
                url = "https://example.com/release-notes",
                isForMainFrame = true,
                hasGesture = true,
                isRedirect = false,
            ),
        )
        assertFalse(
            ApkDownloadNavigationRules.shouldRoute(
                url = "https://user:secret@example.com/App.apk",
                isForMainFrame = true,
                hasGesture = true,
                isRedirect = false,
            ),
        )
    }

    @Test
    fun `preview download grant follows only main frame redirects`() {
        val initialUrl = "https://example.com/releases/latest"
        val redirectUrl = "https://downloads.example.com/App.apk"
        val grant = requireNotNull(
            ExternalPreviewDownloadGrantRules.start(
                url = initialUrl,
                nowElapsedRealtime = 1_000L,
            ),
        )

        val redirected = requireNotNull(
            ExternalPreviewDownloadGrantRules.followRedirect(
                grant = grant,
                url = redirectUrl,
                isForMainFrame = true,
                isRedirect = true,
                nowElapsedRealtime = 1_001L,
            ),
        )

        assertFalse(
            ExternalPreviewDownloadGrantRules.canConsume(
                grant = redirected,
                url = initialUrl,
                nowElapsedRealtime = 1_001L,
            ),
        )
        assertTrue(
            ExternalPreviewDownloadGrantRules.canConsume(
                grant = redirected,
                url = redirectUrl,
                nowElapsedRealtime = 1_001L,
            ),
        )
        assertEquals(
            redirected,
            ExternalPreviewDownloadGrantRules.followRedirect(
                grant = redirected,
                url = "https://attacker.example/Injected.apk",
                isForMainFrame = false,
                isRedirect = true,
                nowElapsedRealtime = 1_002L,
            ),
        )
        assertNull(
            ExternalPreviewDownloadGrantRules.followRedirect(
                grant = redirected,
                url = "https://attacker.example/Injected.apk",
                isForMainFrame = true,
                isRedirect = false,
                nowElapsedRealtime = 1_002L,
            ),
        )
    }

    @Test
    fun `preview download grant rejects stale callbacks and expires`() {
        val redirectUrl = "https://downloads.example.com/App.apk"
        val started = requireNotNull(
            ExternalPreviewDownloadGrantRules.start(
                url = "https://example.com/releases/latest",
                nowElapsedRealtime = 1_000L,
            ),
        )
        val redirected = requireNotNull(
            ExternalPreviewDownloadGrantRules.followRedirect(
                grant = started,
                url = redirectUrl,
                isForMainFrame = true,
                isRedirect = true,
                nowElapsedRealtime = 1_001L,
            ),
        )

        assertFalse(
            ExternalPreviewDownloadGrantRules.shouldClearForMainFrameCallback(
                grant = redirected,
                callbackUrl = started.currentUrl,
                currentWebViewUrl = redirectUrl,
                nowElapsedRealtime = 1_002L,
            ),
        )
        assertTrue(
            ExternalPreviewDownloadGrantRules.shouldClearForMainFrameCallback(
                grant = redirected,
                callbackUrl = redirectUrl,
                currentWebViewUrl = redirectUrl,
                nowElapsedRealtime = 1_002L,
            ),
        )
        assertFalse(
            ExternalPreviewDownloadGrantRules.canConsume(
                grant = redirected,
                url = redirectUrl,
                nowElapsedRealtime =
                    1_000L + ExternalPreviewDownloadGrantRules.MAX_LIFETIME_MILLIS + 1L,
            ),
        )
    }

    @Test
    fun `external navigation rejects passive web redirects subframes and unsafe schemes`() {
        assertFalse(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "https",
                isForMainFrame = true,
                hasGesture = false,
                isRedirect = true,
            ),
        )
        assertFalse(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "folo",
                isForMainFrame = false,
                hasGesture = true,
                isRedirect = false,
            ),
        )
        assertFalse(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "javascript",
                isForMainFrame = true,
                hasGesture = true,
                isRedirect = false,
            ),
        )
        assertFalse(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "https",
                isForMainFrame = true,
                hasGesture = false,
                isRedirect = false,
                hasUserNavigationGrant = true,
            ),
        )
    }

    @Test
    fun linkPeekPreviewKeepsNavigationInsideSafeWebSchemes() {
        assertFalse(LinkPeekPreviewNavigationPolicy.shouldBlock("https://example.com/redirect"))
        assertFalse(LinkPeekPreviewNavigationPolicy.shouldBlock("http://localhost:8080/preview"))
        assertTrue(LinkPeekPreviewNavigationPolicy.shouldBlock("intent://open/#Intent;end"))
        assertTrue(LinkPeekPreviewNavigationPolicy.shouldBlock("javascript:alert(1)"))
        assertTrue(LinkPeekPreviewNavigationPolicy.shouldBlock("file:///data/local/private"))
        assertTrue(LinkPeekPreviewNavigationPolicy.shouldBlock(null))
    }
}
