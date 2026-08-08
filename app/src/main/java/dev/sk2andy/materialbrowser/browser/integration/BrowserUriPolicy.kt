package dev.sk2andy.materialbrowser.browser.integration

import java.net.IDN
import java.net.URI

/** Shared validation for URLs entering the browser from another Android component. */
object BrowserUriPolicy {
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

    fun canOpenExternally(scheme: String?): Boolean {
        val normalized = scheme?.lowercase()?.takeIf(String::isNotBlank) ?: return false
        return normalized != "http" &&
            normalized != "https" &&
            normalized != "intent" &&
            normalized !in blockedExternalSchemes
    }
}

/** Link Peek never hands non-web navigation to another app or internal WebView scheme. */
object LinkPeekPreviewNavigationPolicy {
    fun shouldBlock(url: String?): Boolean = BrowserUriPolicy.normalizeHttpUrl(url) == null
}
