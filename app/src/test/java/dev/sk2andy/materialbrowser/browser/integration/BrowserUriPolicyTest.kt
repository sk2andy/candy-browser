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
    fun `external navigation accepts user driven special scheme links`() {
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
    fun `web navigation stays in current webview`() {
        listOf("http", "https", "HTTPS").forEach { scheme ->
            assertFalse(
                ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                    scheme = scheme,
                    isForMainFrame = true,
                    hasGesture = true,
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
    fun `external navigation accepts special scheme server redirects`() {
        assertTrue(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "folo",
                isForMainFrame = true,
                hasGesture = false,
                isRedirect = true,
            ),
        )
        assertTrue(
            ExternalNavigationPolicy.shouldAttemptExternalLaunch(
                scheme = "intent",
                isForMainFrame = true,
                hasGesture = false,
                isRedirect = true,
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
