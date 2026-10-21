package dev.sk2andy.materialbrowser.browser.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomWorkflowRulesTest {
    @Test
    fun `saving before closing and saving before reading are valid sequences`() {
        assertNull(CustomWorkflowRules.validationFailure(workflow()))
        assertNull(
            CustomWorkflowRules.validationFailure(
                workflow().copy(steps = listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.OpenReader)),
            ),
        )
    }

    @Test
    fun `closing and opening reader must be terminal`() {
        listOf(CustomWorkflowStep.CloseTab, CustomWorkflowStep.OpenReader).forEach { terminal ->
            assertEquals(
                CustomWorkflowValidationFailure.TerminalStepNotLast,
                CustomWorkflowRules.validationFailure(
                    workflow().copy(steps = listOf(terminal, CustomWorkflowStep.CopyUrl)),
                ),
            )
        }
    }

    @Test
    fun `pinning cannot be followed by closing and duplicate effects are rejected`() {
        assertEquals(
            CustomWorkflowValidationFailure.ConflictingSteps,
            CustomWorkflowRules.validationFailure(
                workflow().copy(steps = listOf(CustomWorkflowStep.PinTab, CustomWorkflowStep.CloseTab)),
            ),
        )
        assertEquals(
            CustomWorkflowValidationFailure.DuplicateStep,
            CustomWorkflowRules.validationFailure(
                workflow().copy(steps = listOf(CustomWorkflowStep.AddFavorite, CustomWorkflowStep.AddFavorite)),
            ),
        )
    }

    @Test
    fun `invalid names identifiers and step counts fail validation`() {
        val cases = listOf(
            workflow().copy(id = "../../bad") to CustomWorkflowValidationFailure.InvalidId,
            workflow().copy(name = " ") to CustomWorkflowValidationFailure.EmptyName,
            workflow().copy(name = "a".repeat(81)) to CustomWorkflowValidationFailure.NameTooLong,
            workflow().copy(name = "Hidden\nname") to CustomWorkflowValidationFailure.InvalidName,
            workflow().copy(steps = emptyList()) to CustomWorkflowValidationFailure.EmptySteps,
            workflow().copy(steps = List(6) { CustomWorkflowStep.CopyUrl }) to CustomWorkflowValidationFailure.TooManySteps,
        )
        cases.forEach { (workflow, expected) ->
            assertEquals(expected, CustomWorkflowRules.validationFailure(workflow))
        }
    }

    @Test
    fun `deleting a definition retains every slot and disables every gesture`() {
        val definition = workflow()
        val bindings = CustomWorkflowPlacement.entries.flatMap { placement ->
            List(placement.slotCount) { slot -> CustomWorkflowBinding(placement, slot, definition.id) }
        }
        val configuration = CustomWorkflowConfiguration(listOf(definition), bindings)

        val deleted = CustomWorkflowRules.delete(configuration, definition.id)

        assertTrue(deleted.workflows.isEmpty())
        assertEquals(bindings, deleted.bindings)
        bindings.forEach { binding ->
            assertNotNull(CustomWorkflowRules.binding(deleted, binding.placement, binding.slot))
            assertNull(CustomWorkflowRules.resolve(deleted, binding.placement, binding.slot))
        }
        assertEquals(deleted, CustomWorkflowRules.normalize(deleted))
    }

    @Test
    fun `rebinding one slot preserves other slots and unresolved references`() {
        val original = CustomWorkflowConfiguration(
            workflows = listOf(workflow()),
            bindings = listOf(
                CustomWorkflowBinding(CustomWorkflowPlacement.MenuFooter, 0, "deleted"),
                CustomWorkflowBinding(CustomWorkflowPlacement.MenuFooter, 1, "save"),
                CustomWorkflowBinding(CustomWorkflowPlacement.LinkLongPress, 0, "save"),
            ),
        )

        val rebound = CustomWorkflowRules.bind(original, CustomWorkflowPlacement.MenuFooter, 0, "save")
        val cleared = CustomWorkflowRules.bind(rebound, CustomWorkflowPlacement.LinkLongPress, workflowId = null)

        assertEquals("save", CustomWorkflowRules.resolve(rebound, CustomWorkflowPlacement.MenuFooter, 0)?.id)
        assertEquals("save", CustomWorkflowRules.resolve(rebound, CustomWorkflowPlacement.MenuFooter, 1)?.id)
        assertNull(CustomWorkflowRules.binding(cleared, CustomWorkflowPlacement.LinkLongPress))
        assertEquals(2, cleared.bindings.size)
    }

    @Test
    fun `normalization bounds definitions and rejects invalid and duplicate slots`() {
        val definitions = List(140) { workflow().copy(id = "workflow-$it") }
        val original = CustomWorkflowConfiguration(
            workflows = definitions,
            bindings = listOf(
                CustomWorkflowBinding(CustomWorkflowPlacement.LinkPeekToolbar, 3, "workflow-0"),
                CustomWorkflowBinding(CustomWorkflowPlacement.LinkPeekToolbar, -1, "workflow-0"),
                CustomWorkflowBinding(CustomWorkflowPlacement.LinkPeekToolbar, 2, "deleted"),
                CustomWorkflowBinding(CustomWorkflowPlacement.LinkPeekToolbar, 2, "workflow-1"),
                CustomWorkflowBinding(CustomWorkflowPlacement.LinkPeekSwipe, 0, "../invalid"),
            ),
        )

        val normalized = CustomWorkflowRules.normalize(original)

        assertEquals(64, normalized.workflows.size)
        assertEquals("workflow-63", normalized.workflows.last().id)
        assertEquals(listOf(CustomWorkflowBinding(CustomWorkflowPlacement.LinkPeekToolbar, 2, "deleted")), normalized.bindings)
        assertEquals(normalized, CustomWorkflowRules.normalize(normalized))
    }

    @Test
    fun `normalization trims names and keeps first valid definition for an id`() {
        val configuration = CustomWorkflowConfiguration(
            workflows = listOf(
                workflow().copy(steps = emptyList()),
                workflow().copy(name = "  Save for later  "),
                workflow().copy(name = "Other definition"),
            ),
        )

        assertEquals(listOf(workflow()), CustomWorkflowRules.normalize(configuration).workflows)
        assertTrue(CustomWorkflowRules.isValidId("a4fe911d-5751-413d-8a52-6b752824a3cd"))
        assertFalse(CustomWorkflowRules.isValidId("name with spaces"))
    }

    private fun workflow() = CustomWorkflow(
        id = "save",
        name = "Save for later",
        icon = CustomWorkflowIcon.Download,
        steps = listOf(CustomWorkflowStep.SaveReaderOffline, CustomWorkflowStep.CloseTab),
    )
}
