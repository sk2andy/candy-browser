package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.geometry.Offset
import dev.sk2andy.materialbrowser.data.AddressBarDockEdge
import dev.sk2andy.materialbrowser.data.AddressBarDockPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBarDockingRulesTest {
    @Test
    fun `blank tab disables docking regardless of enabled setting`() {
        assertFalse(AddressBarDockingRules.isAvailable(settingEnabled = true, isBlankTab = true))
        assertTrue(AddressBarDockingRules.isAvailable(settingEnabled = true, isBlankTab = false))
        assertFalse(AddressBarDockingRules.isAvailable(settingEnabled = false, isBlankTab = false))
    }

    @Test
    fun `placement maps physical edges and vertical fraction to normalized position`() {
        assertEquals(
            Offset(-1f, 0.25f),
            AddressBarDockingRules.positionForPlacement(
                AddressBarDockPlacement(AddressBarDockEdge.Left, 0.25f),
            ),
        )
        assertEquals(
            Offset(1f, 0.75f),
            AddressBarDockingRules.positionForPlacement(
                AddressBarDockPlacement(AddressBarDockEdge.Right, 0.75f),
            ),
        )
    }

    @Test
    fun `park chevron points toward last physical edge`() {
        assertEquals(
            90f,
            AddressBarDockingRules.parkChevronRotationDegrees(AddressBarDockEdge.Left),
        )
        assertEquals(
            -90f,
            AddressBarDockingRules.parkChevronRotationDegrees(AddressBarDockEdge.Right),
        )
        assertTrue(AddressBarDockingRules.parkActionPrecedesAddress(AddressBarDockEdge.Left))
        assertFalse(AddressBarDockingRules.parkActionPrecedesAddress(AddressBarDockEdge.Right))
        assertEquals(
            -4f,
            AddressBarDockingRules.compactAddressContentOffsetDp(AddressBarDockEdge.Left),
        )
        assertEquals(
            4f,
            AddressBarDockingRules.compactAddressContentOffsetDp(AddressBarDockEdge.Right),
        )
        assertEquals(16f, AddressBarDockingRules.compactAddressSlackDp(dockingEnabled = true))
        assertEquals(36f, AddressBarDockingRules.compactAddressSlackDp(dockingEnabled = false))
    }

    @Test
    fun `diagonal drag moves freely between edges and resists normal anchor`() {
        assertEquals(
            Offset(-0.5f, 0.0175f),
            AddressBarDockingRules.positionAfterDrag(
                startPosition = Offset(1f, 0f),
                dragDistancePx = Offset(-300f, -50f),
                horizontalTravelPx = 200f,
                verticalTravelPx = 400f,
                resistNormalAnchor = true,
                density = 1f,
            ),
        )
    }

    @Test
    fun `normal anchor needs breakaway distance before release`() {
        assertFalse(AddressBarDockingRules.normalAnchorBreakawayReached(-71f, density = 1f))
        assertTrue(AddressBarDockingRules.normalAnchorBreakawayReached(-72f, density = 1f))
        assertEquals(
            0.07f,
            AddressBarDockingRules.positionAfterDrag(
                startPosition = Offset(1f, 0f),
                dragDistancePx = Offset(0f, -200f),
                horizontalTravelPx = 200f,
                verticalTravelPx = 400f,
                resistNormalAnchor = true,
                density = 1f,
            ).y,
        )
        assertEquals(
            0.5f,
            AddressBarDockingRules.positionAfterDrag(
                startPosition = Offset(1f, 0f),
                dragDistancePx = Offset(0f, -200f),
                horizontalTravelPx = 200f,
                verticalTravelPx = 400f,
                resistNormalAnchor = false,
                density = 1f,
            ).y,
        )
    }

    @Test
    fun `normal anchor resistance progress follows upward breakaway distance`() {
        assertEquals(0f, AddressBarDockingRules.normalAnchorResistanceProgress(24f, 1f))
        assertEquals(0.5f, AddressBarDockingRules.normalAnchorResistanceProgress(-36f, 1f))
        assertEquals(1f, AddressBarDockingRules.normalAnchorResistanceProgress(-90f, 1f))
        assertEquals(0f, AddressBarDockingRules.normalAnchorResistanceProgress(-36f, 0f))
    }

    @Test
    fun `drop chooses nearest physical edge and preserves vertical position`() {
        assertEquals(
            AddressBarDockPlacement(AddressBarDockEdge.Left, 0.6f),
            AddressBarDockingRules.placementAfterDrop(
                position = Offset(-0.01f, 0.6f),
                snapToNormalAnchor = false,
            ),
        )
        assertEquals(
            AddressBarDockPlacement(AddressBarDockEdge.Right, 0f),
            AddressBarDockingRules.placementAfterDrop(
                position = Offset.Zero,
                snapToNormalAnchor = true,
            ),
        )
    }

    @Test
    fun `normal anchor snap zone uses visible pixel distance`() {
        assertTrue(
            AddressBarDockingRules.isInNormalAnchorSnapZone(
                positionY = 0.07f,
                verticalTravelPx = 400f,
                density = 1f,
            ),
        )
        assertFalse(
            AddressBarDockingRules.isInNormalAnchorSnapZone(
                positionY = 0.071f,
                verticalTravelPx = 400f,
                density = 1f,
            ),
        )
    }

    @Test
    fun `movement haptic ignores stationary position`() {
        assertFalse(AddressBarDockingRules.offsetMoved(Offset(80f, 20f), Offset(80.003f, 20f)))
        assertTrue(AddressBarDockingRules.offsetMoved(Offset(80f, 20f), Offset(80.01f, 20f)))
    }

    @Test
    fun `invalid geometry and persisted fractions stay finite`() {
        assertEquals(
            Offset(-1f, 0f),
            AddressBarDockingRules.positionForPlacement(
                AddressBarDockPlacement(AddressBarDockEdge.Left, Float.NaN),
            ),
        )
        assertEquals(
            Offset.Zero,
            AddressBarDockingRules.positionAfterDrag(
                startPosition = Offset(Float.NaN, Float.POSITIVE_INFINITY),
                dragDistancePx = Offset(Float.NaN, Float.NEGATIVE_INFINITY),
                horizontalTravelPx = Float.NaN,
                verticalTravelPx = Float.NaN,
                resistNormalAnchor = true,
                density = Float.NaN,
            ),
        )
    }
}
