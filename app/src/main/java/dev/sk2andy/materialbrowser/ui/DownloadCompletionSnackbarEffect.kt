package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.downloads.DownloadCompletion
import dev.sk2andy.materialbrowser.browser.downloads.DownloadCompletionEvents
import dev.sk2andy.materialbrowser.browser.downloads.DownloadFileOpener
import kotlinx.coroutines.flow.Flow

@Composable
internal fun DownloadCompletionSnackbarEffect(hostState: SnackbarHostState) {
    val context = LocalContext.current
    val completions = remember(context) { DownloadCompletionEvents.observe(context) }
    DownloadCompletionSnackbarEffect(
        completions = completions,
        hostState = hostState,
        onOpen = { completion ->
            DownloadFileOpener.open(context, completion.uri, completion.mimeType)
        },
    )
}

@Composable
internal fun DownloadCompletionSnackbarEffect(
    completions: Flow<DownloadCompletion>,
    hostState: SnackbarHostState,
    onOpen: (DownloadCompletion) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpen by rememberUpdatedState(onOpen)
    val message = stringResource(R.string.download_notification_complete)
    val actionLabel = stringResource(R.string.action_open_popup)
    LaunchedEffect(completions, hostState, lifecycleOwner, message, actionLabel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            completions.collect { completion ->
                hostState.currentSnackbarData?.dismiss()
                val result = hostState.showSnackbar(
                    message = "$message\n${completion.fileName}",
                    actionLabel = actionLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) currentOnOpen(completion)
            }
        }
    }
}
