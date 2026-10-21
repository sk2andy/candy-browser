package dev.sk2andy.materialbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowPlacement
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowRules
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsRadioOption
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSelectionLink

@Composable
internal fun CustomWorkflowAssignmentsPage(
    configuration: CustomWorkflowConfiguration,
    onConfigurationChanged: (CustomWorkflowConfiguration, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var selectedPlacement by rememberSaveable { mutableStateOf<CustomWorkflowPlacement?>(null) }
    var selectedSlot by rememberSaveable { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    BackHandler { if (!saving) onBack() }
    SettingsPage(title = stringResource(R.string.workflow_assignments), onBack = { if (!saving) onBack() }) {
        Text(stringResource(R.string.workflow_assignment_intro), style = MaterialTheme.typography.bodyMedium)
        if (failed) {
            Text(
                stringResource(R.string.workflow_save_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(CustomWorkflowTestTags.SaveError),
            )
        }
        CustomWorkflowPlacement.entries.forEach { placement ->
            SettingsPageSpacer()
            SettingsSectionTitle(stringResource(placement.labelRes()))
            Spacer(Modifier.height(8.dp))
            SettingsPreferenceGroup {
                repeat(placement.slotCount) { slot ->
                    if (slot > 0) SettingsPreferenceDivider()
                    SettingsSelectionLink(
                        title = placement.slotLabel(slot),
                        value = assignmentLabel(configuration, placement, slot),
                        enabled = !saving,
                        onClick = { selectedPlacement = placement; selectedSlot = slot },
                        modifier = Modifier.testTag(CustomWorkflowTestTags.slot(placement, slot)),
                    )
                }
            }
        }
    }
    selectedPlacement?.let { placement ->
        val current = CustomWorkflowRules.binding(configuration, placement, selectedSlot)
        CustomWorkflowChoiceDialog(
            title = stringResource(placement.labelRes()) + " · " + placement.slotLabel(selectedSlot),
            onDismiss = { if (!saving) selectedPlacement = null },
        ) {
            fun select(id: String?) {
                saving = true
                failed = false
                onConfigurationChanged(CustomWorkflowRules.bind(configuration, placement, selectedSlot, id)) { saved ->
                    saving = false
                    failed = !saved
                    if (saved) selectedPlacement = null
                }
            }
            SettingsRadioOption(
                label = stringResource(R.string.workflow_assignment_default),
                selected = current == null,
                enabled = !saving,
                onClick = { select(null) },
                modifier = Modifier.testTag(CustomWorkflowTestTags.ClearAssignment),
            )
            configuration.workflows.forEach { workflow ->
                SettingsPreferenceDivider()
                SettingsRadioOption(
                    label = workflow.name,
                    selected = current?.workflowId == workflow.id,
                    enabled = !saving,
                    onClick = { select(workflow.id) },
                    modifier = Modifier.testTag(CustomWorkflowTestTags.assign(workflow.id)),
                )
            }
            if (failed) Text(stringResource(R.string.workflow_save_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun CustomWorkflowDraftAssignmentDialog(
    placement: CustomWorkflowPlacement,
    draft: CustomWorkflowEditorDraft,
    configuration: CustomWorkflowConfiguration,
    onDraftChanged: (CustomWorkflowEditorDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    CustomWorkflowChoiceDialog(stringResource(placement.labelRes()), onDismiss) {
        Text(stringResource(R.string.workflow_assignment_replace), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        val current = configuration.copy(bindings = draft.bindings)
        SettingsPreferenceGroup {
            repeat(placement.slotCount) { slot ->
                if (slot > 0) SettingsPreferenceDivider()
                val assigned = CustomWorkflowRules.binding(current, placement, slot)?.workflowId == draft.workflow.id
                SettingsSwitch(
                    title = placement.slotLabel(slot),
                    subtitle = if (assigned) draft.workflow.name else stringResource(
                        R.string.workflow_assignment_other,
                        assignmentLabel(current, placement, slot),
                    ),
                    checked = assigned,
                    onCheckedChange = { enabled ->
                        val next = CustomWorkflowRules.bind(current, placement, slot, draft.workflow.id.takeIf { enabled })
                        onDraftChanged(draft.copy(bindings = next.bindings))
                    },
                    modifier = Modifier.testTag(CustomWorkflowTestTags.slot(placement, slot)),
                )
            }
        }
    }
}

@Composable
private fun assignmentLabel(
    configuration: CustomWorkflowConfiguration,
    placement: CustomWorkflowPlacement,
    slot: Int,
): String {
    val binding = CustomWorkflowRules.binding(configuration, placement, slot)
        ?: return stringResource(R.string.workflow_assignment_default)
    return configuration.workflows.firstOrNull { it.id == binding.workflowId }?.name
        ?: stringResource(R.string.workflow_assignment_deleted)
}
