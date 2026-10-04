package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.SiteDataTarget

internal object SiteDataConfirmationTestTags {
    const val Dialog = "site_data_confirmation"
    const val Delete = "site_data_delete"
    const val Cancel = "site_data_cancel"
}

@Composable
internal fun SiteDataConfirmationDialog(
    target: SiteDataTarget,
    supported: Boolean,
    targetIsCurrent: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(SiteDataConfirmationTestTags.Dialog),
        title = { Text(stringResource(R.string.command_site_data_confirm_title, target.host)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(target.origin)
                Text(
                    stringResource(
                        when {
                            !targetIsCurrent -> R.string.command_site_data_target_changed
                            supported -> R.string.command_site_data_confirm_webview
                            else -> R.string.command_site_data_unsupported
                        },
                    ),
                )
                if (supported && target.sharesStorage) {
                    Text(stringResource(R.string.command_site_data_shared_storage))
                }
            }
        },
        confirmButton = {
            if (supported) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.testTag(SiteDataConfirmationTestTags.Delete),
                ) { Text(stringResource(R.string.action_delete)) }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(SiteDataConfirmationTestTags.Cancel),
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
