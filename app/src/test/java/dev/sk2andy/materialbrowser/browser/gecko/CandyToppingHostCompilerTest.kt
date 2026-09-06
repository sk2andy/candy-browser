package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParseResult
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyToppingHostCompilerTest {
    @Test
    fun `compiler maps timing match include exclude and isolated world`() {
        val script = parse(
            id = "style",
            metadata = """
                // @match https://example.com/*
                // @include https://example.net/articles/*
                // @exclude https://example.com/private/*
                // @run-at document-start
                // @grant GM_addStyle
            """.trimIndent(),
            body = "GM_addStyle('body { color: red; }');",
        )

        val registration = CandyToppingHostCompiler.compile(listOf(script))
            .registrations.single()

        assertEquals(listOf("https://example.com/*"), registration.matchPatterns)
        assertEquals(listOf("https://example.net/articles/*"), registration.includeGlobs)
        assertEquals(listOf("https://example.com/private/*"), registration.excludeGlobs)
        assertEquals("document_start", registration.runAt)
        assertTrue(registration.id.startsWith("candy-"))
        assertFalse(registration.id.startsWith("_"))
        assertTrue(registration.worldId.startsWith("candy.topping."))
        assertTrue(registration.javascript.contains("GM_addStyle"))
        assertTrue(registration.javascript.contains("body { color: red; }"))
    }

    @Test
    fun `compiler narrows all urls to http and https and omits disabled scripts`() {
        val enabled = parse(
            id = "all-web",
            metadata = "// @match <all_urls>\n// @grant none",
        )
        val disabled = parse(
            id = "off",
            metadata = "// @match https://disabled.example/*\n// @grant none",
        ).copy(enabled = false)

        val plan = CandyToppingHostCompiler.compile(listOf(enabled, disabled))

        assertEquals(listOf("http://*/*", "https://*/*"), plan.registrations.single().matchPatterns)
        assertTrue(plan.unsupportedScripts.isEmpty())
    }

    @Test
    fun `compiler gives include-only scripts a web-only Gecko base scope`() {
        val script = parse(
            id = "include-only",
            metadata = "// @include https://example.net/articles/*\n// @grant none",
        )

        val registration = CandyToppingHostCompiler.compile(listOf(script)).registrations.single()

        assertEquals(listOf("http://*/*", "https://*/*"), registration.matchPatterns)
        assertEquals(listOf("https://example.net/articles/*"), registration.includeGlobs)
    }

    @Test
    fun `compiler gives changed source a fresh Gecko registration id`() {
        val original = parse(
            id = "revised",
            metadata = "// @match https://example.net/*\n// @grant none",
            body = "document.title = 'one';",
        )
        val updated = original.copy(source = original.source.replace("'one'", "'two'"))

        val originalRegistration = CandyToppingHostCompiler.compile(listOf(original))
            .registrations.single()
        val updatedRegistration = CandyToppingHostCompiler.compile(listOf(updated))
            .registrations.single()

        assertFalse(originalRegistration.id == updatedRegistration.id)
        assertFalse(originalRegistration.worldId == updatedRegistration.worldId)
    }

    @Test
    fun `compiler rejects grants and dependencies requiring native parity`() {
        val valueScript = parse(
            id = "values",
            metadata = "// @match https://example.com/*\n// @grant GM_getValue",
        )
        val dependencyScript = parse(
            id = "dependency",
            metadata = """
                // @match https://example.com/*
                // @require https://cdn.jsdelivr.net/example.js
                // @grant none
            """.trimIndent(),
        ).let { script ->
            script.copy(
                requires = script.requires.map { dependency ->
                    dependency.copy(source = "globalThis.requiredLoaded = true;")
                },
            )
        }

        val plan = CandyToppingHostCompiler.compile(listOf(valueScript, dependencyScript))

        assertTrue(plan.registrations.isEmpty())
        assertEquals(
            GeckoToppingUnsupportedReason.PrivilegedGrant,
            plan.unsupportedScripts["values"],
        )
        assertEquals(
            GeckoToppingUnsupportedReason.Dependencies,
            plan.unsupportedScripts["dependency"],
        )
    }

    @Test
    fun `initialization gate releases queued navigation on ready`() {
        val gate = GeckoToppingInitializationGate()
        val states = mutableListOf<GeckoToppingHostState>()
        val actions = mutableListOf<String>()
        gate.setStateListener(states::add)
        gate.runAfterInitialization { actions += "queued" }

        assertTrue(actions.isEmpty())
        gate.complete(GeckoToppingHostState.Ready)
        gate.runAfterInitialization { actions += "immediate" }

        assertEquals(
            listOf(GeckoToppingHostState.Initializing, GeckoToppingHostState.Ready),
            states,
        )
        assertEquals(listOf("queued", "immediate"), actions)
    }

    @Test
    fun `initialization gate releases queued navigation when host is unavailable`() {
        val gate = GeckoToppingInitializationGate()
        var released = false
        gate.runAfterInitialization { released = true }

        gate.complete(GeckoToppingHostState.Unavailable)

        assertTrue(released)
        assertEquals(GeckoToppingHostState.Unavailable, gate.state)
    }

    @Test
    fun `initialization gate ignores late ready after timeout`() {
        val gate = GeckoToppingInitializationGate()
        val states = mutableListOf<GeckoToppingHostState>()
        gate.setStateListener(states::add)

        gate.complete(GeckoToppingHostState.Unavailable)
        gate.complete(GeckoToppingHostState.Ready)

        assertEquals(GeckoToppingHostState.Unavailable, gate.state)
        assertEquals(
            listOf(
                GeckoToppingHostState.Initializing,
                GeckoToppingHostState.Unavailable,
            ),
            states,
        )
    }

    @Test
    fun `initialization gate waits for every later catalog revision`() {
        val gate = GeckoToppingInitializationGate()
        val states = mutableListOf<GeckoToppingHostState>()
        val actions = mutableListOf<String>()
        gate.setStateListener(states::add)
        gate.complete(GeckoToppingHostState.Ready)

        assertTrue(gate.beginRevision())
        gate.runAfterInitialization { actions += "after-revision" }
        assertTrue(actions.isEmpty())

        gate.complete(GeckoToppingHostState.Ready)

        assertEquals(listOf("after-revision"), actions)
        assertEquals(
            listOf(
                GeckoToppingHostState.Initializing,
                GeckoToppingHostState.Ready,
                GeckoToppingHostState.Initializing,
                GeckoToppingHostState.Ready,
            ),
            states,
        )
    }

    private fun parse(
        id: String,
        metadata: String,
        body: String = "globalThis.candyToppingLoaded = true;",
    ): UserScript {
        val source = """
            // ==UserScript==
            // @name $id
            $metadata
            // ==/UserScript==
            $body
        """.trimIndent()
        return (UserScriptParser.parse(id = id, source = source) as UserScriptParseResult.Accepted)
            .script
    }
}
