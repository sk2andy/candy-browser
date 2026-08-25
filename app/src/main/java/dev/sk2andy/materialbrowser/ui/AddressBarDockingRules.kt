package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import dev.sk2andy.materialbrowser.data.AddressBarDockEdge
import dev.sk2andy.materialbrowser.data.AddressBarDockPlacement

@Immutable
internal data class AddressBarDockState(
    val enabled: Boolean,
    val placement: AddressBarDockPlacement?,
)

internal object AddressBarDockingRules {
    const val NORMAL_ANCHOR_BREAKAWAY_DP = 72f
    const val NORMAL_ANCHOR_SNAP_DP = 28f

    private const val NORMAL_ANCHOR_RESISTANCE = 0.14f
    private const val MOVEMENT_EPSILON_PX = 0.01f

    fun isAvailable(
        settingEnabled: Boolean,
        isBlankTab: Boolean,
    ): Boolean = settingEnabled && !isBlankTab

    fun positionForPlacement(placement: AddressBarDockPlacement?): Offset {
        val normalized = placement?.normalized() ?: return Offset.Zero
        val edgePosition = when (normalized.edge) {
            AddressBarDockEdge.Left -> -1f
            AddressBarDockEdge.Right -> 1f
        }
        return Offset(edgePosition, normalized.verticalFraction)
    }

    fun parkChevronRotationDegrees(edge: AddressBarDockEdge): Float = when (edge) {
        AddressBarDockEdge.Left -> 90f
        AddressBarDockEdge.Right -> -90f
    }

    fun parkActionPrecedesAddress(edge: AddressBarDockEdge): Boolean =
        edge == AddressBarDockEdge.Left

    fun compactAddressContentOffsetDp(edge: AddressBarDockEdge): Float = when (edge) {
        AddressBarDockEdge.Left -> -4f
        AddressBarDockEdge.Right -> 4f
    }

    fun compactAddressSlackDp(dockingEnabled: Boolean): Float =
        if (dockingEnabled) 16f else 36f

    fun positionAfterDrag(
        startPosition: Offset,
        dragDistancePx: Offset,
        horizontalTravelPx: Float,
        verticalTravelPx: Float,
        resistNormalAnchor: Boolean,
        density: Float,
    ): Offset {
        val safeStart = startPosition.sanitized()
        val safeHorizontalTravel = horizontalTravelPx.safePositive()
        val safeVerticalTravel = verticalTravelPx.safePositive()
        val safeDragX = dragDistancePx.x.takeIf(Float::isFinite) ?: 0f
        val safeDragY = dragDistancePx.y.takeIf(Float::isFinite) ?: 0f
        val horizontalPosition = if (safeHorizontalTravel == 0f) {
            safeStart.x
        } else {
            (safeStart.x + safeDragX / safeHorizontalTravel).coerceIn(-1f, 1f)
        }
        val verticalStartPx = -safeStart.y * safeVerticalTravel
        val rawVerticalPx = (verticalStartPx + safeDragY).coerceIn(-safeVerticalTravel, 0f)
        val visibleVerticalPx = if (resistNormalAnchor && safeStart.y == 0f) {
            resistedNormalAnchorOffset(rawVerticalPx, density)
        } else {
            rawVerticalPx
        }
        val verticalPosition = if (safeVerticalTravel == 0f) {
            safeStart.y
        } else {
            (-visibleVerticalPx / safeVerticalTravel).coerceIn(0f, 1f)
        }
        return Offset(horizontalPosition, verticalPosition)
    }

    fun placementAfterDrop(
        position: Offset,
        snapToNormalAnchor: Boolean,
    ): AddressBarDockPlacement {
        val safePosition = position.sanitized()
        return AddressBarDockPlacement(
            edge = if (safePosition.x < 0f) {
                AddressBarDockEdge.Left
            } else {
                AddressBarDockEdge.Right
            },
            verticalFraction = if (snapToNormalAnchor) 0f else safePosition.y,
        ).normalized()
    }

    fun normalAnchorBreakawayReached(
        dragDistanceYpx: Float,
        density: Float,
    ): Boolean = density.isFinite() && density > 0f && dragDistanceYpx.isFinite() &&
        -dragDistanceYpx >= NORMAL_ANCHOR_BREAKAWAY_DP * density

    fun normalAnchorResistanceProgress(
        dragDistanceYpx: Float,
        density: Float,
    ): Float {
        if (!dragDistanceYpx.isFinite() || !density.isFinite() || density <= 0f) return 0f
        val breakawayPx = NORMAL_ANCHOR_BREAKAWAY_DP * density
        return (-dragDistanceYpx / breakawayPx).coerceIn(0f, 1f)
    }

    fun isInNormalAnchorSnapZone(
        positionY: Float,
        verticalTravelPx: Float,
        density: Float,
    ): Boolean {
        if (!positionY.isFinite() || !density.isFinite() || density <= 0f) return false
        val safeVerticalTravel = verticalTravelPx.safePositive()
        return positionY.coerceIn(0f, 1f) * safeVerticalTravel <=
            NORMAL_ANCHOR_SNAP_DP * density
    }

    fun offsetMoved(previousOffsetPx: Offset, currentOffsetPx: Offset): Boolean =
        previousOffsetPx.isFinite() && currentOffsetPx.isFinite() &&
            (currentOffsetPx - previousOffsetPx).getDistance() >= MOVEMENT_EPSILON_PX

    private fun resistedNormalAnchorOffset(rawOffsetYpx: Float, density: Float): Float {
        if (!density.isFinite() || density <= 0f) return rawOffsetYpx
        return rawOffsetYpx * NORMAL_ANCHOR_RESISTANCE
    }

    private fun Float.safePositive(): Float =
        takeIf { it.isFinite() && it > 0f } ?: 0f

    private fun Offset.sanitized(): Offset = Offset(
        x = x.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f,
        y = y.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
    )

    private fun Offset.isFinite(): Boolean = x.isFinite() && y.isFinite()
}
