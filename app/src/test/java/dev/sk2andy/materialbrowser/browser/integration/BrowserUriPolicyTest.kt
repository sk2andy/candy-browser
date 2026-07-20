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
    }

    @Test
    fun rejectsNonWebAndMalformedUrls() {
        assertNull(BrowserUriPolicy.normalizeHttpUrl("javascript:alert(1)"))
        assertNull(BrowserUriPolicy.normalizeHttpUrl("https:///missing-host"))
        assertNull(BrowserUriPolicy.normalizeHttpUrl("https://example.com/line\nbreak"))
    }

    @Test
    fun externalSchemePolicyRejectsInternalSchemes() {
        assertTrue(BrowserUriPolicy.canOpenExternally("mailto"))
        assertTrue(BrowserUriPolicy.canOpenExternally("custom-app"))
        assertFalse(BrowserUriPolicy.canOpenExternally("https"))
        assertFalse(BrowserUriPolicy.canOpenExternally("file"))
        assertFalse(BrowserUriPolicy.canOpenExternally("javascript"))
    }
}
