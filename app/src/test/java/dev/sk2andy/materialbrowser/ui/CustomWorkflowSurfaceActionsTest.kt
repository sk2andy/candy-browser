package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflow
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowBinding
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowPlacement
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomWorkflowSurfaceActionsTest {
    @Test
    fun `missing definition retains its exact slot while unbound slots stay absent`() {
        val configuration = CustomWorkflowConfiguration(
            workflows = listOf(workflow),
            bindings = listOf(
                CustomWorkflowBinding(CustomWorkflowPlacement.MenuShortcut, 1, "deleted"),
                CustomWorkflowBinding(CustomWorkflowPlacement.MenuShortcut, 4, workflow.id),
            ),
        )
        val slots = configuration.surfaceActions().menuShortcutSlots
        assertEquals(setOf(1, 4), slots.keys)
        assertNull(slots[1])
        assertEquals(workflow.id, slots[4]?.id)
    }

    @Test
    fun `hiding menu entry preserves separately assigned workflow triggers`() {
        val configuration = CustomWorkflowConfiguration(
            workflows = listOf(workflow.copy(showInMenu = false)),
            bindings = listOf(CustomWorkflowBinding(CustomWorkflowPlacement.AddressBar, 2, workflow.id)),
        )
        val actions = configuration.surfaceActions()
        assertTrue(actions.menuActions.isEmpty())
        assertEquals(workflow.id, actions.addressSlots[2]?.id)
        assertTrue(actions.addressSlots[2]?.requiresLoadedPage == true)
        assertFalse(workflow.copy(steps = listOf(CustomWorkflowStep.CopyUrl)).toBrowserCustomAction().requiresLoadedPage)
    }

    private val workflow = CustomWorkflow(
        id = "save",
        name = "Save article",
        steps = listOf(CustomWorkflowStep.SaveReaderOffline),
    )
}
