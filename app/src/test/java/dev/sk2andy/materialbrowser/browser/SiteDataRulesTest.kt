package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SiteDataRulesTest {
    @Test
    fun `target keeps profile privacy and navigation identity with normalized origin`() {
        val target = SiteDataRules.target(
            tab = BrowserTab(id = "tab", profileId = "work", isIncognito = true, lastAccessedAt = 1),
            pageUrl = "https://EXAMPLE.com:443/path?q=one#two",
            navigationGeneration = 7,
            sharesStorage = false,
        )!!

        assertEquals("https://example.com", target.origin)
        assertEquals("example.com", target.host)
        assertEquals("work", target.profileId)
        assertEquals(true, target.isPrivate)
        assertEquals(7, target.navigationGeneration)
        assertFalse(SiteDataRules.isCurrent(target, target.copy(profileId = "home")))
        assertFalse(SiteDataRules.isCurrent(target, target.copy(isPrivate = false)))
        assertFalse(SiteDataRules.isCurrent(target, target.copy(navigationGeneration = 8)))
        assertFalse(SiteDataRules.isCurrent(target, target.copy(url = "https://example.com/other")))
        assertFalse(SiteDataRules.isCurrent(target, null))
    }

    @Test
    fun `non-web and malformed origins cannot become deletion targets`() {
        listOf("about:blank", "file:///tmp/page", "https://user@example.com", "https://example.com:99999")
            .forEach { url ->
                assertNull(
                    url,
                    SiteDataRules.target(BrowserTab(id = "tab", lastAccessedAt = 1), url, 1, false),
                )
            }
    }
}
