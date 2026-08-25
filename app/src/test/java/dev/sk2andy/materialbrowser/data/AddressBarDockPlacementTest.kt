package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressBarDockPlacementTest {
    @Test
    fun `normalization clamps finite fraction and repairs invalid values`() {
        assertEquals(
            AddressBarDockPlacement(AddressBarDockEdge.Left, 1f),
            AddressBarDockPlacement(AddressBarDockEdge.Left, 1.4f).normalized(),
        )
        assertEquals(
            AddressBarDockPlacement.Default,
            AddressBarDockPlacement(AddressBarDockEdge.Right, Float.NaN).normalized(),
        )
    }

    @Test
    fun `unknown persisted edge falls back to right`() {
        assertEquals(AddressBarDockEdge.Right, AddressBarDockEdge.fromWireValue("unknown"))
        assertEquals(AddressBarDockEdge.Left, AddressBarDockEdge.fromWireValue("left"))
    }
}
