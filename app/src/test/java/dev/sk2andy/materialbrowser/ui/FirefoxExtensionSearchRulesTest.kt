package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlinx.coroutines.CancellationException

class FirefoxExtensionSearchRulesTest {
    @Test
    fun `blank input produces no search query`() {
        assertEquals(null, FirefoxExtensionSearchRules.normalizeQuery("  \n "))
        assertEquals("uBlock", FirefoxExtensionSearchRules.normalizeQuery("  uBlock  "))
    }

    @Test
    fun `search query is capped at the catalog limit`() {
        val query = "x".repeat(FirefoxExtensionSearchRules.MAX_QUERY_LENGTH + 20)
        assertEquals(100, FirefoxExtensionSearchRules.normalizeQuery(query)?.length)
    }

    @Test
    fun `late results are only current while the normalized query still matches`() {
        assertTrue(FirefoxExtensionSearchRules.isCurrentQuery("ublock", " ublock "))
        assertFalse(FirefoxExtensionSearchRules.isCurrentQuery("ublock", "privacy"))
        assertFalse(FirefoxExtensionSearchRules.isCurrentQuery("ublock", ""))
    }

    @Test
    fun `search url encodes input and requests Android extensions`() {
        val url = FirefoxExtensionSearchRules.searchUrl("uBlock Origin & privacy")
        assertTrue(url.contains("q=uBlock+Origin+%26+privacy"))
        assertTrue(url.contains("app=android&type=extension&page_size=10"))
    }

    @Test
    fun `empty catalog response returns no results`() {
        assertTrue(FirefoxExtensionSearchRules.responseEntries("{\"results\":[]}").isEmpty())
        assertTrue(FirefoxExtensionSearchRules.responseEntries("{}").isEmpty())
    }

    @Test
    fun `official response with singular current file becomes an install result`() {
        val response = """
            {"results":[{"guid":"ublock@test","name":{"de":"uBlock Origin","en-US":"uBlock Origin"},
            "summary":{"en-US":"Blocks ads"},"current_version":{"file":{"url":"https://addons.mozilla.org/firefox/downloads/file/123/ublock.xpi"}}}]}
        """.trimIndent()

        assertEquals(
            listOf(
                AvailableFirefoxExtension(
                    id = "ublock@test",
                    name = "uBlock Origin",
                    summary = "Blocks ads",
                    xpiUrl = "https://addons.mozilla.org/firefox/downloads/file/123/ublock.xpi",
                ),
            ),
            FirefoxExtensionSearchRules.responseEntries(response),
        )
    }

    @Test
    fun `non Mozilla download and incomplete entries are rejected`() {
        val response = """
            {"results":[
              {"guid":"bad-url","name":{"en-US":"Bad URL"},"current_version":{"file":{"url":"https://example.com/addon.xpi"}}},
              {"guid":"no-file","name":{"en-US":"No file"},"current_version":{}},
              {"guid":"no-name","current_version":{"file":{"url":"https://addons.mozilla.org/a.xpi"}}}
            ]}
        """.trimIndent()

        assertTrue(FirefoxExtensionSearchRules.responseEntries(response).isEmpty())
    }

    @Test
    fun `already installed add-ons are hidden regardless of id casing`() {
        val results = listOf(
            AvailableFirefoxExtension("uBlock@test", "uBlock", "", "https://addons.mozilla.org/u.xpi"),
            AvailableFirefoxExtension("new@test", "New", "", "https://addons.mozilla.org/n.xpi"),
        )

        assertEquals(
            listOf(results.last()),
            FirefoxExtensionSearchRules.available(results, setOf("ublock@TEST")),
        )
        assertFalse(FirefoxExtensionSearchRules.available(results, setOf("uBlock@test")).isEmpty())
    }

    @Test
    fun `network failures become retryable failed search outcomes`() = runBlocking {
        val outcome = FirefoxExtensionSearchRules.execute("ublock") {
            throw IOException("offline")
        }

        assertEquals(FirefoxExtensionSearchOutcome.Failed, outcome)
    }

    @Test
    fun `cancelled search is rethrown so obsolete requests cannot publish results`() = runBlocking {
        try {
            FirefoxExtensionSearchRules.execute("older query") {
                throw CancellationException("query changed")
            }
            throw AssertionError("Cancellation should propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("query changed", cancelled.message)
        }
    }
}
