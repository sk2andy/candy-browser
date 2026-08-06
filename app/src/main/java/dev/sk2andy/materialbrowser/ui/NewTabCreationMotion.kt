package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

internal data class NewTabCreationFrame(
    val center: Offset,
    val width: Float,
    val height: Float,
    val cornerFraction: Float,
    val coreScale: Float,
    val alpha: Float,
)

internal object NewTabCreationMotionRules {
    const val DURATION_MILLIS = 300
    const val FALLBACK_DURATION_MILLIS = 180

    fun durationMillis(hasSourceBounds: Boolean): Int =
        if (hasSourceBounds) DURATION_MILLIS else FALLBACK_DURATION_MILLIS

    fun hasUsableBounds(bounds: Rect?): Boolean = bounds != null &&
        bounds.left.isFinite() &&
        bounds.top.isFinite() &&
        bounds.right.isFinite() &&
        bounds.bottom.isFinite() &&
        bounds.width > 0f &&
        bounds.height > 0f

    fun projection(
        sourceBounds: Rect?,
        destinationBounds: Rect,
        seedSize: Float,
        progress: Float,
    ): NewTabCreationFrame {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val destinationCenter = destinationBounds.center
        val sourceCenter = sourceBounds
            ?.takeIf(::hasUsableBounds)
            ?.center
            ?: destinationCenter
        val travelProgress = phase(clampedProgress, start = 0f, end = 0.64f)
        val unfoldProgress = phase(clampedProgress, start = 0.24f, end = 0.82f)
        val settleProgress = phase(clampedProgress, start = 0.72f, end = 1f)
        val fadeProgress = phase(clampedProgress, start = 0.86f, end = 1f)
        val safeSeedSize = seedSize.coerceAtLeast(0f)

        return NewTabCreationFrame(
            center = Offset(
                x = lerp(sourceCenter.x, destinationCenter.x, travelProgress),
                y = lerp(sourceCenter.y, destinationCenter.y, travelProgress),
            ),
            width = lerp(safeSeedSize, destinationBounds.width.coerceAtLeast(0f), unfoldProgress),
            height = lerp(safeSeedSize, destinationBounds.height.coerceAtLeast(0f), unfoldProgress),
            cornerFraction = lerp(0.5f, 0.28f, unfoldProgress) + 0.22f * settleProgress,
            coreScale = lerp(1f, 0.88f, unfoldProgress) + 0.12f * settleProgress,
            alpha = 1f - fadeProgress,
        )
    }

    private fun phase(progress: Float, start: Float, end: Float): Float {
        val linear = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        return linear * linear * (3f - 2f * linear)
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress
}

internal data class NewTabCreationRequest(
    val id: Long,
    val sourceBounds: Rect?,
    val destinationBounds: Rect?,
    val isIncognito: Boolean,
)

internal class NewTabCreationMotionController {
    var request by mutableStateOf<NewTabCreationRequest?>(null)
        private set

    private var nextRequestId = 0L

    fun launch(
        sourceBounds: Rect?,
        destinationBounds: Rect?,
        isIncognito: Boolean,
    ) {
        request = NewTabCreationRequest(
            id = ++nextRequestId,
            sourceBounds = sourceBounds.takeIf(NewTabCreationMotionRules::hasUsableBounds),
            destinationBounds = destinationBounds.takeIf(NewTabCreationMotionRules::hasUsableBounds),
            isIncognito = isIncognito,
        )
    }

    fun updateDestination(bounds: Rect) {
        if (!NewTabCreationMotionRules.hasUsableBounds(bounds)) return
        val activeRequest = request ?: return
        if (activeRequest.destinationBounds != bounds) {
            request = activeRequest.copy(destinationBounds = bounds)
        }
    }

    fun complete(requestId: Long) {
        if (request?.id == requestId) request = null
    }
}

@Composable
internal fun rememberNewTabCreationMotionController(): NewTabCreationMotionController =
    remember { NewTabCreationMotionController() }

@Composable
internal fun NewTabCreationMotionHost(
    controller: NewTabCreationMotionController,
    modifier: Modifier = Modifier,
) {
    val request = controller.request ?: return
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember(request.id) { Animatable(0f) }
    val destinationSizePx = with(density) { 96.dp.toPx() }
    val seedSizePx = with(density) { 14.dp.toPx() }
    val fallbackDestinationBounds = Rect(
        left = (containerSize.width - destinationSizePx) / 2f,
        top = (containerSize.height - destinationSizePx) / 2f,
        right = (containerSize.width + destinationSizePx) / 2f,
        bottom = (containerSize.height + destinationSizePx) / 2f,
    )
    val destinationBounds = request.destinationBounds ?: fallbackDestinationBounds
    val frame = NewTabCreationMotionRules.projection(
        sourceBounds = request.sourceBounds,
        destinationBounds = destinationBounds,
        seedSize = seedSizePx,
        progress = progress.value,
    )
    val colors = MaterialTheme.colorScheme
    val outerColor = if (request.isIncognito) colors.inverseSurface else colors.primary
    val coreColor = if (request.isIncognito) {
        colors.inverseOnSurface
    } else {
        colors.tertiaryContainer
    }

    LaunchedEffect(request.id) {
        snapshotFlow { containerSize }.first { it != IntSize.Zero }
        try {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = NewTabCreationMotionRules.durationMillis(
                        hasSourceBounds = request.sourceBounds != null,
                    ),
                    easing = FastOutSlowInEasing,
                ),
            )
        } finally {
            controller.complete(request.id)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(30f)
            .onSizeChanged { containerSize = it }
            .clearAndSetSemantics { },
    ) {
        if (containerSize != IntSize.Zero) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (frame.center.x - frame.width / 2f).roundToInt(),
                            y = (frame.center.y - frame.height / 2f).roundToInt(),
                        )
                    }
                    .size(
                        width = with(density) { frame.width.toDp() },
                        height = with(density) { frame.height.toDp() },
                    )
                    .graphicsLayer {
                        alpha = frame.alpha
                        shape = RoundedCornerShape(
                            with(density) { (frame.width * frame.cornerFraction).toDp() },
                        )
                        clip = true
                        shadowElevation = with(density) { 8.dp.toPx() } * frame.alpha
                    }
                    .background(outerColor),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            width = with(density) { (frame.width * 0.58f).toDp() },
                            height = with(density) { (frame.height * 0.58f).toDp() },
                        )
                        .graphicsLayer {
                            scaleX = frame.coreScale
                            scaleY = frame.coreScale
                            shape = RoundedCornerShape(42)
                            clip = true
                        }
                        .background(coreColor),
                )
            }
        }
    }
}
