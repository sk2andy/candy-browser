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
        assertFalse(FaviconRules.changedSite("https://example.com", "https://example.com:443"))
    }

    @Test
    fun invalidatesFaviconWhenHostChanges() {
        assertTrue(
            FaviconRules.changedSite(
                previousUrl = "https://example.com",
                newUrl = "https://developer.android.com",
            ),
        )
        assertTrue(FaviconRules.changedSite("https://example.com", "http://example.com"))
        assertTrue(FaviconRules.changedSite("https://example.com", "https://example.com:8443"))
    }

    @Test
    fun ignoresBlankAndMalformedUrls() {
        assertFalse(FaviconRules.changedSite(BLANK_URL, "https://example.com"))
        assertFalse(FaviconRules.changedSite("not a url", "https://example.com"))
        assertTrue(FaviconRules.changedSite("https://example.com", BLANK_URL))
        assertTrue(FaviconRules.changedSite("https://example.com", "not a url"))
    }

    @Test
    fun acceptsIconOnlyForCurrentDocument() {
        assertTrue(FaviconRules.belongsToDocument(
            "https://example.com/article#section",
            "https://example.com/article#another-section",
        ))
        assertFalse(FaviconRules.belongsToDocument(
            "https://example.com/next",
            "https://example.com/article",
        ))
        assertFalse(FaviconRules.belongsToDocument(
            "https://example.com/article?version=2",
            "https://example.com/article?version=1",
        ))
        assertFalse(FaviconRules.belongsToDocument(
            "https://example.com/article",
            "https://other.example.com/article",
        ))
        assertFalse(FaviconRules.belongsToDocument(BLANK_URL, "https://example.com/article"))
        assertFalse(FaviconRules.belongsToDocument("https://example.com/article", null))
    }
}
