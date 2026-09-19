package dev.sk2andy.materialbrowser.browser

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.PathInterpolator

internal object WebContentTopInsetTransitionRules {
    const val DURATION_MILLIS = 160L

    fun shouldAnimate(
        previousState: WebContentTopInsetTransitionState,
        nextState: WebContentTopInsetTransitionState,
    ): Boolean =
        previousState == WebContentTopInsetTransitionState.Document &&
            nextState == WebContentTopInsetTransitionState.WebContentHeader ||
            previousState == WebContentTopInsetTransitionState.WebContentHeader &&
            nextState == WebContentTopInsetTransitionState.Document

    fun startTranslationY(
        currentTranslationY: Float,
        previousTopInsetPx: Int,
        nextTopInsetPx: Int,
        canAnimate: Boolean,
    ): Float {
        if (!canAnimate || previousTopInsetPx == nextTopInsetPx) return 0f
        val currentOffset = currentTranslationY.takeIf(Float::isFinite) ?: 0f
        return currentOffset + previousTopInsetPx - nextTopInsetPx
    }
}

internal fun View.smoothWebContentTopInsetChange(
    previousTopInsetPx: Int,
    nextTopInsetPx: Int,
    animateChange: Boolean,
) {
    if (previousTopInsetPx == nextTopInsetPx) return
    animate().cancel()
    val startTranslationY = WebContentTopInsetTransitionRules.startTranslationY(
        currentTranslationY = translationY,
        previousTopInsetPx = previousTopInsetPx,
        nextTopInsetPx = nextTopInsetPx,
        canAnimate = animateChange &&
            isAttachedToWindow &&
            isLaidOut &&
            ValueAnimator.areAnimatorsEnabled(),
    )
    translationY = startTranslationY
    if (startTranslationY == 0f) return
    animate()
        .translationY(0f)
        .setDuration(WebContentTopInsetTransitionRules.DURATION_MILLIS)
        .setInterpolator(WEB_CONTENT_TOP_INSET_INTERPOLATOR)
        .start()
}

private val WEB_CONTENT_TOP_INSET_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)

internal enum class WebContentTopInsetTransitionState {
    Document,
    WebContentHeader,
    Other,
}
