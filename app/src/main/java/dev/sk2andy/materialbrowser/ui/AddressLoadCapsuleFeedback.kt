package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.ui.theme.CandyPink
import dev.sk2andy.materialbrowser.ui.theme.CandyPurple
import kotlin.math.floor

internal enum class AddressLoadFeedbackMode {
    Hidden,
    Indeterminate,
    Determinate,
    Settling,
}

internal data class AddressLoadFeedbackState(
    val mode: AddressLoadFeedbackMode,
    val progress: Float? = null,
)

internal data class AddressLoadSegment(
    val start: Float,
    val end: Float,
)

internal object AddressLoadCapsuleRules {
    const val ACTIVE_TRAVEL_DURATION_MILLIS = 1_100
    const val ACTIVE_BREATH_DURATION_MILLIS = 650
    const val PROGRESS_DURATION_MILLIS = 80
    const val SETTLE_DURATION_MILLIS = 280

    private const val INDETERMINATE_SEGMENT_FRACTION = 0.32f

    fun resolve(
        isLoading: Boolean,
        progressPercent: Int,
        observedActiveLoad: Boolean,
    ): AddressLoadFeedbackState = when {
        isLoading && progressPercent <= 0 -> AddressLoadFeedbackState(
            mode = AddressLoadFeedbackMode.Indeterminate,
        )
        isLoading -> AddressLoadFeedbackState(
            mode = AddressLoadFeedbackMode.Determinate,
            progress = (progressPercent / 100f).coerceIn(0f, 1f),
        )
        shouldSettle(observedActiveLoad, isLoading, progressPercent) -> AddressLoadFeedbackState(
            mode = AddressLoadFeedbackMode.Settling,
            progress = 1f,
        )
        else -> AddressLoadFeedbackState(mode = AddressLoadFeedbackMode.Hidden)
    }

    fun shouldSettle(
        observedActiveLoad: Boolean,
        isLoading: Boolean,
        progressPercent: Int,
    ): Boolean = observedActiveLoad && !isLoading && progressPercent >= 100

    fun breathAmount(phase: Float): Float {
        val boundedPhase = phase.coerceIn(0f, 1f)
        return 4f * boundedPhase * (1f - boundedPhase)
    }

    fun indeterminateSegment(phase: Float): AddressLoadSegment {
        val start = phase.coerceIn(0f, 1f)
        return AddressLoadSegment(
            start = start,
            end = (start + INDETERMINATE_SEGMENT_FRACTION).coerceAtMost(1f),
        )
    }

    fun indeterminateSegments(phase: Float): List<AddressLoadSegment> {
        val first = indeterminateSegment(phase)
        val wrappedLength = INDETERMINATE_SEGMENT_FRACTION - (first.end - first.start)
        return buildList {
            if (first.end > first.start) add(first)
            if (wrappedLength > 0f) {
                add(AddressLoadSegment(start = 0f, end = wrappedLength))
            }
        }
    }
}

private val AddressLoadRainbow = listOf(
    CandyPink,
    Color(0xFFFF6B35),
    Color(0xFFFFC857),
    Color(0xFF55D187),
    Color(0xFF2EC4B6),
    Color(0xFF3A86FF),
    CandyPurple,
)

private fun shiftedRainbowColors(phase: Float): List<Color> {
    val shift = phase.coerceIn(0f, 1f) * AddressLoadRainbow.size
    val shiftFloor = floor(shift)
    val startIndex = shiftFloor.toInt() % AddressLoadRainbow.size
    val fraction = shift - shiftFloor
    return List(AddressLoadRainbow.size + 1) { index ->
        val colorIndex = (startIndex + index) % AddressLoadRainbow.size
        lerp(
            AddressLoadRainbow[colorIndex],
            AddressLoadRainbow[(colorIndex + 1) % AddressLoadRainbow.size],
            fraction,
        )
    }
}

private data class AddressLoadActiveMotion(
    val travelPhase: State<Float>,
    val breathPhase: State<Float>,
)

@Composable
private fun rememberAddressLoadActiveMotion(): AddressLoadActiveMotion {
    val transition = rememberInfiniteTransition(label = "Address load motion")
    val travelPhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AddressLoadCapsuleRules.ACTIVE_TRAVEL_DURATION_MILLIS,
                easing = LinearEasing,
            ),
        ),
        label = "Address load travel",
    )
    val breathPhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AddressLoadCapsuleRules.ACTIVE_BREATH_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Address load breath",
    )
    return AddressLoadActiveMotion(travelPhase, breathPhase)
}

