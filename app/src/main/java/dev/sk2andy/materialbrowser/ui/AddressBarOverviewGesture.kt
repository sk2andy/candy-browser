package dev.sk2andy.materialbrowser.ui

import kotlin.math.absoluteValue
import dev.sk2andy.materialbrowser.shared.ui.AddressBarMorphRules

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

internal typealias AddressBarMorphCornerRadii =
    dev.sk2andy.materialbrowser.shared.ui.AddressBarMorphCornerRadii

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

    fun resistedProgress(progress: Float): Float = AddressBarMorphRules.resistedProgress(progress)

    fun contentAlpha(progress: Float): Float =
        (1f - progress.coerceIn(0f, 1f) / CONTENT_FADE_END).coerceIn(0f, 1f)

    fun targetAlpha(progress: Float): Float =
        ((progress.coerceIn(0f, 1f) - TARGET_FADE_START) /
            (TARGET_FADE_END - TARGET_FADE_START)).coerceIn(0f, 1f)

    fun targetScale(progress: Float): Float =
        TARGET_START_SCALE + (1f - TARGET_START_SCALE) * targetAlpha(progress)

    fun isDestinationButtonVisible(progress: Float): Boolean =
        progress >= MORPH_COMPLETION_THRESHOLD

    fun isMorphInFront(
        tabOverviewVisible: Boolean,
        destinationChromeVisible: Boolean,
        exitHeroVisible: Boolean,
    ): Boolean = tabOverviewVisible && (!destinationChromeVisible || exitHeroVisible)

    fun containerScale(progress: Float, sourceSize: Float, targetSize: Float): Float =
        AddressBarMorphRules.containerScale(progress, sourceSize, targetSize)

    fun morphCornerRadii(
        progress: Float,
        sourceWidth: Float,
        sourceHeight: Float,
        targetSize: Float,
        sourceCornerRadius: Float? = null,
    ): AddressBarMorphCornerRadii = AddressBarMorphRules.cornerRadii(
        progress = progress,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        targetSize = targetSize,
        sourceCornerRadius = sourceCornerRadius,
    )

    fun landingTranslation(
        progress: Float,
        sourceCenter: Float,
        targetCenter: Float,
    ): Float = (targetCenter - sourceCenter) * resistedProgress(progress)

    private const val CONTENT_FADE_END = 0.52f
    private const val TARGET_FADE_START = 0.28f
    private const val TARGET_FADE_END = 0.78f
    private const val TARGET_START_SCALE = 0.72f
    private const val MORPH_COMPLETION_THRESHOLD = 1f
}
