package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerMode
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerModeRules
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerSeekSettings
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderArtwork
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderIllustration

internal object PlayerSettingsTestTags {
    const val VideoAutoplay = "player_settings_video_autoplay"
    const val InlineMediaPlayer = "player_settings_inline_media_player"
    const val InlineMediaPlayerSeekBackward = "player_settings_inline_media_player_seek_backward"
    const val InlineMediaPlayerSeekForward = "player_settings_inline_media_player_seek_forward"
}

@Composable
internal fun PlayerSettingsPage(
    isVideoAutoplayBlocked: Boolean,
    isVideoAutoplayBlockingSupported: Boolean,
    inlineMediaPlayerMode: InlineMediaPlayerMode = InlineMediaPlayerMode.Default,
    inlineMediaPlayerSeekSettings: InlineMediaPlayerSeekSettings = InlineMediaPlayerSeekSettings(),
    isInlineMediaPlayerSupported: Boolean = true,
    onVideoAutoplayBlockedChanged: (Boolean) -> Unit,
    onInlineMediaPlayerModeChanged: (InlineMediaPlayerMode) -> Unit = {},
    onInlineMediaPlayerSeekSettingsChanged: (InlineMediaPlayerSeekSettings) -> Unit = {},
    onBack: () -> Unit,
    onPlayerMode: () -> Unit = {},
) {
    SettingsPage(
        title = stringResource(R.string.settings_player_title),
        onBack = onBack,
    ) {
        SettingsHeaderIllustration(SettingsHeaderArtwork.Player)
        SettingsPageSpacer()
        SettingsPreferenceGroup {
            SettingsSwitch(
                title = stringResource(R.string.settings_video_autoplay_title),
                subtitle = stringResource(
                    if (isVideoAutoplayBlockingSupported) {
                        R.string.settings_video_autoplay_subtitle
                    } else {
                        R.string.settings_video_autoplay_unsupported
                    },
                ),
                checked = isVideoAutoplayBlocked,
                enabled = isVideoAutoplayBlockingSupported,
                onCheckedChange = onVideoAutoplayBlockedChanged,
                modifier = Modifier.testTag(PlayerSettingsTestTags.VideoAutoplay),
            )
            SettingsPreferenceDivider()
            dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSelectionLink(
                title = stringResource(R.string.settings_inline_media_player_title),
                value = inlineMediaPlayerMode.displayName(),
                onClick = onPlayerMode,
                enabled = isInlineMediaPlayerSupported,
                modifier = Modifier.testTag(PlayerSettingsTestTags.InlineMediaPlayer),
            )
            Text(
                text = stringResource(
                    if (isInlineMediaPlayerSupported) {
                        R.string.settings_inline_media_player_subtitle
                    } else {
                        R.string.settings_inline_media_player_unsupported
                    },
                ),
                modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsPreferenceDivider()
            InlineMediaPlayerSeekSlider(
                title = stringResource(R.string.settings_inline_media_player_seek_backward),
                seconds = inlineMediaPlayerSeekSettings.backwardSeconds,
                enabled = isInlineMediaPlayerSupported &&
                    InlineMediaPlayerModeRules.isEnabled(inlineMediaPlayerMode),
                onSecondsChanged = { seconds ->
                    onInlineMediaPlayerSeekSettingsChanged(
                        inlineMediaPlayerSeekSettings.copy(backwardSeconds = seconds),
                    )
                },
                modifier = Modifier.testTag(PlayerSettingsTestTags.InlineMediaPlayerSeekBackward),
                inPreferenceGroup = true,
            )
            SettingsPreferenceDivider()
            InlineMediaPlayerSeekSlider(
                title = stringResource(R.string.settings_inline_media_player_seek_forward),
                seconds = inlineMediaPlayerSeekSettings.forwardSeconds,
                enabled = isInlineMediaPlayerSupported &&
                    InlineMediaPlayerModeRules.isEnabled(inlineMediaPlayerMode),
                onSecondsChanged = { seconds ->
                    onInlineMediaPlayerSeekSettingsChanged(
                        inlineMediaPlayerSeekSettings.copy(forwardSeconds = seconds),
                    )
                },
                modifier = Modifier.testTag(PlayerSettingsTestTags.InlineMediaPlayerSeekForward),
                inPreferenceGroup = true,
            )
            Text(
                text = stringResource(R.string.settings_inline_media_player_seek_subtitle),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun InlineMediaPlayerMode.displayName(): String = when (this) {
    InlineMediaPlayerMode.Disabled ->
        stringResource(R.string.settings_inline_media_player_mode_disabled)
    InlineMediaPlayerMode.ButtonFullscreen ->
        stringResource(R.string.settings_inline_media_player_mode_button_fullscreen)
    InlineMediaPlayerMode.ButtonInlineAndFullscreen ->
        stringResource(R.string.settings_inline_media_player_mode_button_inline_fullscreen)
    InlineMediaPlayerMode.AlwaysForFullscreen ->
        stringResource(R.string.settings_inline_media_player_mode_always_fullscreen)
    InlineMediaPlayerMode.Automatic ->
        stringResource(R.string.settings_inline_media_player_mode_automatic)
}
