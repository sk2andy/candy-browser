package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class FullscreenVideoLevelIndicator(
    val kind: FullscreenVideoGestureKind,
    val level: Float,
)

@Stable
internal class FullscreenVideoGestureState(
    private val scope: CoroutineScope,
    private val currentBrightness: () -> Float,
    private val setBrightness: (Float) -> Float,
    private val currentVolume: () -> Float,
    private val setVolume: (Float) -> Float,
    private val dismissFullscreen: () -> Unit,
) {
    var dismissOffsetPx by mutableFloatStateOf(0f)
        private set
    var viewportHeightPx by mutableFloatStateOf(0f)
        private set
    var indicator by mutableStateOf<FullscreenVideoLevelIndicator?>(null)
        private set

    private var enabled = false
    private var gestureKind: FullscreenVideoGestureKind? = null
    private var totalDragY = 0f
    private var startLevel = 0f
    private var settleJob: Job? = null
    private var indicatorJob: Job? = null

    val transform: FullscreenVideoGestureTransform
        get() = FullscreenVideoGestureRules.transform(
            offsetY = dismissOffsetPx,
            viewportHeight = viewportHeightPx,
        )

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (!enabled) reset()
    }

    fun begin(pointerX: Float, width: Float, height: Float) {
        if (!enabled) return
        settleJob?.cancel()
        indicatorJob?.cancel()
        viewportHeightPx = height.coerceAtLeast(0f)
        gestureKind = FullscreenVideoGestureRules.kind(pointerX, width)
        totalDragY = 0f
        startLevel = when (gestureKind) {
            FullscreenVideoGestureKind.Brightness -> currentBrightness()
            FullscreenVideoGestureKind.Volume -> currentVolume()
            FullscreenVideoGestureKind.Dismiss,
            null,
            -> 0f
        }.coerceIn(0f, 1f)
    }

    fun drag(deltaY: Float) {
        if (!enabled) return
        totalDragY += deltaY
        when (gestureKind) {
            FullscreenVideoGestureKind.Brightness -> {
                val requested = FullscreenVideoGestureRules.adjustedLevel(
                    startLevel = startLevel,
                    dragY = totalDragY,
                    viewportHeight = viewportHeightPx,
                )
                indicator = FullscreenVideoLevelIndicator(
                    kind = FullscreenVideoGestureKind.Brightness,
                    level = setBrightness(requested),
                )
            }
            FullscreenVideoGestureKind.Volume -> {
                val requested = FullscreenVideoGestureRules.adjustedLevel(
                    startLevel = startLevel,
                    dragY = totalDragY,
                    viewportHeight = viewportHeightPx,
                )
                indicator = FullscreenVideoLevelIndicator(
                    kind = FullscreenVideoGestureKind.Volume,
                    level = setVolume(requested),
                )
            }
            FullscreenVideoGestureKind.Dismiss -> {
                dismissOffsetPx = FullscreenVideoGestureRules.dismissOffset(totalDragY)
            }
            null -> Unit
        }
    }

    fun end() {
        if (!enabled) return reset()
        when (gestureKind) {
            FullscreenVideoGestureKind.Dismiss -> settleDismissGesture()
            FullscreenVideoGestureKind.Brightness,
            FullscreenVideoGestureKind.Volume,
            -> scheduleIndicatorHide()
            null -> Unit
        }
        gestureKind = null
    }

    fun cancel() {
        if (gestureKind == FullscreenVideoGestureKind.Dismiss) animateDismissOffset(0f)
        scheduleIndicatorHide()
        gestureKind = null
    }

    private fun settleDismissGesture() {
        val shouldDismiss = FullscreenVideoGestureRules.shouldDismiss(
            offsetY = dismissOffsetPx,
            viewportHeight = viewportHeightPx,
        )
        if (!shouldDismiss) {
            animateDismissOffset(0f)
            return
        }
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                initialValue = dismissOffsetPx,
                targetValue = viewportHeightPx.coerceAtLeast(dismissOffsetPx),
                animationSpec = tween(durationMillis = DISMISS_ANIMATION_MILLIS),
            ) { value, _ -> dismissOffsetPx = value }
            dismissFullscreen()
            reset()
        }
    }

    private fun animateDismissOffset(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                initialValue = dismissOffsetPx,
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 420f,
                ),
            ) { value, _ -> dismissOffsetPx = value }
        }
    }

    private fun scheduleIndicatorHide() {
        indicatorJob?.cancel()
        indicatorJob = scope.launch {
            delay(INDICATOR_HIDE_DELAY_MILLIS)
            indicator = null
        }
    }

    private fun reset() {
        settleJob?.cancel()
        indicatorJob?.cancel()
        gestureKind = null
        totalDragY = 0f
        dismissOffsetPx = 0f
        viewportHeightPx = 0f
        indicator = null
    }

    private companion object {
        const val DISMISS_ANIMATION_MILLIS = 180
        const val INDICATOR_HIDE_DELAY_MILLIS = 720L
    }
}

@Composable
internal fun rememberFullscreenVideoGestureState(
    systemControls: FullscreenVideoSystemControls,
    onDismissFullscreen: () -> Unit,
): FullscreenVideoGestureState {
    val scope = rememberCoroutineScope()
    val currentDismissFullscreen by rememberUpdatedState(onDismissFullscreen)
    return remember(systemControls, scope) {
        FullscreenVideoGestureState(
            scope = scope,
            currentBrightness = systemControls::currentBrightnessFraction,
            setBrightness = systemControls::setBrightnessFraction,
            currentVolume = systemControls::currentMediaVolumeFraction,
            setVolume = systemControls::setMediaVolumeFraction,
            dismissFullscreen = { currentDismissFullscreen() },
        )
    }
}

internal fun Modifier.fullscreenVideoGestures(
    state: FullscreenVideoGestureState?,
): Modifier {
    if (state == null) return this
    return pointerInput(state) {
        detectVerticalDragGestures(
            onDragStart = { offset ->
                state.begin(
                    pointerX = offset.x,
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                )
            },
            onDragCancel = state::cancel,
            onDragEnd = state::end,
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                state.drag(dragAmount)
            },
        )
    }
}

internal fun Modifier.fullscreenVideoGestureTransform(
    state: FullscreenVideoGestureState?,
): Modifier {
    if (state == null) return this
    return graphicsLayer {
        val transform = state.transform
        translationY = transform.translationY
        scaleX = transform.scale
        scaleY = transform.scale
        shape = RoundedCornerShape(transform.cornerRadiusDp.dp)
        clip = transform.cornerRadiusDp > 0f
    }
}

@Composable
internal fun FullscreenVideoGestureIndicator(
    state: FullscreenVideoGestureState?,
    modifier: Modifier = Modifier,
) {
    val indicator = state?.indicator
    AnimatedVisibility(
        visible = indicator != null,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(180)),
        modifier = modifier,
    ) {
        val visibleIndicator = indicator ?: return@AnimatedVisibility
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .width(260.dp)
                .testTag(FullscreenVideoTestTags.GestureIndicator),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Icon(
                    painter = painterResource(
                        when (visibleIndicator.kind) {
                            FullscreenVideoGestureKind.Brightness ->
                                dev.sk2andy.materialbrowser.R.drawable.ic_player_brightness
                            else -> dev.sk2andy.materialbrowser.R.drawable.ic_player_volume
                        },
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.24f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(visibleIndicator.level.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                when (visibleIndicator.kind) {
                                    FullscreenVideoGestureKind.Brightness ->
                                        MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                },
                            ),
                    )
                }
                Text(
                    text = "${(visibleIndicator.level * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
