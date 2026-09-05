@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.key
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

internal fun Modifier.longPressTabOverviewReorder(
    enabled: Boolean,
    sessionKey: Any,
    onDragStart: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier = pointerInput(enabled, sessionKey) {
    if (!enabled) return@pointerInput
    var accepted = false
    detectDragGesturesAfterLongPress(
        onDragStart = { position -> accepted = onDragStart(position) },
        onDragEnd = {
            if (accepted) onDragEnd()
            accepted = false
        },
        onDragCancel = {
            if (accepted) onDragCancel()
            accepted = false
        },
        onDrag = { change, dragAmount ->
            if (accepted) {
                change.consume()
                onDrag(dragAmount)
            }
        },
    )
}

@Composable
internal fun HeroTabReorderEdgeAutoScroll(
    sessionId: String?,
    pointerInRoot: Offset?,
    viewportBounds: Rect?,
    canStepBackward: Boolean,
    canStepForward: Boolean,
    onStep: suspend (Int) -> Boolean,
) {
    val density = LocalDensity.current
    val edgeSizePx = with(density) { 72.dp.toPx() }
    val maxSpeedPxPerSecond = with(density) { 1_100.dp.toPx() }
    val currentPointer by rememberUpdatedState(pointerInRoot)
    val currentBounds by rememberUpdatedState(viewportBounds)
    val currentCanStepBackward by rememberUpdatedState(canStepBackward)
    val currentCanStepForward by rememberUpdatedState(canStepForward)
    val currentOnStep by rememberUpdatedState(onStep)
    LaunchedEffect(sessionId) {
        if (sessionId == null) return@LaunchedEffect
        var activeDirection = 0
        var nextStepNanos = Long.MAX_VALUE
        while (true) {
            val frameNanos = withFrameNanos { it }
            val pointer = currentPointer
            val bounds = currentBounds
            val speed = if (pointer == null || bounds == null) {
                0f
            } else {
                TabReorderMotion.edgeScrollSpeed(
                    pointerPx = pointer.x,
                    viewportStartPx = bounds.left,
                    viewportEndPx = bounds.right,
                    edgeSizePx = edgeSizePx,
                    maxSpeedPxPerSecond = maxSpeedPxPerSecond,
                )
            }
            val direction = when {
                speed < 0f && currentCanStepBackward -> -1
                speed > 0f && currentCanStepForward -> 1
                else -> 0
            }
            if (direction == 0) {
                activeDirection = 0
                nextStepNanos = Long.MAX_VALUE
                continue
            }
            if (direction != activeDirection) {
                activeDirection = direction
                nextStepNanos = frameNanos + 180_000_000L
                continue
            }
            if (frameNanos < nextStepNanos) continue
            val moved = currentOnStep(direction)
            nextStepNanos = withFrameNanos { it } + if (moved) {
                24_000_000L
            } else {
                90_000_000L
            }
        }
    }
}

@Composable
internal fun TabReorderEdgeAutoScroll(
    sessionId: String?,
    pointerInRoot: Offset?,
    viewportBounds: Rect?,
    orientation: Orientation,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    scrollBy: suspend (Float) -> Float,
    onScrolled: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val edgeSizePx = with(density) { 96.dp.toPx() }
    val maxSpeedPxPerSecond = with(density) { 1_800.dp.toPx() }
    val currentPointer by rememberUpdatedState(pointerInRoot)
    val currentBounds by rememberUpdatedState(viewportBounds)
    val currentCanScrollBackward by rememberUpdatedState(canScrollBackward)
    val currentCanScrollForward by rememberUpdatedState(canScrollForward)
    val currentScrollBy by rememberUpdatedState(scrollBy)
    val currentOnScrolled by rememberUpdatedState(onScrolled)
    LaunchedEffect(sessionId, orientation) {
        if (sessionId == null) return@LaunchedEffect
        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = ((frameNanos - previousFrameNanos) / 1_000_000_000f)
                .coerceIn(0f, 0.032f)
            previousFrameNanos = frameNanos
            val pointer = currentPointer ?: continue
            val bounds = currentBounds ?: continue
            val pointerAxis = if (orientation == Orientation.Horizontal) pointer.x else pointer.y
            val startAxis = if (orientation == Orientation.Horizontal) bounds.left else bounds.top
            val endAxis = if (orientation == Orientation.Horizontal) bounds.right else bounds.bottom
            var speed = TabReorderMotion.edgeScrollSpeed(
                pointerPx = pointerAxis,
                viewportStartPx = startAxis,
                viewportEndPx = endAxis,
                edgeSizePx = edgeSizePx,
                maxSpeedPxPerSecond = maxSpeedPxPerSecond,
            )
            if (speed < 0f && !currentCanScrollBackward) speed = 0f
            if (speed > 0f && !currentCanScrollForward) speed = 0f
            if (speed == 0f) continue
            val consumed = currentScrollBy(speed * elapsedSeconds)
            if (consumed.absoluteValue > 0.01f) currentOnScrolled(consumed)
        }
    }
}

