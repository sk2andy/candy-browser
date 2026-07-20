package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaviconRulesTest {
    @Test
    fun keepsFaviconForRestoredUrlAndSameHostNavigation() {
        assertFalse(
            FaviconRules.changedSite(
                previousUrl = "https://example.com/article",
                newUrl = "https://example.com/article",
            ),
        )
        assertFalse(
            FaviconRules.changedSite(
                previousUrl = "https://example.com/article",
                newUrl = "https://example.com/next",
            ),
        )
    }

    @Test
    fun invalidatesFaviconWhenHostChanges() {
        assertTrue(
            FaviconRules.changedSite(
                previousUrl = "https://example.com",
                newUrl = "https://developer.android.com",
            ),
        )
    }

    @Test
    fun ignoresBlankAndMalformedUrls() {
        assertFalse(FaviconRules.changedSite(BLANK_URL, "https://example.com"))
        assertFalse(FaviconRules.changedSite("not a url", "https://example.com"))
        assertTrue(FaviconRules.changedSite("https://example.com", BLANK_URL))
        assertTrue(FaviconRules.changedSite("https://example.com", "not a url"))
    }
}
