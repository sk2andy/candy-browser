package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.blocking.PrivacyPartyClassifier
import dev.sk2andy.materialbrowser.blocking.PrivacyPartyRelation
import dev.sk2andy.materialbrowser.blocking.PrivacyRequestSanitizer
import java.net.URI

enum class CaptchaProvider(val displayName: String) {
    Cloudflare("Cloudflare"),
    GoogleRecaptcha("Google reCAPTCHA"),
    HCaptcha("hCaptcha"),
}

enum class CaptchaCompatibilityPromptChoice {
    AllowForTab,
    AllowForProfile,
    Deny,
}

data class CaptchaCompatibilityOffer(
    val token: Long,
    val tabId: String,
    val profileId: String,
    val pageHost: String,
    val provider: CaptchaProvider,
    val isPrivate: Boolean,
    val navigationGeneration: Int,
    val showDialog: Boolean = false,
)

object CaptchaCompatibilityRules {
    fun providerForSubresource(
        requestUrl: String,
        pageUrl: String,
    ): CaptchaProvider? {
        val uri = secureWebUri(requestUrl) ?: return null
        val requestHost = PrivacyRequestSanitizer.webHost(requestUrl) ?: return null
        val pageHost = PrivacyRequestSanitizer.webHost(pageUrl) ?: return null
        if (PrivacyPartyClassifier.classify(requestHost, pageHost) !=
            PrivacyPartyRelation.ThirdParty
        ) return null
        val path = uri.path?.lowercase() ?: return null
        return when {
            requestHost.matchesCloudflareChallengeHost() &&
                CLOUDFLARE_PATH_PREFIXES.any(path::startsWith) -> CaptchaProvider.Cloudflare
            requestHost in GOOGLE_RECAPTCHA_HOSTS && path in GOOGLE_RECAPTCHA_PATHS ->
                CaptchaProvider.GoogleRecaptcha
            requestHost == HCAPTCHA_HOST && path == HCAPTCHA_API_PATH -> CaptchaProvider.HCaptcha
            else -> null
        }
    }

    private fun String.matchesCloudflareChallengeHost(): Boolean =
        this == CLOUDFLARE_CHALLENGE_HOST || endsWith(".$CLOUDFLARE_CHALLENGE_HOST")

    private fun secureWebUri(url: String): URI? = runCatching { URI(url) }.getOrNull()
        ?.takeIf { uri -> uri.scheme?.lowercase() == "https" }

    private const val CLOUDFLARE_CHALLENGE_HOST = "challenges.cloudflare.com"
    private val CLOUDFLARE_PATH_PREFIXES = listOf(
        "/turnstile/",
        "/cdn-cgi/challenge-platform/",
    )
    private val GOOGLE_RECAPTCHA_HOSTS = setOf("www.google.com", "www.recaptcha.net")
    private val GOOGLE_RECAPTCHA_PATHS = setOf(
        "/recaptcha/api.js",
        "/recaptcha/enterprise.js",
    )
    private const val HCAPTCHA_HOST = "js.hcaptcha.com"
    private const val HCAPTCHA_API_PATH = "/1/api.js"
}
