package dev.sk2andy.materialbrowser.browser.credentials

import java.net.IDN
import java.net.URI

internal data class CredentialPromptIdentity(
    val tabId: String,
    val profileId: String,
    val origin: String,
    val sessionGeneration: Long,
    val navigationGeneration: Long,
)

/** Password value stays transient and its string representation never exposes credential data. */
internal class CredentialLogin(
    val username: String,
    val password: String,
) {
    override fun toString(): String = "CredentialLogin(username=<redacted>, password=<redacted>)"
}

internal class CredentialLoginSavePrompt(
    val identity: CredentialPromptIdentity,
    val login: CredentialLogin,
)

internal data class CredentialLoginSelectPrompt(
    val identity: CredentialPromptIdentity,
    val allowedUserIds: Set<String>,
)

internal data class IdentityCredentialProvider(
    val id: Int,
    val name: String,
    val domain: String,
)

internal data class IdentityCredentialAccount(
    val id: Int,
    val name: String,
    val email: String,
)

internal data class IdentityCredentialProviderPrompt(
    val identity: CredentialPromptIdentity,
    val providers: List<IdentityCredentialProvider>,
)

internal data class IdentityCredentialAccountPrompt(
    val identity: CredentialPromptIdentity,
    val providerName: String,
    val providerDomain: String,
    val accounts: List<IdentityCredentialAccount>,
)

internal data class IdentityCredentialPrivacyPrompt(
    val identity: CredentialPromptIdentity,
    val providerName: String,
    val providerDomain: String,
    val privacyPolicyUrl: String,
    val termsOfServiceUrl: String?,
)

/**
 * Browser-engine-neutral edge for system credential UI. Implementations must complete each request
 * exactly once and must not persist, log or otherwise retain credential material.
 */
internal interface CredentialPromptHost : AutoCloseable {
    fun saveLogin(prompt: CredentialLoginSavePrompt, onComplete: (Boolean) -> Unit)

    fun selectLogin(prompt: CredentialLoginSelectPrompt, onComplete: (CredentialLogin?) -> Unit)

    fun selectIdentityProvider(
        prompt: IdentityCredentialProviderPrompt,
        onComplete: (Int?) -> Unit,
    )

    fun selectIdentityAccount(
        prompt: IdentityCredentialAccountPrompt,
        onComplete: (Int?) -> Unit,
    )

    fun confirmIdentityPrivacyPolicy(
        prompt: IdentityCredentialPrivacyPrompt,
        onComplete: (Boolean) -> Unit,
    )

    override fun close()
}

internal object CredentialPromptRules {
    private const val MAX_TAB_ID_LENGTH = 200
    private const val MAX_PROFILE_ID_LENGTH = 200
    private const val MAX_USERNAME_LENGTH = 1_024
    private const val MAX_PASSWORD_LENGTH = 4_096
    private const val MAX_OPTIONS = 100
    private const val MAX_LABEL_LENGTH = 256
    private const val MAX_URL_LENGTH = 4_096

    fun identity(
        tabId: String?,
        profileId: String,
        isPrivate: Boolean,
        isActive: Boolean,
        pageUrl: String?,
        sessionGeneration: Long,
        navigationGeneration: Long,
    ): CredentialPromptIdentity? {
        if (isPrivate || !isActive || sessionGeneration <= 0 || navigationGeneration < 0) return null
        val safeTabId = boundedText(tabId, MAX_TAB_ID_LENGTH) ?: return null
        val safeProfileId = boundedText(profileId, MAX_PROFILE_ID_LENGTH) ?: return null
        val origin = httpsOrigin(pageUrl) ?: return null
        return CredentialPromptIdentity(
            tabId = safeTabId,
            profileId = safeProfileId,
            origin = origin,
            sessionGeneration = sessionGeneration,
            navigationGeneration = navigationGeneration,
        )
    }

    fun login(username: String?, password: String?): CredentialLogin? {
        val safeUsername = username
            ?.takeIf { value -> value.length <= MAX_USERNAME_LENGTH }
            ?.takeIf { value -> value.none(::isUnsafeTextCharacter) }
            ?: return null
        val safePassword = password
            ?.takeIf(String::isNotEmpty)
            ?.takeIf { value -> value.length <= MAX_PASSWORD_LENGTH }
            ?.takeIf { value -> '\u0000' !in value }
            ?: return null
        return CredentialLogin(username = safeUsername, password = safePassword)
    }

    fun allowedUserIds(values: List<String>): Set<String>? {
        if (values.size > MAX_OPTIONS) return null
        return values.asSequence()
            .mapNotNull { value ->
                value.takeIf { it.length <= MAX_USERNAME_LENGTH }
                    ?.takeIf { it.none(::isUnsafeTextCharacter) }
            }
            .toSet()
    }

    fun acceptsSelectedUser(username: String, allowedUserIds: Set<String>): Boolean =
        username.length <= MAX_USERNAME_LENGTH &&
            username.none(::isUnsafeTextCharacter) &&
            (allowedUserIds.isEmpty() || username in allowedUserIds)

