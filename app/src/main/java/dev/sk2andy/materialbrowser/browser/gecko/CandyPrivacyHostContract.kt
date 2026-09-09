package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.blocking.CandyRule
import dev.sk2andy.materialbrowser.blocking.CandyRuleAction
import dev.sk2andy.materialbrowser.blocking.CandyRuleKind
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollMetrics
import dev.sk2andy.materialbrowser.browser.CaptchaCompatibilityRules
import dev.sk2andy.materialbrowser.browser.FederatedLoginRules
import org.json.JSONArray
import org.json.JSONObject

internal object CandyPrivacyHostContract {
    const val EXTENSION_ID = "candy-privacy-host@sk2andy.dev"
    const val EXTENSION_LOCATION = "resource://android/assets/candy_privacy/"
    const val NATIVE_APP = "dev.sk2andy.materialbrowser.privacy"
    const val PROTOCOL_VERSION = 2

    fun isTrustedBootstrapNavigation(
        url: String,
        extensionBaseUrl: String,
        token: String,
        isDirectNavigation: Boolean,
        hasUserGesture: Boolean,
        isRedirect: Boolean,
    ): Boolean = isDirectNavigation &&
        !hasUserGesture &&
        !isRedirect &&
        url == "${extensionBaseUrl}bootstrap.html?token=$token"

    fun isTrustedSessionBindingMessage(
        nativeApp: String,
        extensionId: String,
        environmentType: Int,
        expectedEnvironmentType: Int,
        hasExpectedSession: Boolean,
        messageType: String?,
        messageToken: String?,
        expectedToken: String,
        messageRevision: Long,
        currentRevision: Long,
        bootstrapStarted: Boolean,
    ): Boolean = nativeApp == NATIVE_APP &&
        extensionId == EXTENSION_ID &&
        environmentType == expectedEnvironmentType &&
        hasExpectedSession &&
        bootstrapStarted &&
        messageType == "bound" &&
        messageToken == expectedToken &&
        messageRevision in 1..currentRevision
}

private val DEFAULT_COMPATIBILITY_REQUEST_HOSTS =
    FederatedLoginRules.compatibilityRequestHosts +
        CaptchaCompatibilityRules.compatibilityRequestHosts

internal data class GeckoPrivacyPolicy(
    val pageHost: String?,
    val blockAdsAndTrackers: Boolean,
    val hideCookieConsent: Boolean,
    val cookieBannerRemovalDisabled: Boolean,
    val pausedHosts: Set<String>,
    val candyRules: List<CandyRule>,
    val blockThirdPartyCookies: Boolean = true,
    val allowThirdPartyCookiesForSite: Boolean = false,
    val compatibilityRequestHosts: Set<String> = DEFAULT_COMPATIBILITY_REQUEST_HOSTS,
    val topInsetPx: Int = 0,
    val navigationGeneration: Int = 0,
    val scrollMetricsEnabled: Boolean = false,
) {
    companion object {
        val Disabled = GeckoPrivacyPolicy(
            pageHost = null,
            blockAdsAndTrackers = false,
            hideCookieConsent = false,
            cookieBannerRemovalDisabled = true,
            pausedHosts = emptySet(),
            candyRules = emptyList(),
            blockThirdPartyCookies = true,
            allowThirdPartyCookiesForSite = false,
            compatibilityRequestHosts = DEFAULT_COMPATIBILITY_REQUEST_HOSTS,
        )
    }
}

internal object GeckoPrivacyPolicyRules {
    fun extensionOwnedAdFilteringWithCandyCookieDefaults(
        pageHost: String?,
        pausedHosts: Set<String>,
        hideCookieConsent: Boolean,
        cookieBannerRemovalDisabled: Boolean,
        blockThirdPartyCookies: Boolean,
        allowThirdPartyCookiesForSite: Boolean,
        topInsetPx: Int = 0,
        navigationGeneration: Int = 0,
        scrollMetricsEnabled: Boolean = false,
    ): GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled.copy(
        pageHost = pageHost,
        pausedHosts = pausedHosts,
        hideCookieConsent = hideCookieConsent,
        cookieBannerRemovalDisabled = cookieBannerRemovalDisabled,
        blockThirdPartyCookies = blockThirdPartyCookies,
        allowThirdPartyCookiesForSite = allowThirdPartyCookiesForSite,
        topInsetPx = topInsetPx.coerceAtLeast(0),
        navigationGeneration = navigationGeneration.coerceAtLeast(0),
        scrollMetricsEnabled = scrollMetricsEnabled,
    )
}

