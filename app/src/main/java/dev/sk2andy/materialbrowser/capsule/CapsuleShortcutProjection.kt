package dev.sk2andy.materialbrowser.capsule

data class CapsuleShortcutProjection(
    val shortcutId: String,
    val capsuleId: String,
    val shortLabel: String,
    val longLabel: String,
)

object CapsuleShortcutRules {
    private const val SHORTCUT_PREFIX = "site_capsule_"
    const val MAX_SHORT_LABEL_LENGTH = 40

    fun project(capsule: SiteCapsule): CapsuleShortcutProjection = CapsuleShortcutProjection(
        shortcutId = SHORTCUT_PREFIX + capsule.id,
        capsuleId = capsule.id,
        shortLabel = capsule.name.take(MAX_SHORT_LABEL_LENGTH),
        longLabel = capsule.name,
    )
}
