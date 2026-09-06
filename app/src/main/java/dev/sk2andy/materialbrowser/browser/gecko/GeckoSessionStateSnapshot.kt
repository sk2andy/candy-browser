package dev.sk2andy.materialbrowser.browser.gecko

/**
 * Candy's bounded reference to Gecko's own serialized session state.
 *
 * The payload stays engine-specific and is never part of an engine-neutral archive. It is only
 * valid for the same regular Candy tab and stable Gecko context ID on this device.
 */
internal data class GeckoSessionStateSnapshot(
    val tabId: String,
    val profileId: String,
    val encodedState: String,
    val formatVersion: Int = GeckoSessionStateSnapshotRules.FORMAT_VERSION,
)

internal sealed interface GeckoSessionStateRestoreDecision {
    data class Restore(val snapshot: GeckoSessionStateSnapshot) : GeckoSessionStateRestoreDecision

    data object PrivateTab : GeckoSessionStateRestoreDecision

    data object StaleTab : GeckoSessionStateRestoreDecision

    data object ProfileMismatch : GeckoSessionStateRestoreDecision

    data object UnsupportedVersion : GeckoSessionStateRestoreDecision

    data object Malformed : GeckoSessionStateRestoreDecision
}

internal object GeckoSessionStateSnapshotRules {
    const val FORMAT_VERSION = 1
    const val MAX_ENCODED_STATE_CHARS = 8 * 1_024 * 1_024

    fun forPersistence(
        tabId: String,
        profileId: String,
        isPrivate: Boolean,
        encodedState: String?,
    ): GeckoSessionStateSnapshot? {
        if (isPrivate || !isValidIdentifier(tabId) || !isValidIdentifier(profileId)) return null
        val state = encodedState?.takeIf(::isValidState) ?: return null
        return GeckoSessionStateSnapshot(
            tabId = tabId,
            profileId = profileId,
            encodedState = state,
        )
    }

    fun restoreDecision(
        snapshot: GeckoSessionStateSnapshot?,
        tabId: String,
        profileId: String,
        isPrivate: Boolean,
    ): GeckoSessionStateRestoreDecision {
        if (isPrivate) return GeckoSessionStateRestoreDecision.PrivateTab
        val candidate = snapshot ?: return GeckoSessionStateRestoreDecision.Malformed
        if (candidate.formatVersion != FORMAT_VERSION) {
            return GeckoSessionStateRestoreDecision.UnsupportedVersion
        }
        if (!isValidState(candidate.encodedState) ||
            !isValidIdentifier(candidate.tabId) ||
            !isValidIdentifier(candidate.profileId)
        ) {
            return GeckoSessionStateRestoreDecision.Malformed
        }
        if (candidate.tabId != tabId) return GeckoSessionStateRestoreDecision.StaleTab
        if (candidate.profileId != profileId) return GeckoSessionStateRestoreDecision.ProfileMismatch
        return GeckoSessionStateRestoreDecision.Restore(candidate)
    }

    private fun isValidIdentifier(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_IDENTIFIER_CHARS && value.none(Char::isISOControl)

    private fun isValidState(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_ENCODED_STATE_CHARS && value.none(Char::isISOControl)

    private const val MAX_IDENTIFIER_CHARS = 256
}
