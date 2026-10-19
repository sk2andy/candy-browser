package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetContentLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuPresentation

object BrowserMenuSelectionTestTags {
    fun presentation(value: BrowserMenuPresentation): String = "menu_style_option_${value.stableId}"
    fun contentLayout(value: BrowserMenuBottomSheetContentLayout): String = "menu_content_layout_option_${value.stableId}"
}

@Composable
fun BrowserMenuStyleSettingsPage(
    layout: BrowserMenuLayout,
    resources: BrowserMenuPresentationSettingsResources,
    backContentDescription: String,
    onBack: () -> Unit,
    onPresentationChanged: (BrowserMenuPresentation) -> Unit,
) {
    SettingsSelectionPage(
        title = resources.presentationTitle(),
        backContentDescription = backContentDescription,
        onBack = onBack,
        summary = resources.presentationSummary(),
        preview = { BrowserMenuSelectionPreview(layout, showContentLayout = false) },
    ) {
        BrowserMenuPresentation.entries.forEachIndexed { index, value ->
            if (index > 0) SettingsPreferenceDivider()
            SettingsRadioOption(
                label = resources.presentationLabel(value),
                selected = layout.presentation == value,
                modifier = Modifier.testTag(BrowserMenuSelectionTestTags.presentation(value)),
                onClick = { if (layout.presentation != value) onPresentationChanged(value) },
            )
        }
    }
}

@Composable
fun BrowserMenuContentLayoutSettingsPage(
    layout: BrowserMenuLayout,
    resources: BrowserMenuPresentationSettingsResources,
    backContentDescription: String,
    onBack: () -> Unit,
    onContentLayoutChanged: (BrowserMenuBottomSheetContentLayout) -> Unit,
) {
    val enabled = layout.presentation == BrowserMenuPresentation.BottomSheet
    SettingsSelectionPage(
        title = resources.contentLayoutTitle(),
        backContentDescription = backContentDescription,
        onBack = onBack,
        preview = { BrowserMenuSelectionPreview(layout, showContentLayout = true) },
    ) {
        BrowserMenuBottomSheetContentLayout.entries.forEachIndexed { index, value ->
            if (index > 0) SettingsPreferenceDivider()
            SettingsRadioOption(
                label = resources.contentLayoutLabel(value),
                selected = layout.bottomSheetContentLayout == value,
                enabled = enabled,
                modifier = Modifier.testTag(BrowserMenuSelectionTestTags.contentLayout(value)),
                onClick = {
                    if (enabled && layout.bottomSheetContentLayout != value) onContentLayoutChanged(value)
                },
            )
        }
    }
}

/** Theme-colored vector illustration, deliberately independent from a captured browser page. */
@Composable
private fun BrowserMenuSelectionPreview(layout: BrowserMenuLayout, showContentLayout: Boolean) {
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxWidth().height(260.dp)) {
        fun panel(x: Float, y: Float, width: Float, height: Float, color: Color, radius: Float = 0.035f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * x, size.height * y),
                size = Size(size.width * width, size.height * height),
                cornerRadius = CornerRadius(size.minDimension * radius),
            )
        }
        panel(0f, 0f, 1f, 1f, colors.secondaryContainer, 0.1f)
        // Candy-colored orbital shapes surround a simple browser silhouette.
        drawCircle(colors.tertiaryContainer, size.minDimension * 0.12f, Offset(size.width * 0.15f, size.height * 0.3f))
        drawCircle(colors.primaryContainer, size.minDimension * 0.09f, Offset(size.width * 0.86f, size.height * 0.72f))
        panel(0.23f, 0.055f, 0.54f, 0.89f, colors.outline, 0.065f)
        panel(0.245f, 0.07f, 0.51f, 0.86f, colors.surface, 0.055f)
        panel(0.28f, 0.115f, 0.44f, 0.065f, colors.surfaceContainerHigh)
        panel(0.29f, 0.235f, 0.29f, 0.025f, colors.primaryContainer)
        panel(0.29f, 0.285f, 0.39f, 0.02f, colors.surfaceContainerHighest)
        panel(0.29f, 0.33f, 0.33f, 0.02f, colors.surfaceContainerHighest)
        if (!showContentLayout && layout.presentation == BrowserMenuPresentation.Floating) {
            panel(0.43f, 0.195f, 0.29f, 0.49f, colors.surfaceContainerHigh)
            repeat(5) { row ->
                panel(0.46f, 0.23f + row * 0.085f, 0.035f, 0.035f, colors.primary)
                panel(0.52f, 0.239f + row * 0.085f, 0.16f, 0.016f, colors.onSurfaceVariant)
            }
        } else {
            panel(0.255f, 0.405f, 0.49f, 0.515f, colors.surfaceContainerHigh)
            panel(0.45f, 0.425f, 0.1f, 0.013f, colors.outline)
            repeat(5) { slot ->
                drawCircle(colors.primary, size.minDimension * 0.014f, Offset(size.width * (0.31f + slot * 0.095f), size.height * 0.49f))
            }
            if (showContentLayout && layout.bottomSheetContentLayout == BrowserMenuBottomSheetContentLayout.SwipePages) {
                panel(0.28f, 0.55f, 0.32f, 0.28f, colors.tertiaryContainer)
                panel(0.62f, 0.55f, 0.1f, 0.28f, colors.primaryContainer)
                repeat(3) { row -> panel(0.31f, 0.6f + row * 0.07f, 0.23f, 0.02f, colors.onTertiaryContainer) }
                repeat(2) { page ->
                    drawCircle(if (page == 0) colors.primary else colors.outlineVariant, size.minDimension * 0.01f, Offset(size.width * (0.48f + page * 0.045f), size.height * 0.87f))
                }
            } else {
                repeat(4) { row ->
                    panel(0.29f, 0.55f + row * 0.08f, 0.38f, 0.055f, if (row == 0) colors.tertiaryContainer else colors.surface)
                    panel(0.32f, 0.57f + row * 0.08f, 0.24f, 0.014f, colors.onSurfaceVariant)
                }
                panel(0.705f, 0.55f, 0.007f, 0.14f, colors.primary)
            }
        }
    }
}
