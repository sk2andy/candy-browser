package dev.sk2andy.materialbrowser.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledCandyRulesTest {
    @Test
    fun `separates bundled cosmetic groups without entering user storage`() {
        val rules = listOf(
            CandyRule.new(
                action = CandyRuleAction.Cosmetic,
                kind = CandyRuleKind.CosmeticCss,
                firstPartyHost = "news.example",
                cosmeticSelector = "#sponsored-slot",
                group = BundledCandyRuleGroups.Ads,
            ),
            CandyRule.new(
                action = CandyRuleAction.Cosmetic,
                kind = CandyRuleKind.CosmeticCss,
                firstPartyHost = "search.example",
                cosmeticSelector = "#cookie-wall",
                group = BundledCandyRuleGroups.Cookies,
            ),
        )

        val bundled = BundledCandyRules.parse(CandyRuleFormat.export(rules))
        assertEquals(
            listOf("#sponsored-slot"),
            bundled.adCosmeticSelectors("https://m.news.example/article"),
        )
        assertTrue(bundled.adCosmeticSelectors("https://other.example/").isEmpty())
        assertEquals(listOf("#cookie-wall"), bundled.cookieCosmeticRules.map {
            it.cosmeticSelector
        })
    }

    @Test
    fun `malformed bundled file fails closed`() {
        val bundled = BundledCandyRules.parseOrEmpty("not-candy-rules")

        assertTrue(bundled.rules.isEmpty())
    }

    @Test
    fun `native host hot path preserves request rule precedence`() {
        val broadAllow = CandyRule.new(
            action = CandyRuleAction.Allow,
            kind = CandyRuleKind.RequestHost,
            requestHost = "ads.example",
        )
        val specificBlock = CandyRule.new(
            action = CandyRuleAction.Block,
            kind = CandyRuleKind.RequestHost,
            requestHost = "video.ads.example",
        )
        val matcher = CandyMatcherSnapshot.compile(listOf(broadAllow, specificBlock))

        assertEquals(
            CandyDecisionAction.Block,
            matcher.decideHosts(
                requestHost = "video.ads.example",
                pageHost = "news.example",
                profileId = "default",
                isForMainFrame = false,
            )?.action,
        )
        assertEquals(
            CandyDecisionAction.Allow,
            matcher.decideHosts(
                requestHost = "images.ads.example",
                pageHost = "news.example",
                profileId = "default",
                isForMainFrame = false,
            )?.action,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `strict parser rejects malformed bundled file`() {
        BundledCandyRules.parse("not-candy-rules")
    }

    @Test
    fun `scoped cosmetic script embeds selectors as data and checks page host`() {
        val rule = CandyRule.new(
            action = CandyRuleAction.Cosmetic,
            kind = CandyRuleKind.CosmeticCss,
            firstPartyHost = "news.example",
            cosmeticSelector = "#ad-slot",
        )

        val script = CandyCosmeticScript.createScoped(listOf(rule), listOf("paused.example"))

        assertTrue(script.contains("host:'news.example'"))
        assertTrue(script.contains("h===rule.host||h.endsWith('.'+rule.host)"))
        assertTrue(script.contains("'paused.example'"))
        assertTrue(script.contains("new TextDecoder('utf-8')"))
    }
}
