package dev.sk2andy.materialbrowser.browser.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScriptParserTest {
    @Test
    fun `parses supported metadata and defaults to document end`() {
        val result = UserScriptParser.parse(
            id = "local-script",
            source = source(
                """
                // @name Cleaner
                // @match https://*.example.com/articles/*
                // @include http://localhost:8080/*
                // @exclude https://ads.example.com/*
                // @grant none
                """,
            ),
            enabled = false,
            updatedAtMillis = 42L,
        ) as UserScriptParseResult.Accepted

        assertEquals("Cleaner", result.script.name)
        assertEquals(listOf("https://*.example.com/articles/*"), result.script.matchPatterns)
        assertEquals(listOf("http://localhost:8080/*"), result.script.includePatterns)
        assertEquals(listOf("https://ads.example.com/*"), result.script.excludePatterns)
        assertEquals(UserScriptRunAt.DocumentEnd, result.script.runAt)
        assertEquals(false, result.script.enabled)
        assertEquals(42L, result.script.updatedAtMillis)
    }

    @Test
    fun `parses explicit document start`() {
        val result = parse(
            """
            // @name Early
            // @match *://example.com/*
            // @run-at document-start
            """,
        ) as UserScriptParseResult.Accepted

        assertEquals(UserScriptRunAt.DocumentStart, result.script.runAt)
    }

    @Test
    fun `strips utf8 bom before parsing and persistence`() {
        val source = source("// @name BOM\n// @match https://example.com/*")
        val result = UserScriptParser.parse(
            id = "bom",
            source = "\uFEFF$source",
        ) as UserScriptParseResult.Accepted

        assertFalse(result.script.source.startsWith("\uFEFF"))
        assertEquals(source, result.script.source)
    }

    @Test
    fun `allows missing grant and explicit none but rejects privileged grants`() {
        assertTrue(parse("// @name Local\n// @match https://example.com/*") is UserScriptParseResult.Accepted)
        assertTrue(parse("// @name Local\n// @match https://example.com/*\n// @grant none") is UserScriptParseResult.Accepted)
        assertRejected(
            metadata = "// @name Privileged\n// @match https://example.com/*\n// @grant GM_xmlhttpRequest",
            reason = UserScriptRejectionReason.PrivilegedGrant,
        )
        assertRejected(
            metadata = "// @name Connected\n// @match https://example.com/*\n// @connect api.example.com",
            reason = UserScriptRejectionReason.PrivilegedGrant,
        )
    }

    @Test
    fun `rejects every remote dependency directive`() {
        listOf(
            "@require https://cdn.example/library.js",
            "@resource icon https://cdn.example/icon.png",
            "@downloadURL https://example.com/script.user.js",
            "@updateURL https://example.com/script.meta.js",
        ).forEach { directive ->
            assertRejected(
                metadata = "// @name Remote\n// @match https://example.com/*\n// $directive",
                reason = UserScriptRejectionReason.RemoteDependency,
            )
        }
    }

    @Test
    fun `rejects missing malformed and unbounded metadata`() {
        assertEquals(
            UserScriptRejectionReason.InvalidMetadataBlock,
            (UserScriptParser.parse("id", "alert(1)") as UserScriptParseResult.Rejected).reason,
        )
        assertRejected("// @match https://example.com/*", UserScriptRejectionReason.MissingName)
        assertRejected("// @name No target", UserScriptRejectionReason.MissingInclude)
        assertRejected(
            "// @name Wrong scheme\n// @match file://example.com/*",
            UserScriptRejectionReason.InvalidMatchPattern,
        )
        assertRejected(
            "// @name Regex\n// @include /https:\\/\\/example\\.com/",
            UserScriptRejectionReason.InvalidIncludePattern,
        )
        assertRejected(
            "// @name Loose glob\n// @include *example.com/*",
            UserScriptRejectionReason.InvalidIncludePattern,
        )
        assertRejected(
            "// @name Run\n// @match https://example.com/*\n// @run-at document-idle",
            UserScriptRejectionReason.InvalidRunAt,
        )
    }

    @Test
    fun `bounds source by utf8 bytes and identifiers`() {
        val prefix = source("// @name Big\n// @match https://example.com/*\n", body = "")
        val remaining = UserScriptParser.MAX_SOURCE_BYTES - prefix.toByteArray().size
        val oversized = prefix + "é".repeat(remaining / 2 + 1)

        assertEquals(
            UserScriptRejectionReason.SourceTooLarge,
            (UserScriptParser.parse("id", oversized) as UserScriptParseResult.Rejected).reason,
        )
        assertEquals(
            UserScriptRejectionReason.InvalidId,
            (UserScriptParser.parse("bad id", source("// @name Fine\n// @match https://example.com/*")) as
                UserScriptParseResult.Rejected).reason,
        )
    }

    private fun parse(metadata: String): UserScriptParseResult =
        UserScriptParser.parse("script-id", source(metadata))

    private fun assertRejected(metadata: String, reason: UserScriptRejectionReason) {
        assertEquals(reason, (parse(metadata) as UserScriptParseResult.Rejected).reason)
    }

    private fun source(metadata: String, body: String = "window.candy = true;"): String = """
        // ==UserScript==
        ${metadata.trimIndent()}
        // ==/UserScript==
        $body
    """.trimIndent()
}
