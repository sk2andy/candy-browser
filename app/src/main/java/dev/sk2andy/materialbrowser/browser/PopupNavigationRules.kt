package dev.sk2andy.materialbrowser.browser

import java.net.URI

internal data class PendingPopupNavigation(
    val openerTabId: String,
    val openerUrl: String,
    val profileId: String,
    val isIncognito: Boolean,
    val sitePaused: Boolean,
    val hadUserGesture: Boolean,
)

internal enum class PopupNavigationDecision { KeepPending, Allow, Block }

internal object PopupNavigationRules {
    fun decide(
        pending: PendingPopupNavigation,
        targetUrl: String,
        blockerEnabled: Boolean,
        shouldBlock: (targetUrl: String, openerUrl: String) -> Boolean,
    ): PopupNavigationDecision {
        val uri = runCatching { URI(targetUrl) }.getOrNull()
            ?: return PopupNavigationDecision.KeepPending
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            return PopupNavigationDecision.KeepPending
        }
        if (!pending.hadUserGesture || !blockerEnabled || pending.sitePaused) {
            return PopupNavigationDecision.Allow
        }
        return if (shouldBlock(targetUrl, pending.openerUrl)) {
            PopupNavigationDecision.Block
        } else {
            PopupNavigationDecision.Allow
        }
    }
}
