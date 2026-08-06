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
        val dragDistance = state.dragDistance + deltaY
        val thresholdCrossed = threshold > 0f && dragDistance <= -threshold
        return AddressBarOverviewGestureUpdate(
            state = AddressBarOverviewGestureState(
                dragDistance = dragDistance,
                thresholdCrossed = thresholdCrossed,
            ),
            progress = progress(dragDistance, threshold),
            shouldCommit = false,
        )
    }

    fun release(state: AddressBarOverviewGestureState): AddressBarOverviewGestureUpdate =
        AddressBarOverviewGestureUpdate(
            state = state,
            progress = if (state.thresholdCrossed) 1f else 0f,
            shouldCommit = state.thresholdCrossed,
        )

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

    fun contentAlpha(progress: Float): Float =
        (1f - progress.coerceIn(0f, 1f) / CONTENT_FADE_END).coerceIn(0f, 1f)

    fun containerAlpha(progress: Float): Float =
        (1f - (progress.coerceIn(0f, 1f) - CONTAINER_FADE_START) /
            (CONTAINER_FADE_END - CONTAINER_FADE_START)).coerceIn(0f, 1f)

    fun targetAlpha(progress: Float): Float =
        ((progress.coerceIn(0f, 1f) - TARGET_FADE_START) /
            (TARGET_FADE_END - TARGET_FADE_START)).coerceIn(0f, 1f)

    fun targetScale(progress: Float): Float =
        TARGET_START_SCALE + (1f - TARGET_START_SCALE) * targetAlpha(progress)

    fun containerScale(progress: Float, sourceSize: Float, targetSize: Float): Float {
        if (sourceSize <= 0f || targetSize <= 0f) return 1f
        val boundedProgress = resistedProgress(progress)
        return 1f + (targetSize / sourceSize - 1f) * boundedProgress
    }

    fun landingTranslation(
        progress: Float,
        sourceCenter: Float,
        targetCenter: Float,
    ): Float = (targetCenter - sourceCenter) * resistedProgress(progress)

    private const val RESISTANCE_BASE = 0.7f
    private const val RESISTANCE_GROWTH = 0.3f
    private const val CONTENT_FADE_END = 0.52f
    private const val CONTAINER_FADE_START = 0.58f
    private const val CONTAINER_FADE_END = 0.9f
    private const val TARGET_FADE_START = 0.28f
    private const val TARGET_FADE_END = 0.78f
    private const val TARGET_START_SCALE = 0.72f
}
