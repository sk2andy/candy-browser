package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.reader.OfflineSpeechModelFiles
import dev.sk2andy.materialbrowser.reader.ReaderSpeechController
import dev.sk2andy.materialbrowser.reader.ReaderSpeechModelRepository
import dev.sk2andy.materialbrowser.reader.ReaderSpeechModelState
import dev.sk2andy.materialbrowser.reader.ReaderSpeechPreparationRepository
import dev.sk2andy.materialbrowser.reader.ReaderSpeechPreparationState

@Composable
internal fun ReaderSpeechOptions(speech: ReaderSpeechController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, speech) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> speech.refreshPreparationAvailability()
                Lifecycle.Event.ON_STOP -> speech.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            speech.refreshPreparationAvailability()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (speech.choiceRequested) {
        ReaderSpeechChoiceDialog(
            state = speech.modelState,
            playbackFailed = speech.playbackFailed,
            onOfflineVoice = speech::chooseOfflineVoice,
            onSystemVoice = speech::chooseSystemVoice,
            onDismiss = speech::dismissChoice,
            preparationState = speech.preparationState,
            onPreparationEnabledChanged = speech::setPreparationEnabled,
            onPreparationDownload = speech::downloadPreparationModel,
            onPreparationCancelDownload = speech::cancelPreparationDownload,
            onPreparationConfirmAdult = speech::confirmPreparationAdult,
            onPreparationRefresh = speech::refreshPreparationAvailability,
        )
    } else {
        ReaderSpeechPreparationPlaybackStatus(
            preparing = speech.preparingText,
            fallback = speech.preparationFallback,
        )
    }
}

@Composable
internal fun ReaderSpeechChoiceDialog(
    state: ReaderSpeechModelState,
    onOfflineVoice: () -> Unit,
    onSystemVoice: () -> Unit,
    onDismiss: () -> Unit,
    playbackFailed: Boolean = false,
    preparationState: ReaderSpeechPreparationState = ReaderSpeechPreparationState(),
    onPreparationEnabledChanged: (Boolean) -> Unit = {},
    onPreparationDownload: () -> Unit = {},
    onPreparationCancelDownload: () -> Unit = {},
    onPreparationConfirmAdult: () -> Unit = {},
    onPreparationRefresh: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_voice_choice_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.reader_voice_choice_summary))
                ReaderSpeechModelStatus(state)
                if (playbackFailed) {
                    Text(
                        stringResource(R.string.reader_voice_playback_error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(ReaderSpeechOptionsTestTags.PlaybackError),
                    )
                }
                ReaderSpeechPreparationOptions(
                    state = preparationState,
                    onEnabledChanged = onPreparationEnabledChanged,
                    onDownload = onPreparationDownload,
                    onCancelDownload = onPreparationCancelDownload,
                    onConfirmAdult = onPreparationConfirmAdult,
                    onRefresh = onPreparationRefresh,
                )
                ReaderSpeechModelDetails()
            }
        },
        confirmButton = {
            TextButton(
                onClick = onOfflineVoice,
                enabled = state.loaded && !state.busy,
                modifier = Modifier.testTag(ReaderSpeechOptionsTestTags.ChooseOffline),
            ) {
                Text(stringResource(
                    if (state.ready) R.string.reader_voice_use_offline else R.string.reader_voice_download,
                ))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSystemVoice,
                enabled = state.loaded,
                modifier = Modifier.testTag(ReaderSpeechOptionsTestTags.ChooseSystem),
            ) {
                Text(stringResource(R.string.reader_voice_use_system))
            }
        },
    )
}

@Composable
internal fun ReaderSpeechSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        ReaderSpeechModelRepository.get(context)
    }
    val preparation = remember(context.applicationContext) {
        ReaderSpeechPreparationRepository.get(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, preparation) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) preparation.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) preparation.refresh()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SettingsPage(title = stringResource(R.string.reader_voice_settings_title), onBack = onBack) {
        ReaderSpeechSettingsContent(
            state = repository.state,
            onEnabledChanged = repository::setEnabled,
            onDownload = repository::download,
            onDelete = repository::delete,
        )
        ReaderSpeechPreparationOptions(
            state = preparation.state,
            onEnabledChanged = preparation::setEnabled,
            onDownload = preparation::download,
            onCancelDownload = preparation::cancelDownload,
            onConfirmAdult = preparation::confirmAdult,
            onRefresh = preparation::refresh,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

@Composable
internal fun ReaderSpeechSettingsContent(
    state: ReaderSpeechModelState,
    onEnabledChanged: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Text(
        stringResource(R.string.reader_voice_settings_summary),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    SettingsPreferenceGroup {
        SettingsSwitch(
            title = stringResource(R.string.reader_voice_enabled_title),
            subtitle = stringResource(R.string.reader_voice_enabled_summary),
            checked = state.enabled,
            enabled = state.loaded && (state.ready || state.enabled) && !state.busy,
            onCheckedChange = onEnabledChanged,
            modifier = Modifier.testTag(ReaderSpeechOptionsTestTags.Enabled),
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 16.dp),
    ) {
        ReaderSpeechModelStatus(state)
        ReaderSpeechModelDetails()
        Button(
            onClick = onDownload,
            enabled = state.loaded && !state.ready && !state.busy,
            modifier = Modifier.fillMaxWidth().testTag(ReaderSpeechOptionsTestTags.Download),
        ) {
            Text(stringResource(R.string.reader_voice_download))
        }
        OutlinedButton(
            onClick = onDelete,
            enabled = state.loaded && !state.busy,
            modifier = Modifier.fillMaxWidth().testTag(ReaderSpeechOptionsTestTags.Delete),
        ) {
            Text(stringResource(R.string.reader_voice_delete))
        }
    }
}

@Composable
private fun ReaderSpeechModelDetails() {
    val uriHandler = LocalUriHandler.current
    Text(
        stringResource(R.string.reader_voice_model_details),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(R.string.reader_voice_model_terms),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = { uriHandler.openUri(OfflineSpeechModelFiles.LICENSE_URL) }) {
        Text(stringResource(R.string.reader_voice_model_license))
    }
}

@Composable
private fun ReaderSpeechModelStatus(state: ReaderSpeechModelState) {
    when {
        !state.loaded -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.reader_voice_checking))
        }
        state.busy -> {
            val progress = state.progress
            if (progress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.reader_voice_working))
            } else {
                val bounded = progress.coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { bounded }, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.reader_voice_downloading, (bounded * 100).toInt()),
                    modifier = Modifier.testTag(ReaderSpeechOptionsTestTags.Progress),
                )
            }
        }
        state.error -> Text(
            stringResource(R.string.reader_voice_model_error),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(ReaderSpeechOptionsTestTags.Error),
        )
        else -> Text(stringResource(
            if (state.ready) R.string.reader_voice_ready else R.string.reader_voice_missing,
        ))
    }
}

internal object ReaderSpeechOptionsTestTags {
    const val ChooseOffline = "reader_voice_choose_offline"
    const val ChooseSystem = "reader_voice_choose_system"
    const val Enabled = "reader_voice_enabled"
    const val Download = "reader_voice_download"
    const val Delete = "reader_voice_delete"
    const val Progress = "reader_voice_progress"
    const val Error = "reader_voice_error"
    const val PlaybackError = "reader_voice_playback_error"
}
