package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParseResult
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import dev.sk2andy.materialbrowser.shared.topping.ToppingFrameScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoToppingDocumentRulesTest {
    @Test
    fun `session policy excludes private and link peek content`() {
        assertTrue(GeckoToppingSessionRules.allows(false, BrowserEngineContentKind.RegularTab))
        assertTrue(GeckoToppingSessionRules.allows(false, BrowserEngineContentKind.ExternalPreview))
        assertFalse(GeckoToppingSessionRules.allows(false, BrowserEngineContentKind.LinkPeek))
        assertFalse(GeckoToppingSessionRules.allows(true, BrowserEngineContentKind.RegularTab))
    }

    @Test
    fun `top scope rejects matching child frame`() {
        assertFalse(
            GeckoToppingDocumentRules.isEligible(
                script = script(ToppingFrameScope.Top),
                pageUrl = "https://example.com/frame",
                topUrl = "https://example.com/page",
                frameId = 1,
            ),
        )
    }

    @Test
    fun `same origin scope accepts default ports and rejects cross origin frames`() {
        val script = script(ToppingFrameScope.SameOrigin)

        assertTrue(
            GeckoToppingDocumentRules.isEligible(
                script = script,
                pageUrl = "https://example.com:443/frame",
                topUrl = "https://example.com/page",
                frameId = 2,
            ),
        )
        assertFalse(
            GeckoToppingDocumentRules.isEligible(
                script = script,
                pageUrl = "https://cdn.example/frame",
                topUrl = "https://example.com/page",
                frameId = 2,
            ),
        )
    }

    @Test
    fun `all matching scope still enforces script url match`() {
        val script = script(ToppingFrameScope.AllMatching)

        assertTrue(
            GeckoToppingDocumentRules.isEligible(
                script = script,
                pageUrl = "https://example.com/frame",
                topUrl = "https://other.example/page",
                frameId = 3,
            ),
        )
        assertFalse(
            GeckoToppingDocumentRules.isEligible(
                script = script,
                pageUrl = "https://blocked.example/frame",
                topUrl = "https://other.example/page",
                frameId = 3,
            ),
        )
    }

    private fun script(scope: ToppingFrameScope): UserScript {
        val source = """
            // ==UserScript==
            // @name Frames
            // @match https://example.com/*
            // @candy-frames ${scope.wireValue}
            // @grant none
            // ==/UserScript==
        """.trimIndent()
        return (UserScriptParser.parse("frames", source) as UserScriptParseResult.Accepted).script
    }
}
