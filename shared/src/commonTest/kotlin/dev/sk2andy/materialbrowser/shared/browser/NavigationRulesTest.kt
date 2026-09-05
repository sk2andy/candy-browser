package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationRulesTest {
    @Test
    fun committedNavigationUpdatesBoundedState() {
        val url = BrowserUrl("https://example.com")
        val loading = NavigationRules.reduce(NavigationState(), NavigationEvent.Started(url))
        val committed = NavigationRules.reduce(
            loading,
            NavigationEvent.Committed(
                url = url,
                title = "Example",
                canGoBack = true,
                canGoForward = false,
            ),
        )

        assertTrue(loading.isLoading)
        assertFalse(committed.isLoading)
        assertEquals(url, committed.currentUrl)
        assertEquals("Example", committed.title)
        assertTrue(committed.canGoBack)
    }
}
