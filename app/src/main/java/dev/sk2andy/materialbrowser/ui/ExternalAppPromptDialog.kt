package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.ExternalAppPrompt

internal object ExternalAppPromptTestTags {
    const val Dialog = "external_app_prompt_dialog"
    const val Confirm = "external_app_prompt_confirm"
    const val Cancel = "external_app_prompt_cancel"
}

@Composable
internal fun ExternalAppPromptDialog(
    prompt: ExternalAppPrompt,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag(ExternalAppPromptTestTags.Dialog),
        title = { Text(stringResource(R.string.external_app_prompt_title)) },
        text = {
            Text(
                stringResource(
                    R.string.external_app_prompt_message,
                    prompt.destination,
                ),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag(ExternalAppPromptTestTags.Confirm),
            ) {
                Text(stringResource(R.string.external_app_prompt_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(ExternalAppPromptTestTags.Cancel),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
