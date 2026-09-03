package dev.sk2andy.materialbrowser.browser.integration

import java.net.IDN
import java.net.URI

/** Shared validation for URLs entering the browser from another Android component. */
object BrowserUriPolicy {
    private val schemePrefix = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val blockedExternalSchemes = setOf(
        "about",
        "blob",
        "content",
        "data",
        "file",
        "javascript",
    )

    fun normalizeHttpUrl(value: String?): String? {
        val candidate = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (candidate.any { it.code <= 0x20 || it.code == 0x7f }) return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (!uri.isAbsolute || uri.rawAuthority.isNullOrBlank()) return null
        if (uri.rawUserInfo != null) return null
        if (runCatching { uri.toURL().host }.getOrNull().isNullOrBlank()) return null
        return candidate
    }

    fun displayHttpHost(value: String?): String {
        val safeUrl = normalizeHttpUrl(value) ?: return ""
        val host = runCatching { URI(safeUrl).toURL().host }.getOrNull().orEmpty()
        return runCatching { IDN.toUnicode(host) }.getOrDefault(host).removePrefix("www.")
    }

    fun normalizeExternalUri(value: String?): String? {
        val candidate = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (candidate.length > MAX_EXTERNAL_URI_LENGTH) return null
        if (candidate.any { it.code <= 0x20 || it.code == 0x7f }) return null
        if (!schemePrefix.containsMatchIn(candidate)) return null
        val scheme = candidate.substringBefore(':').lowercase()
        return candidate.takeIf { scheme == "intent" || canOpenExternally(scheme) }
    }

    fun canOpenExternally(scheme: String?): Boolean {
        val normalized = scheme?.lowercase()?.takeIf(String::isNotBlank) ?: return false
        return normalized != "http" &&
            normalized != "https" &&
            normalized != "intent" &&
            normalized !in blockedExternalSchemes
    }

    private const val MAX_EXTERNAL_URI_LENGTH = 32_768
}

/** Limits automatic app handoffs to user-driven links and main-frame server redirects. */
object ExternalNavigationPolicy {
    fun isUserNavigationGrantActive(
        expirationElapsedRealtime: Long?,
        nowElapsedRealtime: Long,
    ): Boolean = expirationElapsedRealtime != null &&
        expirationElapsedRealtime >= nowElapsedRealtime

    fun shouldAttemptExternalLaunch(
        scheme: String?,
        isForMainFrame: Boolean,
        hasGesture: Boolean,
        isRedirect: Boolean,
        hasUserNavigationGrant: Boolean = false,
    ): Boolean {
        if (!isForMainFrame) return false
        val normalizedScheme = scheme?.lowercase()?.takeIf(String::isNotBlank) ?: return false
        if (normalizedScheme == "http" || normalizedScheme == "https") return false
        if (
            normalizedScheme != "intent" &&
            !BrowserUriPolicy.canOpenExternally(normalizedScheme)
        ) {
            return false
        }
        return hasGesture || isRedirect || hasUserNavigationGrant
    }
}

/** Link Peek never hands non-web navigation to another app or internal WebView scheme. */
object LinkPeekPreviewNavigationPolicy {
    fun shouldBlock(url: String?): Boolean = BrowserUriPolicy.normalizeHttpUrl(url) == null
}
