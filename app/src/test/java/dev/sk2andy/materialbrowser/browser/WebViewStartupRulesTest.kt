package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewStartupRulesTest {
    @Test
    fun `cold incoming link starts WebView asynchronously independent of preview UI`() {
        assertTrue(
            WebViewStartupRules.shouldStartAsynchronously(
                isColdStart = true,
                hasIncomingBrowserRequest = true,
                isWebViewProcessUnused = true,
            ),
        )
    }

    @Test
    fun `warm launcher and initialized process skip asynchronous startup`() {
        assertFalse(
            WebViewStartupRules.shouldStartAsynchronously(
                isColdStart = false,
                hasIncomingBrowserRequest = true,
                isWebViewProcessUnused = true,
            ),
        )
        assertFalse(
            WebViewStartupRules.shouldStartAsynchronously(
                isColdStart = true,
                hasIncomingBrowserRequest = false,
                isWebViewProcessUnused = true,
            ),
        )
        assertFalse(
            WebViewStartupRules.shouldStartAsynchronously(
                isColdStart = true,
                hasIncomingBrowserRequest = true,
                isWebViewProcessUnused = false,
            ),
        )
    }
}
