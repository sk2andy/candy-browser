package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.userscript.UserScriptDependencyFetch
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptDependencyFetcher
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRejectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreasyForkInstallTest {
    @Test
    fun `offers installation after a gesture or a Greasy Fork source navigation`() {
        assertTrue(GreasyForkInstall.shouldOffer(URL, "https://example.com/", true))
        assertTrue(
            GreasyForkInstall.shouldOffer(URL, "https://greasyfork.org/en/scripts/476331", false),
        )
        assertTrue(GreasyForkInstall.shouldOffer(URL, "https://GREASYFORK.ORG/", false))
        assertFalse(GreasyForkInstall.shouldOffer(URL, "http://greasyfork.org/", false))
        assertFalse(GreasyForkInstall.shouldOffer(URL, "https://evil.greasyfork.org/", false))
        assertFalse(GreasyForkInstall.shouldOffer(URL, "https://greasyfork.org.evil.example/", false))
        assertFalse(GreasyForkInstall.shouldOffer(URL, null, false))
        assertFalse(
            GreasyForkInstall.shouldOffer(
                "https://example.com/script.user.js",
                "https://greasyfork.org/",
                true,
            ),
        )
    }

    @Test
    fun `recognizes current and versioned Greasy Fork install URLs`() {
        listOf(
            "https://update.greasyfork.org/scripts/476331/Insert%20code.user.js",
            "https://update.greasyfork.org/scripts/431691/1440718/Bypass%20All%20Shortlinks.user.js",
            "https://update.greasyfork.org/scripts/476331.user.js",
            "https://update.greasyfork.org/scripts/431691/1440718.user.js",
        ).forEach { assertTrue(it, GreasyForkInstall.isInstallUrl(it)) }
        listOf(
            "http://update.greasyfork.org/scripts/476331/Insert.user.js",
            "https://update.greasyfork.org.evil.example/scripts/476331/Insert.user.js",
            "https://update.greasyfork.org@evil.example/scripts/476331/Insert.user.js",
            "https://update.greasyfork.org:444/scripts/476331/Insert.user.js",
            "https://update.greasyfork.org/scripts/not-a-number/Insert.user.js",
            "https://update.greasyfork.org/scripts/476331/Insert.meta.js",
            "https://greasyfork.org/en/scripts/476331-insert-code",
        ).forEach { assertFalse(it, GreasyForkInstall.isInstallUrl(it)) }
    }

    @Test
    fun `fetches and parses an explicitly requested script`() {
        val result = GreasyForkInstall.fetch(URL, UserScriptDependencyFetcher { url, limit ->
            assertEquals(URL, url)
            assertEquals(UserScriptParser.MAX_SOURCE_BYTES, limit)
            UserScriptDependencyFetch(SCRIPT.toByteArray())
        }) as GreasyForkDownloadResult.Success

        assertEquals("Example", result.script.name)
        assertEquals(SCRIPT, result.script.source)
        assertEquals(listOf("https://example.com/*"), result.script.matchPatterns)
    }

    @Test
    fun `rejects invalid source and reports download failures`() {
        var fetches = 0
        val fetcher = UserScriptDependencyFetcher { _, _ ->
            fetches++
            null
        }
        assertEquals(
            GreasyForkDownloadResult.InvalidUrl,
            GreasyForkInstall.fetch("https://example.com/a.user.js", fetcher),
        )
        assertEquals(0, fetches)
        assertEquals(GreasyForkDownloadResult.Network, GreasyForkInstall.fetch(URL, fetcher))
        assertEquals(
            GreasyForkDownloadResult.TooLarge,
            GreasyForkInstall.fetch(URL) { _, _ ->
                UserScriptDependencyFetch(ByteArray(UserScriptParser.MAX_SOURCE_BYTES + 1))
            },
        )
        assertEquals(
            GreasyForkDownloadResult.InvalidUtf8,
            GreasyForkInstall.fetch(URL) { _, _ ->
                UserScriptDependencyFetch(byteArrayOf(0x80.toByte()))
            },
        )
        assertEquals(
            GreasyForkDownloadResult.InvalidScript(UserScriptRejectionReason.PrivilegedGrant),
            GreasyForkInstall.fetch(URL) { _, _ ->
                UserScriptDependencyFetch(
                    SCRIPT.replace("@grant none", "@grant GM_xmlhttpRequest").toByteArray(),
                )
            },
        )
    }

    private companion object {
        const val URL = "https://update.greasyfork.org/scripts/476331/Insert%20code.user.js"
        val SCRIPT = """
            // ==UserScript==
            // @name Example
            // @match https://example.com/*
            // @grant none
            // ==/UserScript==
            document.title = 'Example';
        """.trimIndent()
    }
}
