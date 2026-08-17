package dev.sk2andy.materialbrowser.browser.userscript

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToppingCatalogParserTest {
    @Test
    fun `parses strict version one catalog`() {
        val result = ToppingCatalogParser.parse(manifest()) as ToppingCatalogParseResult.Accepted

        assertEquals(ToppingCatalogParser.SCHEMA_VERSION, result.catalog.schemaVersion)
        assertEquals("reading-mode", result.catalog.toppings.single().id)
        assertEquals("toppings/reading-mode.user.js", result.catalog.toppings.single().source)
        assertEquals(listOf("https://example.com/*"), result.catalog.toppings.single().matches)
    }

    @Test
    fun `rejects unknown missing and duplicate keys`() {
        assertRejected(
            JSONObject(manifest()).put("unexpected", true).toString(),
            ToppingCatalogRejectionReason.InvalidSchema,
        )
        assertRejected(
            JSONObject(manifest()).apply { remove("schemaVersion") }.toString(),
            ToppingCatalogRejectionReason.InvalidSchema,
        )
        assertRejected(
            """{"schemaVersion":1,"schema\u0056ersion":1,"toppings":[]}""",
            ToppingCatalogRejectionReason.InvalidSchema,
        )
    }

    @Test
    fun `rejects duplicate ids and paths not derived from id`() {
        val duplicate = JSONObject(manifest())
        duplicate.getJSONArray("toppings").put(JSONObject(entry().toString()))
        assertRejected(duplicate.toString(), ToppingCatalogRejectionReason.DuplicateEntry)

        val wrongPath = JSONObject(manifest())
        wrongPath.getJSONArray("toppings").getJSONObject(0)
            .put("source", "toppings/other.user.js")
        assertRejected(wrongPath.toString(), ToppingCatalogRejectionReason.InvalidEntry)
    }

    @Test
    fun `bounds ids descriptions versions hashes and scopes`() {
        val invalidValues = listOf(
            "id" to "a".repeat(65),
            "id" to "reading-mode-",
            "id" to "reading--mode",
            "description" to "d".repeat(241),
            "version" to "01.0.0",
            "sha256" to "A".repeat(64),
            "matches" to JSONArray().put("<all_urls>"),
            "matches" to JSONArray().put("https://*/*"),
            "matches" to JSONArray().put("https://*.com/*"),
            "matches" to JSONArray().put("https://*.co.uk/*"),
            "matches" to JSONArray().put("https://*.example.com/*"),
        )

        invalidValues.forEach { (key, value) ->
            val root = JSONObject(manifest())
            root.getJSONArray("toppings").getJSONObject(0).put(key, value)
            assertRejected(root.toString(), ToppingCatalogRejectionReason.InvalidEntry)
        }
    }

    @Test
    fun `accepts semantic prerelease and exact host match plus include scopes`() {
        val root = JSONObject(manifest())
        root.getJSONArray("toppings").getJSONObject(0)
            .put("version", "1.2.3-beta.1+build.7")
            .put(
                "matches",
                JSONArray()
                    .put("https://example.com/*")
                    .put("https://example.org:8443/articles/*"),
            )

        assertTrue(ToppingCatalogParser.parse(root.toString()) is ToppingCatalogParseResult.Accepted)
    }

    private fun assertRejected(json: String, reason: ToppingCatalogRejectionReason) {
        assertEquals(reason, (ToppingCatalogParser.parse(json) as ToppingCatalogParseResult.Rejected).reason)
    }

    private fun manifest(): String = JSONObject()
        .put("schemaVersion", 1)
        .put("toppings", JSONArray().put(entry()))
        .toString()

    private fun entry(): JSONObject = JSONObject()
        .put("id", "reading-mode")
        .put("name", "Reading Mode")
        .put("description", "Makes long articles calmer.")
        .put("author", "Candy Browser")
        .put("license", "MIT")
        .put("version", "1.0.0")
        .put("source", "toppings/reading-mode.user.js")
        .put("matches", JSONArray().put("https://example.com/*"))
        .put("sha256", "0".repeat(64))
}
