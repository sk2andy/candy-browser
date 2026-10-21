package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R

@Composable
internal fun DownloadAgainDialog(
    fileName: String,
    onDownloadAgain: () -> Unit,
    onOpenExisting: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_again_title)) },
        text = { Text(stringResource(R.string.download_again_message, fileName)) },
        confirmButton = {
            TextButton(onClick = onDownloadAgain) {
                Text(stringResource(R.string.download_again_action))
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onOpenExisting) {
                    Text(stringResource(R.string.download_open_existing))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}
