package dev.sk2andy.materialbrowser.browser.gecko

import java.net.URI

internal object MozillaAccountWebChannelRules {
    fun acceptsSender(url: String, topLevel: Boolean, privateMode: Boolean): Boolean {
        if (!topLevel || privateMode) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host == "accounts.firefox.com" &&
            uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443)
    }
}
