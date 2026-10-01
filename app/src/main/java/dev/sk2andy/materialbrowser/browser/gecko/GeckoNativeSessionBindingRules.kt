package dev.sk2andy.materialbrowser.browser.gecko

internal data class GeckoNativeSessionBindingRequest(
    val tabId: Long,
    val revision: Long,
)

/** A denied internal tabs.update authenticates Gecko's tab ID without loading a document. */
internal object GeckoNativeSessionBindingRules {
    // Gecko can provide a new Java wrapper for the installed extension in native callbacks.
    fun isInstalledExtensionSource(
        extensionId: String,
        extensionBaseUrl: String,
        installedBaseUrl: String,
        hasCurrentInstalledExtension: Boolean,
    ): Boolean = hasCurrentInstalledExtension &&
        extensionId == CandyPrivacyHostContract.EXTENSION_ID &&
        extensionBaseUrl == installedBaseUrl

    fun requestFor(
        url: String?,
        extensionBaseUrl: String,
        token: String,
        challenge: String,
        currentRevision: Long,
        hasInstalledExtension: Boolean,
        hasExpectedSession: Boolean,
        policyReady: Boolean,
    ): GeckoNativeSessionBindingRequest? {
        if (!hasInstalledExtension || !hasExpectedSession || !policyReady || currentRevision < 1) {
            return null
        }
        val prefix = "${extensionBaseUrl}binding.html?token=$token&challenge=$challenge&tab="
        if (url == null || !url.startsWith(prefix)) return null
        val fields = REQUEST_SUFFIX.matchEntire(url.removePrefix(prefix)) ?: return null
        val tabId = fields.groupValues[1].toLongOrNull() ?: return null
        val revision = fields.groupValues[2].toLongOrNull() ?: return null
        if (tabId !in 1..MAX_SAFE_JAVASCRIPT_INTEGER || revision != currentRevision) return null
        return GeckoNativeSessionBindingRequest(tabId, revision)
    }

    private val REQUEST_SUFFIX = Regex("([0-9]{1,16})&revision=([0-9]{1,16})")
    private const val MAX_SAFE_JAVASCRIPT_INTEGER = 9_007_199_254_740_991L
}
