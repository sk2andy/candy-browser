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
        assertTrue("GM_info" in plan.source)
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
}
