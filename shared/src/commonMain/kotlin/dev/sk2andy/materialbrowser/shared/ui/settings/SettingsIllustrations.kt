package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAddressBarStyle
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle

enum class SettingsIllustrationKind { LinkGesture, LinkPreview, UndoTab, DeviceSearch }

/** Decorative diagrams show the feature's location and result, using the live Candy palette. */
@Composable
fun SettingsFeatureIllustration(
    kind: SettingsIllustrationKind,
    active: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.widthIn(max = 400.dp).fillMaxWidth().height(240.dp)) {
            fun panel(x: Float, y: Float, w: Float, h: Float, color: Color) {
                drawRoundRect(color, Offset(x * size.width, y * size.height),
                    Size(w * size.width, h * size.height), CornerRadius(size.minDimension * 0.04f))
            }
            fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color = colors.primary) {
                drawLine(color, Offset(x1 * size.width, y1 * size.height),
                    Offset(x2 * size.width, y2 * size.height), strokeWidth = 3.dp.toPx())
            }
            panel(0f, 0f, 1f, 1f, colors.secondaryContainer)
            panel(0.13f, 0.08f, 0.74f, 0.84f, colors.surface)
            panel(0.19f, 0.15f, 0.62f, 0.08f, colors.surfaceContainerHighest)
            repeat(3) { row -> panel(0.2f, 0.31f + row * 0.12f, 0.51f - row * 0.05f,
                0.025f, colors.outlineVariant) }
            when (kind) {
                SettingsIllustrationKind.LinkGesture -> {
                    panel(0.2f, 0.43f, 0.38f, 0.035f, colors.primary)
                    drawCircle(colors.primary.copy(alpha = 0.16f), size.minDimension * 0.11f,
                        Offset(size.width * 0.48f, size.height * 0.45f))
                    drawCircle(colors.primary, size.minDimension * 0.055f,
                        Offset(size.width * 0.48f, size.height * 0.45f), style = Stroke(2.dp.toPx()))
                    panel(0.3f, 0.63f, 0.43f, 0.17f, colors.primaryContainer)
                    line(0.48f, 0.53f, 0.48f, 0.6f)
                    line(0.44f, 0.56f, 0.48f, 0.6f)
                    line(0.52f, 0.56f, 0.48f, 0.6f)
                }
                SettingsIllustrationKind.LinkPreview -> {
                    panel(0.22f, 0.43f, 0.56f, 0.36f,
                        if (active) colors.primaryContainer else colors.surfaceContainerHigh)
                    panel(0.27f, 0.49f, 0.46f, 0.12f, colors.surface)
                    panel(0.27f, 0.67f, 0.31f, 0.025f, colors.onSurfaceVariant)
                    line(0.68f, 0.71f, 0.76f, 0.63f)
                    line(0.7f, 0.63f, 0.76f, 0.63f)
                    line(0.76f, 0.63f, 0.76f, 0.69f)
                }
                SettingsIllustrationKind.UndoTab -> {
                    panel(0.2f, 0.29f, 0.27f, 0.31f, colors.primaryContainer)
                    panel(0.53f, 0.29f, 0.27f, 0.31f, colors.surfaceContainerHigh)
                    line(0.7f, 0.33f, 0.75f, 0.39f, colors.onSurfaceVariant)
                    line(0.75f, 0.33f, 0.7f, 0.39f, colors.onSurfaceVariant)
                    panel(0.2f, 0.71f, 0.6f, 0.13f, colors.inverseSurface)
                    panel(0.25f, 0.76f, 0.24f, 0.025f, colors.inverseOnSurface)
                    line(0.69f, 0.77f, 0.57f, 0.77f, colors.inversePrimary)
                    line(0.57f, 0.77f, 0.61f, 0.73f, colors.inversePrimary)
                    line(0.57f, 0.77f, 0.61f, 0.81f, colors.inversePrimary)
                }
                SettingsIllustrationKind.DeviceSearch -> {
                    panel(0.19f, 0.15f, 0.62f, 0.14f, colors.primaryContainer)
                    drawCircle(colors.onPrimaryContainer, size.minDimension * 0.025f,
                        Offset(size.width * 0.25f, size.height * 0.21f), style = Stroke(2.dp.toPx()))
                    line(0.27f, 0.23f, 0.29f, 0.25f, colors.onPrimaryContainer)
                    repeat(3) { index ->
                        panel(0.21f, 0.38f + index * 0.15f, 0.11f, 0.1f,
                            if (active) colors.primaryContainer else colors.surfaceContainerHigh)
                        panel(0.37f, 0.4f + index * 0.15f, 0.35f, 0.025f, colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun AppearanceSettingsIllustration(settings: AppearanceSettings) {
    val colors = MaterialTheme.colorScheme
    val radius = when (settings.shapeStyle) {
        BrowserShapeStyle.Angular -> 0.006f
        BrowserShapeStyle.Rounded -> 0.04f
        BrowserShapeStyle.ExtraRounded -> 0.08f
    }
    Box(Modifier.fillMaxWidth().clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.widthIn(max = 400.dp).fillMaxWidth().height(220.dp)) {
            fun panel(x: Float, y: Float, w: Float, h: Float, color: Color) {
                drawRoundRect(color, Offset(x * size.width, y * size.height),
                    Size(w * size.width, h * size.height), CornerRadius(size.minDimension * radius))
            }
            panel(0f, 0f, 1f, 1f, colors.secondaryContainer)
            panel(0.12f, 0.09f, 0.76f, 0.82f, colors.surface)
            panel(0.19f, 0.18f, 0.62f, 0.29f, colors.primaryContainer)
            repeat(3) { i ->
                drawCircle(listOf(colors.primary, colors.secondary, colors.tertiary)[i],
                    size.minDimension * 0.045f, Offset(size.width * (0.29f + i * 0.2f), size.height * 0.32f))
                panel(0.2f, 0.54f + i * 0.08f, 0.45f - i * 0.06f, 0.02f, colors.outlineVariant)
            }
            val alpha = if (settings.surfaceStyle == BrowserSurfaceStyle.Frosted)
                1f - settings.frostedAddressBarTransparencyPercent / 100f else 1f
            if (settings.addressBarStyle == BrowserAddressBarStyle.Segmented) {
                panel(0.19f, 0.75f, 0.13f, 0.1f, colors.primaryContainer.copy(alpha = alpha))
                panel(0.34f, 0.75f, 0.47f, 0.1f, colors.surfaceContainerHighest.copy(alpha = alpha))
            } else {
                panel(0.19f, 0.75f, 0.62f, 0.1f, colors.surfaceContainerHighest.copy(alpha = alpha))
            }
        }
    }
}
