package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal class StartPageSearchTransformState {
    val progress = Animatable(0f)

    var sourceBounds by mutableStateOf<Rect?>(null)
        private set
    var targetBounds by mutableStateOf<Rect?>(null)
        private set

    val hasSourceBounds: Boolean get() = sourceBounds != null
    val hasTargetBounds: Boolean get() = targetBounds != null

    fun updateSource(coordinates: LayoutCoordinates) {
        coordinates.boundsInRoot().takeIf(StartPageSearchTransformRules::isValidBounds)?.let {
            sourceBounds = it
        }
    }

    fun updateTarget(coordinates: LayoutCoordinates) {
        coordinates.boundsInRoot().takeIf(StartPageSearchTransformRules::isValidBounds)?.let {
            targetBounds = it
        }
    }

    suspend fun animate(editing: Boolean, enabled: Boolean) {
        if (!enabled) {
            progress.snapTo(0f)
            return
        }
        if (editing && (sourceBounds == null || targetBounds == null)) {
            delay(StartPageSearchTransformRules.BOUNDS_WAIT_MILLIS)
            if (sourceBounds == null || targetBounds == null) {
                progress.snapTo(1f)
                return
            }
        }
        progress.animateTo(
            targetValue = if (editing) 1f else 0f,
            animationSpec = tween(
                durationMillis = StartPageSearchTransformRules.DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )
    }
}

internal object StartPageSearchTransformRules {
    const val DURATION_MILLIS = 220
    const val BOUNDS_WAIT_MILLIS = 96L

    fun isValidBounds(bounds: Rect): Boolean =
        bounds.left.isFinite() &&
            bounds.top.isFinite() &&
            bounds.right.isFinite() &&
            bounds.bottom.isFinite() &&
            bounds.width > 0f &&
            bounds.height > 0f

    fun bounds(source: Rect, target: Rect, progress: Float): Rect {
        val fraction = progress.coerceIn(0f, 1f)
        return Rect(
            left = lerp(source.left, target.left, fraction),
            top = lerp(source.top, target.top, fraction),
            right = lerp(source.right, target.right, fraction),
            bottom = lerp(source.bottom, target.bottom, fraction),
        )
    }

    fun cornerRadius(progress: Float): Float = lerp(
        START_CORNER_RADIUS_DP,
        END_CORNER_RADIUS_DP,
        progress.coerceIn(0f, 1f),
    )

    fun shadowElevation(progress: Float): Float =
        START_SHADOW_ELEVATION_DP * (1f - progress.coerceIn(0f, 1f))

    fun sourceVisible(editing: Boolean, progress: Float): Boolean =
        !editing && progress <= COMPLETION_THRESHOLD

    fun targetContentAlpha(progress: Float): Float =
        ((progress.coerceIn(0f, 1f) - TARGET_CONTENT_START) / (1f - TARGET_CONTENT_START))
            .coerceIn(0f, 1f)

    fun targetContainerAlpha(editing: Boolean, progress: Float): Float = if (editing) {
        targetContentAlpha(progress)
    } else {
        ((REVERSE_TARGET_END - progress.coerceIn(0f, 1f)) / REVERSE_TARGET_END)
            .coerceIn(0f, 1f)
    }

    fun shouldUpdateTargetBounds(editing: Boolean, progress: Float): Boolean =
        editing || progress <= COMPLETION_THRESHOLD

    fun sourceIconAlpha(progress: Float): Float =
        (1f - progress.coerceIn(0f, 1f) / SOURCE_ICON_END).coerceIn(0f, 1f)

    fun searchIconAlpha(progress: Float): Float =
        ((progress.coerceIn(0f, 1f) - SEARCH_ICON_START) / (1f - SEARCH_ICON_START))
            .coerceIn(0f, 1f)

    fun overlayAlpha(editing: Boolean, progress: Float): Float = when {
        !editing && progress <= COMPLETION_THRESHOLD -> 0f
        editing -> 1f - targetContentAlpha(progress)
        else -> 1f
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction

    private const val START_CORNER_RADIUS_DP = 48f
    private const val END_CORNER_RADIUS_DP = 22f
    private const val START_SHADOW_ELEVATION_DP = 14f
    private const val TARGET_CONTENT_START = 0.76f
    private const val REVERSE_TARGET_END = 0.18f
    private const val SOURCE_ICON_END = 0.52f
    private const val SEARCH_ICON_START = 0.34f
    private const val COMPLETION_THRESHOLD = 0.005f
}

@Composable
internal fun StartPageSearchTransformOverlay(
    state: StartPageSearchTransformState,
    editing: Boolean,
    incognito: Boolean,
    modifier: Modifier = Modifier,
) {
    val source = state.sourceBounds ?: return
    val target = state.targetBounds ?: source
    val progress = state.progress.value.coerceIn(0f, 1f)
    val bounds = StartPageSearchTransformRules.bounds(source, target, progress)
    val colors = MaterialTheme.colorScheme
    val sourceColor = if (incognito) colors.inverseSurface else colors.primary
    val containerColor = lerp(sourceColor, colors.surfaceContainerLowest, progress)
    val sourceIconSize = if (incognito) 48.dp else 68.dp
    val sourceIconAlpha = StartPageSearchTransformRules.sourceIconAlpha(progress)
    val searchIconAlpha = StartPageSearchTransformRules.searchIconAlpha(progress)
    val overlayAlpha = StartPageSearchTransformRules.overlayAlpha(editing, progress)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val width = with(density) { bounds.width.toDp() }
    val height = with(density) { bounds.height.toDp() }
    val searchCenterX = with(density) { SEARCH_ICON_CENTER_X_DP.dp.toPx() }
        .coerceAtMost(bounds.width / 2f)
    val iconCenterX = lerp(bounds.width / 2f, searchCenterX, progress)
    val iconCenterY = bounds.height / 2f

    Surface(
        modifier = modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(width, height)
            .graphicsLayer { alpha = overlayAlpha }
            .clearAndSetSemantics { },
        shape = RoundedCornerShape(StartPageSearchTransformRules.cornerRadius(progress).dp),
        color = containerColor,
        shadowElevation = StartPageSearchTransformRules.shadowElevation(progress).dp,
    ) {
        Box {
            Icon(
                painter = painterResource(
                    if (incognito) {
                        R.drawable.ic_incognito_filled
                    } else {
                        R.drawable.ic_launcher_foreground_art
                    },
                ),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            x = (iconCenterX - with(density) { sourceIconSize.toPx() } / 2f)
                                .roundToInt(),
                            y = (iconCenterY - with(density) { sourceIconSize.toPx() } / 2f)
                                .roundToInt(),
                        )
                    }
                    .size(sourceIconSize)
                    .graphicsLayer {
                        alpha = sourceIconAlpha
                        val scale = 1f - 0.45f * progress
                        scaleX = scale
                        scaleY = scale
                    },
                tint = if (incognito) colors.inverseOnSurface else Color.Unspecified,
            )
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val iconSizePx = with(density) { SEARCH_ICON_SIZE_DP.dp.toPx() }
                        IntOffset(
                            x = (iconCenterX - iconSizePx / 2f).roundToInt(),
                            y = (iconCenterY - iconSizePx / 2f).roundToInt(),
                        )
                    }
                    .size(SEARCH_ICON_SIZE_DP.dp)
                    .graphicsLayer { alpha = searchIconAlpha },
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private const val SEARCH_ICON_CENTER_X_DP = 26f
private const val SEARCH_ICON_SIZE_DP = 24f
