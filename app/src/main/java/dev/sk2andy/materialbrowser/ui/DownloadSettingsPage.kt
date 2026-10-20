package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
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
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderArtwork
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderIllustration
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsRadioOption
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSelectionLink
import dev.sk2andy.materialbrowser.shared.ui.settings.settingsSearchTarget

@Composable
internal fun DownloadsSettingsPage(
    settings: BrowserDownloadSettings,
    externalManagers: List<ExternalDownloadManagerApp>,
    onSettingsChanged: (BrowserDownloadSettings) -> Unit,
    onBack: () -> Unit,
) {
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
        SettingsHeaderIllustration(SettingsHeaderArtwork.Downloads)
        SettingsPageSpacer()
        SettingsPreferenceGroup {
            Column(
                Modifier.settingsSearchTarget(stringResource(R.string.settings_download_manager_title))
                    .selectableGroup(),
            ) {
                SettingsSectionTitle(stringResource(R.string.settings_download_manager_title))
                SettingsRadioOption(
                    label = stringResource(R.string.settings_download_manager_builtin),
                    selected = settings.managerMode == DownloadManagerMode.BuiltIn,
                    modifier = Modifier.testTag(DownloadSettingsTestTags.BuiltIn),
                    onClick = {
                        onSettingsChanged(settings.copy(
                            managerMode = DownloadManagerMode.BuiltIn,
                            externalManagerId = null,
                        ))
                    },
                )
                SettingsPreferenceDivider()
                SettingsRadioOption(
                    label = stringResource(R.string.settings_download_manager_ask),
                    selected = settings.managerMode == DownloadManagerMode.AskEveryTime,
                    modifier = Modifier.testTag(DownloadSettingsTestTags.AskEveryTime),
                    onClick = {
                        onSettingsChanged(settings.copy(
                            managerMode = DownloadManagerMode.AskEveryTime,
                            externalManagerId = null,
                        ))
                    },
                )
                externalManagers.forEach { manager ->
                    SettingsPreferenceDivider()
                    SettingsRadioOption(
                        label = manager.label,
                        selected = settings.managerMode == DownloadManagerMode.External &&
                            settings.externalManagerId == manager.id,
                        modifier = Modifier.testTag(DownloadSettingsTestTags.externalManager(manager.id)),
                        onClick = {
                            onSettingsChanged(settings.copy(
                                managerMode = DownloadManagerMode.External,
                                externalManagerId = manager.id,
                            ))
                        },
                    )
                }
            }
            if (externalManagers.isEmpty()) {
                Text(
                    stringResource(R.string.settings_download_no_external_managers),
                    modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (settings.managerMode == DownloadManagerMode.BuiltIn) 1f else 0.6f,
                    ),
                )
            }
                SettingsPreferenceDivider()
                SettingsSelectionLink(
                    title = stringResource(R.string.settings_download_folder_title),
                    value = settings.downloadSubdirectory?.let { relativePath ->
                        stringResource(R.string.settings_download_folder_path, relativePath)
                    } ?: stringResource(R.string.settings_download_folder_default),
                    onClick = {
                        if (settings.managerMode == DownloadManagerMode.BuiltIn) {
                            directoryDraft = settings.downloadSubdirectory.orEmpty()
                            directoryDialogVisible = true
                        }
                    },
                    modifier = Modifier.testTag(DownloadSettingsTestTags.Directory),
                    enabled = settings.managerMode == DownloadManagerMode.BuiltIn,
                )
                Text(
                    stringResource(R.string.settings_download_folder_summary),
                    modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (settings.managerMode == DownloadManagerMode.BuiltIn) 1f else 0.6f,
                    ),
                )
                if (settings.downloadSubdirectory != null) {
                    TextButton(
                        onClick = {
                            if (settings.managerMode == DownloadManagerMode.BuiltIn) {
                                onSettingsChanged(settings.copy(downloadSubdirectory = null))
                            }
                        },
                        enabled = settings.managerMode == DownloadManagerMode.BuiltIn,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .testTag(DownloadSettingsTestTags.DirectoryReset),
                    ) {
                        Text(stringResource(R.string.settings_download_folder_reset))
                    }
                }
                SettingsPreferenceDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_download_one_dm_session_title),
                    subtitle = stringResource(R.string.settings_download_one_dm_session_summary),
                    checked = settings.shareSessionDataWithOneDm,
                    enabled = oneDmRelevant,
                    onCheckedChange = {
                        onSettingsChanged(settings.copy(shareSessionDataWithOneDm = it))
                    },
                    modifier = Modifier.testTag(DownloadSettingsTestTags.OneDmSession),
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
    const val BuiltIn = "download_settings_manager_builtin"
    const val AskEveryTime = "download_settings_manager_ask"
    const val OneDmSession = "download_settings_one_dm_session"
    fun externalManager(id: String) = "download_settings_manager_$id"
    const val Directory = "download_settings_directory"
    const val DirectoryReset = "download_settings_directory_reset"
    const val DirectoryDialog = "download_settings_directory_dialog"
    const val DirectoryInput = "download_settings_directory_input"
    const val DirectoryConfirm = "download_settings_directory_confirm"
}
