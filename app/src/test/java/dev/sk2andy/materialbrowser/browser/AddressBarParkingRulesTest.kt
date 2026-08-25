package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBarParkingRulesTest {
    @Test
    fun `subdomains share registrable parking state`() {
        assertEquals(
            "example.co.uk",
            AddressBarParkingRules.domainForUrl("https://mobile.news.example.co.uk/story"),
        )
        assertTrue(
            AddressBarParkingRules.isAlwaysParked(
                "https://www.example.co.uk/story",
                setOf("example.co.uk"),
            ),
        )
        assertFalse(
            AddressBarParkingRules.isAlwaysParked(
                "https://other.example/story",
                setOf("example.co.uk"),
            ),
        )
    }

    @Test
    fun `non web pages cannot enable domain parking`() {
        assertNull(AddressBarParkingRules.domainForUrl(BLANK_URL))
        assertNull(AddressBarParkingRules.domainForUrl("file:///tmp/page.html"))
    }

    @Test
    fun `parking state is canonical bounded and reversible`() {
        val initial = (1..64).map { index -> "site$index.example" }

        val enabled = AddressBarParkingRules.withAlwaysParkedState(
            current = initial,
            domain = "MOBILE.Example",
            enabled = true,
        )
        val disabled = AddressBarParkingRules.withAlwaysParkedState(
            current = enabled,
            domain = "mobile.example",
            enabled = false,
        )

        assertEquals(64, enabled.size)
        assertEquals("mobile.example", enabled.last())
        assertFalse("site64.example" in enabled)
        assertFalse("mobile.example" in disabled)
    }

    @Test
    fun `global or matching domain parks after load`() {
        assertTrue(
            AddressBarParkingRules.shouldParkAfterLoad(
                alwaysParkAfterLoad = true,
                url = "https://other.example",
                alwaysParkedDomains = emptySet(),
            ),
        )
        assertTrue(
            AddressBarParkingRules.shouldParkAfterLoad(
                alwaysParkAfterLoad = false,
                url = "https://news.example",
                alwaysParkedDomains = setOf("news.example"),
            ),
        )
        assertFalse(
            AddressBarParkingRules.shouldParkAfterLoad(
                alwaysParkAfterLoad = false,
                url = "https://other.example",
                alwaysParkedDomains = setOf("news.example"),
            ),
        )
        assertFalse(
            AddressBarParkingRules.shouldParkAfterLoad(
                alwaysParkAfterLoad = true,
                url = BLANK_URL,
                alwaysParkedDomains = emptySet(),
            ),
        )
    }

    @Test
    fun `only current completed navigation may trigger parking`() {
        fun currentCompletion(
            destroyed: Boolean = false,
            currentWebView: Boolean = true,
            finishedGeneration: Int = 4,
            currentGeneration: Int = 4,
            callbackUrl: String = "https://news.example/",
            webViewUrl: String = callbackUrl,
            progress: Int = 100,
            pageUrl: String = callbackUrl,
        ) = AddressBarParkingRules.isCurrentPageCompletion(
            controllerDestroyed = destroyed,
            isCurrentWebView = currentWebView,
            finishedNavigationGeneration = finishedGeneration,
            currentNavigationGeneration = currentGeneration,
            callbackUrl = callbackUrl,
            currentWebViewUrl = webViewUrl,
            currentProgress = progress,
            currentPageUrl = pageUrl,
        )

        assertTrue(currentCompletion())
        assertFalse(currentCompletion(currentGeneration = 5))
        assertFalse(currentCompletion(currentWebView = false))
        assertFalse(currentCompletion(progress = 80))
        assertFalse(currentCompletion(webViewUrl = "https://other.example/"))
        assertFalse(currentCompletion(destroyed = true))
    }
}
