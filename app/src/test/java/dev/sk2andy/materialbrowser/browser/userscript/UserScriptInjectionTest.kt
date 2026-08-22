package dev.sk2andy.materialbrowser.browser.userscript

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScriptInjectionTest {
    @Test
    fun `execution worlds are stable and isolated by script id`() {
        assertTrue(
            UserScriptInjection.executionWorldName("script-a")
                .startsWith("candy.topping."),
        )
        assertNotEquals(
            UserScriptInjection.executionWorldName("script-a"),
            UserScriptInjection.executionWorldName("script-b"),
        )
        assertTrue(
            UserScriptInjection.executionWorldName("script-a") ==
                UserScriptInjection.executionWorldName("script-a"),
        )
    }

    @Test
    fun `guard checks main frame protocol path and excludes before page scripts`() {
        val sources = requireNotNull(
            UserScriptInjection.sources(
                script(body = "window.started = true;"),
            ),
        )

        assertTrue(sources.guardSource.contains("window.top === window.self"))
        assertTrue(sources.guardSource.contains("window.location.protocol === \"http:\""))
        assertTrue(sources.guardSource.contains("const __candyMatchUrl = __candyUrl.split(\"#\", 1)[0];"))
        assertTrue(sources.guardSource.contains("some(__candyTestMatch)"))
        assertTrue(sources.guardSource.contains("some(__candyTestFullUrl)"))
        assertTrue(sources.guardSource.contains("writable: false"))
        assertTrue(sources.guardSource.contains("configurable: false"))
        assertTrue(sources.guardSource.contains("enumerable: false"))
        assertTrue(sources.userSource.startsWith("if (this[\"__candy_userscript_allowed:script-id\"] !== true) throw 0;"))
        assertTrue(sources.userSource.contains("window.started = true;"))
        assertFalse(sources.guardSource.contains("window.started = true;"))
        assertFalse(sources.userSource.contains("eval"))
        assertFalse(sources.userSource.contains("DOMContentLoaded"))
    }

    @Test
    fun `raw source stays separate so source syntax cannot escape trusted guard`() {
        val hostileSource = "\";\n}); window.escaped = true; (function() { // trailing"
        val sources = requireNotNull(
            UserScriptInjection.sources(
                script(body = hostileSource, name = "Name \"quoted\""),
            ),
        )

        assertFalse(sources.guardSource.contains(hostileSource))
        assertTrue(sources.userSource.contains(hostileSource))
        assertTrue(sources.guardSource.trimEnd().endsWith("})();"))
        assertTrue(sources.userSource.indexOf("throw 0;") < sources.userSource.indexOf(hostileSource))
    }

    @Test
    fun `syntax error remains confined to separately registered user source`() {
        val sources = requireNotNull(
            UserScriptInjection.sources(
                script(body = "window.broken = ;"),
            ),
        )

        assertFalse(sources.guardSource.contains("window.broken = ;"))
        assertTrue(sources.userSource.contains("window.broken = ;"))
        assertFalse(sources.userSource.contains("catch"))
    }

    @Test
    fun `invalid script produces no injectable sources`() {
        val valid = script(body = "window.ok = true;")

        assertNull(UserScriptInjection.sources(valid.copy(name = "forged")))
    }

    @Test
    fun `resolved requires execute after api and before main source`() {
        val parsed = script(body = "window.main = true;")
        val resolved = parsed.copy(
            requires = listOf(
                UserScriptRequire(
                    url = "https://cdn.example/one.js",
                    source = "window.requiredOne = typeof GM_info !== 'undefined';",
                ),
                UserScriptRequire(
                    url = "https://cdn.example/two.js",
                    source = "window.requiredTwo = true;",
                ),
            ),
            source = parsed.source.replace(
                "// @run-at document-start",
                """
                // @run-at document-start
                // @require https://cdn.example/one.js
                // @require https://cdn.example/two.js
                """.trimIndent(),
            ),
        )
        val sources = requireNotNull(UserScriptInjection.sources(resolved))

        val apiIndex = sources.userSource.indexOf("GM_info")
        val firstIndex = sources.userSource.indexOf("window.requiredOne")
        val secondIndex = sources.userSource.indexOf("window.requiredTwo")
        val mainIndex = sources.userSource.indexOf("window.main")
        assertTrue(apiIndex in 0 until firstIndex)
        assertTrue(firstIndex in 0 until secondIndex)
        assertTrue(secondIndex in 0 until mainIndex)
    }

    @Test
    fun `unresolved dependency produces no injectable sources`() {
        val parsed = script(body = "window.main = true;")
        val source = parsed.source.replace(
            "// @run-at document-start",
            "// @run-at document-start\n// @require https://cdn.example/a.js",
        )
        val unresolved = (UserScriptParser.parse("script-id", source) as UserScriptParseResult.Accepted).script

        assertNull(UserScriptInjection.sources(unresolved))
    }

    private fun script(
        body: String,
        name: String = "Injection test",
    ): UserScript {
        val source = """
            // ==UserScript==
            // @name $name
            // @match https://*.example.com/articles/*
            // @exclude https://private.example.com/*
            // @run-at document-start
            // ==/UserScript==
            $body
        """.trimIndent()
        return (UserScriptParser.parse("script-id", source) as UserScriptParseResult.Accepted).script
    }
}
