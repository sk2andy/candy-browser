package dev.sk2andy.materialbrowser.capsule

data class CapsuleDeletionPlan(
    val disableShortcutId: String,
    val deleteDedicatedProfile: Boolean,
)

object CapsuleDeletionRules {
    fun plan(
        capsule: SiteCapsule,
        remainingCapsules: List<SiteCapsule>,
        deleteDedicatedProfileConfirmed: Boolean,
    ): CapsuleDeletionPlan = CapsuleDeletionPlan(
        disableShortcutId = CapsuleShortcutRules.project(capsule).shortcutId,
        deleteDedicatedProfile = capsule.ownsDedicatedProfile &&
            deleteDedicatedProfileConfirmed &&
            remainingCapsules.none { it.id != capsule.id && it.profileId == capsule.profileId },
    )
}
