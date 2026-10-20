package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A focused choice page that preserves the parent page's navigation and scroll state. */
@Composable
internal fun SettingsSelectionDialog(
    title: String,
    backContentDescription: String,
    artwork: SettingsHeaderArtwork,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().testTag("settings_selection_dialog"),
            color = settingsPageColor(),
        ) {
            SettingsSelectionPage(
                title = title,
                backContentDescription = backContentDescription,
                onBack = onDismissRequest,
                preview = { SettingsHeaderIllustration(artwork) },
                content = content,
            )
        }
    }
}
