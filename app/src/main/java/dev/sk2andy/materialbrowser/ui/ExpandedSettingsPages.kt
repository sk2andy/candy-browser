package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerMode
import dev.sk2andy.materialbrowser.browser.LinkLongPressAction
import dev.sk2andy.materialbrowser.browser.LinkPeekSwipeAction
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsFeatureIllustration
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderArtwork
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderIllustration
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsPlayerModeArtwork
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsPlayerModeIllustration
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsIllustrationKind
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsRadioOption
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSelectionPage
import dev.sk2andy.materialbrowser.shared.ui.settings.settingsSearchTarget

@Composable
internal fun LinkLongPressSettingsPage(
    action: LinkLongPressAction,
    onActionChanged: (LinkLongPressAction) -> Unit,
    onBack: () -> Unit,
) {
    GestureChoicesPage(
        title = stringResource(R.string.settings_link_long_press_action),
        summary = stringResource(R.string.settings_link_long_press_explanation),
        preview = { SettingsHeaderIllustration(SettingsHeaderArtwork.LinkLongPress) },
        selected = action,
        groups = listOf(
            R.string.settings_gesture_preview_group to listOf(LinkLongPressAction.LinkPeek),
            R.string.settings_gesture_normal_tabs_group to listOf(
                LinkLongPressAction.OpenInNewTabInForeground, LinkLongPressAction.OpenInNewTabInBackground,
            ),
            R.string.settings_gesture_private_tabs_group to listOf(
                LinkLongPressAction.OpenInPrivateTabInForeground, LinkLongPressAction.OpenInPrivateTabInBackground,
            ),
            R.string.settings_gesture_link_actions_group to listOf(
                LinkLongPressAction.CopyLink, LinkLongPressAction.Share, LinkLongPressAction.DownloadLink,
            ),
        ),
        label = { stringResource(it.labelRes()) },
        onSelected = onActionChanged,
        onBack = onBack,
    )
}

@Composable
internal fun LinkPeekSwipeSettingsPage(
    action: LinkPeekSwipeAction,
    enabled: Boolean,
    onActionChanged: (LinkPeekSwipeAction) -> Unit,
    onBack: () -> Unit,
) {
    GestureChoicesPage(
        title = stringResource(R.string.settings_link_peek_swipe_action),
        summary = stringResource(R.string.settings_link_peek_swipe_action_summary),
        selected = action,
        enabled = enabled,
        groups = listOf(
            R.string.settings_gesture_normal_tabs_group to listOf(
                LinkPeekSwipeAction.OpenForeground, LinkPeekSwipeAction.OpenBackground,
                LinkPeekSwipeAction.OpenNewStack,
            ),
            R.string.settings_gesture_private_tabs_group to listOf(LinkPeekSwipeAction.OpenPrivate),
            R.string.settings_gesture_link_actions_group to listOf(
                LinkPeekSwipeAction.Copy, LinkPeekSwipeAction.Share, LinkPeekSwipeAction.Favorite,
                LinkPeekSwipeAction.Snooze, LinkPeekSwipeAction.ReaderLater,
            ),
        ),
        label = { stringResource(it.labelRes()) },
        onSelected = onActionChanged,
        onBack = onBack,
    )
}

@Composable
private fun <T> GestureChoicesPage(
    title: String,
    summary: String,
    selected: T,
    groups: List<Pair<Int, List<T>>>,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onBack: () -> Unit,
    enabled: Boolean = true,
    preview: @Composable () -> Unit = { SettingsFeatureIllustration(SettingsIllustrationKind.LinkGesture) },
) {
    SettingsPage(title = title, onBack = onBack) {
        Text(summary, modifier = Modifier.settingsSearchTarget(title), style = MaterialTheme.typography.bodyMedium)
        SettingsPageSpacer()
        preview()
        SettingsPageSpacer()
        Column(Modifier.selectableGroup()) {
            groups.forEach { (heading, choices) ->
                SettingsSectionTitle(stringResource(heading))
                SettingsPreferenceGroup {
                    choices.forEachIndexed { index, choice ->
                        if (index > 0) SettingsPreferenceDivider()
                        SettingsRadioOption(
                            label = label(choice), selected = choice == selected, enabled = enabled,
                            onClick = { if (choice != selected) onSelected(choice) },
                            modifier = Modifier.testTag("gesture_choice_$choice"),
                        )
                    }
                }
                SettingsPageSpacer()
            }
        }
    }
}

@Composable
internal fun PlayerModeSettingsPage(
    mode: InlineMediaPlayerMode,
    enabled: Boolean,
    onModeChanged: (InlineMediaPlayerMode) -> Unit,
    onBack: () -> Unit,
) {
    SettingsSelectionPage(
        title = stringResource(R.string.settings_inline_media_player_title),
        summary = stringResource(if (enabled) R.string.settings_inline_media_player_subtitle
            else R.string.settings_inline_media_player_unsupported),
        backContentDescription = stringResource(R.string.action_back),
        onBack = onBack,
        preview = {
            SettingsPlayerModeIllustration(
                when (mode) {
                    InlineMediaPlayerMode.Disabled -> SettingsPlayerModeArtwork.Disabled
                    InlineMediaPlayerMode.ButtonFullscreen -> SettingsPlayerModeArtwork.ButtonFullscreen
                    InlineMediaPlayerMode.ButtonInlineAndFullscreen -> SettingsPlayerModeArtwork.ButtonInlineAndFullscreen
                    InlineMediaPlayerMode.AlwaysForFullscreen -> SettingsPlayerModeArtwork.AlwaysForFullscreen
                    InlineMediaPlayerMode.Automatic -> SettingsPlayerModeArtwork.Automatic
                },
            )
        },
    ) {
        InlineMediaPlayerMode.entries.forEachIndexed { index, candidate ->
            if (index > 0) SettingsPreferenceDivider()
            SettingsRadioOption(
                label = candidate.displayName(),
                supportingText = stringResource(when (candidate) {
                    InlineMediaPlayerMode.Disabled -> R.string.settings_player_disabled_explanation
                    InlineMediaPlayerMode.ButtonFullscreen -> R.string.settings_player_button_fullscreen_explanation
                    InlineMediaPlayerMode.ButtonInlineAndFullscreen -> R.string.settings_player_button_inline_explanation
                    InlineMediaPlayerMode.AlwaysForFullscreen -> R.string.settings_player_fullscreen_explanation
                    InlineMediaPlayerMode.Automatic -> R.string.settings_player_automatic_explanation
                }),
                selected = mode == candidate,
                enabled = enabled,
                onClick = { if (candidate != mode) onModeChanged(candidate) },
                modifier = Modifier.testTag("player_mode_${candidate.stableId}"),
            )
        }
    }
}

@Composable
internal fun ClosedTabUndoSettingsPage(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(R.string.settings_closed_tab_undo_title)
    SettingsPage(title = title, onBack = onBack) {
        Text(stringResource(R.string.settings_closed_tab_undo_summary), style = MaterialTheme.typography.bodyMedium)
        SettingsPageSpacer()
        SettingsFeatureIllustration(SettingsIllustrationKind.UndoTab, active = enabled)
        SettingsPageSpacer()
        SettingsSwitch(
            title = title, subtitle = stringResource(R.string.settings_closed_tab_undo_summary),
            checked = enabled, prominent = true, onCheckedChange = onEnabledChanged,
            modifier = Modifier.testTag(TabSettingsTestTags.ClosedTabUndo),
        )
    }
}
