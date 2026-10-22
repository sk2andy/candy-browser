package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedPlaybackManager
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.reader.ReaderSpeechStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedQueueSheet(repository: FeedRepository, onDismiss: () -> Unit) {
    if (repository.isPrivate) return
    val context = LocalContext.current
    val manager = remember(context) { FeedPlaybackManager.get(context) }
    val state by repository.state.collectAsState()
    val playback by manager.state.collectAsState()
    val ownsPlayback = playback.profileId == repository.profileId
    val article =
        state.queue.firstOrNull()?.let { id -> state.articles.firstOrNull { it.id == id } }
    val favicon = rememberFeedQueueFavicon(repository, article)
    val dismissDescription = stringResource(R.string.feeds_player_close)
    LaunchedEffect(repository) { manager.prepare(repository.profileId) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = {
            FeedSheetDragHandle(dismissDescription, Modifier.testTag("feeds_queue_handle"))
        },
        sheetMaxWidth = 640.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
    ) {
        FeedQueueContent(
            state = state,
            playback =
                FeedQueuePlaybackState(
                    status = if (ownsPlayback) playback.status else ReaderSpeechStatus.Ready,
                    progress =
                        if (ownsPlayback) playback.progress else article?.readingProgress ?: 0f,
                    speed = if (ownsPlayback) playback.speed else 1f,
                    loading = !ownsPlayback || playback.loading,
                    loadFailed = ownsPlayback && playback.loadFailed,
                    mutationFailed = ownsPlayback && playback.mutationFailed,
                    busy = !ownsPlayback || playback.busy,
                ),
            onTogglePlayback = { manager.togglePlayback(repository.profileId) },
            onNext = { if (ownsPlayback) manager.next() },
            onSpeedChange = { if (ownsPlayback) manager.setSpeed(it) },
            onMoveUp = { id ->
                val ids = state.queue.toMutableList()
                val index = ids.indexOf(id)
                if (ownsPlayback && index > 0) {
                    ids.removeAt(index)
                    ids.add(index - 1, id)
                    manager.setQueue(ids)
                }
            },
            onRemove = { id -> if (ownsPlayback) manager.setQueue(state.queue - id) },
            onImportSaved = {
                if (ownsPlayback) {
                    manager.setQueue(
                        state.queue + state.articles.filter { it.isStarred }.map { it.id }
                    )
                }
            },
            onRetry = { if (ownsPlayback) manager.retry() },
            favicon = favicon,
            onStop = if (ownsPlayback && playback.isActive) manager::stop else null,
        )
    }
}
