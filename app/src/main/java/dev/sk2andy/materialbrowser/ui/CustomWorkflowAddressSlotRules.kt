package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.AddressBarActionLayoutRules

internal data class CustomWorkflowAddressSlot(
    val index: Int,
    val builtinAction: AddressBarAction?,
    val overridden: Boolean,
)

internal data class CustomWorkflowAddressSlots(
    val beforeAddress: List<CustomWorkflowAddressSlot>,
    val afterAddress: List<CustomWorkflowAddressSlot>,
)

internal object CustomWorkflowAddressSlotRules {
    fun slots(
        layout: AddressBarActionLayout,
        boundSlots: Set<Int>,
        reservedSlots: Int = 0,
    ): CustomWorkflowAddressSlots {
        val normalized = AddressBarActionLayoutRules.normalize(layout)
        val builtin = normalized.beforeAddress + normalized.afterAddress
        val limit = (AddressBarActionLayoutRules.MAX_CONFIGURABLE_ACTIONS - reservedSlots)
            .coerceIn(0, AddressBarActionLayoutRules.MAX_CONFIGURABLE_ACTIONS)
        val count = maxOf(builtin.size, (boundSlots.filter { it in 0 until limit }.maxOrNull() ?: -1) + 1)
            .coerceAtMost(limit)
        val slots = List(count) { index ->
            CustomWorkflowAddressSlot(index, builtin.getOrNull(index), index in boundSlots)
        }
        val beforeCount = normalized.beforeAddress.size.coerceAtMost(count)
        return CustomWorkflowAddressSlots(slots.take(beforeCount), slots.drop(beforeCount))
    }
}
