package dev.sk2andy.materialbrowser.capsule

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.isSynced

object CapsuleWindowRules {
    fun canOpen(capsule: SiteCapsule, profiles: List<BrowserProfile>): Boolean {
        if (!capsule.opensInOwnWindow) return false
        val profile = profiles.firstOrNull { it.id == capsule.profileId } ?: return false
        return profile.protection == null && !profile.isSynced
    }
}
