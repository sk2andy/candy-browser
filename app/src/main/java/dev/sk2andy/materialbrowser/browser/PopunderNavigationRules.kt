package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.blocking.CandyHostCanonicalizer
import dev.sk2andy.materialbrowser.blocking.CandyPublicSuffixRules
import java.net.URI

internal data class PendingPopunderNavigation(
    val openerTabId: String,
    val popupTabId: String,
    val originalOpenerUrl: String,
    val createdAtMillis: Long,
    val sitePaused: Boolean,
    val childUrl: String? = null,
    val openerTargetUrl: String? = null,
)

internal enum class PopunderNavigationDecision { KeepPending, Allow, Block }

internal object PopunderNavigationRules {
    const val WINDOW_MILLIS = 5_000L

    fun withChildUrl(
        pending: PendingPopunderNavigation,
        childUrl: String,
    ): PendingPopunderNavigation = pending.copy(childUrl = childUrl)

    fun withOpenerTarget(
        pending: PendingPopunderNavigation,
        openerTargetUrl: String,
    ): PendingPopunderNavigation = pending.copy(openerTargetUrl = openerTargetUrl)

    fun decide(
        pending: PendingPopunderNavigation,
        nowMillis: Long,
        blockerEnabled: Boolean,
        filterDecision: (openerTargetUrl: String, childUrl: String) -> PopupFilterDecision,
    ): PopunderNavigationDecision {
        if (nowMillis - pending.createdAtMillis !in 0..WINDOW_MILLIS) {
            return PopunderNavigationDecision.Allow
        }
        val childUrl = pending.childUrl ?: return PopunderNavigationDecision.KeepPending
        val openerTargetUrl = pending.openerTargetUrl
            ?: return PopunderNavigationDecision.KeepPending
        if (!blockerEnabled || pending.sitePaused) return PopunderNavigationDecision.Allow
        if (!changedSite(pending.originalOpenerUrl, openerTargetUrl)) {
            return PopunderNavigationDecision.KeepPending
        }
        return when (filterDecision(openerTargetUrl, childUrl)) {
            PopupFilterDecision.Block -> PopunderNavigationDecision.Block
            PopupFilterDecision.Allow -> PopunderNavigationDecision.Allow
            PopupFilterDecision.NoMatch -> PopunderNavigationDecision.KeepPending
        }
    }

    private fun changedSite(originalUrl: String, targetUrl: String): Boolean {
        val original = webHost(originalUrl) ?: return false
        val target = webHost(targetUrl) ?: return false
        val originalSite = CandyPublicSuffixRules.registrableDomain(original) ?: original
        val targetSite = CandyPublicSuffixRules.registrableDomain(target) ?: target
        return originalSite != targetSite
    }

    private fun webHost(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        return CandyHostCanonicalizer.canonicalHost(uri.host)
    }
}
