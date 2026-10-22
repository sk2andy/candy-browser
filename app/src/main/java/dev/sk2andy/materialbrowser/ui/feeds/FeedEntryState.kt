package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import dev.sk2andy.materialbrowser.feeds.FeedRules

/** Only the normal profile/tab that opened a collection may restore its navigation entry. */
internal object FeedEntryStateRules {
    fun save(
        entry: FeedsEntry?,
        profileId: String,
        tabId: String,
        isPrivate: Boolean,
    ): List<String>? {
        if (
            entry == null ||
                isPrivate ||
                profileId.isBlank() ||
                tabId.isBlank() ||
                entry.invocationToken.isBlank() ||
                entry.invocationToken.length > 64
        )
            return null
        return listOf(
            "2",
            profileId,
            tabId,
            entry.libraryOnly.toString(),
            entry.discoveryUrl?.takeIf { it.length <= 4096 }?.let(FeedRules::safeUrl).orEmpty(),
            entry.queueRequestId.coerceAtLeast(0).toString(),
            entry.invocationToken,
        )
    }

    fun restore(
        values: List<String>,
        profileId: String,
        tabId: String,
        isPrivate: Boolean,
    ): FeedsEntry? {
        if (
            isPrivate ||
                profileId.isBlank() ||
                tabId.isBlank() ||
                values.size != 7 ||
                values[0] != "2" ||
                values[1] != profileId ||
                values[2] != tabId
        )
            return null
        if (values[6].isBlank() || values[6].length > 64) return null
        val libraryOnly = values[3].toBooleanStrictOrNull() ?: return null
        val queueRequestId = values[5].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return FeedsEntry(
            libraryOnly = libraryOnly,
            discoveryUrl = values[4].takeIf { it.length <= 4096 }?.let(FeedRules::safeUrl),
            queueRequestId = queueRequestId,
            invocationToken = values[6],
        )
    }
}

@Composable
internal fun rememberFeedsEntryState(
    profileId: String,
    tabId: String,
    isPrivate: Boolean,
): MutableState<FeedsEntry?> {
    val saver =
        remember(profileId, tabId, isPrivate) {
            Saver<FeedsEntry?, List<String>>(
                save = { FeedEntryStateRules.save(it, profileId, tabId, isPrivate) },
                restore = { FeedEntryStateRules.restore(it, profileId, tabId, isPrivate) },
            )
        }
    return rememberSaveable(profileId, tabId, isPrivate, stateSaver = saver) {
        mutableStateOf<FeedsEntry?>(null)
    }
}
