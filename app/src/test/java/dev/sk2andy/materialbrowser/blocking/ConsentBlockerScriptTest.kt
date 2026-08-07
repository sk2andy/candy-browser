package dev.sk2andy.materialbrowser.blocking

import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentBlockerScriptTest {
    @Test
    fun `script is restricted to the top frame`() {
        val script = ConsentBlockerScript.create("body{}".toByteArray())

        assertTrue(script.contains("if (window.top !== window) return"))
        assertFalse(script.contains("pausedHosts"))
        assertFalse(script.contains("exactPausedHosts"))
    }

    @Test
    fun `embeds css as utf8 base64 instead of executable source`() {
        val css = "#cookie-ä { display: none } </style><script>bad()</script>"

        val script = ConsentBlockerScript.create(css.toByteArray())

        assertTrue(script.contains(Base64.getEncoder().encodeToString(css.toByteArray())))
        assertTrue(script.contains("new TextDecoder('utf-8')"))
        assertFalse(script.contains("<script>bad()</script>"))
    }

    @Test
    fun `script inserts css once without waiting or interacting`() {
        val script = ConsentBlockerScript.create("#banner {}".toByteArray())

        assertTrue(script.contains("document.getElementById(styleId)"))
        assertTrue(script.contains("target.appendChild(style)"))
        assertFalse(script.contains("MutationObserver"))
        assertFalse(script.contains("setTimeout"))
        assertFalse(script.contains("addEventListener"))
        assertFalse(script.contains("querySelector"))
        assertFalse(script.contains(".click()"))
        assertFalse(script.contains("overflow-y"))
    }

    @Test
    fun `removal only removes the injected style`() {
        assertTrue(ConsentBlockerScript.removalScript.contains("getElementById"))
        assertTrue(ConsentBlockerScript.removalScript.contains("?.remove()"))
        assertFalse(ConsentBlockerScript.removalScript.contains("disconnect"))
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
        assertTrue(script.contains("pageHost.endsWith('.' + rule.host)"))
        assertTrue(script.contains(Base64.getEncoder().encodeToString(selector.toByteArray())))
        assertFalse(script.contains(selector))
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

        assertTrue(script.contains(".map(rule => decodeBase64Utf8(rule.selector) +"))
        assertTrue(script.contains(".join('\\n')"))
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