@Composable
internal fun AddressLoadCapsuleFeedback(
    tabId: String,
    isLoading: Boolean,
    progressPercent: Int,
    morphProgress: Float,
    morphTargetSizePx: Float,
    modifier: Modifier = Modifier,
) {
    var observedActiveLoad by remember(tabId) { mutableStateOf(isLoading) }
    val settleProgress = remember(tabId) { Animatable(0f) }
    val loadCompleted = progressPercent >= 100
    val state = AddressLoadCapsuleRules.resolve(
        isLoading = isLoading,
        progressPercent = progressPercent,
        observedActiveLoad = observedActiveLoad,
    )

    LaunchedEffect(tabId, isLoading, loadCompleted) {
        if (isLoading) {
            observedActiveLoad = true
            settleProgress.snapTo(0f)
        } else if (observedActiveLoad) {
            settleProgress.snapTo(0f)
            if (loadCompleted) {
                settleProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = AddressLoadCapsuleRules.SETTLE_DURATION_MILLIS,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
            observedActiveLoad = false
        }
    }

    if (state.mode == AddressLoadFeedbackMode.Hidden) return

    val activeMotion = if (isLoading) {
        rememberAddressLoadActiveMotion()
    } else {
        remember {
            AddressLoadActiveMotion(
                travelPhase = mutableFloatStateOf(0f),
                breathPhase = mutableFloatStateOf(0f),
            )
        }
    }
    val animatedProgress = animateFloatAsState(
        targetValue = state.progress ?: 0f,
        animationSpec = tween(AddressLoadCapsuleRules.PROGRESS_DURATION_MILLIS),
        label = "Address load progress",
    )
    val semanticsModifier = when (state.mode) {
        AddressLoadFeedbackMode.Indeterminate -> Modifier.progressSemantics()
        AddressLoadFeedbackMode.Determinate,
        AddressLoadFeedbackMode.Settling -> Modifier.progressSemantics(animatedProgress.value)
        AddressLoadFeedbackMode.Hidden -> Modifier
    }

    Box(
        modifier = modifier
            .then(semanticsModifier)
            .drawWithCache {
                val outlineInset = 4.dp.toPx()
                val outlineBounds = Rect(
                    left = outlineInset,
                    top = outlineInset,
                    right = size.width - outlineInset,
                    bottom = size.height - outlineInset,
                )
                if (outlineBounds.width <= 0f || outlineBounds.height <= 0f) {
                    return@drawWithCache onDrawBehind {}
                }
                val morphRadii = AddressBarOverviewGestureRules.morphCornerRadii(
                    progress = morphProgress,
                    sourceWidth = size.width,
                    sourceHeight = size.height,
                    targetSize = morphTargetSizePx,
                )
                val outlineCornerRadius = CornerRadius(
                    x = (morphRadii.horizontal - outlineInset).coerceAtLeast(0f),
                    y = (morphRadii.vertical - outlineInset).coerceAtLeast(0f),
                )
                val outlinePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = outlineBounds.left,
                            top = outlineBounds.top,
                            right = outlineBounds.right,
                            bottom = outlineBounds.bottom,
                            topLeftCornerRadius = outlineCornerRadius,
                            topRightCornerRadius = outlineCornerRadius,
                            bottomRightCornerRadius = outlineCornerRadius,
                            bottomLeftCornerRadius = outlineCornerRadius,
                        ),
                    )
                }
                val pathMeasure = PathMeasure().apply {
                    setPath(outlinePath, forceClosed = true)
                }
                val segmentPath = Path()
                onDrawBehind {
                    val breath = AddressLoadCapsuleRules.breathAmount(
                        activeMotion.breathPhase.value,
                    )
                    val settle = if (state.mode == AddressLoadFeedbackMode.Settling) {
                        settleProgress.value.coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val settleAlpha = 1f - settle
                    val bandWidth = (3.dp + 1.dp * breath - 0.5.dp * settle).toPx()
                    val rainbowBrush = Brush.sweepGradient(
                        colors = shiftedRainbowColors(activeMotion.travelPhase.value),
                        center = center,
                    )
                    drawPath(
                        path = outlinePath,
                        brush = rainbowBrush,
                        alpha = (0.08f + 0.04f * breath) * settleAlpha,
                        style = Stroke(width = bandWidth + 4.dp.toPx()),
                    )
                    drawPath(
                        path = outlinePath,
                        brush = rainbowBrush,
                        alpha = 0.24f * settleAlpha,
                        style = Stroke(width = bandWidth),
                    )

                    val segments = when (state.mode) {
                        AddressLoadFeedbackMode.Indeterminate -> AddressLoadCapsuleRules
                            .indeterminateSegments(activeMotion.travelPhase.value)
                        AddressLoadFeedbackMode.Determinate,
                        AddressLoadFeedbackMode.Settling -> listOf(
                            AddressLoadSegment(start = 0f, end = animatedProgress.value),
                        )
                        AddressLoadFeedbackMode.Hidden -> emptyList()
                    }
                    segments.forEach { segment ->
                        if (segment.end <= segment.start) return@forEach
                        segmentPath.reset()
                        pathMeasure.getSegment(
                            startDistance = pathMeasure.length * segment.start,
                            stopDistance = pathMeasure.length * segment.end,
                            destination = segmentPath,
                            startWithMoveTo = true,
                        )
                        drawPath(
                            path = segmentPath,
                            brush = rainbowBrush,
                            alpha = 0.96f * settleAlpha,
                            style = Stroke(
                                width = bandWidth,
                                cap = StrokeCap.Round,
                            ),
                        )
                    }
                }
            },
    ) {}
}
