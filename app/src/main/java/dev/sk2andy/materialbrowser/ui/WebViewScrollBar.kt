package dev.sk2andy.materialbrowser.ui

import android.view.ViewTreeObserver
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
import androidx.compose.runtime.DisposableEffect
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
import dev.sk2andy.materialbrowser.browser.BrowserWebView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class WebViewScrollMetrics(
    val scrollY: Int = 0,
    val viewportHeightPx: Int = 0,
    val contentHeightPx: Int = 0,
)

@Composable
internal fun WebViewScrollBar(
    webView: BrowserWebView,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var metrics by remember(webView) { mutableStateOf(WebViewScrollMetrics()) }
    var revealNonce by remember(webView) { mutableIntStateOf(0) }
    var isDragging by remember(webView) { mutableStateOf(false) }
    var dragTargetScrollY by remember(webView) { mutableIntStateOf(0) }
    val alpha = remember(webView) { Animatable(0f) }

    DisposableEffect(webView) {
        val observer = webView.viewTreeObserver
        val nativeScrollBarWasEnabled = webView.isVerticalScrollBarEnabled
        webView.isVerticalScrollBarEnabled = false
        val updateMetrics = {
            metrics = webView.scrollMetrics()
        }
        val scrollListener = ViewTreeObserver.OnScrollChangedListener {
            updateMetrics()
            revealNonce++
        }
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener(updateMetrics)
        val initialUpdate = Runnable(updateMetrics)
        observer.addOnScrollChangedListener(scrollListener)
        observer.addOnGlobalLayoutListener(layoutListener)
        webView.post(initialUpdate)
        onDispose {
            webView.removeCallbacks(initialUpdate)
            if (observer.isAlive) {
                observer.removeOnScrollChangedListener(scrollListener)
                observer.removeOnGlobalLayoutListener(layoutListener)
            }
            webView.isVerticalScrollBarEnabled = nativeScrollBarWasEnabled
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(SCROLL_BAR_TOUCH_WIDTH)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val geometry = WebViewScrollBarRules.geometry(
            scrollY = metrics.scrollY,
            viewportHeightPx = metrics.viewportHeightPx,
            contentHeightPx = metrics.contentHeightPx,
            trackHeightPx = trackHeightPx,
            minimumThumbHeightPx = with(density) { MINIMUM_THUMB_HEIGHT.toPx() },
        )
        val currentGeometry by rememberUpdatedState(geometry)
        val currentMetrics by rememberUpdatedState(metrics)

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
            val scrollFraction = metrics.scrollY.toFloat()
                .div(geometry.scrollRangePx.toFloat())
                .coerceIn(0f, 1f)
            val description = stringResource(R.string.scroll_bar_content_description)
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = geometry.thumbTopPx.roundToInt()) }
                    .width(SCROLL_BAR_TOUCH_WIDTH)
                    .height(with(density) { geometry.thumbHeightPx.toDp() })
                    .semantics {
                        contentDescription = description
                        progressBarRangeInfo = ProgressBarRangeInfo(scrollFraction, 0f..1f)
                        setProgress { targetFraction ->
                            webView.scrollToVerticalOffset(
                                (targetFraction.coerceIn(0f, 1f) * geometry.scrollRangePx)
                                    .roundToInt(),
                            )
                            true
                        }
                    }
                    .draggable(
                        state = rememberDraggableState { delta ->
                            val activeGeometry = currentGeometry ?: return@rememberDraggableState
                            dragTargetScrollY = WebViewScrollBarRules.scrollYAfterDrag(
                                currentScrollY = dragTargetScrollY,
                                dragDeltaPx = delta,
                                geometry = activeGeometry,
                            )
                            webView.scrollToVerticalOffset(dragTargetScrollY)
                        },
                        orientation = Orientation.Vertical,
                        enabled = alpha.value > 0f || isDragging,
                        onDragStarted = {
                            dragTargetScrollY = currentMetrics.scrollY
                            isDragging = true
                            revealNonce++
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

private fun BrowserWebView.scrollMetrics(): WebViewScrollMetrics {
    val metrics = scrollMetricsSnapshot()
    return WebViewScrollMetrics(
        scrollY = metrics.offsetPx,
        viewportHeightPx = metrics.extentPx,
        contentHeightPx = metrics.rangePx,
    )
}

private val SCROLL_BAR_TOUCH_WIDTH = 28.dp
private val THUMB_WIDTH = 5.dp
private val MINIMUM_THUMB_HEIGHT = 48.dp
private const val HIDE_DELAY_MILLIS = 700L
private const val FADE_DURATION_MILLIS = 220
