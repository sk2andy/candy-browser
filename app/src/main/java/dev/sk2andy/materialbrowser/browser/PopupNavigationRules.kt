package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.blocking.CandyHostCanonicalizer
import dev.sk2andy.materialbrowser.blocking.CandyPublicSuffixRules
import java.net.URI

internal data class PendingPopupNavigation(
    val openerTabId: String,
    val openerUrl: String,
    val profileId: String,
    val isIncognito: Boolean,
    val sitePaused: Boolean,
    val hadUserGesture: Boolean,
)

internal data class BlockedPopupOffer(
    val token: Long,
    val popupTabId: String,
    val targetUrl: String,
)

internal enum class PopupFilterDecision { NoMatch, Allow, Block }

internal enum class PopupNavigationDecision {
    KeepPending,
    Allow,
    AllowSameSite,
    AllowListed,
    BlockListed,
    BlockCrossSite,
    ;

    val isBlocked: Boolean
        get() = this == BlockListed || this == BlockCrossSite
}

internal object PopupNavigationRules {
    const val PENDING_TIMEOUT_MILLIS = 5_000L

    fun decide(
        pending: PendingPopupNavigation,
        targetUrl: String,
        blockerEnabled: Boolean,
        filterDecision: (targetUrl: String, openerUrl: String) -> PopupFilterDecision,
    ): PopupNavigationDecision {
        val uri = runCatching { URI(targetUrl) }.getOrNull()
            ?: return PopupNavigationDecision.KeepPending
        val targetHost = CandyHostCanonicalizer.canonicalHost(uri.host)
        if (uri.scheme?.lowercase() !in setOf("http", "https") || targetHost == null) {
            return PopupNavigationDecision.KeepPending
        }
        if (!pending.hadUserGesture || !blockerEnabled || pending.sitePaused) {
            return PopupNavigationDecision.Allow
        }
        return when (filterDecision(targetUrl, pending.openerUrl)) {
            PopupFilterDecision.Block -> PopupNavigationDecision.BlockListed
            PopupFilterDecision.Allow -> PopupNavigationDecision.AllowListed
            PopupFilterDecision.NoMatch -> if (isCrossSite(targetHost, pending.openerUrl)) {
                PopupNavigationDecision.Allow
            } else {
                PopupNavigationDecision.AllowSameSite
            }
        }
    }

    private fun isCrossSite(targetHost: String, openerUrl: String): Boolean {
        val opener = runCatching { URI(openerUrl) }.getOrNull() ?: return false
        if (opener.scheme?.lowercase() !in setOf("http", "https")) return false
        val openerHost = CandyHostCanonicalizer.canonicalHost(opener.host) ?: return false
        val targetSite = CandyPublicSuffixRules.registrableDomain(targetHost) ?: targetHost
        val openerSite = CandyPublicSuffixRules.registrableDomain(openerHost) ?: openerHost
        return targetSite != openerSite
    }
}
