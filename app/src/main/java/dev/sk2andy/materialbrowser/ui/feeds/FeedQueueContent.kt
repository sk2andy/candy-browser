package dev.sk2andy.materialbrowser.ui.feeds

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedArticle
import dev.sk2andy.materialbrowser.feeds.FeedState
import dev.sk2andy.materialbrowser.reader.ReaderSpeechStatus
import kotlin.math.roundToInt

internal data class FeedQueuePlaybackState(
    val status: ReaderSpeechStatus = ReaderSpeechStatus.Ready,
    val progress: Float = 0f,
    val speed: Float = 1f,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val mutationFailed: Boolean = false,
    val busy: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedQueueContent(
    state: FeedState,
    playback: FeedQueuePlaybackState,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onMoveUp: (String) -> Unit,
    onRemove: (String) -> Unit,
    onImportSaved: () -> Unit,
    onRetry: () -> Unit,
    favicon: Bitmap? = null,
    onStop: (() -> Unit)? = null,
) {
    val queue = state.queue.mapNotNull { id -> state.articles.firstOrNull { it.id == id } }
    val current = queue.firstOrNull()
    val canImport = state.articles.any { it.isStarred && it.id !in state.queue }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag("feeds_queue_player"),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.feeds_queue),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            QueuePlayer(current, state, playback, onTogglePlayback, onNext, onSpeedChange, favicon)
        }
        if (onStop != null) {
            item {
                TextButton(onClick = onStop, modifier = Modifier.testTag("feeds_queue_stop")) {
                    Text(stringResource(R.string.feeds_player_stop))
                }
            }
        }
        if (
            playback.loadFailed ||
                playback.status == ReaderSpeechStatus.Unavailable ||
                playback.mutationFailed
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            when {
                                playback.status == ReaderSpeechStatus.Unavailable ->
                                    R.string.feeds_speech_unavailable
                                playback.loadFailed -> R.string.feeds_player_load_failed
                                else -> R.string.feeds_error
                            }
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (playback.loadFailed) {
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.feeds_player_retry))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feeds_player_up_next, queue.size),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (canImport) {
                    TextButton(onClick = onImportSaved, enabled = !playback.busy) {
                        Text(stringResource(R.string.feeds_player_import_saved))
                    }
                }
            }
        }
        if (queue.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.feeds_player_empty_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        itemsIndexed(queue, key = { _, article -> article.id }) { index, article ->
            QueueArticleRow(
                article = article,
                source = queueSource(article, state),
                index = index,
                busy = playback.busy,
                onMoveUp = { onMoveUp(article.id) },
                onRemove = { onRemove(article.id) },
            )
        }
        item {
            Text(
                stringResource(R.string.feeds_player_foreground),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QueuePlayer(
    article: FeedArticle?,
    state: FeedState,
    playback: FeedQueuePlaybackState,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    favicon: Bitmap?,
) {
    val speaking = playback.status == ReaderSpeechStatus.Speaking
    val motionDurationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val animateWave = speaking && motionDurationScale?.scaleFactor != 0f
    val playScale by
        animateFloatAsState(
            targetValue = if (speaking) 1.06f else 1f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "queuePlayScale",
        )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FeedQueueArtwork(
                    favicon = favicon,
                    source = article?.let { queueSource(it, state) }.orEmpty(),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(
                        when {
                            article == null -> R.string.feeds_player_empty
                            playback.loading ||
                                playback.status == ReaderSpeechStatus.Initializing ->
                                R.string.feeds_player_preparing
                            speaking -> R.string.feeds_player_listening
                            else -> R.string.feeds_player_ready
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Column(
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (article != null) {
                    Text(queueSource(article, state), style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    article?.title ?: stringResource(R.string.feeds_player_empty_title),
                    modifier = Modifier.testTag("feeds_queue_current_title"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (article != null) {
                if (playback.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearWavyProgressIndicator(
                        progress = { playback.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().testTag("feeds_queue_progress"),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        waveSpeed =
                            if (animateWave) {
                                WavyProgressIndicatorDefaults.LinearDeterminateWavelength
                            } else {
                                0.dp
                            },
                    )
                }
                Text(
                    stringResource(
                        R.string.feeds_player_progress,
                        (playback.progress.coerceIn(0f, 1f) * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QueueSpeedButton(playback.speed, onSpeedChange)
                    FilledIconButton(
                        onClick = onTogglePlayback,
                        enabled =
                            !playback.loading &&
                                !playback.loadFailed &&
                                !playback.busy &&
                                playback.status in
                                    setOf(
                                        ReaderSpeechStatus.Ready,
                                        ReaderSpeechStatus.Paused,
                                        ReaderSpeechStatus.Speaking,
                                    ),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier =
                            Modifier.size(72.dp)
                                .graphicsLayer {
                                    scaleX = playScale
                                    scaleY = playScale
                                }
                                .testTag("feeds_queue_play"),
                    ) {
                        if (speaking) {
                            Icon(
                                painterResource(R.drawable.ic_reader_pause),
                                stringResource(R.string.reader_speech_pause),
                                Modifier.size(32.dp),
                            )
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                stringResource(R.string.reader_speech_start),
                                Modifier.size(32.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = onNext,
                        enabled = !playback.busy,
                        modifier = Modifier.testTag("feeds_queue_next"),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_feed_skip_next),
                            stringResource(R.string.feeds_speech_next),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSpeedButton(speed: Float, onSpeedChange: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val rateDescription =
        stringResource(R.string.feeds_speech_speed, speed.toString().trimEnd('0').trimEnd('.'))
    Box {
        TextButton(
            onClick = { expanded = true },
            colors =
                ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
            modifier =
                Modifier.testTag("feeds_queue_speed").semantics {
                    contentDescription = rateDescription
                },
        ) {
            Text(
                stringResource(
                    R.string.feeds_player_rate,
                    speed.toString().trimEnd('0').trimEnd('.'),
                )
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { value ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.feeds_speech_speed,
                                value.toString().trimEnd('0').trimEnd('.'),
                            )
                        )
                    },
                    onClick = {
                        onSpeedChange(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueArticleRow(
    article: FeedArticle,
    source: String,
    index: Int,
    busy: Boolean,
    onMoveUp: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("feeds_queue_article_${article.id}"),
        shape = MaterialTheme.shapes.large,
        color =
            if (index == 0) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor =
            if (index == 0) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (index == 0) stringResource(R.string.feeds_player_current, source)
                    else source,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index > 0) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !busy,
                    modifier = Modifier.testTag("feeds_queue_up_${article.id}"),
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        stringResource(R.string.feeds_player_move_up, article.title),
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                enabled = !busy,
                modifier = Modifier.testTag("feeds_queue_remove_${article.id}"),
            ) {
                Icon(
                    Icons.Default.Close,
                    stringResource(R.string.feeds_player_remove, article.title),
                )
            }
        }
    }
}

private fun queueSource(article: FeedArticle, state: FeedState): String =
    state.sources
        .filter { it.id in article.sourceIds }
        .joinToString { it.title }
        .ifBlank { runCatching { java.net.URI(article.url).host.orEmpty() }.getOrDefault("") }
