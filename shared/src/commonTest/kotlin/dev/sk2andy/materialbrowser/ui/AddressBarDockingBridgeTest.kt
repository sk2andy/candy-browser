package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.data.AddressBarDockEdge
import dev.sk2andy.materialbrowser.data.AddressBarDockPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressBarDockingBridgeTest {
    @Test
    fun `stored physical edge and height project through inset adjusted viewport travel`() {
        val position = AddressBarDockingBridge.positionForPlacement(
            AddressBarDockPlacement(AddressBarDockEdge.Left, 0.5f),
        )
        val offset = AddressBarDockingBridge.offsetForPosition(
            position = position,
            maxWidth = 300.0,
            barWidth = 52.0,
            verticalTravel = 400.0,
        )

        assertEquals(-136.0, offset.x)
        assertEquals(-200.0, offset.y)
    }

    @Test
    fun `drag moves between physical edges and drop preserves released height`() {
        val position = AddressBarDockingBridge.positionAfterDrag(
            startPosition = AddressBarDockPosition(1.0, 0.0),
            dragX = -300.0,
            dragY = -200.0,
            horizontalTravel = 200.0,
            verticalTravel = 400.0,
            resistNormalAnchor = false,
        )
        val placement = AddressBarDockingBridge.placementAfterDrop(position, snapToNormalAnchor = false)

        assertEquals(-0.5, position.horizontalFraction)
        assertEquals(0.5, position.verticalFraction)
        assertEquals(AddressBarDockPlacement(AddressBarDockEdge.Left, 0.5f), placement)
    }

    @Test
    fun `normal anchor uses original resistance breakaway and physical snap thresholds`() {
        val position = AddressBarDockingBridge.positionAfterDrag(
            startPosition = AddressBarDockPosition(1.0, 0.0),
            dragX = 0.0,
            dragY = -50.0,
            horizontalTravel = 200.0,
            verticalTravel = 400.0,
            resistNormalAnchor = true,
        )
        assertEquals(0.0175, position.verticalFraction, absoluteTolerance = 0.000001)
        assertFalse(AddressBarDockingBridge.normalAnchorBreakawayReached(-71.0))
        assertTrue(AddressBarDockingBridge.normalAnchorBreakawayReached(-72.0))
        assertEquals(0.5, AddressBarDockingBridge.normalAnchorResistanceProgress(-36.0))
        val snap = AddressBarDockingBridge.normalAnchorSnap(
            AddressBarDockPosition(-1.0, 0.07), verticalTravel = 400.0, enabled = true,
        )
        assertTrue(snap.snappedToNormalAnchor)
        assertEquals(AddressBarDockPosition(-1.0, 0.0), snap.position)
    }

    @Test
    fun `unresolved geometry does not snap and invalid persisted height stays finite`() {
        val position = AddressBarDockingBridge.positionForPlacement(
            AddressBarDockPlacement(AddressBarDockEdge.Right, Float.NaN),
        )
        assertEquals(AddressBarDockPosition(1.0, 0.0), position)
        assertFalse(AddressBarDockingBridge.normalAnchorSnap(position, 0.0, enabled = true).snappedToNormalAnchor)
        assertEquals(320L, AddressBarGestureRules.PARKED_REPOSITION_LONG_PRESS_MILLIS)
        assertEquals(24f, AddressBarGestureRules.PARKED_OPEN_TABS_THRESHOLD_DP)
    }
}
