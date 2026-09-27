package dev.sk2andy.materialbrowser.browser.gecko.webpush

import java.math.BigInteger
import java.net.URI

internal data class FossPushSubscription(
    val scope: String,
    val endpoint: String,
    val channelId: String,
    val appServerKey: ByteArray?,
    val keys: WebPushKeyMaterial,
)

internal data class FossPushMessage(
    val subscription: FossPushSubscription,
    val encryptedPayload: ByteArray?,
    val headers: Map<String, String>,
)

/** Gecko appends origin attributes after the service worker URL with a caret separator. */
internal object FossWebPushScopeRules {
    fun isPersistentScope(scope: String, isPrivate: Boolean = false): Boolean {
        if (isPrivate || scope.length !in 1..MAX_SCOPE_LENGTH) return false
        val separator = scope.indexOf('^')
        val url = if (separator >= 0) scope.substring(0, separator) else scope
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host ?: return false
        if (uri.userInfo != null || uri.fragment != null || host.isBlank()) return false
        if (uri.scheme != "https" && !(uri.scheme == "http" && isLoopbackHost(host))) return false
        if (separator < 0) return true

        val attributes = scope.substring(separator + 1)
        if (attributes.isBlank() || '^' in attributes) return false
        return attributes.split('&').all { attribute ->
            val parts = attribute.split('=', limit = 2)
            parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank() &&
                (parts[0] != "privateBrowsingId" || parts[1] == "0")
        }
    }

    fun isSecureEndpoint(endpoint: String): Boolean {
        if (endpoint.length !in 1..MAX_ENDPOINT_LENGTH) return false
        val uri = runCatching { URI(endpoint) }.getOrNull() ?: return false
        return uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.fragment == null
    }

    /** Mirrors Gecko StorageController.createSafeSessionContextId for isolated profiles. */
    fun belongsToIsolatedProfile(scope: String, profileId: String): Boolean {
        if (!isPersistentScope(scope) || profileId.isEmpty()) return false
        val separator = scope.indexOf('^')
        if (separator < 0) return false
        val safeContextId = "gvctx${BigInteger(profileId.toByteArray(Charsets.UTF_8)).toString(16)}"
        val matches = scope.substring(separator + 1).split('&')
            .filter { it.startsWith("geckoViewSessionContextId=") }
        return matches.size == 1 && matches.single() == "geckoViewSessionContextId=$safeContextId"
    }

    private fun isLoopbackHost(host: String): Boolean =
        host.equals("localhost", ignoreCase = true) || host == "127.0.0.1" || host == "[::1]"

    private const val MAX_SCOPE_LENGTH = 2_048
    private const val MAX_ENDPOINT_LENGTH = 2_048
}
