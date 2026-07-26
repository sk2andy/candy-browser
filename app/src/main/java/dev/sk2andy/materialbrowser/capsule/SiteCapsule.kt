package dev.sk2andy.materialbrowser.capsule

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

enum class CapsuleNavigationMode(val wireValue: String) {
    SameOrigin("same_origin"),
    SameRegistrableDomain("same_registrable_domain"),
    AllLinks("all_links"),
    ;

    companion object {
        fun fromWireValue(value: String?): CapsuleNavigationMode =
            entries.firstOrNull { it.wireValue == value } ?: SameOrigin
    }
}

enum class CapsuleChromeMode(val wireValue: String) {
    Minimal("minimal"),
    Compact("compact"),
    ;

    companion object {
        fun fromWireValue(value: String?): CapsuleChromeMode =
            entries.firstOrNull { it.wireValue == value } ?: Compact
    }
}

enum class CapsuleIconMode(val wireValue: String) {
    Favicon("favicon"),
    ProfileFallback("profile_fallback"),
    ;

    companion object {
        fun fromWireValue(value: String?): CapsuleIconMode =
            entries.firstOrNull { it.wireValue == value } ?: Favicon
    }
}

data class SiteCapsule(
    val id: String,
    val name: String,
    val startUrl: String,
    val profileId: String,
    val ownsDedicatedProfile: Boolean = false,
    val isolatedStorageRequested: Boolean = false,
    val navigationMode: CapsuleNavigationMode = CapsuleNavigationMode.SameOrigin,
    val chromeMode: CapsuleChromeMode = CapsuleChromeMode.Compact,
    val iconMode: CapsuleIconMode = CapsuleIconMode.Favicon,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class SiteCapsuleDraft(
    val id: String? = null,
    val name: String,
    val startUrl: String,
    val profileId: String,
    val ownsDedicatedProfile: Boolean = false,
    val isolatedStorageRequested: Boolean = false,
    val navigationMode: CapsuleNavigationMode = CapsuleNavigationMode.SameOrigin,
    val chromeMode: CapsuleChromeMode = CapsuleChromeMode.Compact,
    val iconMode: CapsuleIconMode = CapsuleIconMode.Favicon,
)

object SiteCapsuleRules {
    const val MAX_CAPSULES = 64
    const val MAX_NAME_LENGTH = 48
    const val MAX_URL_LENGTH = 4_096
    const val MAX_PROFILE_ID_LENGTH = 128

    fun canCreate(existingCount: Int): Boolean = existingCount in 0 until MAX_CAPSULES

    fun create(
        draft: SiteCapsuleDraft,
        id: String,
        nowMillis: Long,
        multiProfileSupported: Boolean,
    ): SiteCapsule? {
        val safeId = opaqueId(id) ?: return null
        val name = draft.name.trim().take(MAX_NAME_LENGTH).takeIf(String::isNotEmpty) ?: return null
        val url = BrowserUriPolicy.normalizeHttpUrl(draft.startUrl)
            ?.takeIf { it.length <= MAX_URL_LENGTH }
            ?: return null
        val profileId = draft.profileId.trim()
            .takeIf { it.isNotEmpty() && it.length <= MAX_PROFILE_ID_LENGTH }
            ?: return null
        val isolated = draft.isolatedStorageRequested &&
            draft.ownsDedicatedProfile &&
            multiProfileSupported
        return SiteCapsule(
            id = safeId,
            name = name,
            startUrl = url,
            profileId = profileId,
            ownsDedicatedProfile = draft.ownsDedicatedProfile,
            isolatedStorageRequested = isolated,
            navigationMode = draft.navigationMode,
            chromeMode = draft.chromeMode,
            iconMode = draft.iconMode,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
    }

    fun update(
        existing: SiteCapsule,
        draft: SiteCapsuleDraft,
        nowMillis: Long,
        multiProfileSupported: Boolean,
    ): SiteCapsule? = create(
        draft = draft,
        id = existing.id,
        nowMillis = existing.createdAtMillis,
        multiProfileSupported = multiProfileSupported,
    )?.copy(updatedAtMillis = nowMillis)

    fun bounded(capsules: List<SiteCapsule>): List<SiteCapsule> = capsules.asSequence()
        .distinctBy(SiteCapsule::id)
        .sortedByDescending(SiteCapsule::updatedAtMillis)
        .take(MAX_CAPSULES)
        .toList()

    fun sanitizePersisted(capsule: SiteCapsule): SiteCapsule? {
        val id = opaqueId(capsule.id) ?: return null
        val name = capsule.name.trim().take(MAX_NAME_LENGTH).takeIf(String::isNotEmpty) ?: return null
        val startUrl = BrowserUriPolicy.normalizeHttpUrl(capsule.startUrl)
            ?.takeIf { it.length <= MAX_URL_LENGTH }
            ?: return null
        val profileId = capsule.profileId.trim()
            .takeIf { it.isNotEmpty() && it.length <= MAX_PROFILE_ID_LENGTH }
            ?: return null
        val createdAt = capsule.createdAtMillis.coerceAtLeast(0L)
        return capsule.copy(
            id = id,
            name = name,
            startUrl = startUrl,
            profileId = profileId,
            createdAtMillis = createdAt,
            updatedAtMillis = capsule.updatedAtMillis.coerceAtLeast(createdAt),
        )
    }

    fun opaqueId(value: String?): String? {
        val candidate = value?.trim()?.takeIf { it.length in 32..64 } ?: return null
        if (candidate.any { !it.isLetterOrDigit() && it != '-' && it != '_' }) return null
        return candidate
    }
}
