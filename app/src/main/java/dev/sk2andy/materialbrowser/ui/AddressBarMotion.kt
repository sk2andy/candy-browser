package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.ui.theme.CandyMotionScheme
import dev.sk2andy.materialbrowser.ui.theme.LocalCandyMotionScheme

@Immutable
internal data class AddressBarMotionState(
    val width: Dp,
    val height: Dp,
    val dockOffset: DpOffset,
)

internal object AddressBarMotion {
    val OVERVIEW_WIDTH = 112.dp

    fun containerAnimationSpec(motionScheme: CandyMotionScheme): SpringSpec<Dp> =
        spring(
            dampingRatio = motionScheme.addressBarContainerDampingRatio,
            stiffness = motionScheme.addressBarContainerStiffness,
        )

    fun dockProgressAnimationSpec(motionScheme: CandyMotionScheme): SpringSpec<Float> =
        spring(
            dampingRatio = motionScheme.addressBarDockDampingRatio,
            stiffness = motionScheme.addressBarDockStiffness,
        )

    fun dockBreakawayAnimationSpec(motionScheme: CandyMotionScheme): SpringSpec<Float> =
        spring(
            dampingRatio = motionScheme.addressBarBreakawayDampingRatio,
            stiffness = motionScheme.addressBarBreakawayStiffness,
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

    fun dockOffsetForPosition(
        position: Offset,
        maxWidth: Dp,
        barWidth: Dp,
        verticalTravel: Dp,
    ): DpOffset {
        val horizontalPosition = position.x
            .takeIf(Float::isFinite)
            ?.coerceIn(-1f, 1f)
            ?: 0f
        val verticalPosition = position.y
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: 0f
        val horizontalTravel = ((maxWidth - barWidth) / 2f + 12.dp).coerceAtLeast(0.dp)
        return DpOffset(
            x = horizontalTravel * horizontalPosition,
            y = if (verticalPosition == 0f) {
                0.dp
            } else {
                -verticalTravel.coerceAtLeast(0.dp) * verticalPosition
            },
        )
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
        motionScheme: CandyMotionScheme,
    ): Int = if (usesFadeThrough(initial, target)) {
        motionScheme.addressBarFadeThroughExitMillis
    } else {
        motionScheme.addressBarQuickFadeOutMillis
    }

    fun enterDurationMillis(
        initial: AddressBarPresentation,
        target: AddressBarPresentation,
        motionScheme: CandyMotionScheme,
    ): Int = if (usesFadeThrough(initial, target)) {
        motionScheme.addressBarFadeThroughEnterMillis
    } else {
        motionScheme.addressBarQuickFadeInMillis
    }
}

@Composable
internal fun rememberAddressBarMotionState(
    presentation: AddressBarPresentation,
    compactWidth: Dp,
    maxWidth: Dp,
    feedbackWidth: Dp,
    edgeTabWidth: Dp,
    verticalTravel: Dp,
    dockPosition: Offset,
): AddressBarMotionState {
    val motionScheme = LocalCandyMotionScheme.current
    val width by animateDpAsState(
        targetValue = AddressBarMotion.widthTarget(
            presentation = presentation,
            compactWidth = compactWidth,
            maxWidth = maxWidth,
            feedbackWidth = feedbackWidth,
            edgeTabWidth = edgeTabWidth,
        ),
        animationSpec = AddressBarMotion.containerAnimationSpec(motionScheme),
        label = "Adressleistenbreite beim Scrollen und Parken",
    )
    val height by animateDpAsState(
        targetValue = AddressBarMotion.heightTarget(presentation),
        animationSpec = AddressBarMotion.containerAnimationSpec(motionScheme),
        label = "Adressleistenhöhe beim Parken",
    )
    val dockOffset = AddressBarMotion.dockOffsetForPosition(
        position = dockPosition,
        maxWidth = maxWidth,
        barWidth = width,
        verticalTravel = verticalTravel,
    )
    return AddressBarMotionState(width = width, height = height, dockOffset = dockOffset)
}

@Composable
internal fun AddressBarPresentationTransition(
    presentation: AddressBarPresentation,
    modifier: Modifier = Modifier,
    content: @Composable (AddressBarPresentation) -> Unit,
) {
    val motionScheme = LocalCandyMotionScheme.current
    var displayedPresentation by remember { mutableStateOf(presentation) }
    val contentAlpha = remember { Animatable(1f) }

    LaunchedEffect(presentation, motionScheme) {
        if (presentation == displayedPresentation) {
            contentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = AddressBarMotion.enterDurationMillis(
                        initial = displayedPresentation,
                        target = presentation,
                        motionScheme = motionScheme,
                    ),
                    easing = motionScheme.addressBarFadeInEasing,
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
                    motionScheme = motionScheme,
                ),
                easing = motionScheme.addressBarFadeOutEasing,
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
                    motionScheme = motionScheme,
                ),
                easing = motionScheme.addressBarFadeInEasing,
            ),
        )
    }

    Box(
        modifier = modifier.graphicsLayer { alpha = contentAlpha.value },
    ) {
        content(displayedPresentation)
    }
}
