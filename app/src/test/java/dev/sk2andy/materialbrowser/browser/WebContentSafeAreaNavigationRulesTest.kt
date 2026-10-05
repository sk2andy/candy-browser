package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebContentSafeAreaNavigationRulesTest {
    @Test
    fun `another host resets automatic safe area decisions`() {
        assertTrue(WebContentSafeAreaNavigationRules.shouldReset("https://lidl.de/", "https://github.com/"))
        assertTrue(WebContentSafeAreaNavigationRules.shouldReset("https://a.example.com/", "https://b.example.com/"))
    }

    @Test
    fun `same host keeps reload route and scheme decisions`() {
        assertFalse(WebContentSafeAreaNavigationRules.shouldReset("https://GITHUB.com./a", "http://github.com/b"))
        assertFalse(WebContentSafeAreaNavigationRules.shouldReset("https://github.com/", "https://github.com/"))
    }

    @Test
    fun `bootstrap and invalid targets do not reset web state`() {
        assertFalse(WebContentSafeAreaNavigationRules.shouldReset(null, "https://github.com/"))
        assertFalse(WebContentSafeAreaNavigationRules.shouldReset("about:blank", "https://github.com/"))
        assertFalse(WebContentSafeAreaNavigationRules.shouldReset("https://lidl.de/", "about:blank"))
        assertFalse(WebContentSafeAreaNavigationRules.shouldReset("https://lidl.de/", "invalid"))
    }
}
