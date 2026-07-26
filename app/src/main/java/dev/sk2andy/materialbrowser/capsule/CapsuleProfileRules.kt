package dev.sk2andy.materialbrowser.capsule

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.WebViewProfileRules

data class CapsuleProfileProjection(
    val profileId: String,
    val isolationEnabled: Boolean,
    val webViewProfileName: String?,
)

object CapsuleProfileRules {
    fun resolve(
        capsule: SiteCapsule,
        profiles: List<BrowserProfile>,
        multiProfileSupported: Boolean,
    ): CapsuleProfileProjection? {
        val profile = profiles.firstOrNull { it.id == capsule.profileId } ?: return null
        val isolated = profile.isolationEnabled && multiProfileSupported
        return CapsuleProfileProjection(
            profileId = profile.id,
            isolationEnabled = isolated,
            webViewProfileName = if (isolated) {
                WebViewProfileRules.isolatedProfileName(profile.id)
            } else {
                null
            },
        )
    }
}
