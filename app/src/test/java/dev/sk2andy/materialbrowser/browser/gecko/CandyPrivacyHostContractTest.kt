package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.blocking.CandyRule
import dev.sk2andy.materialbrowser.blocking.CandyRuleAction
import dev.sk2andy.materialbrowser.blocking.CandyRuleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyPrivacyHostContractTest {
    @Test
    fun `policy message preserves host pair allow precedence inputs deterministically`() {
        val message = GeckoPrivacyPolicy(
            pageHost = "news.example",
            blockAdsAndTrackers = true,
            hideCookieConsent = true,
            cookieBannerRemovalDisabled = false,
            pausedHosts = setOf("z.example", "a.example"),
            candyRules = listOf(
                rule(
                    id = "pair-allow",
                    action = CandyRuleAction.Allow,
                    kind = CandyRuleKind.HostPair,
                    requestHost = "tracker.example",
                    firstPartyHost = "news.example",
                ),
                rule(
                    id = "host-block",
                    action = CandyRuleAction.Block,
                    kind = CandyRuleKind.RequestHost,
                    requestHost = "tracker.example",
                ),
            ),
        ).toMessage(token = "session-token", revision = 7)

        assertEquals(CandyPrivacyHostContract.PROTOCOL_VERSION, message.getInt("protocolVersion"))
        assertEquals("session-token", message.getString("token"))
        assertEquals(7, message.getLong("revision"))
        assertEquals("news.example", message.getString("pageHost"))
        assertTrue(message.getBoolean("blockAds"))
        assertTrue(message.getBoolean("hideConsent"))
        assertFalse(message.getBoolean("cookieBannerRemovalDisabled"))
        assertEquals(listOf("a.example", "z.example"), message.getJSONArray("pausedHosts").strings())
        assertEquals(listOf("host-block", "pair-allow"), message.getJSONArray("rules").ids())
        assertEquals("P", message.getJSONArray("rules").getJSONObject(1).getString("k"))
        assertEquals("A", message.getJSONArray("rules").getJSONObject(1).getString("a"))
    }

    @Test
    fun `policy separates active cosmetic rules from request rules`() {
        val message = GeckoPrivacyPolicy.Disabled.copy(
            candyRules = listOf(
                rule(
                    id = "inactive",
                    action = CandyRuleAction.Block,
                    kind = CandyRuleKind.RequestHost,
                    requestHost = "tracker.example",
                    active = false,
                ),
                rule(
                    id = "cosmetic",
                    action = CandyRuleAction.Cosmetic,
                    kind = CandyRuleKind.CosmeticCss,
                    firstPartyHost = "news.example",
                ),
                rule(
                    id = "inactive-cosmetic",
                    action = CandyRuleAction.Cosmetic,
                    kind = CandyRuleKind.CosmeticCss,
                    firstPartyHost = "news.example",
                    active = false,
                ),
            ),
        ).toMessage(token = "session-token", revision = 1)

        assertEquals(0, message.getJSONArray("rules").length())
        assertEquals(listOf("cosmetic"), message.getJSONArray("cosmetics").ids())
        assertEquals("news.example", message.getJSONArray("cosmetics").getJSONObject(0).getString("h"))
        assertEquals(".ad", message.getJSONArray("cosmetics").getJSONObject(0).getString("s"))
    }

    private fun rule(
        id: String,
        action: CandyRuleAction,
        kind: CandyRuleKind,
        requestHost: String? = null,
        firstPartyHost: String? = null,
        active: Boolean = true,
    ) = CandyRule(
        id = id,
        action = action,
        kind = kind,
        requestHost = requestHost,
        firstPartyHost = firstPartyHost,
        cosmeticSelector = if (kind == CandyRuleKind.CosmeticCss) ".ad" else null,
        active = active,
    )
}

private fun org.json.JSONArray.strings(): List<String> =
    (0 until length()).map(::getString)

private fun org.json.JSONArray.ids(): List<String> =
    (0 until length()).map { index -> getJSONObject(index).getString("id") }