internal data class GeckoPrivacyEvent(
    val requestUrl: String,
    val pageUrl: String?,
    val ruleId: String?,
    val wasBlocked: Boolean,
    val isBuiltIn: Boolean,
    val isCompatibilityObservation: Boolean,
    val safeAreaFallbackNavigationGeneration: Int? = null,
)

internal fun interface GeckoPrivacyEventSink {
    fun onEvent(event: GeckoPrivacyEvent)
}

internal fun GeckoPrivacyPolicy.toMessage(token: String, revision: Long): JSONObject = JSONObject()
    .put("type", "policy")
    .put("protocolVersion", CandyPrivacyHostContract.PROTOCOL_VERSION)
    .put("token", token)
    .put("revision", revision)
    .put("pageHost", pageHost)
    .put("blockAds", blockAdsAndTrackers)
    .put("hideConsent", hideCookieConsent)
    .put("cookieBannerRemovalDisabled", cookieBannerRemovalDisabled)
    .put("topInsetPx", topInsetPx)
    .put("navigationGeneration", navigationGeneration)
    .put("scrollMetricsEnabled", scrollMetricsEnabled)
    .put(
        "compatibilityRequestHosts",
        JSONArray(compatibilityRequestHosts.sorted()),
    )
    .put("pausedHosts", JSONArray(pausedHosts.sorted()))
    .put(
        "rules",
        JSONArray().also { values ->
            candyRules.asSequence()
                .filter { rule ->
                    rule.active && rule.kind != CandyRuleKind.CosmeticCss &&
                        rule.requestHost != null
                }
                .sortedBy(CandyRule::id)
                .forEach { rule ->
                    values.put(
                        JSONObject()
                            .put("id", rule.id)
                            .put("a", if (rule.action == CandyRuleAction.Allow) "A" else "B")
                            .put("k", if (rule.kind == CandyRuleKind.HostPair) "P" else "H")
                            .put("r", rule.requestHost)
                            .put("f", rule.firstPartyHost),
                    )
                }
        },
    )
    .put(
        "cosmetics",
        JSONArray().also { values ->
            candyRules.asSequence()
                .filter { rule ->
                    rule.active && rule.kind == CandyRuleKind.CosmeticCss &&
                        rule.action == CandyRuleAction.Cosmetic &&
                        rule.firstPartyHost != null && rule.cosmeticSelector != null
                }
                .sortedBy(CandyRule::id)
                .take(64)
                .forEach { rule ->
                    values.put(
                        JSONObject()
                            .put("id", rule.id)
                            .put("h", rule.firstPartyHost)
                            .put("s", rule.cosmeticSelector),
                    )
                }
        },
    )

internal fun pictureInPicturePlaybackMessage(
    token: String,
    revision: Long,
    expected: Boolean,
): JSONObject = JSONObject()
    .put("type", "picture-in-picture-playback")
    .put("protocolVersion", CandyPrivacyHostContract.PROTOCOL_VERSION)
    .put("token", token)
    .put("revision", revision)
    .put("expected", expected)

internal fun geckoScrollMetricsFromMessage(
    message: JSONObject,
    currentRevision: Long,
): BrowserEngineScrollMetrics? {
    if (message.optLong("revision", -1) != currentRevision) return null
    val offsetPx = message.optLong("offsetPx", -1)
    val extentPx = message.optLong("extentPx", -1)
    val rangePx = message.optLong("rangePx", -1)
    if (
        offsetPx !in 0..Int.MAX_VALUE.toLong() ||
        extentPx !in 1..Int.MAX_VALUE.toLong() ||
        rangePx !in extentPx..Int.MAX_VALUE.toLong()
    ) {
        return null
    }
    return BrowserEngineScrollMetrics(
        offsetPx = offsetPx.coerceAtMost(rangePx - extentPx).toInt(),
        extentPx = extentPx.toInt(),
        rangePx = rangePx.toInt(),
    )
}
