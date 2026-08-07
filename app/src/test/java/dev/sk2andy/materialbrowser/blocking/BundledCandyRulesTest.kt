package dev.sk2andy.materialbrowser.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledCandyRulesTest {
    @Test
    fun `routes bundled rule kinds without entering user storage`() {
        val rules = listOf(
            CandyRule.new(
                action = CandyRuleAction.Block,
                kind = CandyRuleKind.RequestHost,
                requestHost = "ads.vendor.example",
                group = BundledCandyRuleGroups.Ads,
            ),
            CandyRule.new(
                action = CandyRuleAction.Block,
                kind = CandyRuleKind.HostPair,
                requestHost = "promo.vendor.example",
                firstPartyHost = "news.example",
                group = BundledCandyRuleGroups.Ads,
            ),
            CandyRule.new(
                action = CandyRuleAction.Allow,
                kind = CandyRuleKind.RequestHost,
                requestHost = "required.vendor.example",
                group = BundledCandyRuleGroups.Ads,
            ),
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
        val blocker = RequestBlocker(
            hostRules = emptySequence(),
            candyRules = bundled.matcher,
        )

        assertTrue(
            blocker.shouldBlock(
                "https://ads.vendor.example/ad.js",
                "https://other.example/",
            ),
        )
        assertTrue(
            blocker.shouldBlock(
                "https://promo.vendor.example/ad.js",
                "https://news.example/",
            ),
        )
        assertFalse(blocker.shouldBlock(
            "https://required.vendor.example/app.js",
            "https://other.example/",
        ))
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

    @Test(expected = IllegalArgumentException::class)
    fun `strict parser rejects malformed bundled file`() {
        BundledCandyRules.parse("not-candy-rules")
    }

    @Test
    fun `candy specificity survives request blocker routing`() {
        val broadAllow = CandyRule.new(
            action = CandyRuleAction.Allow,
            kind = CandyRuleKind.RequestHost,
            requestHost = "ads.example",
            group = BundledCandyRuleGroups.Ads,
        )
        val specificBlock = CandyRule.new(
            action = CandyRuleAction.Block,
            kind = CandyRuleKind.RequestHost,
            requestHost = "video.ads.example",
            group = BundledCandyRuleGroups.Ads,
        )
        val bundled = BundledCandyRules.parse(CandyRuleFormat.export(
            listOf(broadAllow, specificBlock),
        ))
        val blocker = RequestBlocker(emptySequence(), candyRules = bundled.matcher)

        assertTrue(blocker.shouldBlock(
            "https://video.ads.example/ad.js",
            "https://news.example/",
        ))
        assertFalse(blocker.shouldBlock(
            "https://images.ads.example/image.jpg",
            "https://news.example/",
        ))
    }

    @Test
    fun `upstream exception still wins over bundled block`() {
        val block = CandyRule.new(
            action = CandyRuleAction.Block,
            kind = CandyRuleKind.RequestHost,
            requestHost = "required.example",
            group = BundledCandyRuleGroups.Ads,
        )
        val bundled = BundledCandyRules.parse(CandyRuleFormat.export(listOf(block)))
        val blocker = RequestBlocker(
            hostRules = emptySequence(),
            allowedHostPairs = sequenceOf("required.example\tnews.example"),
            candyRules = bundled.matcher,
        )

        assertFalse(blocker.shouldBlock(
            "https://required.example/app.js",
            "https://news.example/",
        ))
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
