package dev.sk2andy.materialbrowser.shared.topping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ToppingRulesTest {
    @Test
    fun acceptedScriptProducesIsolatedMainFramePlan() {
        val source = """
            // ==UserScript==
            // @name Example
            // @match https://example.com/*
            // @run-at document-start
            // ==/UserScript==
            document.body.dataset.example = "ready";
        """.trimIndent()
        val accepted = assertIs<ToppingParseResult.Accepted>(ToppingRules.parse("example", source))

        val plan = ToppingRules.injectionPlan(accepted.script)

        assertEquals(ToppingRunAt.DocumentStart, plan.runAt)
        assertTrue(plan.forMainFrameOnly)
        assertEquals("candy.topping.example", plan.contentWorldName)
        assertTrue("window.top !== window" in plan.source)
    }

    @Test
    fun includeExcludeAndLocalGrantsAreCompiledIntoIsolatedPlan() {
        val source = """
            // ==UserScript==
            // @name Local APIs
            // @include https://*.example.com/*
            // @exclude https://private.example.com/*
            // @grant GM_addStyle
            // @grant GM.info
            // ==/UserScript==
            GM_addStyle("body { color: red }");
        """.trimIndent()

        val accepted = assertIs<ToppingParseResult.Accepted>(ToppingRules.parse("local", source))
        val plan = ToppingRules.injectionPlan(accepted.script)

        assertEquals(listOf(ToppingGrant.AddStyle, ToppingGrant.Info), accepted.script.grants)
        assertTrue("private.example.com" in plan.source)
        assertTrue("GM_addStyle" in plan.source)
    }

    @Test
    fun privilegedGrantIsRejectedInsteadOfBecomingNoOp() {
        val source = """
            // ==UserScript==
            // @name Network
            // @match https://example.com/*
            // @grant GM_xmlhttpRequest
            // ==/UserScript==
        """.trimIndent()

        val rejected = assertIs<ToppingParseResult.Rejected>(ToppingRules.parse("network", source))

        assertEquals("privileged_grant", rejected.reason)
    }

    @Test
    fun disabledToppingsAreNotRegisteredAndEnabledOrderIsStable() {
        fun script(id: String, enabled: Boolean) = ToppingScript(
            id = id,
            name = id,
            source = "console.log('$id')",
            enabled = enabled,
            matchPatterns = listOf("https://example.com/*"),
            runAt = ToppingRunAt.DocumentEnd,
        )

        val plans = ToppingRules.injectionPlans(
            listOf(script("z", true), script("disabled", false), script("a", true)),
        )

        assertEquals(listOf("a", "z"), plans.map(ToppingInjectionPlan::id))
    }

    @Test
    fun completePrivilegedContractAndResolvedDependenciesAreParsedInOrder() {
        val source = """
            // ==UserScript==
            // @name Complete
            // @match https://*.example.com/*
            // @grant GM_getValue
            // @grant GM.setValue
            // @grant GM_deleteValue
            // @grant GM.listValues
            // @grant GM_registerMenuCommand
            // @grant GM.unregisterMenuCommand
            // @grant GM.openInTab
            // @grant GM_getResourceText
            // @grant GM.getResourceURL
            // @require https://cdn.jsdelivr.net/library.js#sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
            // @require https://unpkg.com/plugin.js
            // @resource logo https://raw.githubusercontent.com/example/logo.png
            // ==/UserScript==
        """.trimIndent()

        val script = assertIs<ToppingParseResult.Accepted>(ToppingRules.parse("complete", source)).script

        assertEquals(listOf("https://cdn.jsdelivr.net/library.js", "https://unpkg.com/plugin.js"), script.requires.map { it.url })
        assertEquals("a".repeat(64), script.requires.first().sha256)
        assertEquals("logo", script.resources.single().name)
        assertEquals(9, script.grants.size)
    }

    @Test
    fun dependencyAndConnectPolicyFailsClosed() {
        val localDependency = """
            // ==UserScript==
            // @name Local
            // @match https://example.com/*
            // @require http://localhost/code.js
            // ==/UserScript==
        """.trimIndent()
        val connected = """
            // ==UserScript==
            // @name Connected
            // @match https://example.com/*
            // @connect example.com
            // ==/UserScript==
        """.trimIndent()

        assertEquals("invalid_require", assertIs<ToppingParseResult.Rejected>(ToppingRules.parse("local", localDependency)).reason)
        assertEquals("privileged_grant", assertIs<ToppingParseResult.Rejected>(ToppingRules.parse("connected", connected)).reason)
    }

    @Test
    fun sharedUrlMatcherHonorsIncludeAndExclude() {
        val script = ToppingScript(
            id = "scope",
            name = "Scope",
            source = "void 0",
            matchPatterns = listOf("https://*.example.com/*"),
            excludePatterns = listOf("https://private.example.com/*"),
            runAt = ToppingRunAt.DocumentEnd,
        )

        assertTrue(ToppingRules.matchesUrl(script, "https://www.example.com/page"))
        assertTrue(!ToppingRules.matchesUrl(script, "https://private.example.com/page"))
        assertTrue(!ToppingRules.matchesUrl(script, "https://www.example.com:8443/page"))
        assertTrue(!ToppingRules.matchesUrl(script, "file:///tmp/page"))
    }
}
