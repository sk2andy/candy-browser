package dev.sk2andy.materialbrowser.browser.userscript

import java.net.URI
import java.security.MessageDigest
import java.util.Base64

internal data class UserScriptDependencyFetch(
    val bytes: ByteArray,
    val mimeType: String? = null,
)

internal fun interface UserScriptDependencyFetcher {
    fun fetch(url: String, maxBytes: Int): UserScriptDependencyFetch?
}

internal enum class UserScriptDependencyFailureReason {
    InvalidDeclaration,
    Network,
    TooLarge,
    InvalidUtf8,
    IntegrityMismatch,
    TotalTooLarge,
}

internal sealed interface UserScriptDependencyResolution {
    data class Resolved(val script: UserScript) : UserScriptDependencyResolution

    data class Failed(
        val reason: UserScriptDependencyFailureReason,
        val dependencyUrl: String? = null,
    ) : UserScriptDependencyResolution
}

/** Resolves declared dependencies only when explicitly called by an import/install flow. */
internal class UserScriptDependencyResolver(
    private val fetcher: UserScriptDependencyFetcher,
) {
    fun resolve(script: UserScript): UserScriptDependencyResolution {
        if (!UserScriptRules.hasCanonicalMetadata(script)) {
            return failed(UserScriptDependencyFailureReason.InvalidDeclaration)
        }
        var totalBytes = 0L
        val requires = buildList {
            script.requires.forEach { dependency ->
                val fetched = fetch(dependency.url, MAX_REQUIRE_BYTES)
                    ?: return failed(UserScriptDependencyFailureReason.Network, dependency.url)
                val bytes = fetched.bytes
                if (bytes.size > MAX_REQUIRE_BYTES) {
                    return failed(UserScriptDependencyFailureReason.TooLarge, dependency.url)
                }
                totalBytes += bytes.size
                if (totalBytes > MAX_TOTAL_DEPENDENCY_BYTES) {
                    return failed(UserScriptDependencyFailureReason.TotalTooLarge, dependency.url)
                }
                if (!UserScriptDependencyRules.matchesIntegrity(bytes, dependency.sha256)) {
                    return failed(UserScriptDependencyFailureReason.IntegrityMismatch, dependency.url)
                }
                val source = UserScriptDependencyRules.decodeStrictUtf8(bytes)
                    ?: return failed(UserScriptDependencyFailureReason.InvalidUtf8, dependency.url)
                add(dependency.copy(source = source))
            }
        }
        val resources = buildList {
            script.resources.forEach { dependency ->
                val fetched = fetch(dependency.url, MAX_RESOURCE_BYTES)
                    ?: return failed(UserScriptDependencyFailureReason.Network, dependency.url)
                val bytes = fetched.bytes
                if (bytes.size > MAX_RESOURCE_BYTES) {
                    return failed(UserScriptDependencyFailureReason.TooLarge, dependency.url)
                }
                totalBytes += bytes.size
                if (totalBytes > MAX_TOTAL_DEPENDENCY_BYTES) {
                    return failed(UserScriptDependencyFailureReason.TotalTooLarge, dependency.url)
                }
                if (!UserScriptDependencyRules.matchesIntegrity(bytes, dependency.sha256)) {
                    return failed(UserScriptDependencyFailureReason.IntegrityMismatch, dependency.url)
                }
                add(
                    dependency.copy(
                        encodedContent = Base64.getEncoder().encodeToString(bytes),
                        mimeType = UserScriptDependencyRules.normalizeMimeType(fetched.mimeType),
                    ),
                )
            }
        }
        val resolved = script.copy(requires = requires, resources = resources)
        return if (UserScriptRules.isCanonical(resolved)) {
            UserScriptDependencyResolution.Resolved(resolved)
        } else {
            failed(UserScriptDependencyFailureReason.InvalidDeclaration)
        }
    }

    private fun fetch(url: String, maxBytes: Int): UserScriptDependencyFetch? =
        runCatching { fetcher.fetch(url, maxBytes) }.getOrNull()

    private fun failed(
        reason: UserScriptDependencyFailureReason,
        url: String? = null,
    ) = UserScriptDependencyResolution.Failed(reason, url)

    internal companion object {
        const val MAX_REQUIRE_BYTES = 256 * 1_024
        const val MAX_RESOURCE_BYTES = 512 * 1_024
        const val MAX_TOTAL_DEPENDENCY_BYTES = 2 * 1_024 * 1_024
    }
}

