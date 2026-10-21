package dev.sk2andy.materialbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.ui.CustomWorkflowIcons
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflow
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowIcon
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowPlacement
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowRules
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStep

@Composable
internal fun CustomWorkflowEditorPage(
    draft: CustomWorkflowEditorDraft,
    originalDraft: CustomWorkflowEditorDraft,
    configuration: CustomWorkflowConfiguration,
    onDraftChanged: (CustomWorkflowEditorDraft) -> Unit,
    onConfigurationChanged: (CustomWorkflowConfiguration, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit,
    onDuplicate: (CustomWorkflow) -> Unit,
) {
    val workflow = draft.workflow
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var discardVisible by rememberSaveable { mutableStateOf(false) }
    var deleteVisible by rememberSaveable { mutableStateOf(false) }
    var iconVisible by rememberSaveable { mutableStateOf(false) }
    var previewVisible by rememberSaveable { mutableStateOf(false) }
    var stepIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var placement by rememberSaveable { mutableStateOf<CustomWorkflowPlacement?>(null) }
    val changed = draft != originalDraft
    val failure = CustomWorkflowRules.validationFailure(workflow.copy(name = workflow.name.trim()))
    val candidate = CustomWorkflowEditorRules.configuration(configuration, draft)
    val existing = configuration.workflows.any { it.id == workflow.id }
    val requestBack = {
        if (!saving) {
            if (changed) discardVisible = true else onBack()
        }
    }
    BackHandler(onBack = requestBack)

    fun update(next: CustomWorkflow) {
        if (!saving) {
            saveFailed = false
            onDraftChanged(draft.copy(workflow = next))
        }
    }

    fun save(next: CustomWorkflowConfiguration) {
        saving = true
        saveFailed = false
        onConfigurationChanged(next) { saved ->
            saving = false
            if (saved) onBack() else saveFailed = true
        }
    }

    SettingsPage(
        title = stringResource(R.string.workflow_edit),
        onBack = requestBack,
        actions = {
            TextButton(
                enabled = candidate != null && !saving,
                onClick = { candidate?.let(::save) },
                modifier = Modifier.testTag(CustomWorkflowTestTags.Save),
            ) { Text(stringResource(R.string.workflow_save)) }
        },
    ) {
        SettingsPreferenceGroup {
            OutlinedTextField(
                value = workflow.name,
                onValueChange = { update(workflow.copy(name = it.take(CustomWorkflowRules.MAX_NAME_LENGTH))) },
                label = { Text(stringResource(R.string.workflow_name)) },
                enabled = !saving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(CustomWorkflowTestTags.Name),
            )
            SettingsPreferenceDivider()
            SettingsLink(
                icon = workflow.icon.imageVector(),
                title = stringResource(R.string.workflow_symbol),
                subtitle = stringResource(workflow.icon.labelRes()),
                onClick = { if (!saving) iconVisible = true },
            )
        }
        SettingsPageSpacer()
        SettingsSectionTitle(stringResource(R.string.workflow_steps))
        Spacer(Modifier.height(8.dp))
        SettingsPreferenceGroup {
            workflow.steps.forEachIndexed { index, step ->
                key(step) {
                    if (index > 0) SettingsPreferenceDivider()
                    CustomWorkflowStepRow(
                        index = index,
                        step = step,
                        steps = workflow.steps,
                        enabled = !saving,
                        onEdit = { stepIndex = index },
                        onRemove = { update(workflow.copy(steps = workflow.steps.filterIndexed { i, _ -> i != index })) },
                        onMove = { from, to -> update(workflow.copy(steps = CustomWorkflowEditorRules.moveStep(workflow.steps, from, to))) },
                    )
                }
            }
            if (workflow.steps.isNotEmpty()) SettingsPreferenceDivider()
            TextButton(
                onClick = { stepIndex = workflow.steps.size },
                enabled = !saving && workflow.steps.size < CustomWorkflowRules.MAX_STEPS,
                modifier = Modifier.fillMaxWidth().padding(8.dp).testTag(CustomWorkflowTestTags.AddStep),
            ) {
                Icon(Icons.Default.Add, null)
                Text(stringResource(R.string.workflow_add_step), Modifier.padding(start = 8.dp))
            }
        }
        if (failure != null && workflow.name.isNotBlank()) {
            Text(
                failure.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(CustomWorkflowTestTags.ValidationError),
            )
        }
        Text(
            stringResource(R.string.workflow_failure_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        SettingsPageSpacer()
        SettingsSectionTitle(stringResource(R.string.workflow_access))
        Spacer(Modifier.height(8.dp))
        SettingsPreferenceGroup {
            SettingsSwitch(
                title = stringResource(R.string.workflow_show_menu),
                subtitle = stringResource(R.string.workflow_show_menu_summary),
                checked = workflow.showInMenu,
                enabled = !saving,
                onCheckedChange = { update(workflow.copy(showInMenu = it)) },
            )
            CustomWorkflowPlacement.entries.forEach { target ->
                SettingsPreferenceDivider()
                val count = draft.bindings.count { it.placement == target && it.workflowId == workflow.id }
                dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSelectionLink(
                    title = stringResource(target.labelRes()),
                    value = if (count == 0) stringResource(R.string.workflow_assignment_none)
                    else stringResource(R.string.workflow_assignment_count, count),
                    enabled = !saving,
                    onClick = { placement = target },
                    modifier = Modifier.testTag(CustomWorkflowTestTags.placement(target)),
                )
            }
        }
        SettingsPageSpacer()
        OutlinedButton(
            onClick = { previewVisible = true },
            enabled = workflow.steps.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag(CustomWorkflowTestTags.Preview),
        ) { Text(stringResource(R.string.workflow_preview)) }
        if (existing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = { onDuplicate(workflow) },
                    enabled = !saving && configuration.workflows.size < CustomWorkflowRules.MAX_WORKFLOWS,
                ) { Text(stringResource(R.string.workflow_duplicate)) }
                TextButton(
                    onClick = { deleteVisible = true },
                    enabled = !saving,
                    modifier = Modifier.testTag(CustomWorkflowTestTags.Delete),
                ) { Text(stringResource(R.string.workflow_delete), color = MaterialTheme.colorScheme.error) }
            }
        }
        if (saveFailed) {
            Text(
                stringResource(R.string.workflow_save_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(CustomWorkflowTestTags.SaveError),
            )
        }
    }
    if (discardVisible) {
        AlertDialog(
            onDismissRequest = { discardVisible = false },
            title = { Text(stringResource(R.string.workflow_discard_title)) },
            confirmButton = {
                TextButton(onClick = onBack, modifier = Modifier.testTag(CustomWorkflowTestTags.Discard)) {
                    Text(stringResource(R.string.workflow_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { discardVisible = false }) { Text(stringResource(R.string.workflow_keep_editing)) }
            },
        )
    }
    if (deleteVisible) {
        AlertDialog(
            onDismissRequest = { if (!saving) deleteVisible = false },
            title = { Text(stringResource(R.string.workflow_delete_title, workflow.name)) },
            text = { Text(stringResource(R.string.workflow_delete_message)) },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = { deleteVisible = false; save(CustomWorkflowRules.delete(configuration, workflow.id)) },
                    modifier = Modifier.testTag(CustomWorkflowTestTags.ConfirmDelete),
                ) { Text(stringResource(R.string.workflow_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteVisible = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (iconVisible) {
        CustomWorkflowChoiceDialog(stringResource(R.string.workflow_symbol), { iconVisible = false }) {
            CustomWorkflowIcon.entries.forEach { icon ->
                TextButton(onClick = { update(workflow.copy(icon = icon)); iconVisible = false }, modifier = Modifier.fillMaxWidth()) {
                    Icon(icon.imageVector(), null)
                    Text(stringResource(icon.labelRes()), Modifier.padding(start = 12.dp))
                }
            }
        }
    }
    stepIndex?.let { editingIndex ->
        CustomWorkflowChoiceDialog(stringResource(R.string.workflow_add_step), { stepIndex = null }) {
            CustomWorkflowStep.entries.forEach { step ->
                val usedElsewhere = workflow.steps.withIndex().any { it.index != editingIndex && it.value == step }
                TextButton(
                    enabled = !usedElsewhere,
                    onClick = {
                        val next = workflow.steps.toMutableList()
                        if (editingIndex in next.indices) next[editingIndex] = step
                        else next.add(if (!step.isTerminal && next.lastOrNull()?.isTerminal == true) next.lastIndex else next.size, step)
                        update(workflow.copy(steps = next))
                        stepIndex = null
                    },
                    modifier = Modifier.fillMaxWidth().testTag(CustomWorkflowTestTags.chooseStep(step)),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(step.labelRes()), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(step.summaryRes()), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    placement?.let { target ->
        CustomWorkflowDraftAssignmentDialog(
            placement = target,
            draft = draft,
            configuration = configuration,
            onDraftChanged = onDraftChanged,
            onDismiss = { placement = null },
        )
    }
    if (previewVisible) {
        CustomWorkflowChoiceDialog(stringResource(R.string.workflow_preview), { previewVisible = false }) {
            Text(stringResource(R.string.workflow_preview_intro), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            workflow.steps.forEachIndexed { index, step ->
                Text("${index + 1}. ${stringResource(step.labelRes())}", Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.workflow_failure_notice), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.workflow_link_notice), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CustomWorkflowStepRow(
    index: Int,
    step: CustomWorkflowStep,
    steps: List<CustomWorkflowStep>,
    enabled: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSteps by rememberUpdatedState(steps)
    val currentOnMove by rememberUpdatedState(onMove)
    val threshold = with(LocalDensity.current) { CustomWorkflowEditorRules.REORDER_DISTANCE_DP.dp.toPx() }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text((index + 1).toString()) }
        }
        TextButton(onClick = onEdit, enabled = enabled, modifier = Modifier.weight(1f).testTag(CustomWorkflowTestTags.step(index))) {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(step.labelRes()), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(step.summaryRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            Modifier.size(40.dp).pointerInput(step, enabled, threshold) {
                if (!enabled) return@pointerInput
                var distance = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = { distance = 0f },
                    onDrag = { change, delta ->
                        change.consume()
                        distance += delta.y
                        val from = currentSteps.indexOf(step)
                        val target = CustomWorkflowEditorRules.dragTarget(from, currentSteps.size, distance, threshold)
                        if (target != null) {
                            currentOnMove(from, target)
                            distance = 0f
                        }
                    },
                )
            },
            contentAlignment = Alignment.Center,
        ) { Icon(CustomWorkflowIcons.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Box {
            IconButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.testTag(CustomWorkflowTestTags.stepMenu(index)),
            ) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.cd_more_options))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.workflow_move_up, index + 1)) },
                    enabled = index > 0,
                    onClick = { expanded = false; onMove(index, index - 1) },
                    modifier = Modifier.testTag(CustomWorkflowTestTags.moveUp(index)),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.workflow_move_down, index + 1)) },
                    enabled = index < steps.lastIndex,
                    onClick = { expanded = false; onMove(index, index + 1) },
                    modifier = Modifier.testTag(CustomWorkflowTestTags.moveDown(index)),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.workflow_remove_step, index + 1)) },
                    onClick = { expanded = false; onRemove() },
                    modifier = Modifier.testTag(CustomWorkflowTestTags.removeStep(index)),
                )
            }
        }
    }
}

@Composable
internal fun CustomWorkflowChoiceDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { content() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}
