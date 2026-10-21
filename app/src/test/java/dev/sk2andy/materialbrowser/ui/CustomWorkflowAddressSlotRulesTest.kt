package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomWorkflowAddressSlotRulesTest {
    @Test
    fun `deleted workflow binding retains space and does not restore builtin action`() {
        val slots = CustomWorkflowAddressSlotRules.slots(AddressBarActionLayout.Default, setOf(1))
        assertEquals(AddressBarAction.Tabs, slots.beforeAddress.single().builtinAction)
        assertTrue(slots.afterAddress.single().overridden)
        assertEquals(1, slots.afterAddress.single().index)
    }

    @Test
    fun `binding in third slot extends shorter layout without moving earlier actions`() {
        val slots = CustomWorkflowAddressSlotRules.slots(AddressBarActionLayout.Default, setOf(2))
        assertEquals(listOf(0), slots.beforeAddress.map { it.index })
        assertEquals(listOf(1, 2), slots.afterAddress.map { it.index })
        assertEquals(AddressBarAction.NewTab, slots.afterAddress.first().builtinAction)
        assertFalse(slots.afterAddress.first().overridden)
        assertNull(slots.afterAddress.last().builtinAction)
        assertTrue(slots.afterAddress.last().overridden)
    }

    @Test
    fun `cast slot reservation hides the last custom slot without shifting it`() {
        val slots = CustomWorkflowAddressSlotRules.slots(AddressBarActionLayout.Default, setOf(2), 1)
        assertEquals(listOf(0, 1), (slots.beforeAddress + slots.afterAddress).map { it.index })
        assertTrue((slots.beforeAddress + slots.afterAddress).none { it.overridden })
    }

    @Test
    fun `invalid binding indices never expand address chrome`() {
        val slots = CustomWorkflowAddressSlotRules.slots(AddressBarActionLayout.Default, setOf(-1, 500))
        assertEquals(2, slots.beforeAddress.size + slots.afterAddress.size)
    }
}
