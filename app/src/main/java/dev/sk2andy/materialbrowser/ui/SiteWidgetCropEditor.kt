@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetCrop
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetCropRules
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetFormat
import kotlin.math.roundToInt

@Composable
internal fun SiteWidgetCropEditor(
    bitmap: ImageBitmap,
    initialFormat: SiteWidgetFormat = SiteWidgetFormat.FourByThree,
    formatChangeEnabled: Boolean = true,
    initialRefreshIntervalMinutes: Int = 0,
    saving: Boolean = false,
    error: String? = null,
    onSubmit: (SiteWidgetFormat, SiteWidgetCrop, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var format by rememberSaveable(bitmap, stateSaver = SiteWidgetFormatSaver) { mutableStateOf(initialFormat) }
    var refreshIntervalMinutes by rememberSaveable(bitmap) {
        mutableStateOf(initialRefreshIntervalMinutes.takeIf { it in SiteWidgetRefreshIntervals } ?: 0)
    }
    val maximumCrop = remember(bitmap, format) {
        SiteWidgetCropRules.initialCrop(format, bitmap.width, bitmap.height)!!
    }
    var crop by rememberSaveable(bitmap, stateSaver = SiteWidgetCropSaver) {
        mutableStateOf(maximumCrop)
    }
    var viewportSize by remember(bitmap) { mutableStateOf(IntSize.Zero) }
    val imageAspect = bitmap.width.toFloat() / bitmap.height
    val zoom = (maximumCrop.width / crop.width).coerceIn(1f, SiteWidgetCropRules.MAXIMUM_ZOOM)
    val outlineColor = MaterialTheme.colorScheme.primary
    val columnsDescription = stringResource(R.string.site_widget_editor_columns, format.columns)
    val rowsDescription = stringResource(R.string.site_widget_editor_rows, format.rows)
    val zoomDescription = stringResource(R.string.site_widget_editor_zoom)
    val intervalDescription = stringResource(R.string.site_widget_editor_interval)
    fun updateFormat(next: SiteWidgetFormat) {
        crop = SiteWidgetCropRules.reformatCrop(crop, format, next, bitmap.width, bitmap.height) ?: crop
        format = next
    }
    BackHandler {
        if (!saving) onDismiss()
    }
    Scaffold(
        modifier = Modifier.testTag(SiteWidgetCropEditorTestTags.Screen),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_widget_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss, enabled = !saving) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            Modifier.fillMaxSize().padding(contentPadding).navigationBarsPadding(),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val previewModifier = if (maxWidth / maxHeight > imageAspect) {
                    Modifier.fillMaxHeight().aspectRatio(imageAspect)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(imageAspect)
                }
                Canvas(
                    previewModifier
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(bitmap, format, viewportSize, saving) {
                            if (saving || viewportSize.width <= 0 || viewportSize.height <= 0) {
                                return@pointerInput
                            }
                            detectTransformGestures { _, pan, zoom, _ ->
                                val resized = SiteWidgetCropRules.resizeCrop(
                                    crop = crop,
                                    scale = 1f / zoom,
                                    format = format,
                                    viewportWidth = bitmap.width,
                                    viewportHeight = bitmap.height,
                                ) ?: crop
                                val previewZoom = maximumCrop.width / resized.width
                                crop = SiteWidgetCropRules.moveCrop(
                                    crop = resized,
                                    deltaX = -pan.x / viewportSize.width / previewZoom,
                                    deltaY = -pan.y / viewportSize.height / previewZoom,
                                ) ?: resized
                            }
                        }
                        .testTag(SiteWidgetCropEditorTestTags.Preview),
                ) {
                    val previewZoom = maximumCrop.width / crop.width
                    val left = maximumCrop.left * size.width
                    val top = maximumCrop.top * size.height
                    val width = maximumCrop.width * size.width
                    val height = maximumCrop.height * size.height
                    clipRect {
                        withTransform({
                            translate(
                                left - crop.left * size.width * previewZoom,
                                top - crop.top * size.height * previewZoom,
                            )
                            scale(previewZoom, previewZoom, pivot = Offset.Zero)
                        }) {
                            drawImage(bitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                        }
                    }
                    val right = left + width
                    val bottom = top + height
                    val shade = Color.Black.copy(alpha = 0.6f)
                    drawRect(shade, size = Size(size.width, top))
                    drawRect(shade, Offset(0f, bottom), Size(size.width, size.height - bottom))
                    drawRect(shade, Offset(0f, top), Size(left, height))
                    drawRect(shade, Offset(right, top), Size(size.width - right, height))
                    repeat(format.columns - 1) { index ->
                        val fraction = (index + 1).toFloat() / format.columns
                        drawLine(
                            Color.White.copy(alpha = 0.55f),
                            Offset(left + width * fraction, top),
                            Offset(left + width * fraction, bottom),
                        )
                    }
                    repeat(format.rows - 1) { index ->
                        val fraction = (index + 1).toFloat() / format.rows
                        drawLine(
                            Color.White.copy(alpha = 0.55f),
                            Offset(left, top + height * fraction),
                            Offset(right, top + height * fraction),
                        )
                    }
                    drawRect(outlineColor, Offset(left, top), Size(width, height), style = Stroke(2.dp.toPx()))
                }
            }
            HorizontalDivider()
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 330.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.site_widget_editor_instructions),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(columnsDescription, style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = format.columns.toFloat(),
                        onValueChange = { updateFormat(format.copy(columns = it.roundToInt())) },
                        enabled = !saving && formatChangeEnabled,
                        valueRange = 2f..6f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth()
                            .semantics { contentDescription = columnsDescription }
                            .testTag(SiteWidgetCropEditorTestTags.Columns),
                    )
                    Text(rowsDescription, style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = format.rows.toFloat(),
                        onValueChange = { updateFormat(format.copy(rows = it.roundToInt())) },
                        enabled = !saving && formatChangeEnabled,
                        valueRange = 2f..6f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth()
                            .semantics { contentDescription = rowsDescription }
                            .testTag(SiteWidgetCropEditorTestTags.Rows),
                    )
                    Text(
                        stringResource(R.string.site_widget_editor_zoom_value, zoom),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = zoom,
                        onValueChange = { value ->
                            crop = SiteWidgetCropRules.zoomCrop(
                                crop, value, format, bitmap.width, bitmap.height,
                            ) ?: crop
                        },
                        enabled = !saving,
                        valueRange = 1f..SiteWidgetCropRules.MAXIMUM_ZOOM,
                        modifier = Modifier.fillMaxWidth()
                            .semantics { contentDescription = zoomDescription }
                            .testTag(SiteWidgetCropEditorTestTags.Zoom),
                    )
                    Text(
                        stringResource(
                            R.string.site_widget_editor_interval_value,
                            siteWidgetRefreshIntervalLabel(refreshIntervalMinutes),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = SiteWidgetRefreshIntervals.indexOf(refreshIntervalMinutes).toFloat(),
                        onValueChange = { refreshIntervalMinutes = SiteWidgetRefreshIntervals[it.roundToInt()] },
                        enabled = !saving,
                        valueRange = 0f..SiteWidgetRefreshIntervals.lastIndex.toFloat(),
                        steps = SiteWidgetRefreshIntervals.size - 2,
                        modifier = Modifier.fillMaxWidth()
                            .semantics { contentDescription = intervalDescription }
                            .testTag(SiteWidgetCropEditorTestTags.Interval),
                    )
                    Text(
                        stringResource(R.string.site_widget_editor_interval_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.site_widget_editor_interaction_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    error?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = { onSubmit(format, crop, refreshIntervalMinutes) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().testTag(SiteWidgetCropEditorTestTags.Submit),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.site_widget_editor_pin))
                        }
                    }
                }
            }
        }
    }
}

