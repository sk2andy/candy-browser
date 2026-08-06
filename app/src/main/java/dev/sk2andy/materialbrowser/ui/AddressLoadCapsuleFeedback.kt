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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

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
        val boundedPhase = phase.coerceIn(0f, 1f)
        val start = -INDETERMINATE_SEGMENT_FRACTION +
            boundedPhase * (1f + INDETERMINATE_SEGMENT_FRACTION)
        return AddressLoadSegment(
            start = start.coerceIn(0f, 1f),
            end = (start + INDETERMINATE_SEGMENT_FRACTION).coerceIn(0f, 1f),
        )
    }
}

private data class AddressLoadActiveMotion(
    val travelPhase: Float,
    val breathPhase: Float,
)

@Composable
private fun rememberAddressLoadActiveMotion(): AddressLoadActiveMotion {
    val transition = rememberInfiniteTransition(label = "Address load motion")
    val travelPhase by transition.animateFloat(
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
    val breathPhase by transition.animateFloat(
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
        AddressLoadActiveMotion(travelPhase = 0f, breathPhase = 0f)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress ?: 0f,
        animationSpec = tween(AddressLoadCapsuleRules.PROGRESS_DURATION_MILLIS),
        label = "Address load progress",
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.primaryContainer
    val breath = AddressLoadCapsuleRules.breathAmount(activeMotion.breathPhase)
    val settle = if (state.mode == AddressLoadFeedbackMode.Settling) {
        settleProgress.value.coerceIn(0f, 1f)
    } else {
        0f
    }
    val semanticsModifier = when (state.mode) {
        AddressLoadFeedbackMode.Indeterminate -> Modifier.progressSemantics()
        AddressLoadFeedbackMode.Determinate,
        AddressLoadFeedbackMode.Settling -> Modifier.progressSemantics(animatedProgress)
        AddressLoadFeedbackMode.Hidden -> Modifier
    }

    Box(
        modifier = modifier
            .then(semanticsModifier)
            .drawBehind {
                val settleAlpha = 1f - settle
                val feedbackColor = lerp(primary, tertiary, 0.22f * breath)
                drawRoundRect(
                    color = feedbackColor.copy(
                        alpha = (0.035f + 0.035f * breath) * settleAlpha,
                    ),
                    cornerRadius = CornerRadius(size.height / 2f),
                )

                val horizontalInset = 8.dp.toPx()
                val availableWidth = (size.width - horizontalInset * 2f).coerceAtLeast(0f)
                val indicatorHeight = (3.dp + 2.dp * breath - 1.dp * settle).toPx()
                val indicatorTop = size.height - indicatorHeight
                val indicatorRadius = CornerRadius(indicatorHeight / 2f)
                drawRoundRect(
                    color = track.copy(alpha = 0.52f * settleAlpha),
                    topLeft = Offset(horizontalInset, indicatorTop),
                    size = Size(availableWidth, indicatorHeight),
                    cornerRadius = indicatorRadius,
                )

                val segment = when (state.mode) {
                    AddressLoadFeedbackMode.Indeterminate ->
                        AddressLoadCapsuleRules.indeterminateSegment(activeMotion.travelPhase)
                    AddressLoadFeedbackMode.Determinate,
                    AddressLoadFeedbackMode.Settling ->
                        AddressLoadSegment(start = 0f, end = animatedProgress)
                    AddressLoadFeedbackMode.Hidden -> AddressLoadSegment(0f, 0f)
                }
                val startX = horizontalInset + availableWidth * segment.start
                val endX = horizontalInset + availableWidth * segment.end
                clipRect(
                    left = horizontalInset,
                    right = size.width - horizontalInset,
                    top = indicatorTop,
                    bottom = size.height,
                ) {
                    drawRoundRect(
                        color = feedbackColor.copy(alpha = 0.92f * settleAlpha),
                        topLeft = Offset(startX, indicatorTop),
                        size = Size((endX - startX).coerceAtLeast(0f), indicatorHeight),
                        cornerRadius = indicatorRadius,
                    )
                }
            },
    ) {}
}
