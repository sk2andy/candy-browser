package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflow
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowPlacement
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStep
import dev.sk2andy.materialbrowser.shared.ui.BrowserCustomAction

internal data class CustomWorkflowSurfaceActions(
    val menuActions: List<BrowserCustomAction> = emptyList(),
    val menuShortcutSlots: Map<Int, BrowserCustomAction?> = emptyMap(),
    val menuFooterSlots: Map<Int, BrowserCustomAction?> = emptyMap(),
    val addressSlots: Map<Int, BrowserCustomAction?> = emptyMap(),
) {
    companion object {
        val Empty = CustomWorkflowSurfaceActions()
    }
}

internal fun CustomWorkflow.toBrowserCustomAction(enabled: Boolean = true): BrowserCustomAction =
    BrowserCustomAction(
        id = id,
        label = name,
        icon = icon.imageVector(),
        enabled = enabled,
        requiresLoadedPage = steps.any {
            it == CustomWorkflowStep.SaveReaderOffline || it == CustomWorkflowStep.OpenReader
        },
    )

/** A missing definition stays a null value: the binding reserves its original slot. */
internal fun CustomWorkflowConfiguration.surfaceSlots(
    placement: CustomWorkflowPlacement,
): Map<Int, BrowserCustomAction?> = bindings
    .filter { it.placement == placement }
    .associate { binding ->
        binding.slot to workflows.firstOrNull { it.id == binding.workflowId }?.toBrowserCustomAction()
    }

internal fun CustomWorkflowConfiguration.surfaceActions(): CustomWorkflowSurfaceActions =
    CustomWorkflowSurfaceActions(
        menuActions = workflows.filter { it.showInMenu }.map { it.toBrowserCustomAction() },
        menuShortcutSlots = surfaceSlots(CustomWorkflowPlacement.MenuShortcut),
        menuFooterSlots = surfaceSlots(CustomWorkflowPlacement.MenuFooter),
        addressSlots = surfaceSlots(CustomWorkflowPlacement.AddressBar),
    )