internal object UserScriptDependencyRules {
    const val MAX_REQUIRES = 16
    const val MAX_RESOURCES = 16
    const val MAX_RESOURCE_NAME_CHARS = 128

    fun parseUrl(value: String): UserScriptDependencyUrl? {
        if (value.isBlank() || value.any { it.isWhitespace() || it.isISOControl() }) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https" || !uri.isAbsolute) return null
        if (uri.rawAuthority.isNullOrBlank() || uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port != 443) return null
        uri.host?.lowercase()?.takeIf(::isPublicHostnameCandidate) ?: return null
        val sha256 = when (val fragment = uri.rawFragment) {
            null -> null
            else -> SHA256_FRAGMENT.matchEntire(fragment)?.groupValues?.get(1)?.lowercase()
                ?: return null
        }
        return UserScriptDependencyUrl(value.substringBefore('#'), sha256)
    }

    fun isValidResourceName(value: String): Boolean =
        value.length in 1..MAX_RESOURCE_NAME_CHARS &&
            value.none { it.isWhitespace() || it.isISOControl() }

    fun isTrustedFetchHost(value: String): Boolean = value.lowercase() in TRUSTED_FETCH_HOSTS

    fun resolveTrustedRedirect(currentUrl: String, location: String): String? {
        val resolved = runCatching { URI(currentUrl).resolve(location).toString() }.getOrNull()
            ?: return null
        val parsed = parseUrl(resolved) ?: return null
        val host = runCatching { URI(parsed.url).host }.getOrNull() ?: return null
        return parsed.url.takeIf { isTrustedFetchHost(host) }
    }

    fun isResolved(script: UserScript): Boolean {
        var totalBytes = 0L
        script.requires.forEach { dependency ->
            val bytes = dependency.source?.toByteArray(Charsets.UTF_8) ?: return false
            if (bytes.size > UserScriptDependencyResolver.MAX_REQUIRE_BYTES) return false
            if (!matchesIntegrity(bytes, dependency.sha256)) return false
            totalBytes += bytes.size
        }
        script.resources.forEach { dependency ->
            val bytes = decodeBase64(dependency.encodedContent) ?: return false
            if (bytes.size > UserScriptDependencyResolver.MAX_RESOURCE_BYTES) return false
            if (normalizeMimeType(dependency.mimeType) != dependency.mimeType) return false
            if (!matchesIntegrity(bytes, dependency.sha256)) return false
            totalBytes += bytes.size
        }
        return totalBytes <= UserScriptDependencyResolver.MAX_TOTAL_DEPENDENCY_BYTES
    }

    fun matchesIntegrity(bytes: ByteArray, expectedSha256: String?): Boolean {
        if (expectedSha256 == null) return true
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.US_ASCII),
            expectedSha256.toByteArray(Charsets.US_ASCII),
        )
    }

    fun decodeStrictUtf8(bytes: ByteArray): String? = runCatching {
        Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }.getOrNull()

    fun decodeBase64(value: String?): ByteArray? {
        if (value == null) return null
        return runCatching { Base64.getDecoder().decode(value) }.getOrNull()
    }

    fun normalizeMimeType(value: String?): String {
        val normalized = value
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf { candidate -> MIME_TYPE.matches(candidate) }
        return normalized ?: DEFAULT_MIME_TYPE
    }

    private fun isPublicHostnameCandidate(value: String): Boolean {
        if (value == "localhost" || value.endsWith(".localhost") || value.endsWith(".local")) return false
        if (value.contains(':') || IPV4_LIKE.matches(value)) return false
        if (value.length > 253 || !HOSTNAME.matches(value)) return false
        return value.split('.').all { label ->
            label.length in 1..63 && !label.startsWith('-') && !label.endsWith('-')
        }
    }

    private const val DEFAULT_MIME_TYPE = "application/octet-stream"
    private val SHA256_FRAGMENT = Regex("^sha256=([0-9A-Fa-f]{64})$")
    private val MIME_TYPE = Regex("^[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*$")
    private val IPV4_LIKE = Regex("^[0-9.]+$")
    private val HOSTNAME = Regex("^[a-z0-9.-]+$")
    private val TRUSTED_FETCH_HOSTS = setOf(
        "cdn.jsdelivr.net",
        "fonts.googleapis.com",
        "fonts.gstatic.com",
        "gist.githubusercontent.com",
        "gitlab.com",
        "greasyfork.org",
        "openuserjs.org",
        "raw.githubusercontent.com",
        "unpkg.com",
        "update.greasyfork.org",
    )
}

internal data class UserScriptDependencyUrl(
    val url: String,
    val sha256: String?,
)
