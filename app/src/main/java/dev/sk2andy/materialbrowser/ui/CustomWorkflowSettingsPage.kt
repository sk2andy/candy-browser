package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflow
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowBinding
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowIcon
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowPlacement
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowRules
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStep
import java.util.UUID

internal object CustomWorkflowTestTags {
    const val Add = "workflow_add"
    const val Name = "workflow_name"
    const val Save = "workflow_save"
    const val AddStep = "workflow_add_step"
    const val Delete = "workflow_delete"
    const val ConfirmDelete = "workflow_confirm_delete"
    const val Assignments = "workflow_assignments"
    const val SaveError = "workflow_save_error"
    const val ValidationError = "workflow_validation_error"
    const val Discard = "workflow_discard"
    const val Preview = "workflow_preview"

    fun workflow(id: String): String = "workflow_$id"
    fun template(index: Int): String = "workflow_template_$index"
    fun step(index: Int): String = "workflow_step_$index"
    fun chooseStep(step: CustomWorkflowStep): String = "workflow_choose_step_${step.wireValue}"
    fun moveUp(index: Int): String = "workflow_move_up_$index"
    fun moveDown(index: Int): String = "workflow_move_down_$index"
    fun removeStep(index: Int): String = "workflow_remove_step_$index"
    fun stepMenu(index: Int): String = "workflow_step_menu_$index"
    fun placement(placement: CustomWorkflowPlacement): String = "workflow_placement_${placement.wireValue}"
    fun slot(placement: CustomWorkflowPlacement, index: Int): String = "workflow_slot_${placement.wireValue}_$index"
    fun assign(id: String): String = "workflow_assign_$id"
    const val ClearAssignment = "workflow_clear_assignment"
}

@Composable
internal fun CustomWorkflowSettingsPage(
    configuration: CustomWorkflowConfiguration,
    onConfigurationChanged: (CustomWorkflowConfiguration, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = CustomWorkflowDraftSaver) {
        mutableStateOf<CustomWorkflowEditorDraft?>(null)
    }
    var originalDraft by rememberSaveable(stateSaver = CustomWorkflowDraftSaver) {
        mutableStateOf<CustomWorkflowEditorDraft?>(null)
    }
    var assignmentsVisible by rememberSaveable { mutableStateOf(false) }

    fun edit(workflow: CustomWorkflow) {
        val next = CustomWorkflowEditorDraft(workflow, configuration.bindings)
        originalDraft = next
        draft = next
    }

    val currentDraft = draft
    when {
        currentDraft != null -> CustomWorkflowEditorPage(
            draft = currentDraft,
            originalDraft = originalDraft ?: currentDraft,
            configuration = configuration,
            onDraftChanged = { draft = it },
            onConfigurationChanged = onConfigurationChanged,
            onBack = {
                draft = null
                originalDraft = null
            },
            onDuplicate = { workflow -> edit(workflow.copy(id = UUID.randomUUID().toString())) },
        )
        assignmentsVisible -> CustomWorkflowAssignmentsPage(
            configuration = configuration,
            onConfigurationChanged = onConfigurationChanged,
            onBack = { assignmentsVisible = false },
        )
        else -> SettingsPage(
            title = stringResource(R.string.workflow_title),
            onBack = onBack,
            actions = {
                IconButton(
                    enabled = configuration.workflows.size < CustomWorkflowRules.MAX_WORKFLOWS,
                    onClick = { edit(CustomWorkflow(UUID.randomUUID().toString(), "", steps = emptyList())) },
                    modifier = Modifier.testTag(CustomWorkflowTestTags.Add),
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.workflow_add))
                }
            },
        ) {
            Text(
                stringResource(R.string.workflow_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            SettingsPageSpacer()
            SettingsSectionTitle(stringResource(R.string.workflow_mine))
            Spacer(Modifier.height(8.dp))
            if (configuration.workflows.isEmpty()) {
                Text(
                    stringResource(R.string.workflow_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                SettingsPreferenceGroup {
                    configuration.workflows.forEachIndexed { index, workflow ->
                        if (index > 0) SettingsPreferenceDivider()
                        SettingsLink(
                            icon = workflow.icon.imageVector(),
                            title = workflow.name,
                            subtitle = workflow.summary() + if (workflow.showInMenu) {
                                "\n" + stringResource(R.string.workflow_in_menu)
                            } else "",
                            onClick = { edit(workflow) },
                            modifier = Modifier.testTag(CustomWorkflowTestTags.workflow(workflow.id)),
                        )
                    }
                }
            }
            SettingsPageSpacer()
            SettingsPreferenceGroup {
                SettingsLink(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.workflow_assignments),
                    subtitle = stringResource(R.string.workflow_assignments_summary),
                    onClick = { assignmentsVisible = true },
                    modifier = Modifier.testTag(CustomWorkflowTestTags.Assignments),
                )
            }
            SettingsPageSpacer()
            SettingsSectionTitle(stringResource(R.string.workflow_templates))
            Spacer(Modifier.height(8.dp))
            val templates = listOf(
                CustomWorkflow(
                    "later", stringResource(R.string.workflow_template_later), CustomWorkflowIcon.Download,
                    listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.CloseTab),
                ),
                CustomWorkflow(
                    "read", stringResource(R.string.workflow_template_read), CustomWorkflowIcon.Reader,
                    listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.OpenReader),
                ),
                CustomWorkflow(
                    "favorite", stringResource(R.string.workflow_template_favorite), CustomWorkflowIcon.Favorite,
                    listOf(CustomWorkflowStep.AddFavorite, CustomWorkflowStep.OpenReader),
                ),
            )
            SettingsPreferenceGroup {
                templates.forEachIndexed { index, template ->
                    if (index > 0) SettingsPreferenceDivider()
                    dev.sk2andy.materialbrowser.shared.ui.settings.SettingsLink(
                        title = template.name,
                        subtitle = template.summary(),
                        containerColor = dev.sk2andy.materialbrowser.shared.ui.settings.settingsRowColor(),
                        icon = { modifier, tint -> Icon(template.icon.imageVector(), null, modifier, tint) },
                        enabled = configuration.workflows.size < CustomWorkflowRules.MAX_WORKFLOWS,
                        onClick = { edit(template.copy(id = UUID.randomUUID().toString())) },
                        modifier = Modifier.testTag(CustomWorkflowTestTags.template(index)),
                    )
                }
            }
        }
    }
}

private val CustomWorkflowDraftSaver: Saver<CustomWorkflowEditorDraft?, Any> = listSaver(
    save = { draft ->
        if (draft == null) emptyList() else listOf(
            draft.workflow.id,
            draft.workflow.name,
            draft.workflow.icon.name,
            draft.workflow.showInMenu.toString(),
            draft.workflow.steps.joinToString(",") { it.name },
        ) + draft.bindings.map { "${it.placement.name},${it.slot},${it.workflowId}" }
    },
    restore = { values ->
        if (values.size < 5) null else runCatching {
            CustomWorkflowEditorDraft(
                workflow = CustomWorkflow(
                    id = values[0],
                    name = values[1],
                    icon = CustomWorkflowIcon.valueOf(values[2]),
                    showInMenu = values[3].toBoolean(),
                    steps = values[4].split(',').filter(String::isNotEmpty).map(CustomWorkflowStep::valueOf),
                ),
                bindings = values.drop(5).map { encoded ->
                    val parts = encoded.split(',')
                    CustomWorkflowBinding(CustomWorkflowPlacement.valueOf(parts[0]), parts[1].toInt(), parts[2])
                },
            )
        }.getOrNull()
    },
)
