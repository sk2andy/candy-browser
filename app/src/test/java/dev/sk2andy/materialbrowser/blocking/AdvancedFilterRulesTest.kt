package dev.sk2andy.materialbrowser.blocking

import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedFilterRulesTest {
    @Test
    fun `request rules match host paths and wildcards`() {
        val rules = parse(
            rule("B", "N", "ads.example", "|/assets/*/video-ad.js^"),
        )

        assertTrue(
            rules.shouldBlockRequest(
                "https://cdn.ads.example/assets/42/video-ad.js?slot=1",
                "https://news.example/story",
            ),
        )
        assertFalse(
            rules.shouldBlockRequest(
                "https://cdn.ads.example/assets/42/article.js",
                "https://news.example/story",
            ),
        )
    }

    @Test
    fun `host fast path preserves request matching without parsing page url`() {
        val rules = parse(
            rule("B", "N", "ads.example", "|/assets/*/video-ad.js^"),
        )

        assertTrue(
            rules.decideRequest(
                requestUrl = "https://cdn.ads.example/assets/42/video-ad.js?slot=1",
                requestHost = "cdn.ads.example",
                pageHost = "news.example",
            ) == AdvancedFilterAction.Block,
        )
        assertTrue(
            rules.decideRequest(
                requestUrl = "not parsed for a host miss",
                requestHost = "cdn.safe.example",
                pageHost = "news.example",
            ) == null,
        )
        assertTrue(
            rules.decideRequest(
                requestUrl = "https://safe.example/assets/42/video-ad.js",
                requestHost = "ads.example",
                pageHost = "news.example",
            ) == null,
        )
    }

    @Test
    fun `scoped allow wins over matching popup block`() {
        val rules = parse(
            rule("B", "P", "popup.example", "*"),
            rule("A", "P", "popup.example", "|/account/*", pages = "news.example"),
        )

        assertFalse(
            rules.shouldBlockPopup(
                "https://popup.example/account/login",
                "https://news.example/story",
            ),
        )
        assertTrue(
            rules.shouldBlockPopup(
                "https://popup.example/ad/click",
                "https://news.example/story",
            ),
        )
    }

    @Test
    fun `site scoped generic popup rule respects party`() {
        val rules = parse(
            rule("B", "P", "*", "*", pages = "stream.example", party = "3"),
        )

        assertTrue(
            rules.shouldBlockPopup(
                "https://ad-network.example/click",
                "https://stream.example/watch",
            ),
        )
        assertFalse(
            rules.shouldBlockPopup(
                "https://account.stream.example/login",
                "https://stream.example/watch",
            ),
        )
        assertFalse(rules.shouldBlockPopupWithoutTarget("https://stream.example/watch"))
    }

    @Test
    fun `target independent popup rule blocks before blank window creation`() {
        val rules = parse(
            rule("B", "P", "*", "*", pages = "stream.example"),
        )

        assertTrue(rules.shouldBlockPopupWithoutTarget("https://stream.example/watch"))
        assertFalse(rules.shouldBlockPopupWithoutTarget("https://safe.example/watch"))
    }

    @Test
    fun `target allow defers generic popup block until target is known`() {
        val rules = parse(
            rule("B", "P", "*", "*", pages = "stream.example"),
            rule("A", "P", "login.example", "*", pages = "stream.example"),
        )

        assertFalse(rules.shouldBlockPopupWithoutTarget("https://stream.example/watch"))
        assertFalse(
            rules.shouldBlockPopup(
                "https://login.example/account",
                "https://stream.example/watch",
            ),
        )
        assertTrue(
            rules.shouldBlockPopup(
                "https://ads.example/click",
                "https://stream.example/watch",
            ),
        )
    }

    @Test
    fun `window open defuser rules remain separate from native popup policy`() {
        val rules = parse(
            rule(
                "B",
                "P",
                "*",
                "*",
                pages = "stream.*",
                behavior = "W",
            ),
        )

        assertTrue(rules.shouldDefuseWindowOpen("https://stream.com/watch"))
        assertFalse(rules.shouldBlockPopupWithoutTarget("https://stream.com/watch"))
        assertTrue("return null" in CandyWindowOpenDefuserScript.script)
    }

    @Test
    fun `wildcard token handles literal star in URL`() {
        assertTrue(CandyUrlPattern.matches("/*xxad", "|/*ad"))
    }

    @Test
    fun `excluded page and wildcard tld stay bounded`() {
        val rules = parse(
            rule(
                "B",
                "N",
                "metrics.*",
                "|/collect*",
                pages = "news.example",
                excludedPages = "safe.news.example",
            ),
        )

        assertTrue(
            rules.shouldBlockRequest(
                "https://metrics.com/collect?v=1",
                "https://news.example/story",
            ),
        )
        assertFalse(
            rules.shouldBlockRequest(
                "https://metrics.com/collect?v=1",
                "https://safe.news.example/story",
            ),
        )
    }

    private fun parse(vararg rules: String): AdvancedFilterRules = AdvancedFilterRules.parse(
        buildString {
            appendLine(AdvancedFilterRules.HEADER)
            appendLine("# Rules: ${rules.size}")
            rules.forEach(::appendLine)
        },
    )

    private fun rule(
        action: String,
        scope: String,
        targetHost: String,
        pattern: String,
        pages: String = "-",
        excludedPages: String = "-",
        party: String = "*",
        behavior: String = "-",
    ): String = listOf(
        action,
        scope,
        targetHost,
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(pattern.toByteArray(Charsets.UTF_8)),
        pages,
        excludedPages,
        party,
        behavior,
    ).joinToString("\t")
}
