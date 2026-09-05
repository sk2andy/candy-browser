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
}
