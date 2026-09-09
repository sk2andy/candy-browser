package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollMetrics
import dev.sk2andy.materialbrowser.browser.BrowserScrollBarRules
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun BrowserScrollBar(
    metrics: BrowserEngineScrollMetrics?,
    revealNonce: Int,
    onScrollToVerticalOffset: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var dragTargetOffsetPx by remember { mutableIntStateOf(0) }
    val alpha = remember { Animatable(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(SCROLL_BAR_TOUCH_WIDTH)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val geometry = metrics?.let { snapshot ->
            BrowserScrollBarRules.geometry(
                metrics = snapshot,
                trackLengthPx = with(density) { maxHeight.toPx() },
                minimumThumbLengthPx = with(density) { MINIMUM_THUMB_LENGTH.toPx() },
            )
        }
        val currentGeometry by rememberUpdatedState(geometry)
        val currentMetrics by rememberUpdatedState(metrics)
        val currentOnScrollToVerticalOffset by rememberUpdatedState(onScrollToVerticalOffset)

        LaunchedEffect(revealNonce, isDragging, geometry != null) {
            if (geometry == null || revealNonce == 0 && !isDragging) {
                alpha.snapTo(0f)
                return@LaunchedEffect
            }
            alpha.snapTo(1f)
            if (!isDragging) {
                delay(HIDE_DELAY_MILLIS)
                alpha.animateTo(0f, tween(FADE_DURATION_MILLIS))
            }
        }

        if (geometry != null) {
            val activeMetrics = requireNotNull(metrics)
            val scrollFraction = activeMetrics.offsetPx.toFloat()
                .div(geometry.scrollRangePx.toFloat())
                .coerceIn(0f, 1f)
            val description = stringResource(R.string.scroll_bar_content_description)
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = geometry.thumbOffsetPx.roundToInt()) }
                    .width(SCROLL_BAR_TOUCH_WIDTH)
                    .height(with(density) { geometry.thumbLengthPx.toDp() })
                    .semantics {
                        contentDescription = description
                        progressBarRangeInfo = ProgressBarRangeInfo(scrollFraction, 0f..1f)
                        setProgress { targetFraction ->
                            currentOnScrollToVerticalOffset(
                                (targetFraction.coerceIn(0f, 1f) * geometry.scrollRangePx)
                                    .roundToInt(),
                            )
                            true
                        }
                    }
                    .draggable(
                        state = rememberDraggableState { delta ->
                            val activeGeometry = currentGeometry
                                ?: return@rememberDraggableState
                            dragTargetOffsetPx = BrowserScrollBarRules.scrollOffsetAfterDrag(
                                currentOffsetPx = dragTargetOffsetPx,
                                dragDeltaPx = delta,
                                geometry = activeGeometry,
                            )
                            currentOnScrollToVerticalOffset(dragTargetOffsetPx)
                        },
                        orientation = Orientation.Vertical,
                        enabled = alpha.value > 0f || isDragging,
                        onDragStarted = {
                            dragTargetOffsetPx = currentMetrics?.offsetPx ?: 0
                            isDragging = true
                        },
                        onDragStopped = { isDragging = false },
                    )
                    .graphicsLayer { this.alpha = alpha.value },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(THUMB_WIDTH)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

private val SCROLL_BAR_TOUCH_WIDTH = 28.dp
private val THUMB_WIDTH = 5.dp
private val MINIMUM_THUMB_LENGTH = 48.dp
private const val HIDE_DELAY_MILLIS = 700L
private const val FADE_DURATION_MILLIS = 220
