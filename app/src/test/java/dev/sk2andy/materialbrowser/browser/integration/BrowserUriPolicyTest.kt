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
    fun linkPeekPreviewKeepsNavigationInsideSafeWebSchemes() {
        assertFalse(LinkPeekPreviewNavigationPolicy.shouldBlock("https://example.com/redirect"))
        assertFalse(LinkPeekPreviewNavigationPolicy.shouldBlock("http://localhost:8080/preview"))
        assertTrue(LinkPeekPreviewNavigationPolicy.shouldBlock("intent://open/#Intent;end"))
        assertTrue(LinkPeekPreviewNavigationPolicy.shouldBlock("javascript:alert(1)"))
        assertTrue(LinkPeekPreviewNavigationPolicy.shouldBlock("file:///data/local/private"))
        assertTrue(LinkPeekPreviewNavigationPolicy.shouldBlock(null))
    }
}
