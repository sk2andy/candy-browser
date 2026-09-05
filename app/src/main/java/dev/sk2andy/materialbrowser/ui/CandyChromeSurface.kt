package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import dev.sk2andy.materialbrowser.ui.theme.BrowserChromeSurfaceTokens

internal interface CandyChromeBackdropSource

internal fun interface CandyChromeSurfaceRenderer {
    @Composable
    fun render(
        backdropSource: CandyChromeBackdropSource?,
        tokens: BrowserChromeSurfaceTokens,
        modifier: Modifier,
        shape: Shape,
        blurCornerRadius: Dp,
        containerColor: Color,
        backdropBlurEnabled: Boolean,
        content: @Composable () -> Unit,
    )
}

internal val LocalCandyChromeSurfaceRenderer = staticCompositionLocalOf<CandyChromeSurfaceRenderer> {
    error("CandyChromeSurfaceRenderer must be provided by the platform theme.")
}

@Composable
internal fun CandyChromeSurface(
    backdropSource: CandyChromeBackdropSource?,
    tokens: BrowserChromeSurfaceTokens,
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(tokens.cornerRadius),
    blurCornerRadius: Dp = tokens.cornerRadius,
    containerColor: Color = tokens.containerColor,
    backdropBlurEnabled: Boolean = tokens.backdropBlurEnabled,
    content: @Composable () -> Unit,
) {
    LocalCandyChromeSurfaceRenderer.current.render(
        backdropSource = backdropSource,
        tokens = tokens,
        modifier = modifier,
        shape = shape,
        blurCornerRadius = blurCornerRadius,
        containerColor = containerColor,
        backdropBlurEnabled = backdropBlurEnabled,
        content = content,
    )
}
