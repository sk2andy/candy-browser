package dev.sk2andy.materialbrowser.ui

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.ui.theme.BrowserChromeSurfaceTokens
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

@Composable
internal fun BrowserChromeSurface(
    blurTarget: BlurTarget?,
    tokens: BrowserChromeSurfaceTokens,
    modifier: Modifier,
    shape: Shape,
    blurCornerRadius: Dp = tokens.cornerRadius,
    containerColor: Color = tokens.containerColor,
    backdropBlurEnabled: Boolean = tokens.backdropBlurEnabled,
    content: @Composable () -> Unit,
) {
    val drawsBackdropBlur = backdropBlurEnabled && blurTarget != null
    val blurCornerRadiusPx = with(LocalDensity.current) { blurCornerRadius.toPx() }
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (drawsBackdropBlur) Color.Transparent else containerColor,
        tonalElevation = tokens.tonalElevation,
        shadowElevation = tokens.shadowElevation,
    ) {
        Box {
            if (drawsBackdropBlur && blurTarget != null) {
                key(blurTarget) {
                    AndroidView(
                        factory = { context ->
                            BlurView(context).apply {
                                importantForAccessibility =
                                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                                isClickable = false
                                isFocusable = false
                                background = GradientDrawable().apply {
                                    setColor(android.graphics.Color.TRANSPARENT)
                                    cornerRadius = blurCornerRadiusPx
                                }
                                outlineProvider = ViewOutlineProvider.BACKGROUND
                                clipToOutline = true
                                setupWith(blurTarget, 1f, true)
                                    .setBlurRadius(tokens.blurRadiusPx)
                                    .setOverlayColor(containerColor.toArgb())
                            }
                        },
                        update = { blurView ->
                            (blurView.background as? GradientDrawable)?.cornerRadius =
                                blurCornerRadiusPx
                            blurView
                                .setBlurRadius(tokens.blurRadiusPx)
                                .setOverlayColor(containerColor.toArgb())
                        },
                        onRelease = { blurView -> blurView.setBlurAutoUpdate(false) },
                        modifier = Modifier
                            .matchParentSize()
                            .clip(shape),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .zIndex(1f),
            ) {
                content()
            }
        }
    }
}
