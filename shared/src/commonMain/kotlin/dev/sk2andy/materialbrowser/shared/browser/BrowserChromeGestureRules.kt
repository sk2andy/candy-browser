package dev.sk2andy.materialbrowser.shared.browser

import kotlin.math.absoluteValue

object BrowserChromeGestureRules {
    const val OPEN_OVERVIEW_THRESHOLD = 56.0

    fun overviewProgress(dragY: Double): Double =
        (-dragY / OPEN_OVERVIEW_THRESHOLD).coerceIn(0.0, 1.0)

    fun shouldOpenOverview(
        dragX: Double,
        dragY: Double,
        touchSlop: Double,
        isAddressEditing: Boolean = false,
    ): Boolean =
        !isAddressEditing &&
            dragY <= -OPEN_OVERVIEW_THRESHOLD &&
            dragY.absoluteValue > dragX.absoluteValue &&
            maxOf(dragX.absoluteValue, dragY.absoluteValue) >= touchSlop
}

enum class BrowserTabSwitchTarget {
    Stay,
    Previous,
    Next,
}

object BrowserTabSwitchGestureRules {
    const val DISTANCE_FRACTION = 0.24
    const val MIN_FLING_DISTANCE = 24.0
    const val MIN_FLING_VELOCITY = 900.0

    fun target(
        dragX: Double,
        dragY: Double,
        velocityX: Double,
        viewportWidth: Double,
        hasPreviousTab: Boolean,
        hasNextTab: Boolean,
        isAddressEditing: Boolean,
    ): BrowserTabSwitchTarget {
        if (
            isAddressEditing ||
            !dragX.isFinite() ||
            !dragY.isFinite() ||
            !velocityX.isFinite() ||
            !viewportWidth.isFinite() ||
            viewportWidth <= 0.0 ||
            dragX.absoluteValue <= dragY.absoluteValue
        ) {
            return BrowserTabSwitchTarget.Stay
        }
        val candidate = if (dragX > 0.0) {
            BrowserTabSwitchTarget.Previous
        } else {
            BrowserTabSwitchTarget.Next
        }
        val hasTarget = when (candidate) {
            BrowserTabSwitchTarget.Previous -> hasPreviousTab
            BrowserTabSwitchTarget.Next -> hasNextTab
            BrowserTabSwitchTarget.Stay -> false
        }
        if (!hasTarget) return BrowserTabSwitchTarget.Stay

        val distanceReached = dragX.absoluteValue >= viewportWidth * DISTANCE_FRACTION
        val velocityReached = dragX.absoluteValue >= MIN_FLING_DISTANCE &&
            velocityX.absoluteValue >= MIN_FLING_VELOCITY &&
            velocityX.compareTo(0.0) == dragX.compareTo(0.0)
        return if (distanceReached || velocityReached) {
            candidate
        } else {
            BrowserTabSwitchTarget.Stay
        }
    }
}
