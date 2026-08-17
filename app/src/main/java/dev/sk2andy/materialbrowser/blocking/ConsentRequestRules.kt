package dev.sk2andy.materialbrowser.blocking

object ConsentRequestRules {
    private val blockedRuntimeHosts = setOf(
        "cmp.inmobi.com",
    )

    fun shouldBlock(
        isForMainFrame: Boolean,
        cookieBannerRemovalEnabled: Boolean,
        sitePaused: Boolean,
        requestHost: String?,
    ): Boolean {
        if (isForMainFrame || !cookieBannerRemovalEnabled || sitePaused) return false
        val host = PrivacyRequestSanitizer.normalizeHost(requestHost) ?: return false
        return blockedRuntimeHosts.any { blockedHost ->
            host == blockedHost || host.endsWith(".$blockedHost")
        }
    }
}
