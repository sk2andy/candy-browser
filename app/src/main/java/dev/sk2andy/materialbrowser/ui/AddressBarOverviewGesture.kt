package dev.sk2andy.materialbrowser.ui

import kotlin.math.absoluteValue
import kotlin.math.min

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

internal data class AddressBarMorphCornerRadii(
    val horizontal: Float,
    val vertical: Float,
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

    fun targetAlpha(progress: Float): Float =
        ((progress.coerceIn(0f, 1f) - TARGET_FADE_START) /
            (TARGET_FADE_END - TARGET_FADE_START)).coerceIn(0f, 1f)

    fun targetScale(progress: Float): Float =
        TARGET_START_SCALE + (1f - TARGET_START_SCALE) * targetAlpha(progress)

    fun isDestinationButtonVisible(progress: Float): Boolean =
        progress >= MORPH_COMPLETION_THRESHOLD

    fun containerScale(progress: Float, sourceSize: Float, targetSize: Float): Float {
        if (sourceSize <= 0f || targetSize <= 0f) return 1f
        val boundedProgress = resistedProgress(progress)
        return 1f + (targetSize / sourceSize - 1f) * boundedProgress
    }

    fun morphCornerRadii(
        progress: Float,
        sourceWidth: Float,
        sourceHeight: Float,
        targetSize: Float,
        sourceCornerRadius: Float? = null,
    ): AddressBarMorphCornerRadii {
        if (
            !sourceWidth.isFinite() ||
            !sourceHeight.isFinite() ||
            !targetSize.isFinite() ||
            sourceWidth <= 0f ||
            sourceHeight <= 0f ||
            targetSize <= 0f
        ) {
            return AddressBarMorphCornerRadii(horizontal = 0f, vertical = 0f)
        }
        val morphProgress = resistedProgress(progress)
        val maximumSourceRadius = min(sourceWidth, sourceHeight) / 2f
        val sourceRadius = sourceCornerRadius
            ?.takeIf(Float::isFinite)
            ?.coerceIn(0f, maximumSourceRadius)
            ?: maximumSourceRadius
        val displayedRadius = sourceRadius + (targetSize / 2f - sourceRadius) * morphProgress
        val scaleX = containerScale(progress, sourceWidth, targetSize)
        val scaleY = containerScale(progress, sourceHeight, targetSize)
        return AddressBarMorphCornerRadii(
            horizontal = (displayedRadius / scaleX).coerceIn(0f, sourceWidth / 2f),
            vertical = (displayedRadius / scaleY).coerceIn(0f, sourceHeight / 2f),
        )
    }

    fun landingTranslation(
        progress: Float,
        sourceCenter: Float,
        targetCenter: Float,
    ): Float = (targetCenter - sourceCenter) * resistedProgress(progress)

    private const val RESISTANCE_BASE = 0.7f
    private const val RESISTANCE_GROWTH = 0.3f
    private const val CONTENT_FADE_END = 0.52f
    private const val TARGET_FADE_START = 0.28f
    private const val TARGET_FADE_END = 0.78f
    private const val TARGET_START_SCALE = 0.72f
    private const val MORPH_COMPLETION_THRESHOLD = 1f
}
