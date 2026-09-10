package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadManagerApp
import dev.sk2andy.materialbrowser.data.BrowserDownloadSettings
import dev.sk2andy.materialbrowser.data.DownloadDirectoryRules
import dev.sk2andy.materialbrowser.data.DownloadManagerMode

@Composable
internal fun DownloadsSettingsPage(
    settings: BrowserDownloadSettings,
    externalManagers: List<ExternalDownloadManagerApp>,
    onSettingsChanged: (BrowserDownloadSettings) -> Unit,
    onBack: () -> Unit,
) {
    var managerMenuExpanded by remember { mutableStateOf(false) }
    var directoryDialogVisible by remember { mutableStateOf(false) }
    var directoryDraft by remember { mutableStateOf("") }
    val selectedExternalManager = externalManagers.firstOrNull {
        it.id == settings.externalManagerId
    }
    val oneDmRelevant = when (settings.managerMode) {
        DownloadManagerMode.BuiltIn -> false
        DownloadManagerMode.AskEveryTime -> externalManagers.any(ExternalDownloadManagerApp::isOneDm)
        DownloadManagerMode.External -> selectedExternalManager?.isOneDm == true
    }
    SettingsPage(
        title = stringResource(R.string.settings_downloads_title),
        onBack = onBack,
    ) {
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_download_manager_title),
                value = settings.displayName(externalManagers),
                expanded = managerMenuExpanded,
                onClick = { managerMenuExpanded = true },
            )
            SettingsDropdown(
                expanded = managerMenuExpanded,
                onDismissRequest = { managerMenuExpanded = false },
            ) {
                SettingsDropdownItem(
                    label = stringResource(R.string.settings_download_manager_builtin),
                    selected = settings.managerMode == DownloadManagerMode.BuiltIn,
                    onClick = {
                        managerMenuExpanded = false
                        onSettingsChanged(
                            settings.copy(
                                managerMode = DownloadManagerMode.BuiltIn,
                                externalManagerId = null,
                            ),
                        )
                    },
                )
                SettingsDropdownItem(
                    label = stringResource(R.string.settings_download_manager_ask),
                    selected = settings.managerMode == DownloadManagerMode.AskEveryTime,
                    onClick = {
                        managerMenuExpanded = false
                        onSettingsChanged(
                            settings.copy(
                                managerMode = DownloadManagerMode.AskEveryTime,
                                externalManagerId = null,
                            ),
                        )
                    },
                )
                externalManagers.forEach { manager ->
                    SettingsDropdownItem(
                        label = manager.label,
                        selected = settings.managerMode == DownloadManagerMode.External &&
                            settings.externalManagerId == manager.id,
                        onClick = {
                            managerMenuExpanded = false
                            onSettingsChanged(
                                settings.copy(
                                    managerMode = DownloadManagerMode.External,
                                    externalManagerId = manager.id,
                                ),
                            )
                        },
                    )
                }
            }
        }
        if (externalManagers.isEmpty()) {
            Text(
                stringResource(R.string.settings_download_no_external_managers),
                modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (settings.managerMode == DownloadManagerMode.BuiltIn) {
            Spacer(Modifier.height(18.dp))
            SettingsChoice(
                title = stringResource(R.string.settings_download_folder_title),
                value = settings.downloadSubdirectory?.let { relativePath ->
                    stringResource(R.string.settings_download_folder_path, relativePath)
                } ?: stringResource(R.string.settings_download_folder_default),
                expanded = false,
                onClick = {
                    directoryDraft = settings.downloadSubdirectory.orEmpty()
                    directoryDialogVisible = true
                },
                modifier = Modifier.testTag(DownloadSettingsTestTags.Directory),
            )
            Text(
                stringResource(R.string.settings_download_folder_summary),
                modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.downloadSubdirectory != null) {
                TextButton(
                    onClick = { onSettingsChanged(settings.copy(downloadSubdirectory = null)) },
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .testTag(DownloadSettingsTestTags.DirectoryReset),
                ) {
                    Text(stringResource(R.string.settings_download_folder_reset))
                }
            }
        }
        if (oneDmRelevant) {
            Spacer(Modifier.height(18.dp))
            SettingsSwitch(
                title = stringResource(R.string.settings_download_one_dm_session_title),
                subtitle = stringResource(R.string.settings_download_one_dm_session_summary),
                checked = settings.shareSessionDataWithOneDm,
                onCheckedChange = {
                    onSettingsChanged(settings.copy(shareSessionDataWithOneDm = it))
                },
            )
        }
    }
    if (directoryDialogVisible) {
        val validDirectory = DownloadDirectoryRules.isValidSubdirectoryInput(directoryDraft)
        AlertDialog(
            onDismissRequest = { directoryDialogVisible = false },
            modifier = Modifier.testTag(DownloadSettingsTestTags.DirectoryDialog),
            title = { Text(stringResource(R.string.settings_download_folder_title)) },
            text = {
                OutlinedTextField(
                    value = directoryDraft,
                    onValueChange = { directoryDraft = it },
                    modifier = Modifier.testTag(DownloadSettingsTestTags.DirectoryInput),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_download_folder_input)) },
                    isError = !validDirectory,
                    supportingText = if (validDirectory) {
                        null
                    } else {
                        { Text(stringResource(R.string.settings_download_folder_invalid)) }
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        directoryDialogVisible = false
                        onSettingsChanged(
                            settings.copy(
                                downloadSubdirectory = DownloadDirectoryRules
                                    .normalizedSubdirectory(directoryDraft),
                            ),
                        )
                    },
                    enabled = validDirectory,
                    modifier = Modifier.testTag(DownloadSettingsTestTags.DirectoryConfirm),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { directoryDialogVisible = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

internal object DownloadSettingsTestTags {
    const val Directory = "download_settings_directory"
    const val DirectoryReset = "download_settings_directory_reset"
    const val DirectoryDialog = "download_settings_directory_dialog"
    const val DirectoryInput = "download_settings_directory_input"
    const val DirectoryConfirm = "download_settings_directory_confirm"
}