internal object SiteWidgetCropEditorTestTags {
    const val Screen = "site_widget_crop_editor"
    const val Preview = "site_widget_crop_preview"
    const val Columns = "site_widget_crop_columns"
    const val Rows = "site_widget_crop_rows"
    const val Zoom = "site_widget_crop_zoom"
    const val Size = Zoom
    const val Interval = "site_widget_crop_interval"
    const val Submit = "site_widget_crop_submit"
}

private val SiteWidgetCropSaver = listSaver<SiteWidgetCrop, Float>(
    save = { crop -> listOf(crop.left, crop.top, crop.width, crop.height) },
    restore = { values ->
        SiteWidgetCropRules.sanitize(SiteWidgetCrop(values[0], values[1], values[2], values[3]))
    },
)

private val SiteWidgetFormatSaver = listSaver<SiteWidgetFormat, Int>(
    save = { format -> listOf(format.columns, format.rows) },
    restore = { values -> SiteWidgetFormat(values[0], values[1]).takeIf { it.isValid } },
)

private val SiteWidgetRefreshIntervals = dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetRefreshRules.intervalsMinutes

@Composable
private fun siteWidgetRefreshIntervalLabel(minutes: Int): String = when {
    minutes == 0 -> stringResource(R.string.site_widget_interval_manual)
    minutes < 60 -> stringResource(R.string.site_widget_interval_minutes, minutes)
    else -> stringResource(R.string.site_widget_interval_hours, minutes / 60)
}