@Composable
internal fun Modifier.tabReorderVisualMotion(
    sessionId: String?,
    isDragged: Boolean,
    targetOffset: Offset,
): Modifier {
    val animatedOffset = key(sessionId) {
        animateValueAsState(
            targetValue = if (isDragged) Offset.Zero else targetOffset,
            typeConverter = Offset.VectorConverter,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f),
            label = "tab-reorder-slot",
        )
    }
    return graphicsLayer {
        val offset = if (isDragged) Offset.Zero else animatedOffset.value
        translationX = offset.x
        translationY = offset.y
        alpha = if (isDragged) 0f else 1f
    }
}

@Composable
internal fun DraggedTabReorderOverlay(
    reorder: ActiveTabReorder,
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    favorites: List<FavoriteEntry>,
    selected: Boolean,
    rootTopLeft: Offset,
) {
    val density = LocalDensity.current
    val width = with(density) { reorder.sourceBounds.width.toDp() }
    val height = with(density) { reorder.sourceBounds.height.toDp() }
    val shape = when (reorder.mode) {
        TabOverviewMode.Hero -> RoundedCornerShape(28.dp)
        TabOverviewMode.Grid -> RoundedCornerShape(22.dp)
        TabOverviewMode.List -> RoundedCornerShape(18.dp)
    }
    val lift by animateFloatAsState(
        targetValue = if (reorder.lifted) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
        label = "tab-reorder-overlay-lift",
    )
    val elevation = (2f + lift * 5f).dp
    Box(
        modifier = Modifier
            .offset {
                val topLeft = reorder.sourceBounds.topLeft + reorder.dragOffset - rootTopLeft
                IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
            }
            .size(width, height)
            .zIndex(30f)
            .graphicsLayer {
                val scale = 1f + lift * 0.018f
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
            )
            .clearAndSetSemantics { },
    ) {
        when (reorder.mode) {
            TabOverviewMode.Hero -> Surface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                TabPreviewContent(
                    tab = tab,
                    preview = preview,
                    favicon = favicon,
                    favorites = favorites,
                )
            }
            TabOverviewMode.Grid -> Surface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                border = if (selected) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
            ) {
                Box(Modifier.fillMaxSize()) {
                    TabPreviewContent(
                        tab = tab,
                        preview = preview,
                        favicon = favicon,
                        favorites = favorites,
                    )
                    GridTabPreviewChrome(
                        tab = tab,
                        favicon = favicon,
                        interactionsEnabled = false,
                        onClose = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            TabOverviewMode.List -> Surface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.98f)
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabFavicon(tab = tab, favicon = favicon, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            displayTabTitle(tab),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        )
                        Text(
                            if (tab.url == BLANK_URL) {
                                stringResource(R.string.new_tab_title)
                            } else {
                                AddressResolver.displayText(tab.url)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (tab.isPinned) {
                        Icon(
                            painter = painterResource(R.drawable.ic_push_pin),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 15.dp)
                                .size(20.dp),
                        )
                    } else {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 15.dp)
                                .size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

