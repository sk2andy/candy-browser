package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.blocking.PrivacyRequestSanitizer

internal object WebContentSafeAreaNavigationRules {
    fun shouldReset(currentUrl: String?, nextUrl: String?): Boolean {
        val currentHost = currentUrl?.let(PrivacyRequestSanitizer::webHost) ?: return false
        val nextHost = nextUrl?.let(PrivacyRequestSanitizer::webHost) ?: return false
        return currentHost != nextHost
    }
}
