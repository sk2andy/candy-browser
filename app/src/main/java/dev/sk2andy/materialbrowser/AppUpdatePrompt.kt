package dev.sk2andy.materialbrowser

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.browser.actions.BrowserDownloadManager
import dev.sk2andy.materialbrowser.browser.actions.DownloadActionResult
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequest
import dev.sk2andy.materialbrowser.update.AppReleaseChannel
import dev.sk2andy.materialbrowser.update.AvailableAppUpdate
import dev.sk2andy.materialbrowser.update.GitHubAppUpdateChecker

@Composable
internal fun AppUpdatePrompt(
    context: Context,
    visible: Boolean,
) {
    var updateCheckCompleted by rememberSaveable { mutableStateOf(false) }
    var availableUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var availableUpdateUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var availableUpdateFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val updateChecker = remember { GitHubAppUpdateChecker() }
    val downloadManager = remember { BrowserDownloadManager(context) }
    val availableUpdate = availableUpdateVersion?.let { version ->
        val url = availableUpdateUrl ?: return@let null
        val fileName = availableUpdateFileName ?: return@let null
        AvailableAppUpdate(version, url, fileName)
    }

    LaunchedEffect(updateCheckCompleted) {
        if (updateCheckCompleted) return@LaunchedEffect
        if (BuildConfig.ENABLE_GITHUB_UPDATES && !BuildConfig.FOSS_DISTRIBUTION) {
            val releaseChannel = AppReleaseChannel.forUserCertificateTrust(
                BuildConfig.TRUST_USER_CERTIFICATES,
            )
            updateChecker.findAvailableUpdate(
                currentVersionName = BuildConfig.VERSION_NAME,
                channel = releaseChannel,
            )?.let { update ->
                availableUpdateVersion = update.versionName
                availableUpdateUrl = update.downloadUrl
                availableUpdateFileName = update.fileName
            }
        }
        updateCheckCompleted = true
    }

    if (availableUpdate != null && !dismissed && visible) {
        AppUpdateDialog(
            update = availableUpdate,
            onDismiss = { dismissed = true },
            onDownload = {
                val result = downloadManager.enqueue(
                    BrowserDownloadRequest(
                        url = availableUpdate.downloadUrl,
                        fileName = availableUpdate.fileName,
                        mimeType = AvailableAppUpdate.APK_MIME_TYPE,
                    ),
                )
                Toast.makeText(
                    context,
                    when (result) {
                        is DownloadActionResult.Enqueued -> context.getString(
                            R.string.toast_download_started,
                            result.fileName,
                        )
                        is DownloadActionResult.HandedOff -> context.getString(
                            R.string.toast_download_handed_off,
                            result.appName,
                        )
                        is DownloadActionResult.Failed -> result.message
                    },
                    Toast.LENGTH_SHORT,
                ).show()
                if (result is DownloadActionResult.Enqueued) dismissed = true
            },
        )
    }
}

@Composable
private fun AppUpdateDialog(
    update: AvailableAppUpdate,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Text(
                stringResource(
                    R.string.update_available_message,
                    update.versionName,
                ),
            )
        },
        confirmButton = {
            Button(onClick = onDownload) {
                Text(stringResource(R.string.action_download_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_later))
            }
        },
    )
}
