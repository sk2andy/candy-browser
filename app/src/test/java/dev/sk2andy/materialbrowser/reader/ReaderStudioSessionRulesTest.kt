package dev.sk2andy.materialbrowser.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderStudioSessionRulesTest {
    private val privateSession = ReaderStudioSession(
        tabId = "private-tab",
        sourceUrl = "https://private.example/article",
        isPrivate = true,
        requestId = 7,
    )

    @Test
    fun `tab switch closes session before privacy boundary can change`() {
        assertFalse(ReaderStudioSessionRules.shouldClose(privateSession, "private-tab"))
        assertTrue(ReaderStudioSessionRules.shouldClose(privateSession, "normal-tab"))
    }

    @Test
    fun `only current request result is accepted`() {
        assertTrue(ReaderStudioSessionRules.acceptsResult(privateSession, 7))
        assertFalse(ReaderStudioSessionRules.acceptsResult(privateSession, 6))
        assertFalse(ReaderStudioSessionRules.acceptsResult(null, 7))
    }

    @Test
    fun `reader source eligibility accepts only http and https`() {
        assertTrue(ReaderStudioSessionRules.isSupportedSource("https://example.com/article"))
        assertTrue(ReaderStudioSessionRules.isSupportedSource("HTTP://EXAMPLE.COM/article"))
        assertFalse(ReaderStudioSessionRules.isSupportedSource("file:///tmp/article.html"))
        assertFalse(ReaderStudioSessionRules.isSupportedSource("javascript:alert(1)"))
        assertFalse(ReaderStudioSessionRules.isSupportedSource("about:blank"))
    }
}
