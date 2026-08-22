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
    fun `accepts local grants but rejects unsupported privileges`() {
        assertTrue(parse("// @name Local\n// @match https://example.com/*") is UserScriptParseResult.Accepted)
        assertTrue(parse("// @name Local\n// @match https://example.com/*\n// @grant none") is UserScriptParseResult.Accepted)
        val local = parse(
            """
            // @name Local API
            // @match https://example.com/*
            // @grant GM_info
            // @grant GM_addStyle
            // @grant GM_getValue
            // @grant GM_setValue
            // @grant GM_deleteValue
            // @grant GM_listValues
            """.trimIndent(),
        ) as UserScriptParseResult.Accepted
        assertEquals(
            setOf(
                UserScriptGrant.Info,
                UserScriptGrant.AddStyle,
                UserScriptGrant.GetValue,
                UserScriptGrant.SetValue,
                UserScriptGrant.DeleteValue,
                UserScriptGrant.ListValues,
            ),
            local.script.grants,
        )
        val modern = parse(
            """
            // @name Modern API
            // @match https://example.com/*
            // @grant GM.info
            // @grant GM.addStyle
            // @grant GM.getValue
            // @grant GM.setValue
            // @grant GM.deleteValue
            // @grant GM.listValues
            """.trimIndent(),
        ) as UserScriptParseResult.Accepted
        assertEquals(
            setOf(
                UserScriptGrant.Info,
                UserScriptGrant.AddStyle,
                UserScriptGrant.GetValue,
                UserScriptGrant.SetValue,
                UserScriptGrant.DeleteValue,
                UserScriptGrant.ListValues,
            ),
            modern.script.grants,
        )
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
    fun `parses bounded https dependencies and optional integrity fragments`() {
        val digest = "a".repeat(64)
        val result = parse(
            """
            // @name Remote
            // @match https://example.com/*
            // @require https://cdn.example/library.js#sha256=$digest
            // @resource icon https://cdn.example/icon.png
            // @grant GM_getResourceText
            // @grant GM.getResourceUrl
            """.trimIndent(),
        ) as UserScriptParseResult.Accepted

        assertEquals(
            UserScriptRequire("https://cdn.example/library.js", digest),
            result.script.requires.single(),
        )
        assertEquals("icon", result.script.resources.single().name)
        assertEquals("https://cdn.example/icon.png", result.script.resources.single().url)
        assertTrue(UserScriptGrant.GetResourceText in result.script.grants)
        assertTrue(UserScriptGrant.GetResourceUrl in result.script.grants)
        assertTrue(
            parse(
                """
                // @name Updates
                // @match https://example.com/*
                // @downloadURL https://example.com/script.user.js
                // @updateURL https://example.com/script.meta.js
                """.trimIndent(),
            ) is UserScriptParseResult.Accepted,
        )
    }

    @Test
    fun `rejects unsafe malformed and duplicate dependencies`() {
        listOf(
            "http://cdn.example/library.js",
            "https://localhost/library.js",
            "https://127.0.0.1/library.js",
            "https://user@cdn.example/library.js",
            "https://cdn.example:8443/library.js",
            "https://cdn.example/library.js#other",
            "https://cdn.example/library.js#sha256=abcd",
        ).forEach { url ->
            assertRejected(
                metadata = "// @name Remote\n// @match https://example.com/*\n// @require $url",
                reason = UserScriptRejectionReason.InvalidRequire,
            )
        }
        assertRejected(
            metadata = """
                // @name Duplicate
                // @match https://example.com/*
                // @resource icon https://cdn.example/a.png
                // @resource icon https://cdn.example/b.png
            """.trimIndent(),
            reason = UserScriptRejectionReason.InvalidResource,
        )
        assertRejected(
            metadata = "// @name Bad resource\n// @match https://example.com/*\n// @resource missing-url",
            reason = UserScriptRejectionReason.InvalidResource,
        )
    }

    @Test
    fun `bounds dependency counts`() {
        val requires = List(UserScriptDependencyRules.MAX_REQUIRES + 1) { index ->
            "// @require https://cdn$index.example/file.js"
        }.joinToString("\n")

        assertRejected(
            metadata = "// @name Too many\n// @match https://example.com/*\n$requires",
            reason = UserScriptRejectionReason.TooManyDependencies,
        )
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
