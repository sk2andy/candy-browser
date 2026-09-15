package dev.sk2andy.materialbrowser.browser

data class BrowserProfileDraft(
    val emoji: String,
    val isolationRequested: Boolean,
)

object BrowserProfileRules {
    fun create(
        draft: BrowserProfileDraft,
        profileId: String,
        isolationSupported: Boolean,
    ): BrowserProfile? {
        val safeProfileId = profileId.trim().takeIf(String::isNotEmpty) ?: return null
        val safeEmoji = normalizeEmoji(draft.emoji) ?: return null
        return BrowserProfile(
            id = safeProfileId,
            emoji = safeEmoji,
            isolationEnabled = draft.isolationRequested && isolationSupported,
        )
    }

    fun updateEmoji(
        profile: BrowserProfile,
        emoji: String,
    ): BrowserProfile? {
        if (profile.isSynced) return null
        val safeEmoji = normalizeEmoji(emoji) ?: return null
        return profile.copy(emoji = safeEmoji).takeIf { it != profile }
    }

    fun updateIsolation(
        profile: BrowserProfile,
        enabled: Boolean,
        isolationSupported: Boolean,
    ): BrowserProfile? {
        if (profile.isSynced || !isolationSupported) return null
        return profile.copy(isolationEnabled = enabled).takeIf { it != profile }
    }

    fun updateProtection(
        profile: BrowserProfile,
        protection: ProfileProtection?,
        protectionSupported: Boolean,
    ): BrowserProfile? {
        if (profile.isSynced || protection != null && !protectionSupported) return null
        val normalized = protection?.let(ProfileProtectionRules::normalize)
        return profile.copy(protection = normalized).takeIf { it != profile }
    }

    private fun normalizeEmoji(value: String): String? =
        value.trim().takeIf(String::isNotEmpty)
}

object ProfileProtectionRules {
    const val DEFAULT_COOLDOWN_MINUTES = 5
    const val MIN_COOLDOWN_MINUTES = 1
    const val MAX_COOLDOWN_MINUTES = 1_440

    fun normalize(protection: ProfileProtection): ProfileProtection = protection.copy(
        cooldownMinutes = protection.cooldownMinutes.coerceIn(
            MIN_COOLDOWN_MINUTES,
            MAX_COOLDOWN_MINUTES,
        ),
    )

    fun shouldLockAfterBackground(
        protection: ProfileProtection,
        elapsedBackgroundMillis: Long,
    ): Boolean = when (protection.lockTrigger) {
        ProfileLockTrigger.AppBackgrounded -> true
        ProfileLockTrigger.AppClosed -> false
        ProfileLockTrigger.Cooldown -> elapsedBackgroundMillis >=
            normalize(protection).cooldownMinutes * MILLIS_PER_MINUTE
    }

    private const val MILLIS_PER_MINUTE = 60_000L
}
