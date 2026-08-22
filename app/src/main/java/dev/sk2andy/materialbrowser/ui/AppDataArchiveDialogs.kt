package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppDataArchiveCompatibility
import dev.sk2andy.materialbrowser.data.StagedAppDataArchive

internal data class AppDataImportPreview(
    val staged: StagedAppDataArchive,
    val compatibility: AppDataArchiveCompatibility,
)

@Composable
internal fun AppDataExportWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.data_archive_export_warning_title)) },
        text = { Text(stringResource(R.string.data_archive_export_warning_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.data_archive_export_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun AppDataImportConfirmationDialog(
    pending: AppDataImportPreview,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val hasVersionMismatch = pending.compatibility != AppDataArchiveCompatibility.Same
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.data_archive_import_confirm_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.data_archive_import_confirm_message,
                        pending.staged.inspection.manifest.appVersionName,
                        pending.staged.inspection.entries.count { entry -> !entry.isDirectory },
                    ),
                )
                if (hasVersionMismatch) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.data_archive_import_version_warning),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(
                    stringResource(
                        if (hasVersionMismatch) {
                            R.string.data_archive_import_mismatch_action
                        } else {
                            R.string.data_archive_import_action
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
