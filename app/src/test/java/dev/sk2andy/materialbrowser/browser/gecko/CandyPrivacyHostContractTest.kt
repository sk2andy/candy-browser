package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.blocking.CandyRule
import dev.sk2andy.materialbrowser.blocking.CandyRuleAction
import dev.sk2andy.materialbrowser.blocking.CandyRuleKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyPrivacyHostContractTest {
    @Test
    fun `extension owned ad filtering keeps only Candy cookie defaults`() {
        val policy = GeckoPrivacyPolicyRules.extensionOwnedAdFilteringWithCandyCookieDefaults(
            pageHost = "news.example",
            pausedHosts = setOf("paused.example"),
            hideCookieConsent = true,
            cookieBannerRemovalDisabled = false,
            blockThirdPartyCookies = true,
            allowThirdPartyCookiesForSite = false,
            topInsetPx = 96,
            navigationGeneration = 4,
            scrollMetricsEnabled = true,
            safeAreaLayoutQuietPeriodMillis = 250,
            safeAreaRequiredFailureCount = 4,
        )

        assertFalse(policy.blockAdsAndTrackers)
        assertTrue(policy.hideCookieConsent)
        assertFalse(policy.cookieBannerRemovalDisabled)
        assertTrue(policy.candyRules.isEmpty())
        assertEquals("news.example", policy.pageHost)
        assertEquals(setOf("paused.example"), policy.pausedHosts)
        assertTrue(policy.blockThirdPartyCookies)
        assertFalse(policy.allowThirdPartyCookiesForSite)
        assertEquals(96, policy.topInsetPx)
        assertEquals(4, policy.navigationGeneration)
        assertTrue(policy.scrollMetricsEnabled)
        assertEquals(250, policy.safeAreaLayoutQuietPeriodMillis)
        assertEquals(4, policy.safeAreaRequiredFailureCount)
    }

    @Test
    fun `safe area quiet period stays on the developer settings grid`() {
        val policy = GeckoPrivacyPolicyRules.extensionOwnedAdFilteringWithCandyCookieDefaults(
            pageHost = null,
            pausedHosts = emptySet(),
            hideCookieConsent = false,
            cookieBannerRemovalDisabled = false,
            blockThirdPartyCookies = true,
            allowThirdPartyCookiesForSite = false,
            safeAreaLayoutQuietPeriodMillis = 126,
        )

        assertEquals(150, policy.safeAreaLayoutQuietPeriodMillis)
    }

    @Test
    fun `only exact internal bootstrap navigation is trusted`() {
        val baseUrl = "moz-extension://trusted-origin/"
        val token = "session-token"

        assertTrue(
            CandyPrivacyHostContract.isTrustedBootstrapNavigation(
                url = "${baseUrl}bootstrap.html?token=$token",
                extensionBaseUrl = baseUrl,
                token = token,
                isDirectNavigation = true,
                hasUserGesture = false,
                isRedirect = false,
            ),
        )
        listOf(
            "moz-extension://other-origin/bootstrap.html?token=$token",
            "${baseUrl}other.html?token=$token",
            "${baseUrl}bootstrap.html?token=other-token",
        ).forEach { url ->
            assertFalse(
                CandyPrivacyHostContract.isTrustedBootstrapNavigation(
                    url = url,
                    extensionBaseUrl = baseUrl,
                    token = token,
                    isDirectNavigation = true,
                    hasUserGesture = false,
                    isRedirect = false,
                ),
            )
        }
    }

    @Test
    fun `gesture redirect and indirect bootstrap navigation remain untrusted`() {
        val baseUrl = "moz-extension://trusted-origin/"
        val url = "${baseUrl}bootstrap.html?token=session-token"

        listOf(
            Triple(false, false, false),
            Triple(true, true, false),
            Triple(true, false, true),
        ).forEach { (isDirectNavigation, hasUserGesture, isRedirect) ->
            assertFalse(
                CandyPrivacyHostContract.isTrustedBootstrapNavigation(
                    url = url,
                    extensionBaseUrl = baseUrl,
                    token = "session-token",
                    isDirectNavigation = isDirectNavigation,
                    hasUserGesture = hasUserGesture,
                    isRedirect = isRedirect,
                ),
            )
        }
    }

    @Test
    fun `session binding requires exact sender generation and an active bootstrap`() {
        fun accepted(
            nativeApp: String = CandyPrivacyHostContract.NATIVE_APP,
            extensionId: String = CandyPrivacyHostContract.EXTENSION_ID,
            environmentType: Int = 7,
            hasExpectedSession: Boolean = true,
            messageType: String = "bound",
            messageToken: String = "session-token",
            messageRevision: Long = 2,
            currentRevision: Long = 2,
            bootstrapStarted: Boolean = true,
        ) = CandyPrivacyHostContract.isTrustedSessionBindingMessage(
            nativeApp = nativeApp,
            extensionId = extensionId,
            environmentType = environmentType,
            expectedEnvironmentType = 7,
            hasExpectedSession = hasExpectedSession,
            messageType = messageType,
            messageToken = messageToken,
            expectedToken = "session-token",
            messageRevision = messageRevision,
            currentRevision = currentRevision,
            bootstrapStarted = bootstrapStarted,
        )

        assertTrue(accepted())
        assertTrue(accepted(messageRevision = 1, currentRevision = 2))
        assertFalse(accepted(nativeApp = "other.native.app"))
        assertFalse(accepted(extensionId = "other@extension"))
        assertFalse(accepted(environmentType = 8))
        assertFalse(accepted(hasExpectedSession = false))
        assertFalse(accepted(messageType = "policy-ready"))
        assertFalse(accepted(messageToken = "stale-token"))
        assertFalse(accepted(messageRevision = 3, currentRevision = 2))
        assertFalse(accepted(bootstrapStarted = false))
    }

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
        assertEquals(0, message.getInt("topInsetPx"))
        assertFalse(message.has("viewportCoverAllowed"))
        assertEquals(0, message.getInt("navigationGeneration"))
        assertFalse(message.getBoolean("scrollMetricsEnabled"))
        assertEquals(400, message.getInt("safeAreaLayoutQuietPeriodMillis"))
        assertEquals(3, message.getInt("safeAreaRequiredFailureCount"))
        assertEquals(
            listOf(
                "accounts.google.com",
                "challenges.cloudflare.com",
                "js.hcaptcha.com",
                "www.google.com",
                "www.recaptcha.net",
            ),
            message.getJSONArray("compatibilityRequestHosts").strings(),
        )
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

    @Test
    fun `picture in picture playback message is scoped to one bound revision`() {
        val message = pictureInPicturePlaybackMessage(
            token = "session-token",
            revision = 7,
            expected = true,
        )

        assertEquals("picture-in-picture-playback", message.getString("type"))
        assertEquals(CandyPrivacyHostContract.PROTOCOL_VERSION, message.getInt("protocolVersion"))
        assertEquals("session-token", message.getString("token"))
        assertEquals(7, message.getLong("revision"))
        assertTrue(message.getBoolean("expected"))
    }

    @Test
    fun `scroll metrics accept current bounded document geometry`() {
        val metrics = geckoScrollMetricsFromMessage(
            message = JSONObject()
                .put("revision", 7)
                .put("offsetPx", 900)
                .put("extentPx", 1_000)
                .put("rangePx", 4_000),
            currentRevision = 7,
        )

        requireNotNull(metrics)
        assertEquals(900, metrics.offsetPx)
        assertEquals(1_000, metrics.extentPx)
        assertEquals(4_000, metrics.rangePx)
    }

    @Test
    fun `scroll metrics reject stale invalid and unbounded messages`() {
        fun message(revision: Long = 7, offset: Long = 0, extent: Long = 1, range: Long = 1) =
            JSONObject()
                .put("revision", revision)
                .put("offsetPx", offset)
                .put("extentPx", extent)
                .put("rangePx", range)

        assertEquals(null, geckoScrollMetricsFromMessage(message(revision = 6), 7))
        assertEquals(null, geckoScrollMetricsFromMessage(message(offset = -1), 7))
        assertEquals(null, geckoScrollMetricsFromMessage(message(extent = 0), 7))
        assertEquals(null, geckoScrollMetricsFromMessage(message(extent = 2, range = 1), 7))
        assertEquals(
            null,
            geckoScrollMetricsFromMessage(message(range = Int.MAX_VALUE.toLong() + 1), 7),
        )
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
