package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.geometry.Offset
import dev.sk2andy.materialbrowser.data.AddressBarDockPlacement

data class AddressBarDockPosition(
    val horizontalFraction: Double,
    val verticalFraction: Double,
)

data class AddressBarDockSnapResult(
    val position: AddressBarDockPosition,
    val snappedToNormalAnchor: Boolean,
)

data class AddressBarDockOffset(
    val x: Double,
    val y: Double,
)

/** Scalar UIKit-point adapter; the existing Android rules retain every docking decision. */
object AddressBarDockingBridge {
    fun positionForPlacement(placement: AddressBarDockPlacement?): AddressBarDockPosition =
        AddressBarDockingRules.positionForPlacement(placement).toPosition()

    fun offsetForPosition(
        position: AddressBarDockPosition,
        maxWidth: Double,
        barWidth: Double,
        verticalTravel: Double,
    ): AddressBarDockOffset {
        val offset = AddressBarDockingRules.offsetForPosition(
            position = position.toOffset(),
            maxWidth = maxWidth.toFloat(),
            barWidth = barWidth.toFloat(),
            verticalTravel = verticalTravel.toFloat(),
        )
        return AddressBarDockOffset(offset.x.toDouble(), offset.y.toDouble())
    }

    fun positionAfterDrag(
        startPosition: AddressBarDockPosition,
        dragX: Double,
        dragY: Double,
        horizontalTravel: Double,
        verticalTravel: Double,
        resistNormalAnchor: Boolean,
    ): AddressBarDockPosition = AddressBarDockingRules.positionAfterDrag(
        startPosition = startPosition.toOffset(),
        dragDistancePx = Offset(dragX.toFloat(), dragY.toFloat()),
        horizontalTravelPx = horizontalTravel.toFloat(),
        verticalTravelPx = verticalTravel.toFloat(),
        resistNormalAnchor = resistNormalAnchor,
        density = 1f,
    ).toPosition()

    fun placementAfterDrop(
        position: AddressBarDockPosition,
        snapToNormalAnchor: Boolean,
    ): AddressBarDockPlacement = AddressBarDockingRules.placementAfterDrop(
        position = position.toOffset(),
        snapToNormalAnchor = snapToNormalAnchor,
    )

    fun normalAnchorSnap(
        position: AddressBarDockPosition,
        verticalTravel: Double,
        enabled: Boolean,
    ): AddressBarDockSnapResult {
        val result = AddressBarDockingRules.normalAnchorSnap(
            position = position.toOffset(),
            verticalTravelPx = verticalTravel.toFloat(),
            density = 1f,
            enabled = enabled,
        )
        return AddressBarDockSnapResult(result.position.toPosition(), result.snappedToNormalAnchor)
    }

    fun normalAnchorBreakawayReached(dragY: Double): Boolean =
        AddressBarDockingRules.normalAnchorBreakawayReached(dragY.toFloat(), density = 1f)

    fun normalAnchorResistanceProgress(dragY: Double): Double =
        AddressBarDockingRules.normalAnchorResistanceProgress(dragY.toFloat(), density = 1f).toDouble()

    private fun AddressBarDockPosition.toOffset(): Offset = Offset(
        horizontalFraction.toFloat(), verticalFraction.toFloat(),
    )

    private fun Offset.toPosition(): AddressBarDockPosition = AddressBarDockPosition(x.toDouble(), y.toDouble())
}
