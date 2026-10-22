package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.feeds.FeedRules

/** Tracks one-shot browser launches separately from the surfaces they opened. */
internal class FeedLaunchState(
    discoveryHandledUrl: String? = null,
    selectedFeedUrl: String? = null,
    discoveryCandidates: List<String> = emptyList(),
    queueVisible: Boolean = false,
    queueHandledId: Int = 0,
    discoveryHandledToken: String? = null,
    queueHandledToken: String? = null,
) {
    var discoveryHandledUrl by mutableStateOf(discoveryHandledUrl)
        private set

    var selectedFeedUrl by mutableStateOf(selectedFeedUrl)
        private set

    var discoveryCandidates by mutableStateOf(discoveryCandidates)
        private set

    var queueVisible by mutableStateOf(queueVisible)
    var discoveryHandledToken by mutableStateOf(discoveryHandledToken)
        private set

    var queueHandledToken by mutableStateOf(queueHandledToken)
        private set

    var queueHandledId by mutableIntStateOf(queueHandledId)
        private set

    fun needsDiscovery(invocationToken: String, url: String): Boolean =
        discoveryHandledToken != invocationToken || discoveryHandledUrl != url

    fun completeDiscovery(invocationToken: String, url: String, candidates: List<String>) {
        discoveryHandledToken = invocationToken
        discoveryHandledUrl = url
        if (candidates.size > 1) discoveryCandidates = candidates
        else selectedFeedUrl = candidates.firstOrNull() ?: url
    }

    fun selectCandidate(url: String) {
        selectedFeedUrl = url
        discoveryCandidates = emptyList()
    }

    fun dismissCandidates() {
        discoveryCandidates = emptyList()
    }

    fun consumeSelectedFeed() {
        selectedFeedUrl = null
    }

    fun acceptQueueRequest(invocationToken: String, requestId: Int) {
        if (
            requestId > 0 && (invocationToken != queueHandledToken || requestId != queueHandledId)
        ) {
            queueHandledToken = invocationToken
            queueHandledId = requestId
            queueVisible = true
        }
    }
}

@Composable
internal fun rememberFeedLaunchState(profileId: String, isPrivate: Boolean): FeedLaunchState {
    val saver =
        remember(profileId, isPrivate) {
            listSaver<FeedLaunchState, String>(
                save = { state ->
                    if (isPrivate) emptyList()
                    else
                        listOf(
                            profileId,
                            savedFeedLaunchUrl(state.discoveryHandledUrl).orEmpty(),
                            savedFeedLaunchUrl(state.selectedFeedUrl).orEmpty(),
                            state.queueVisible.toString(),
                            state.queueHandledId.toString(),
                            state.discoveryHandledToken.orEmpty().take(64),
                            state.queueHandledToken.orEmpty().take(64),
                        ) + state.discoveryCandidates.take(32).mapNotNull(::savedFeedLaunchUrl)
                },
                restore = { saved ->
                    if (isPrivate || saved.size < 7 || saved[0] != profileId) null
                    else
                        FeedLaunchState(
                            discoveryHandledUrl = savedFeedLaunchUrl(saved[1]),
                            selectedFeedUrl = savedFeedLaunchUrl(saved[2]),
                            queueVisible = saved[3].toBooleanStrictOrNull() ?: false,
                            queueHandledId = saved[4].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            discoveryHandledToken =
                                saved[5].takeIf { it.isNotBlank() && it.length <= 64 },
                            queueHandledToken =
                                saved[6].takeIf { it.isNotBlank() && it.length <= 64 },
                            discoveryCandidates =
                                saved.drop(7).take(32).mapNotNull(::savedFeedLaunchUrl),
                        )
                },
            )
        }
    return rememberSaveable(profileId, isPrivate, saver = saver) { FeedLaunchState() }
}

private fun savedFeedLaunchUrl(value: String?): String? =
    value?.takeIf { it.length <= 4096 }?.let(FeedRules::safeUrl)
