package dev.sk2andy.materialbrowser.blocking

import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentBlockerScriptTest {
    @Test
    fun `paused hosts are embedded as normalized document-start guard`() {
        val script = ConsentBlockerScript.create(
            cssBytes = "body{}".toByteArray(),
            pausedHosts = listOf("News.Example", "notexample.com"),
        )

        assertTrue(script.contains("const pausedHosts = [\"news.example\", \"notexample.com\"]"))
        assertTrue(script.contains("scopeHosts.some(scope => hostMatches(scope, host))"))
        assertTrue(script.contains("const scopeHosts = isTopFrame ? [frameHost]"))
    }

    @Test
    fun `embeds css as utf8 base64 instead of executable source`() {
        val css = "#cookie-ä { display: none } </style><script>bad()</script>"

        val script = ConsentBlockerScript.create(css.toByteArray())

        val encodedCss = Base64.getEncoder().encodeToString(css.toByteArray())
        assertTrue(script.contains(encodedCss))
        assertTrue(script.contains("new TextDecoder('utf-8')"))
        assertFalse(script.contains("<script>bad()</script>"))
    }

    @Test
    fun `script is idempotent per document`() {
        val script = ConsentBlockerScript.create("#banner {}".toByteArray())

        assertTrue(script.contains("document.getElementById(styleId)"))
        assertTrue(script.contains("material-browser-easylist-cookie-css"))
    }

    @Test
    fun `scroll cleanup requires a known hidden cmp`() {
        val script = ConsentBlockerScript.create("#banner {}".toByteArray())

        assertTrue(script.contains("document.querySelectorAll"))
        assertTrue(script.contains("getComputedStyle(banner).display === 'none'"))
        assertTrue(ConsentBlockerScript.cleanupScript.contains("__materialBrowserUnlockCookieScroll"))
    }

    @Test
    fun `document start work stays in top frame and observes late cmp locks`() {
        val script = ConsentBlockerScript.create("#banner {}".toByteArray())

        assertTrue(script.contains("if (!isTopFrame) return"))
        assertTrue(script.contains("new MutationObserver"))
        assertTrue(script.contains("attributeFilter: ['class', 'style']"))
        assertTrue(ConsentBlockerScript.removalScript.contains(".disconnect()"))
    }

    @Test
    fun `known reject action runs before subframe exit and stays encoded`() {
        val selector = "#reject-all"
        val script = ConsentBlockerScript.create(
            cssBytes = "body{}".toByteArray(),
            actionRules = listOf(
                BundledConsentAction("reject", "cmp.example", selector),
            ),
        )

        assertTrue(
            script.indexOf("const consentActions") < script.indexOf("if (!isTopFrame) return"),
        )
        assertTrue(script.contains("host:\"cmp.example\""))
        assertTrue(script.contains("location.ancestorOrigins"))
        assertTrue(script.contains("document.referrer"))
        assertTrue(script.contains("control.click()"))
        assertTrue(script.contains("frameHost === rule.host"))
        assertTrue(script.contains("attempts >= 3"))
        assertTrue(script.contains("window.__materialBrowserConsentActionObserver"))
        assertTrue(script.contains("if (clickedSelector && !query(clickedSelector))"))
        assertFalse(script.contains("confirmedAction"))
        assertFalse(script.contains("?.(true)"))
        assertFalse(script.contains("stop(true)"))
        assertFalse(script.contains("__materialBrowserConsentActionApplied"))
        assertTrue(
            ConsentBlockerScript.removalScript.contains(
                "window.__materialBrowserConsentActionObserver?.disconnect()",
            ),
        )
        assertTrue(script.contains(java.util.Base64.getEncoder().encodeToString(
            selector.toByteArray(),
        )))
        assertFalse(script.contains(selector))
    }

    @Test
    fun `site scoped cookie rules are selected by host and kept out of source`() {
        val selector = "#site-cookie-wall"
        val rule = CandyRule.new(
            action = CandyRuleAction.Cosmetic,
            kind = CandyRuleKind.CosmeticCss,
            firstPartyHost = "news.example",
            cosmeticSelector = selector,
            group = BundledCandyRuleGroups.Cookies,
        )

        val script = ConsentBlockerScript.create(
            cssBytes = "body{}".toByteArray(),
            siteRules = listOf(rule),
        )

        assertTrue(script.contains("host:\"news.example\""))
        assertTrue(script.contains("hostMatches(pageHost, rule.host)"))
        assertTrue(script.contains(Base64.getEncoder().encodeToString(selector.toByteArray())))
        assertFalse(script.contains(selector))
    }

    @Test
    fun `all globally hidden cmp overrides only clear inline scroll locks`() {
        val script = ConsentBlockerScript.create("body{}".toByteArray())

        listOf(
            "#BorlabsCookieBox",
            "#didomi-host",
            "#axeptio_overlay",
            "[class^=\"axeptio_widget\"]",
            "#cmpbox",
            ".cky-consent-container",
            ".cky-overlay",
            "#fides-banner-container",
            "#fides-overlay",
            ".fides-modal-overlay",
        ).forEach { selector -> assertTrue("missing $selector", script.contains(selector)) }
        assertFalse(script.contains("computed.overflowY === 'hidden'"))
        assertFalse(script.contains("setProperty('overflow-y', 'auto', 'important')"))
        assertTrue(script.contains("element.style.removeProperty(property)"))
        assertTrue(script.contains("document.querySelectorAll(selector)"))
    }

    @Test
    fun `site selectors become independent css rules`() {
        val rules = listOf("#first", "#second").mapIndexed { index, selector ->
            CandyRule.new(
                action = CandyRuleAction.Cosmetic,
                kind = CandyRuleKind.CosmeticCss,
                firstPartyHost = "news.example",
                cosmeticSelector = selector,
                group = BundledCandyRuleGroups.Cookies,
            ).copy(id = "site-$index")
        }

        val script = ConsentBlockerScript.create("body{}".toByteArray(), siteRules = rules)

        assertTrue(script.contains("siteSelectors.map(selector => selector +"))
        assertFalse(script.contains("siteSelectors.join(',')"))
    }

    @Test
    fun `site rules beyond user cosmetic limit remain embedded`() {
        val rules = (1..80).map { index ->
            CandyRule.new(
                action = CandyRuleAction.Cosmetic,
                kind = CandyRuleKind.CosmeticCss,
                firstPartyHost = "site$index.example",
                cosmeticSelector = "#cookie-$index",
                group = BundledCandyRuleGroups.Cookies,
            )
        }

        val consentScript = ConsentBlockerScript.create(
            cssBytes = "body{}".toByteArray(),
            siteRules = rules,
        )
        val adScript = CandyCosmeticScript.createScoped(rules)

        val lastSelector = Base64.getEncoder().encodeToString("#cookie-80".toByteArray())
        assertTrue(consentScript.contains(lastSelector))
        assertTrue(adScript.contains(lastSelector))
    }
}
