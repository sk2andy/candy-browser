package dev.sk2andy.materialbrowser.browser.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalAppWebUrlRulesTest {
    @Test
    fun `adds root slash only to empty web paths`() {
        listOf(
            "https://github.com" to "https://github.com/",
            "http://example.com:8080" to "http://example.com:8080/",
            "HTTPS://EXAMPLE.COM" to "HTTPS://EXAMPLE.COM/",
            "https://bücher.example" to "https://bücher.example/",
            "http://[::1]:8080" to "http://[::1]:8080/",
            " https://github.com " to "https://github.com/",
        ).forEach { (url, expected) ->
            assertEquals(url, expected, ExternalAppWebUrlRules.forAndroidApp(url))
        }
    }

    @Test
    fun `root slash preserves raw query fragment and empty suffixes`() {
        listOf(
            "https://github.com?q=%2F+a%25#%2f" to "https://github.com/?q=%2F+a%25#%2f",
            "https://github.com#part%23?raw=%252f" to "https://github.com/#part%23?raw=%252f",
            "https://github.com?" to "https://github.com/?",
            "https://github.com#" to "https://github.com/#",
            "https://github.com?#" to "https://github.com/?#",
            "http://[::1]:8080?q=%25#%2F" to "http://[::1]:8080/?q=%25#%2F",
        ).forEach { (url, expected) ->
            assertEquals(url, expected, ExternalAppWebUrlRules.forAndroidApp(url))
        }
    }

    @Test
    fun `existing paths and their raw escaping stay unchanged`() {
        listOf(
            "https://github.com/",
            "https://github.com/sk2andy/candy-browser",
            "https://github.com/a%2Fb?q=%252f+raw#part%23",
            "https://github.com//a/../b?x=1#top",
            "http://localhost:8080/?#",
        ).forEach { url ->
            assertEquals(url, url, ExternalAppWebUrlRules.forAndroidApp(url))
        }
    }

    @Test
    fun `rejects unsafe malformed and non web inputs through shared policy`() {
        listOf(
            null,
            "",
            "github.com",
            "https:///missing-host",
            "https://user:secret@github.com",
            "https://github.com/line\nbreak",
            "https://github.com/with space",
            "https://github.com?bad=%xy",
            "javascript:alert(1)",
            "intent://github.com#Intent;scheme=https;end",
            "file:///private/data",
        ).forEach { url ->
            assertNull(url, ExternalAppWebUrlRules.forAndroidApp(url))
        }
    }

    @Test
    fun `Android target canonicalization leaves browser validation unchanged`() {
        val original = "https://github.com?q=%2F#part%23"

        assertEquals("https://github.com/?q=%2F#part%23", ExternalAppWebUrlRules.forAndroidApp(original))
        assertEquals(original, BrowserUriPolicy.normalizeHttpUrl(original))
    }
}
