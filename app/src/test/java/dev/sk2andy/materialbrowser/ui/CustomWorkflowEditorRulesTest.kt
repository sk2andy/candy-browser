package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflow
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowBinding
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowConfiguration
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowPlacement
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomWorkflowEditorRulesTest {
    @Test
    fun `moving a step preserves the other steps in order`() {
        val steps = listOf(CustomWorkflowStep.CopyUrl, CustomWorkflowStep.AddFavorite, CustomWorkflowStep.OpenReader)
        assertEquals(
            listOf(CustomWorkflowStep.AddFavorite, CustomWorkflowStep.CopyUrl, CustomWorkflowStep.OpenReader),
            CustomWorkflowEditorRules.moveStep(steps, 0, 1),
        )
        assertEquals(steps, CustomWorkflowEditorRules.moveStep(steps, 0, -1))
    }

    @Test
    fun `drag target rejects small movement and boundaries`() {
        assertNull(CustomWorkflowEditorRules.dragTarget(0, 3, -100f, 72f))
        assertNull(CustomWorkflowEditorRules.dragTarget(1, 3, 72f, 72f))
        assertEquals(2, CustomWorkflowEditorRules.dragTarget(1, 3, 73f, 72f))
        assertNull(CustomWorkflowEditorRules.dragTarget(1, 3, Float.NaN, 72f))
    }

    @Test
    fun `saving trims a name and retains unrelated workflows and orphan bindings`() {
        val existing = CustomWorkflow("existing", "Existing", steps = listOf(CustomWorkflowStep.CopyUrl))
        val orphan = CustomWorkflowBinding(CustomWorkflowPlacement.MenuFooter, 4, "deleted")
        val next = CustomWorkflowEditorRules.configuration(
            CustomWorkflowConfiguration(listOf(existing), listOf(orphan)),
            CustomWorkflowEditorDraft(
                CustomWorkflow("new", "  New workflow  ", steps = listOf(CustomWorkflowStep.SaveReaderOffline)),
                listOf(orphan),
            ),
        )!!
        assertEquals(listOf("Existing", "New workflow"), next.workflows.map { it.name })
        assertEquals(listOf(orphan), next.bindings)
    }

    @Test
    fun `terminal step before another action cannot be saved`() {
        assertNull(
            CustomWorkflowEditorRules.configuration(
                CustomWorkflowConfiguration(),
                CustomWorkflowEditorDraft(
                    CustomWorkflow("new", "Invalid", steps = listOf(CustomWorkflowStep.CloseTab, CustomWorkflowStep.CopyUrl)),
                    emptyList(),
                ),
            ),
        )
    }

    @Test
    fun `pinning workflow supports link assignments`() {
        val draft = CustomWorkflowEditorDraft(
            CustomWorkflow("pin", "Keep tab", steps = listOf(CustomWorkflowStep.PinTab)),
            listOf(CustomWorkflowBinding(CustomWorkflowPlacement.LinkLongPress, 0, "pin")),
        )
        assertEquals(
            "pin",
            CustomWorkflowEditorRules.configuration(CustomWorkflowConfiguration(), draft)!!.workflows.single().id,
        )
    }
}
