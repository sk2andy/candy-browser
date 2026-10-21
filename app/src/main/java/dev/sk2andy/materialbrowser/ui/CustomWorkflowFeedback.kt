package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowFailure
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowRunState
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStepStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomWorkflowFeedback(
    state: CustomWorkflowRunState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var details by remember(state.workflow.id) { mutableStateOf(false) }
    val summary = when {
        state.cancellationRequested && state.running -> stringResource(R.string.workflow_run_cancelling)
        state.failure != null -> stringResource(state.failure.messageRes())
        state.running -> state.workflow.steps.getOrNull(
            state.statuses.indexOf(CustomWorkflowStepStatus.Running),
        )?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.workflow_run_running)
        else -> stringResource(R.string.workflow_run_done)
    }
    Snackbar(
        modifier = modifier.testTag("workflow_run_feedback"),
        action = { TextButton(onClick = { details = true }) { Text(stringResource(R.string.workflow_run_details)) } },
        dismissAction = {
            TextButton(onClick = if (state.running) onCancel else onDismiss, enabled = !state.cancellationRequested || !state.running) {
                Text(stringResource(if (state.running) R.string.action_cancel else R.string.workflow_run_dismiss))
            }
        },
    ) {
        Text("${state.workflow.name}: $summary", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (details) {
        ModalBottomSheet(onDismissRequest = { details = false }) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(state.workflow.name, style = MaterialTheme.typography.titleLarge)
                state.workflow.steps.forEachIndexed { index, step ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("${index + 1}")
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(step.labelRes()))
                            Text(stringResource(state.statuses[index].labelRes()), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(summary)
                TextButton(onClick = { if (state.running) onCancel() else { details = false; onDismiss() } }) {
                    Text(stringResource(if (state.running) R.string.action_cancel else R.string.workflow_run_dismiss))
                }
            }
        }
    }
}

private fun CustomWorkflowStepStatus.labelRes(): Int = when (this) {
    CustomWorkflowStepStatus.Waiting -> R.string.workflow_run_waiting
    CustomWorkflowStepStatus.Running -> R.string.workflow_run_running
    CustomWorkflowStepStatus.Succeeded -> R.string.workflow_run_succeeded
    CustomWorkflowStepStatus.Failed -> R.string.workflow_run_failed
    CustomWorkflowStepStatus.Skipped -> R.string.workflow_run_skipped
}

private fun CustomWorkflowFailure.messageRes(): Int = when (this) {
    CustomWorkflowFailure.InvalidWorkflow -> R.string.workflow_run_invalid
    CustomWorkflowFailure.UnsupportedPage -> R.string.workflow_run_unsupported
    CustomWorkflowFailure.PrivatePersistence -> R.string.workflow_run_private
    CustomWorkflowFailure.PinnedTab -> R.string.workflow_run_pinned
    CustomWorkflowFailure.PageChanged -> R.string.workflow_run_changed
    CustomWorkflowFailure.EmptyArticle -> R.string.workflow_run_empty
    CustomWorkflowFailure.SaveFailed, CustomWorkflowFailure.LibraryFull -> R.string.workflow_run_save_failed
    CustomWorkflowFailure.ActionFailed -> R.string.workflow_run_failed
    CustomWorkflowFailure.Cancelled -> R.string.workflow_run_cancelled
    CustomWorkflowFailure.TimedOut -> R.string.workflow_run_timeout
}
