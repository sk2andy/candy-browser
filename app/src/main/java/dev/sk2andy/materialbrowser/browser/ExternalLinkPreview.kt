package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

data class ExternalLinkPreviewState(
    val sessionId: Long,
    val generation: Int,
    val currentUrl: String,
    val targetProfileId: String,
    val progress: Int = 0,
    val isLoading: Boolean = true,
    val canGoBack: Boolean = false,
)

sealed interface ExternalLinkPreviewCommitResult {
    data class Opened(val tabId: String) : ExternalLinkPreviewCommitResult
    data object MissingPreview : ExternalLinkPreviewCommitResult
    data object TabLimitReached : ExternalLinkPreviewCommitResult
}

internal object ExternalLinkPreviewRules {
    fun targetProfileId(
        profiles: List<BrowserProfile>,
        profilesEnabled: Boolean,
        requestedProfileId: String?,
        activeProfileId: String,
    ): String? {
        val firstProfileId = profiles.firstOrNull()?.id ?: return null
        if (!profilesEnabled) return firstProfileId
        return requestedProfileId
            ?.takeIf { requested -> profiles.any { it.id == requested } }
            ?: activeProfileId.takeIf { active -> profiles.any { it.id == active } }
            ?: firstProfileId
    }

    fun safeCurrentUrl(url: String?): String? = BrowserUriPolicy.normalizeHttpUrl(url)
}
