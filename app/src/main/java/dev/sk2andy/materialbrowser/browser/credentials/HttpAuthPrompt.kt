package dev.sk2andy.materialbrowser.browser.credentials

import java.net.IDN
import java.net.URI

data class HttpAuthPrompt(
    val id: Long,
    val tabId: String,
    val host: String,
    val realm: String?,
    val isPageSecure: Boolean,
)

internal data class HttpAuthChallengeDetails(
    val host: String,
    val realm: String?,
    val isPageSecure: Boolean,
)

internal object HttpAuthPromptRules {
    private const val MAX_HOST_LENGTH = 255
    private const val MAX_REALM_LENGTH = 200

    fun challengeDetails(
        host: String?,
        realm: String?,
        pageUrl: String?,
    ): HttpAuthChallengeDetails? {
        val challengeHost = canonicalHost(host) ?: return null
        val page = pageUrl?.let { value -> runCatching { URI(value) }.getOrNull() }
            ?.takeIf { uri -> uri.isAbsolute && !uri.isOpaque && uri.rawUserInfo == null }
            ?: return null
        val scheme = page.scheme?.lowercase()?.takeIf { it == "http" || it == "https" }
            ?: return null
        val pageHost = canonicalHost(page.host) ?: return null
        if (challengeHost != pageHost) return null
        val displayHost = if (':' in pageHost) "[$pageHost]" else pageHost
        val displayRealm = realm
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.takeIf { value -> value.length <= MAX_REALM_LENGTH }
            ?.takeIf { value -> value.none(::isUnsafeDisplayCharacter) }
        return HttpAuthChallengeDetails(
            host = displayHost,
            realm = displayRealm,
            isPageSecure = scheme == "https",
        )
    }

    private fun canonicalHost(value: String?): String? {
        val candidate = value
            ?.trim()
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.trimEnd('.')
            ?.takeIf(String::isNotEmpty)
            ?.takeIf { host -> host.length <= MAX_HOST_LENGTH }
            ?.takeIf { host -> host.none(::isUnsafeDisplayCharacter) }
            ?: return null
        return runCatching {
            if (':' in candidate) candidate.lowercase()
            else IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).lowercase()
        }.getOrNull()?.takeIf(String::isNotEmpty)
    }

    private fun isUnsafeDisplayCharacter(character: Char): Boolean =
        character.isISOControl() || when (character.code) {
            in 0x202A..0x202E,
            in 0x2066..0x2069,
            -> true
            else -> false
        }
}