    fun matchesOrigin(candidate: String?, expected: String): Boolean =
        httpsOrigin(candidate) == expected

    fun displayLabel(value: String?): String? = boundedText(value, MAX_LABEL_LENGTH)

    fun providerDomain(value: String?): String? = canonicalDomain(value)

    fun providers(
        values: List<IdentityCredentialProvider>,
    ): List<IdentityCredentialProvider>? {
        if (values.isEmpty() || values.size > MAX_OPTIONS) return null
        val sanitized = values.mapNotNull { provider ->
            val name = boundedText(provider.name, MAX_LABEL_LENGTH) ?: return@mapNotNull null
            val domain = canonicalDomain(provider.domain) ?: return@mapNotNull null
            provider.copy(name = name, domain = domain)
        }
        if (sanitized.size != values.size || sanitized.any { it.id < 0 }) return null
        return sanitized.takeIf { providers ->
            providers.map(IdentityCredentialProvider::id).distinct().size == providers.size
        }
    }

    fun accounts(
        values: List<IdentityCredentialAccount>,
    ): List<IdentityCredentialAccount>? {
        if (values.isEmpty() || values.size > MAX_OPTIONS) return null
        val sanitized = values.mapNotNull { account ->
            val name = boundedText(account.name, MAX_LABEL_LENGTH) ?: return@mapNotNull null
            val email = boundedText(account.email, MAX_LABEL_LENGTH) ?: return@mapNotNull null
            account.copy(name = name, email = email)
        }
        if (sanitized.size != values.size || sanitized.any { it.id < 0 }) return null
        return sanitized.takeIf { accounts ->
            accounts.map(IdentityCredentialAccount::id).distinct().size == accounts.size
        }
    }

    fun identityPrivacyPrompt(
        identity: CredentialPromptIdentity,
        providerName: String?,
        providerDomain: String?,
        relyingPartyHost: String?,
        privacyPolicyUrl: String?,
        termsOfServiceUrl: String?,
    ): IdentityCredentialPrivacyPrompt? {
        val name = boundedText(providerName, MAX_LABEL_LENGTH) ?: return null
        val domain = canonicalDomain(providerDomain) ?: return null
        val currentHost = runCatching { URI(identity.origin).host }.getOrNull()
            ?.let(::canonicalDomain)
            ?: return null
        if (canonicalDomain(relyingPartyHost) != currentHost) return null
        val privacyUrl = httpsUrlForDomain(privacyPolicyUrl, domain) ?: return null
        val termsUrl = termsOfServiceUrl
            ?.takeIf(String::isNotBlank)
            ?.let { value -> httpsUrlForDomain(value, domain) ?: return null }
        return IdentityCredentialPrivacyPrompt(
            identity = identity,
            providerName = name,
            providerDomain = domain,
            privacyPolicyUrl = privacyUrl,
            termsOfServiceUrl = termsUrl,
        )
    }

    fun remainsCurrent(
        captured: CredentialPromptIdentity,
        current: CredentialPromptIdentity?,
    ): Boolean = captured == current

    private fun httpsOrigin(value: String?): String? {
        val uri = parseHttps(value) ?: return null
        return runCatching {
            URI("https", null, canonicalDomain(uri.host) ?: return null, uri.port, null, null, null)
                .toASCIIString()
        }.getOrNull()
    }

    private fun httpsUrlForDomain(value: String?, domain: String): String? {
        val candidate = value?.takeIf { it.length <= MAX_URL_LENGTH } ?: return null
        val uri = parseHttps(candidate) ?: return null
        val host = canonicalDomain(uri.host) ?: return null
        if (host != domain && !host.endsWith(".$domain")) return null
        return uri.toASCIIString()
    }

    private fun parseHttps(value: String?): URI? = value
        ?.takeIf { it.length <= MAX_URL_LENGTH }
        ?.let { candidate -> runCatching { URI(candidate) }.getOrNull() }
        ?.takeIf { uri ->
            uri.isAbsolute && !uri.isOpaque && uri.scheme.equals("https", ignoreCase = true) &&
                uri.rawUserInfo == null && uri.host != null
        }

    private fun canonicalDomain(value: String?): String? {
        val candidate = value
            ?.trim()
            ?.trimEnd('.')
            ?.takeIf(String::isNotEmpty)
            ?.takeIf { it.length <= 255 && ':' !in it }
            ?.takeIf { it.none(::isUnsafeTextCharacter) }
            ?: return null
        return runCatching { IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).lowercase() }
            .getOrNull()
            ?.takeIf(String::isNotEmpty)
    }

    private fun boundedText(value: String?, maximumLength: Int): String? = value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.takeIf { it.length <= maximumLength }
        ?.takeIf { it.none(::isUnsafeTextCharacter) }

    private fun isUnsafeTextCharacter(character: Char): Boolean =
        character.isISOControl() || when (character.code) {
            in 0x202A..0x202E,
            in 0x2066..0x2069,
            -> true
            else -> false
        }
}
