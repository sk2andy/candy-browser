package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserInitialNavigationRulesTest {
    @Test
    fun `initial blank cannot replace a web page waiting for its first load`() {
        listOf(
            BrowserEngineEventType.NavigationStarted,
            BrowserEngineEventType.NavigationCommitted,
            BrowserEngineEventType.StateChanged,
        ).forEach { type ->
            assertTrue(
                BrowserInitialNavigationRules.shouldIgnoreInitialBlank(
                    currentUrl = "https://example.com/restored",
                    pendingUrl = "https://example.com/restored",
                    eventUrl = BLANK_URL,
                    eventType = type,
                ),
            )
        }
    }

    @Test
    fun `blank tabs popups and explicit blank navigation remain observable`() {
        listOf(
            BLANK_URL to "https://example.com/new",
            "https://example.com/restored" to null,
            "https://example.com/restored" to BLANK_URL,
            "https://example.com/restored" to "moz-extension://example/options.html",
        ).forEach { (current, pending) ->
            assertFalse(
                BrowserInitialNavigationRules.shouldIgnoreInitialBlank(
                    currentUrl = current,
                    pendingUrl = pending,
                    eventUrl = BLANK_URL,
                    eventType = BrowserEngineEventType.StateChanged,
                ),
            )
        }
    }

    @Test
    fun `real navigation failures and renderer termination remain observable`() {
        listOf(
            BrowserEngineEventType.NavigationFailed,
            BrowserEngineEventType.Crashed,
            BrowserEngineEventType.Closed,
        ).forEach { type ->
            assertFalse(
                BrowserInitialNavigationRules.shouldIgnoreInitialBlank(
                    currentUrl = "https://example.com/restored",
                    pendingUrl = "https://example.com/restored",
                    eventUrl = BLANK_URL,
                    eventType = type,
                ),
            )
        }
        assertFalse(
            BrowserInitialNavigationRules.shouldIgnoreInitialBlank(
                currentUrl = "https://example.com/restored",
                pendingUrl = "https://example.com/restored",
                eventUrl = "https://example.com/redirect",
                eventType = BrowserEngineEventType.NavigationStarted,
            ),
        )
    }
}
