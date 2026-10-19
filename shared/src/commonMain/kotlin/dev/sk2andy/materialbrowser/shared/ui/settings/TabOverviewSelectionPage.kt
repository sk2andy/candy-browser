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
import dev.sk2andy.materialbrowser.data.TabOverviewMode

object TabOverviewSelectionTestTags {
    fun option(mode: TabOverviewMode): String = "tab_overview_option_${mode.wireValue}"
}

@Composable
fun TabOverviewSelectionPage(
    mode: TabOverviewMode,
    strings: TabOverviewSettingsStrings,
    backContentDescription: String,
    onModeChanged: (TabOverviewMode) -> Unit,
    onBack: () -> Unit,
    testTagPrefix: String = "tab_overview_option",
) {
    SettingsSelectionPage(
        title = strings.overviewMode,
        backContentDescription = backContentDescription,
        onBack = onBack,
        preview = { TabOverviewPreview(mode) },
    ) {
        TabOverviewMode.entries.forEachIndexed { index, candidate ->
            if (index > 0) SettingsPreferenceDivider()
            SettingsRadioOption(
                label = strings.modeNames.getValue(candidate),
                selected = candidate == mode,
                modifier = Modifier.testTag("${testTagPrefix}_${candidate.wireValue}"),
                onClick = { if (candidate != mode) onModeChanged(candidate) },
            )
        }
    }
}

/** A theme-aware Candy illustration of the existing three overview arrangements. */
@Composable
private fun TabOverviewPreview(mode: TabOverviewMode) {
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxWidth().height(260.dp)) {
        fun panel(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Float = 0.04f) {
            drawRoundRect(color, Offset(x * size.width, y * size.height),
                Size(w * size.width, h * size.height), CornerRadius(size.minDimension * radius))
        }
        fun tab(x: Float, y: Float, w: Float, h: Float, color: Color) {
            panel(x, y, w, h, colors.surface)
            panel(x + w * 0.08f, y + h * 0.1f, w * 0.15f, h * 0.08f, color)
            panel(x + w * 0.3f, y + h * 0.12f, w * 0.55f, h * 0.04f, colors.outlineVariant)
            if (h > 0.2f) {
                panel(x + w * 0.08f, y + h * 0.28f, w * 0.84f, h * 0.58f, color)
                panel(x + w * 0.2f, y + h * 0.4f, w * 0.46f, h * 0.04f, colors.onPrimaryContainer)
                panel(x + w * 0.2f, y + h * 0.52f, w * 0.6f, h * 0.03f, colors.onPrimaryContainer)
            }
        }
        panel(0f, 0f, 1f, 1f, colors.secondaryContainer, 0.1f)
        when (mode) {
            TabOverviewMode.Hero -> {
                tab(0.04f, 0.18f, 0.28f, 0.64f, colors.tertiaryContainer)
                tab(0.68f, 0.18f, 0.28f, 0.64f, colors.tertiaryContainer)
                tab(0.26f, 0.1f, 0.48f, 0.8f, colors.primaryContainer)
            }
            TabOverviewMode.Grid -> repeat(4) { index ->
                tab(0.13f + (index % 2) * 0.39f, 0.09f + (index / 2) * 0.43f,
                    0.35f, 0.39f, if (index == 0) colors.primaryContainer else colors.tertiaryContainer)
            }
            TabOverviewMode.List -> repeat(5) { index ->
                tab(0.1f, 0.1f + index * 0.165f, 0.8f, 0.135f,
                    if (index == 0) colors.primaryContainer else colors.tertiaryContainer)
            }
        }
    }
}
