package dev.sk2andy.materialbrowser.ui

import kotlin.math.absoluteValue

internal enum class AddressBarOverviewGestureDirection {
    Pending,
    Upward,
    Rejected,
}

internal data class AddressBarOverviewGestureState(
    val dragDistance: Float = 0f,
    val thresholdCrossed: Boolean = false,
)

internal data class AddressBarOverviewGestureUpdate(
    val state: AddressBarOverviewGestureState,
    val progress: Float,
    val shouldCommit: Boolean,
)

internal object AddressBarOverviewGestureRules {
    val Idle = AddressBarOverviewGestureState()

    fun direction(
        dragX: Float,
        dragY: Float,
        touchSlop: Float,
    ): AddressBarOverviewGestureDirection = when {
        maxOf(dragX.absoluteValue, dragY.absoluteValue) < touchSlop ->
            AddressBarOverviewGestureDirection.Pending
        dragY < 0f && dragY.absoluteValue > dragX.absoluteValue ->
            AddressBarOverviewGestureDirection.Upward
        else -> AddressBarOverviewGestureDirection.Rejected
    }

    fun stateForProgress(progress: Float, threshold: Float): AddressBarOverviewGestureState {
        if (threshold <= 0f) return Idle
        return AddressBarOverviewGestureState(
            dragDistance = -progress.coerceIn(0f, 1f) * threshold,
        )
    }

    fun update(
        state: AddressBarOverviewGestureState,
        deltaY: Float,
        threshold: Float,
    ): AddressBarOverviewGestureUpdate {
        if (state.thresholdCrossed) {
            return AddressBarOverviewGestureUpdate(
                state = state,
                progress = 1f,
                shouldCommit = false,
            )
        }
        val dragDistance = state.dragDistance + deltaY
        val thresholdCrossed = threshold > 0f && dragDistance <= -threshold
        return AddressBarOverviewGestureUpdate(
            state = AddressBarOverviewGestureState(
                dragDistance = dragDistance,
                thresholdCrossed = thresholdCrossed,
            ),
            progress = progress(dragDistance, threshold),
            shouldCommit = thresholdCrossed,
        )
    }

    fun cancel(): AddressBarOverviewGestureUpdate = AddressBarOverviewGestureUpdate(
        state = Idle,
        progress = 0f,
        shouldCommit = false,
    )

    fun progress(dragDistance: Float, threshold: Float): Float {
        if (threshold <= 0f) return 0f
        return (-dragDistance / threshold).coerceIn(0f, 1f)
    }

    fun resistedProgress(progress: Float): Float {
        val boundedProgress = progress.coerceIn(0f, 1f)
        return boundedProgress * (RESISTANCE_BASE + RESISTANCE_GROWTH * boundedProgress)
    }

    private const val RESISTANCE_BASE = 0.7f
    private const val RESISTANCE_GROWTH = 0.3f
}
