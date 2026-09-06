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

    private fun normalizeEmoji(value: String): String? =
        value.trim().takeIf(String::isNotEmpty)
}
