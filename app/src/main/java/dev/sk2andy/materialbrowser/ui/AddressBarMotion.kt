package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Immutable
internal data class AddressBarMotionState(
    val width: Dp,
    val height: Dp,
    val dockOffset: Dp,
)

internal object AddressBarMotion {
    const val FADE_THROUGH_EXIT_MILLIS = 80
    const val FADE_THROUGH_ENTER_MILLIS = 120
    const val QUICK_FADE_OUT_MILLIS = 50
    const val QUICK_FADE_IN_MILLIS = 70

    private const val CONTAINER_DAMPING_RATIO = 0.88f
    private const val CONTAINER_STIFFNESS = 600f
    val OVERVIEW_WIDTH = 112.dp

    val containerAnimationSpec: SpringSpec<Dp>
        get() = spring(
            dampingRatio = CONTAINER_DAMPING_RATIO,
            stiffness = CONTAINER_STIFFNESS,
        )

    fun widthTarget(
        presentation: AddressBarPresentation,
        compactWidth: Dp,
        maxWidth: Dp,
        feedbackWidth: Dp,
        edgeTabWidth: Dp,
    ): Dp = when (presentation) {
        AddressBarPresentation.Docked -> edgeTabWidth.coerceAtMost(maxWidth)
        AddressBarPresentation.Compact -> compactWidth
            .coerceAtLeast(96.dp.coerceAtMost(maxWidth))
            .coerceAtMost(maxWidth)
        AddressBarPresentation.Expanded -> maxWidth
        AddressBarPresentation.Overview -> OVERVIEW_WIDTH.coerceAtMost(maxWidth)
        AddressBarPresentation.CommandFeedback -> feedbackWidth
            .coerceAtLeast(160.dp.coerceAtMost(maxWidth))
            .coerceAtMost(maxWidth)
    }

    fun heightTarget(presentation: AddressBarPresentation): Dp = when (presentation) {
        AddressBarPresentation.Docked,
        AddressBarPresentation.Compact,
        -> 48.dp
        AddressBarPresentation.Expanded -> 56.dp
        AddressBarPresentation.Overview -> 56.dp
        AddressBarPresentation.CommandFeedback -> 46.dp
    }

    fun dockOffsetTarget(
        presentation: AddressBarPresentation,
        maxWidth: Dp,
        edgeTabWidth: Dp,
        layoutDirection: LayoutDirection,
    ): Dp {
        if (presentation != AddressBarPresentation.Docked) return 0.dp
        val distance = ((maxWidth - edgeTabWidth) / 2f + 12.dp).coerceAtLeast(0.dp)
        return if (layoutDirection == LayoutDirection.Ltr) distance else -distance
    }

    fun usesFadeThrough(
        initial: AddressBarPresentation,
        target: AddressBarPresentation,
    ): Boolean =
        initial == AddressBarPresentation.Compact && target == AddressBarPresentation.Expanded ||
            initial == AddressBarPresentation.Expanded && target == AddressBarPresentation.Compact ||
            initial == AddressBarPresentation.Overview ||
            target == AddressBarPresentation.Overview

    fun exitDurationMillis(
        initial: AddressBarPresentation,
        target: AddressBarPresentation,
    ): Int = if (usesFadeThrough(initial, target)) {
        FADE_THROUGH_EXIT_MILLIS
    } else {
        QUICK_FADE_OUT_MILLIS
    }

    fun enterDurationMillis(
        initial: AddressBarPresentation,
        target: AddressBarPresentation,
    ): Int = if (usesFadeThrough(initial, target)) {
        FADE_THROUGH_ENTER_MILLIS
    } else {
        QUICK_FADE_IN_MILLIS
    }
}

@Composable
internal fun rememberAddressBarMotionState(
    presentation: AddressBarPresentation,
    compactWidth: Dp,
    maxWidth: Dp,
    feedbackWidth: Dp,
    edgeTabWidth: Dp,
    layoutDirection: LayoutDirection,
): AddressBarMotionState {
    val width by animateDpAsState(
        targetValue = AddressBarMotion.widthTarget(
            presentation = presentation,
            compactWidth = compactWidth,
            maxWidth = maxWidth,
            feedbackWidth = feedbackWidth,
            edgeTabWidth = edgeTabWidth,
        ),
        animationSpec = AddressBarMotion.containerAnimationSpec,
        label = "Adressleistenbreite beim Scrollen und Parken",
    )
    val height by animateDpAsState(
        targetValue = AddressBarMotion.heightTarget(presentation),
        animationSpec = AddressBarMotion.containerAnimationSpec,
        label = "Adressleistenhöhe beim Parken",
    )
    val dockOffset by animateDpAsState(
        targetValue = AddressBarMotion.dockOffsetTarget(
            presentation = presentation,
            maxWidth = maxWidth,
            edgeTabWidth = edgeTabWidth,
            layoutDirection = layoutDirection,
        ),
        animationSpec = AddressBarMotion.containerAnimationSpec,
        label = "Adressleiste am Bildschirmrand",
    )
    return AddressBarMotionState(width = width, height = height, dockOffset = dockOffset)
}

@Composable
internal fun AddressBarPresentationTransition(
    presentation: AddressBarPresentation,
    modifier: Modifier = Modifier,
    content: @Composable (AddressBarPresentation) -> Unit,
) {
    var displayedPresentation by remember { mutableStateOf(presentation) }
    val contentAlpha = remember { Animatable(1f) }

    LaunchedEffect(presentation) {
        if (presentation == displayedPresentation) {
            contentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = AddressBarMotion.enterDurationMillis(
                        initial = displayedPresentation,
                        target = presentation,
                    ),
                    easing = LinearOutSlowInEasing,
                ),
            )
            return@LaunchedEffect
        }

        val initialPresentation = displayedPresentation
        contentAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = AddressBarMotion.exitDurationMillis(
                    initial = initialPresentation,
                    target = presentation,
                ),
                easing = FastOutLinearInEasing,
            ),
        )
        displayedPresentation = presentation
        contentAlpha.snapTo(0f)
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = AddressBarMotion.enterDurationMillis(
                    initial = initialPresentation,
                    target = presentation,
                ),
                easing = LinearOutSlowInEasing,
            ),
        )
    }

    Box(
        modifier = modifier.graphicsLayer { alpha = contentAlpha.value },
    ) {
        content(displayedPresentation)
    }
}
