package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.blocking.PrivacyPartyClassifier
import dev.sk2andy.materialbrowser.blocking.PrivacyPartyRelation
import dev.sk2andy.materialbrowser.blocking.PrivacyRequestSanitizer
import java.net.URI

enum class FederatedLoginProvider(val displayName: String) {
    Google("Google"),
}

enum class FederatedLoginPromptChoice {
    AllowForTab,
    AllowForProfile,
    Deny,
}

data class FederatedLoginOffer(
    val token: Long,
    val tabId: String,
    val profileId: String,
    val pageHost: String,
    val provider: FederatedLoginProvider,
    val isPrivate: Boolean,
    val navigationGeneration: Int,
    val showDialog: Boolean = false,
)

object FederatedLoginRules {
    private val webViewMarkerPattern = Regex(""";\s*wv(?=[;)])""", RegexOption.IGNORE_CASE)
    private val versionTokenPattern = Regex("""\s+Version/\S+""", RegexOption.IGNORE_CASE)
    private val repeatedWhitespacePattern = Regex("""\s{2,}""")

    fun providerForSubresource(
        requestUrl: String,
        pageUrl: String,
    ): FederatedLoginProvider? {
        val requestHost = PrivacyRequestSanitizer.webHost(requestUrl) ?: return null
        val pageHost = PrivacyRequestSanitizer.webHost(pageUrl) ?: return null
        if (PrivacyPartyClassifier.classify(requestHost, pageHost) ==
            PrivacyPartyRelation.FirstParty
        ) return null
        val path = webPath(requestUrl) ?: return null
        return when {
            requestHost == GOOGLE_ACCOUNTS_HOST && path in GOOGLE_SDK_PATHS ->
                FederatedLoginProvider.Google
            else -> null
        }
    }

    fun isProviderNavigation(url: String): Boolean {
        val uri = secureWebUri(url) ?: return false
        val host = PrivacyRequestSanitizer.webHost(url) ?: return false
        val path = uri.path?.lowercase() ?: return false
        return host == GOOGLE_ACCOUNTS_HOST && GOOGLE_AUTH_PATH_PREFIXES.any(path::startsWith)
    }

    fun compatibleUserAgent(defaultUserAgent: String): String = defaultUserAgent
        .replace(webViewMarkerPattern, "")
        .replace(versionTokenPattern, "")
        .replace(repeatedWhitespacePattern, " ")
        .trim()

    private fun webPath(url: String): String? {
        return secureWebUri(url)?.path?.lowercase()
    }

    private fun secureWebUri(url: String): URI? = runCatching { URI(url) }.getOrNull()
        ?.takeIf { uri -> uri.scheme?.lowercase() == "https" }

    private const val GOOGLE_ACCOUNTS_HOST = "accounts.google.com"
    private val GOOGLE_SDK_PATHS = setOf("/gsi/client", "/gsi/fedcm.json")
    private val GOOGLE_AUTH_PATH_PREFIXES = listOf(
        "/gsi/",
        "/o/oauth2/",
        "/signin/oauth/",
    )
}
