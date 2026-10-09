package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

internal class BrowserMainMenuPositionProvider(
    private val containerHeight: Int,
    private val topInset: Int,
    private val bottomInset: Int,
    private val verticalMargin: Int,
    private val downwardOffset: Int,
    private val onPositionCalculated: (IntRect, IntRect) -> Unit = { _, _ -> },
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        // Keep Material's start/end/window-edge horizontal candidates unchanged.
        val horizontalCandidates = if (layoutDirection == LayoutDirection.Ltr) {
            listOf(anchorBounds.left, anchorBounds.right - width)
        } else {
            listOf(anchorBounds.right - width, anchorBounds.left)
        }
        val edgeX = if (width >= windowSize.width) {
            (windowSize.width - width) / 2
        } else if (anchorBounds.center.x < windowSize.width / 2) {
            0
        } else {
            windowSize.width - width
        }
        val x = horizontalCandidates.firstOrNull { it >= 0 && it + width <= windowSize.width }
            ?: edgeX
        val verticalCandidates = listOf(
            anchorBounds.bottom,
            anchorBounds.top - height,
            anchorBounds.top - height / 2,
        )
        val edgeY = if (height >= windowSize.height - 2 * verticalMargin) {
            (windowSize.height - height) / 2
        } else if (anchorBounds.center.y < windowSize.height / 2) {
            verticalMargin
        } else {
            windowSize.height - verticalMargin - height
        }
        val originalY = verticalCandidates.firstOrNull {
            it >= verticalMargin && it + height <= windowSize.height - verticalMargin
        } ?: edgeY
        // Compose's visible window can exclude system bars even for an edge-to-edge Activity.
        // Apply the shift after Material's fallback, then bound it by the actual container/insets.
        val minimumY = maxOf(verticalMargin, topInset)
        val maximumY = containerHeight - maxOf(verticalMargin, bottomInset) - height
        val y = if (maximumY >= minimumY) {
            (originalY + downwardOffset).coerceIn(minimumY, maximumY)
        } else {
            minimumY
        }
        val position = IntOffset(x, y)
        onPositionCalculated(anchorBounds, IntRect(position, popupContentSize))
        return position
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BrowserMainMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onPopupStateChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    val scrollState = rememberScrollState()
    LaunchedEffect(expanded) {
        if (expanded) scrollState.scrollTo(0)
    }
    if (expandedState.currentState || expandedState.targetState) {
        val density = LocalDensity.current
        val containerSize = LocalWindowInfo.current.containerSize
        val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        var transformOrigin by remember { mutableStateOf(TransformOrigin.Center) }
        val positionProvider = remember(
            density, containerSize, safeInsets.getTop(density), safeInsets.getBottom(density),
        ) {
            BrowserMainMenuPositionProvider(
                containerHeight = containerSize.height,
                topInset = safeInsets.getTop(density),
                bottomInset = safeInsets.getBottom(density),
                verticalMargin = with(density) { 48.dp.roundToPx() },
                downwardOffset = with(density) { 24.dp.roundToPx() },
            ) { anchor, menu ->
                transformOrigin = TransformOrigin(
                    ((anchor.center.x - menu.left).toFloat() / menu.width).coerceIn(0f, 1f),
                    ((anchor.center.y - menu.top).toFloat() / menu.height).coerceIn(0f, 1f),
                )
            }
        }
        // Match Material 3 DropdownMenu's scale/alpha motion and transparent padded host.
        val transition = rememberTransition(expandedState, label = "DropDownMenu")
        val scaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        val alphaSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val scale by transition.animateFloat(transitionSpec = { scaleSpec }, label = "scale") {
            if (it) 1f else 0.8f
        }
        val alpha by transition.animateFloat(transitionSpec = { alphaSpec }, label = "alpha") {
            if (it) 1f else 0f
        }
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = expanded),
        ) {
            DisposableEffect(expanded) {
                onPopupStateChange(true)
                onDispose {}
            }
            DisposableEffect(Unit) {
                onDispose { onPopupStateChange(false) }
            }
            Surface(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    this.transformOrigin = transformOrigin
                },
                shape = MenuDefaults.shape,
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    Modifier.padding(vertical = 8.dp)
                        .width(IntrinsicSize.Max)
                        .verticalScroll(scrollState),
                ) { content() }
            }
        }
    }
}
