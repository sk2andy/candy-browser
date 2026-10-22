package dev.sk2andy.materialbrowser.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.reader.ReaderSpeechPreparationAvailability
import dev.sk2andy.materialbrowser.reader.ReaderSpeechPreparationState
import kotlinx.coroutines.delay

@Composable
internal fun ReaderSpeechPreparationOptions(
    state: ReaderSpeechPreparationState,
    onEnabledChanged: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onConfirmAdult: () -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val available = state.availability == ReaderSpeechPreparationAvailability.Available
    val downloadable = state.availability == ReaderSpeechPreparationAvailability.Downloadable
    val downloading = state.availability == ReaderSpeechPreparationAvailability.Downloading
    if (!available && !downloadable && !downloading && !state.enabled) return
    var pendingAction by remember { mutableStateOf<ReaderSpeechPreparationAction?>(null) }
    val requestAction: (ReaderSpeechPreparationAction) -> Unit = { action ->
        if (!state.adultConfirmed) {
            pendingAction = action
        } else {
            when (action) {
                ReaderSpeechPreparationAction.Enable -> onEnabledChanged(true)
                ReaderSpeechPreparationAction.Download -> onDownload()
            }
        }
    }
    pendingAction?.let { action ->
        ReaderSpeechPreparationAdultDialog(
            onConfirm = {
                pendingAction = null
                onConfirmAdult()
                when (action) {
                    ReaderSpeechPreparationAction.Enable -> onEnabledChanged(true)
                    ReaderSpeechPreparationAction.Download -> onDownload()
                }
            },
            onDismiss = { pendingAction = null },
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.testTag(ReaderSpeechPreparationTestTags.Options),
    ) {
        SettingsPreferenceGroup {
            SettingsSwitch(
                title = stringResource(R.string.reader_nano_enabled_title),
                subtitle = stringResource(R.string.reader_nano_enabled_summary),
                checked = state.enabled,
                enabled = available || state.enabled,
                onCheckedChange = { enabled ->
                    if (enabled) requestAction(ReaderSpeechPreparationAction.Enable)
                    else onEnabledChanged(false)
                },
                modifier = Modifier.testTag(ReaderSpeechPreparationTestTags.Enabled),
            )
        }
        Text(
            stringResource(R.string.reader_nano_system_managed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.reader_nano_metrics_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val uriHandler = LocalUriHandler.current
        TextButton(onClick = { uriHandler.openUri("https://developers.google.com/ml-kit/genai-terms") }) {
            Text(stringResource(R.string.reader_nano_terms))
        }
        if (state.error) {
            Text(
                stringResource(R.string.reader_nano_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(ReaderSpeechPreparationTestTags.Error),
            )
        }
        when {
            downloading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                val progressText = if (state.busy) {
                    stringResource(
                        R.string.reader_nano_downloading,
                        Formatter.formatShortFileSize(LocalContext.current, state.downloadedBytes.coerceAtLeast(0)),
                    )
                } else {
                    stringResource(R.string.reader_nano_system_downloading)
                }
                Text(progressText, modifier = Modifier.testTag(ReaderSpeechPreparationTestTags.Progress))
                if (state.busy) {
                    OutlinedButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.fillMaxWidth().testTag(ReaderSpeechPreparationTestTags.CancelDownload),
                    ) {
                        Text(stringResource(R.string.reader_nano_cancel_download))
                    }
                } else {
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth().testTag(ReaderSpeechPreparationTestTags.Refresh),
                    ) {
                        Text(stringResource(R.string.reader_nano_refresh))
                    }
                }
            }
            downloadable -> {
                Text(stringResource(R.string.reader_nano_download_summary))
                Button(
                    onClick = { requestAction(ReaderSpeechPreparationAction.Download) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().testTag(ReaderSpeechPreparationTestTags.Download),
                ) {
                    Text(stringResource(
                        if (state.error) R.string.reader_nano_retry_download else R.string.reader_nano_download,
                    ))
                }
            }
            !available && state.enabled -> Text(
                stringResource(R.string.reader_nano_unavailable),
                modifier = Modifier.testTag(ReaderSpeechPreparationTestTags.Unavailable),
            )
        }
    }
}

private enum class ReaderSpeechPreparationAction { Enable, Download }

@Composable
private fun ReaderSpeechPreparationAdultDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_nano_adult_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.reader_nano_adult_summary))
                Text(stringResource(R.string.reader_nano_metrics_notice))
                TextButton(onClick = { uriHandler.openUri("https://developers.google.com/ml-kit/genai-terms") }) {
                    Text(stringResource(R.string.reader_nano_terms))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(ReaderSpeechPreparationTestTags.ConfirmAdult),
            ) {
                Text(stringResource(R.string.reader_nano_adult_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(ReaderSpeechPreparationTestTags.CancelAdult),
            ) {
                Text(stringResource(R.string.reader_nano_adult_cancel))
            }
        },
        modifier = Modifier.testTag(ReaderSpeechPreparationTestTags.AdultDialog),
    )
}

/** The speech-options slot overlays both Reader and selected-text playback. */
@Composable
internal fun ReaderSpeechPreparationPlaybackStatus(preparing: Boolean, fallback: Boolean) {
    var showFallback by remember { mutableStateOf(false) }
    LaunchedEffect(fallback) {
        showFallback = fallback
        if (fallback) {
            delay(5_000)
            showFallback = false
        }
    }
    if (!preparing && !showFallback) return
    Popup(alignment = Alignment.BottomCenter) {
        Snackbar(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(ReaderSpeechPreparationTestTags.PlaybackStatus),
        ) {
            Text(stringResource(
                if (preparing) R.string.reader_nano_preparing else R.string.reader_nano_fallback,
            ))
        }
    }
}

internal object ReaderSpeechPreparationTestTags {
    const val Options = "reader_nano_options"
    const val Refresh = "reader_nano_refresh"
    const val AdultDialog = "reader_nano_adult_dialog"
    const val ConfirmAdult = "reader_nano_confirm_adult"
    const val CancelAdult = "reader_nano_cancel_adult"
    const val Enabled = "reader_nano_enabled"
    const val Download = "reader_nano_download"
    const val CancelDownload = "reader_nano_cancel_download"
    const val Progress = "reader_nano_progress"
    const val Error = "reader_nano_error"
    const val Unavailable = "reader_nano_unavailable"
    const val PlaybackStatus = "reader_nano_playback_status"
}
