package dev.sk2andy.materialbrowser.capsule

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

sealed interface CapsuleFullCandyTransition {
    val sourceTabId: String
    val profileId: String

    data class KeepCurrentTab(
        override val sourceTabId: String,
        override val profileId: String,
    ) : CapsuleFullCandyTransition

    data class OpenTargetInNewTab(
        override val sourceTabId: String,
        override val profileId: String,
        val targetUrl: String,
    ) : CapsuleFullCandyTransition
}

object CapsuleFullCandyTransitionRules {
    fun resolve(
        capsule: SiteCapsule,
        activeCapsuleTabId: String?,
        selectedTabId: String,
        selectedProfileId: String,
        selectedTabIsPrivate: Boolean,
        targetUrl: String? = null,
    ): CapsuleFullCandyTransition? {
        if (
            activeCapsuleTabId != selectedTabId ||
            selectedTabIsPrivate ||
            selectedProfileId != capsule.profileId
        ) {
            return null
        }
        val safeTargetUrl = targetUrl?.let(BrowserUriPolicy::normalizeHttpUrl)
        if (targetUrl != null && safeTargetUrl == null) return null
        return if (safeTargetUrl == null) {
            CapsuleFullCandyTransition.KeepCurrentTab(
                sourceTabId = selectedTabId,
                profileId = selectedProfileId,
            )
        } else {
            CapsuleFullCandyTransition.OpenTargetInNewTab(
                sourceTabId = selectedTabId,
                profileId = selectedProfileId,
                targetUrl = safeTargetUrl,
            )
        }
    }
}
