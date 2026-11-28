package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.browser.BrowserTabOverviewLayoutRules
import dev.sk2andy.materialbrowser.shared.ui.LocalCandyBrowserChromeEffects
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewGridRules
import kotlin.math.roundToInt

internal const val TAB_GRID_DENSITY_SLIDER = "tab_overview_grid_density_slider"

@Composable
private fun gridWindowSize(): Size {
    val pixels = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { Size(pixels.width.toDp().value, pixels.height.toDp().value) }
}

@Composable
internal fun rememberTabGridDensityDraft(preferredColumns: Int): MutableIntState {
    return remember(preferredColumns) { mutableIntStateOf(preferredColumns) }
}

@Composable
internal fun TabGridDensityControl(
    draftColumns: Int,
    onDraftChanged: (Int) -> Unit,
    onChangeFinished: () -> Unit,
) {
    val window = gridWindowSize()
    val maxColumns = BrowserTabOverviewLayoutRules.maximumGridColumns(window.width)
    val layout = BrowserTabOverviewLayoutRules.grid(window.width, window.height, draftColumns)
    val columnCount = layout.columnCount
    val columns = pluralStringResource(R.plurals.tab_grid_density_columns, columnCount, columnCount)
    val description = if (draftColumns == BrowserTabOverviewLayoutRules.AUTO_GRID_COLUMNS) {
        stringResource(R.string.tab_grid_density_automatic, columns)
    } else {
        columns
    }
    val title = stringResource(R.string.tab_grid_density_title)
    val panelColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = panelColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            LocalCandyBrowserChromeEffects.current.settingsSlider(
                value = draftColumns.coerceAtMost(maxColumns).toFloat(),
                onValueChange = { onDraftChanged(it.roundToInt()) },
                onValueChangeFinished = onChangeFinished,
                enabled = true,
                modifier = Modifier.testTag(TAB_GRID_DENSITY_SLIDER).semantics {
                    contentDescription = title
                    stateDescription = description
                },
                valueRange = 0f..maxColumns.toFloat(),
                steps = maxColumns - 1,
                backgroundColor = panelColor,
            )
            Text(
                stringResource(R.string.tab_grid_density_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fake thumbnails only: the preview never opens tabs or renders profile data. */
@Composable
internal fun TabGridDensityPreview(preferredColumns: Int) {
    val window = gridWindowSize()
    val colors = MaterialTheme.colorScheme
    val layout = BrowserTabOverviewLayoutRules.grid(window.width, window.height, preferredColumns)
    val accents = listOf(colors.primary, colors.tertiary, colors.secondary)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer, colors.surface))),
    ) {
        if (window.width <= 0f || layout.cardWidth <= 0f) return@Canvas
        val scale = size.width / window.width
        val cardWidth = layout.cardWidth * scale
        val denseTitle = TabOverviewGridRules.usesDenseTitle(layout.cardWidth)
        val cardHeight = cardWidth / layout.previewAspectRatio
        val pitch = layout.columnPitch * scale
        val spacing = layout.itemSpacing * scale
        val padding = layout.contentPadding * scale
        val radius = CornerRadius((cardWidth * 0.11f).coerceAtMost(15.dp.toPx()))
        val pillHeight = minOf(cardHeight * 0.18f, 25.dp.toPx())
        var y = 12.dp.toPx()
        var row = 0
        while (y < size.height && row < 12) {
            repeat(layout.columnCount) { column ->
                val x = padding + column * pitch
                val accent = accents[(row + column) % accents.size]
                val cardSize = Size(cardWidth, cardHeight)
                drawRoundRect(
                    color = lerp(colors.surfaceContainerLowest, accent, 0.12f),
                    topLeft = Offset(x, y),
                    size = cardSize,
                    cornerRadius = radius,
                )
                val inset = cardWidth * 0.10f
                val thumbWidth = cardWidth - 2 * inset
                drawRoundRect(
                    color = lerp(colors.surfaceContainerHigh, accent, 0.42f),
                    topLeft = Offset(x + inset, y + cardHeight * 0.17f),
                    size = Size(thumbWidth, cardHeight * 0.36f),
                    cornerRadius = CornerRadius(radius.x * 0.65f),
                )
                repeat(3) { line ->
                    drawRoundRect(
                        color = accent.copy(alpha = if (line == 0) 0.55f else 0.22f),
                        topLeft = Offset(x + inset, y + cardHeight * (0.60f + line * 0.07f)),
                        size = Size(thumbWidth * (1f - line * 0.15f), cardHeight * 0.025f),
                        cornerRadius = CornerRadius(cardHeight * 0.0125f),
                    )
                }
                val pillInset = cardHeight * 0.04f
                val pillTop = if (denseTitle) y + cardHeight - pillHeight - pillInset else y + pillInset
                drawRoundRect(
                    color = colors.surfaceContainerHigh,
                    topLeft = Offset(x + inset * 0.6f, pillTop),
                    size = Size(cardWidth * 0.64f, pillHeight),
                    cornerRadius = CornerRadius(pillHeight / 2f),
                )
                drawCircle(
                    color = accent,
                    radius = pillHeight * 0.25f,
                    center = Offset(x + inset * 0.6f + pillHeight * 0.52f, pillTop + pillHeight / 2f),
                )
                drawCircle(
                    color = colors.surfaceContainerHigh,
                    radius = pillHeight / 2f,
                    center = Offset(x + cardWidth - inset * 0.6f - pillHeight / 2f, y + pillInset + pillHeight / 2f),
                )
            }
            y += cardHeight + spacing
            row++
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(colors.surface.copy(alpha = 0f), colors.surface),
                startY = size.height - 26.dp.toPx(),
            ),
            topLeft = Offset(0f, size.height - 26.dp.toPx()),
            size = Size(size.width, 26.dp.toPx()),
        )
    }
}
