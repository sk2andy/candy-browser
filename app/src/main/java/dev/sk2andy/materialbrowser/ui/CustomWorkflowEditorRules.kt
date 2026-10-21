package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflow
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowBinding
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowRules
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStep

internal data class CustomWorkflowEditorDraft(
    val workflow: CustomWorkflow,
    val bindings: List<CustomWorkflowBinding>,
)

internal object CustomWorkflowEditorRules {
    const val REORDER_DISTANCE_DP = 72

    fun dragTarget(index: Int, count: Int, distance: Float, threshold: Float): Int? {
        if (index !in 0 until count || !distance.isFinite() || !threshold.isFinite() || threshold <= 0f) return null
        val direction = when {
            distance > threshold -> 1
            distance < -threshold -> -1
            else -> return null
        }
        return (index + direction).takeIf { it in 0 until count }
    }

    fun moveStep(steps: List<CustomWorkflowStep>, from: Int, to: Int): List<CustomWorkflowStep> {
        if (from !in steps.indices || to !in steps.indices || from == to) return steps
        return steps.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun configuration(
        current: CustomWorkflowConfiguration,
        draft: CustomWorkflowEditorDraft,
    ): CustomWorkflowConfiguration? {
        val workflow = draft.workflow.copy(name = draft.workflow.name.trim())
        if (CustomWorkflowRules.validationFailure(workflow) != null) return null
        val existing = current.workflows.any { it.id == workflow.id }
        if (!existing && current.workflows.size >= CustomWorkflowRules.MAX_WORKFLOWS) return null
        val workflows = if (existing) {
            current.workflows.map { if (it.id == workflow.id) workflow else it }
        } else {
            current.workflows + workflow
        }
        return CustomWorkflowRules.normalize(CustomWorkflowConfiguration(workflows, draft.bindings))
    }
}
