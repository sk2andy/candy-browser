package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.reader.ReaderSpeech
import dev.sk2andy.materialbrowser.reader.ReaderSpeechStatus

@Composable
internal fun SelectedTextResearchScreen(
    text: String,
    profiles: List<BrowserProfile>,
    onResearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsPage(title = stringResource(R.string.process_text_research), onBack = onDismiss) {
        Text(stringResource(R.string.process_text_choose_profile), style = MaterialTheme.typography.bodyMedium)
        Text(
            text.take(240),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        profiles.forEach { profile ->
            Button(
                onClick = { onResearch(profile.id) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .testTag(SelectedTextTestTags.profile(profile.id)),
            ) {
                Text(stringResource(R.string.process_text_research_profile, profile.emoji))
            }
        }
        if (profiles.isEmpty()) Text(stringResource(R.string.process_text_no_profiles))
    }
}

@Composable
internal fun SelectedTextSpeechScreen(
    text: String,
    speech: ReaderSpeech,
    onDismiss: () -> Unit,
    playbackAllowed: Boolean = true,
) {
    var playbackStarted by remember { mutableStateOf(false) }
    val status = speech.state.status
    LaunchedEffect(status, playbackAllowed) {
        if (playbackAllowed && status == ReaderSpeechStatus.Ready && !playbackStarted) {
            playbackStarted = true
            speech.play(text)
        }
    }
    SettingsPage(title = stringResource(R.string.process_text_read_aloud), onBack = onDismiss) {
        Text(
            stringResource(R.string.process_text_speech_memory_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = playbackAllowed && status in setOf(
                    ReaderSpeechStatus.Ready, ReaderSpeechStatus.Paused, ReaderSpeechStatus.Speaking,
                ),
                onClick = {
                    if (status == ReaderSpeechStatus.Speaking) speech.pause() else speech.play(text)
                },
                modifier = Modifier.testTag(SelectedTextTestTags.PlayPause),
            ) {
                Text(
                    stringResource(
                        if (status == ReaderSpeechStatus.Speaking) R.string.reader_speech_pause else R.string.process_text_play,
                    ),
                )
            }
            OutlinedButton(
                enabled = status == ReaderSpeechStatus.Speaking || status == ReaderSpeechStatus.Paused,
                onClick = speech::stop,
                modifier = Modifier.testTag(SelectedTextTestTags.Stop),
            ) {
                Text(stringResource(R.string.reader_speech_stop))
            }
        }
        if (status == ReaderSpeechStatus.Unavailable) {
            Text(stringResource(R.string.reader_voice_unavailable), color = MaterialTheme.colorScheme.error)
        }
        Text(text, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

internal object SelectedTextTestTags {
    const val PlayPause = "selected_text_play_pause"
    const val Stop = "selected_text_stop"
    fun profile(id: String): String = "selected_text_profile_$id"
}
