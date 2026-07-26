package dev.sk2andy.materialbrowser.capsule

sealed interface CapsuleLaunchResolution {
    data class Open(val capsule: SiteCapsule) : CapsuleLaunchResolution
    data object NormalHome : CapsuleLaunchResolution
    data object NotCapsuleIntent : CapsuleLaunchResolution
}

object CapsuleIntentRules {
    const val ACTION_OPEN_CAPSULE = "dev.sk2andy.materialbrowser.action.OPEN_SITE_CAPSULE"
    const val EXTRA_CAPSULE_ID = "dev.sk2andy.materialbrowser.extra.CAPSULE_ID"

    fun resolve(
        action: String?,
        capsuleId: String?,
        capsules: List<SiteCapsule>,
    ): CapsuleLaunchResolution {
        if (action != ACTION_OPEN_CAPSULE) return CapsuleLaunchResolution.NotCapsuleIntent
        val safeId = SiteCapsuleRules.opaqueId(capsuleId) ?: return CapsuleLaunchResolution.NormalHome
        val capsule = capsules.firstOrNull { it.id == safeId }
            ?: return CapsuleLaunchResolution.NormalHome
        return CapsuleLaunchResolution.Open(capsule)
    }
}
