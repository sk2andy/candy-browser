package dev.sk2andy.materialbrowser.ui

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.cast.CastUiState

internal object CastControlsTestTags {
    const val RouteButton = "cast_route_button"
    const val MiniController = "cast_mini_controller"
    const val PlayPause = "cast_play_pause"
    const val Disconnect = "cast_disconnect"
    const val ExpandedController = "cast_expanded_controller"
    const val Seek = "cast_seek"
    const val Volume = "cast_volume"
}

@Composable
internal fun CastRouteButton(modifier: Modifier = Modifier) {
    val contentDescription = stringResource(R.string.cd_cast_video)
    AndroidView(
        factory = { context ->
            MediaRouteButton(
                ContextThemeWrapper(context, R.style.Theme_MaterialBrowser_MediaRouteButton),
            ).apply {
                CastButtonFactory.setUpMediaRouteButton(context, this)
                this.contentDescription = contentDescription
            }
        },
        update = { it.contentDescription = contentDescription },
        modifier = modifier
            .size(48.dp)
            .testTag(CastControlsTestTags.RouteButton),
    )
}

@Composable
internal fun CastControls(
    state: CastUiState,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isConnected || !state.hasMedia) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    CastMiniController(
        state = state,
        onExpand = { expanded = true },
        onTogglePlayback = onTogglePlayback,
        onDisconnect = onDisconnect,
        modifier = modifier,
    )
    if (expanded) {
        CastExpandedController(
            state = state,
            onDismiss = { expanded = false },
            onTogglePlayback = onTogglePlayback,
            onSeek = onSeek,
            onVolumeChange = onVolumeChange,
            onDisconnect = onDisconnect,
        )
    }
}

@Composable
private fun CastMiniController(
    state: CastUiState,
    onExpand: () -> Unit,
    onTogglePlayback: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = state.title.ifBlank { stringResource(R.string.cast_unknown_title) }
    val device = state.deviceName.ifBlank { stringResource(R.string.cast_unknown_device) }
    val progress = state.durationMillis
        ?.takeIf { it > 0L }
        ?.let { duration -> (state.positionMillis.toFloat() / duration).coerceIn(0f, 1f) }
        ?: 0f
    Surface(
        onClick = onExpand,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(CastControlsTestTags.MiniController),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cast_connected),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(R.string.cast_playing_on, device),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.testTag(CastControlsTestTags.PlayPause),
            ) {
                Icon(
                    painter = if (state.isPlaying) {
                        painterResource(R.drawable.ic_pause)
                    } else {
                        androidx.compose.ui.graphics.vector.rememberVectorPainter(
                            Icons.Default.PlayArrow,
                        )
                    },
                    contentDescription = stringResource(
                        if (state.isPlaying) R.string.cd_cast_pause else R.string.cd_cast_play,
                    ),
                )
            }
            IconButton(
                onClick = onDisconnect,
                modifier = Modifier.testTag(CastControlsTestTags.Disconnect),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_cast_stop),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CastExpandedController(
    state: CastUiState,
    onDismiss: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onDisconnect: () -> Unit,
) {
    val duration = state.durationMillis?.coerceAtLeast(0L) ?: 0L
    var seekPosition by remember(state.positionMillis, duration) {
        mutableFloatStateOf(state.positionMillis.coerceIn(0L, duration.coerceAtLeast(1L)).toFloat())
    }
    var volume by remember(state.deviceVolume) {
        mutableFloatStateOf(state.deviceVolume.coerceIn(0f, 1f))
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(CastControlsTestTags.ExpandedController),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title.ifBlank {
                            stringResource(R.string.cast_unknown_title)
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.cast_playing_on,
                            state.deviceName.ifBlank {
                                stringResource(R.string.cast_unknown_device)
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                CastRouteButton()
            }
            Text(
                text = stringResource(
                    R.string.cast_position,
                    formattedMediaTime(seekPosition.toLong()),
                    formattedMediaTime(duration),
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = seekPosition,
                onValueChange = { seekPosition = it },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                onValueChangeFinished = { onSeek(seekPosition.toLong()) },
                enabled = duration > 0L,
                modifier = Modifier.testTag(CastControlsTestTags.Seek),
            )
            Text(
                text = stringResource(R.string.cast_volume),
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    onVolumeChange(it)
                },
                valueRange = 0f..1f,
                modifier = Modifier.testTag(CastControlsTestTags.Volume),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        painter = if (state.isPlaying) {
                            painterResource(R.drawable.ic_pause)
                        } else {
                            androidx.compose.ui.graphics.vector.rememberVectorPainter(
                                Icons.Default.PlayArrow,
                            )
                        },
                        contentDescription = stringResource(
                            if (state.isPlaying) {
                                R.string.cd_cast_pause
                            } else {
                                R.string.cd_cast_play
                            },
                        ),
                    )
                }
                IconButton(onClick = onDisconnect) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_cast_stop),
                    )
                }
            }
        }
    }
}

private fun formattedMediaTime(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
