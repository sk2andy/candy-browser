@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.capsule.CapsuleIconCrop
import dev.sk2andy.materialbrowser.capsule.CapsuleIconCropRules
import kotlin.math.roundToInt

@Composable
internal fun CapsuleCustomIconEditorScreen(
    bitmap: ImageBitmap?,
    imageRevision: Int = 0,
    loading: Boolean,
    errorMessage: String?,
    onChooseImage: () -> Unit,
    onChooseIconPack: () -> Unit = {},
    onSave: (CapsuleIconCrop) -> Unit,
    onDismiss: () -> Unit,
) {
    var crop by rememberSaveable(
        imageRevision,
        stateSaver = CapsuleIconCropSaver,
    ) { mutableStateOf(CapsuleIconCrop()) }
    var viewportSize by remember(bitmap) { mutableStateOf(IntSize.Zero) }
    BackHandler(enabled = loading) {}
    Scaffold(
        modifier = Modifier.testTag(CapsuleCustomIconEditorTestTags.Screen),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capsule_custom_icon_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss, enabled = !loading) {
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
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .onSizeChanged { viewportSize = it }
                            .then(
                                if (loading) {
                                    Modifier
                                } else {
                                    Modifier.pointerInput(bitmap, viewportSize) {
                                        detectTransformGestures { centroid, pan, zoom, _ ->
                                            crop = CapsuleIconCropRules.transformed(
                                                crop = crop,
                                                zoomChange = zoom,
                                                panX = pan.x,
                                                panY = pan.y,
                                                centroidX = centroid.x,
                                                centroidY = centroid.y,
                                                imageWidth = bitmap.width.toFloat(),
                                                imageHeight = bitmap.height.toFloat(),
                                                viewportSize = viewportSize.width.toFloat(),
                                            )
                                        }
                                    }
                                },
                            )
                            .testTag(CapsuleCustomIconEditorTestTags.Preview),
                        shape = RoundedCornerShape(36.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 8.dp,
                    ) {
                        Box {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCapsuleIconCrop(bitmap, crop)
                                drawCapsuleIconCropGrid()
                            }
                            Text(
                                text = stringResource(R.string.capsule_custom_icon_gesture_hint),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.58f),
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else if (loading) {
                    CircularProgressIndicator()
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onChooseImage,
                            modifier = Modifier.testTag(
                                CapsuleCustomIconEditorTestTags.ChooseImage,
                            ),
                        ) {
                            Text(stringResource(R.string.capsule_custom_icon_choose_image))
                        }
                        OutlinedButton(
                            onClick = onChooseIconPack,
                            modifier = Modifier.testTag(
                                CapsuleCustomIconEditorTestTags.ChooseIconPack,
                            ),
                        ) {
                            Text(stringResource(R.string.capsule_custom_icon_choose_pack))
                        }
                    }
                }
            }
            HorizontalDivider()
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    if (bitmap != null) {
                        Text(
                            text = stringResource(R.string.capsule_custom_icon_zoom),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Slider(
                            value = crop.zoom,
                            enabled = !loading,
                            onValueChange = { zoom ->
                                val currentZoom = crop.zoom.coerceAtLeast(
                                    CapsuleIconCropRules.MIN_ZOOM,
                                )
                                crop = CapsuleIconCropRules.transformed(
                                    crop = crop,
                                    zoomChange = zoom / currentZoom,
                                    panX = 0f,
                                    panY = 0f,
                                    centroidX = viewportSize.width / 2f,
                                    centroidY = viewportSize.height / 2f,
                                    imageWidth = bitmap.width.toFloat(),
                                    imageHeight = bitmap.height.toFloat(),
                                    viewportSize = viewportSize.width.toFloat(),
                                )
                            },
                            valueRange = CapsuleIconCropRules.MIN_ZOOM..
                                CapsuleIconCropRules.MAX_ZOOM,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(CapsuleCustomIconEditorTestTags.Zoom),
                        )
                    }
                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (bitmap != null) {
                            TextButton(onClick = onChooseImage, enabled = !loading) {
                                Text(stringResource(R.string.capsule_custom_icon_replace))
                            }
                            TextButton(
                                onClick = onChooseIconPack,
                                enabled = !loading,
                                modifier = Modifier.testTag(
                                    CapsuleCustomIconEditorTestTags.ChooseIconPack,
                                ),
                            ) {
                                Text(stringResource(R.string.capsule_custom_icon_choose_pack_short))
                            }
                            Button(
                                onClick = { onSave(crop) },
                                enabled = !loading,
                                modifier = Modifier.testTag(CapsuleCustomIconEditorTestTags.Save),
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(stringResource(R.string.action_save))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun DrawScope.drawCapsuleIconCrop(
    bitmap: ImageBitmap,
    crop: CapsuleIconCrop,
) {
    val layout = CapsuleIconCropRules.layout(
        imageWidth = bitmap.width.toFloat(),
        imageHeight = bitmap.height.toFloat(),
        viewportSize = size.width,
        crop = crop,
    ) ?: return
    clipRect {
        drawImage(
            image = bitmap,
            dstOffset = IntOffset(layout.left.roundToInt(), layout.top.roundToInt()),
            dstSize = IntSize(
                layout.width.roundToInt().coerceAtLeast(1),
                layout.height.roundToInt().coerceAtLeast(1),
            ),
        )
    }
}

private fun DrawScope.drawCapsuleIconCropGrid() {
    val lineColor = Color.White.copy(alpha = 0.42f)
    repeat(2) { index ->
        val fraction = (index + 1) / 3f
        drawLine(
            color = lineColor,
            start = Offset(size.width * fraction, 0f),
            end = Offset(size.width * fraction, size.height),
        )
        drawLine(
            color = lineColor,
            start = Offset(0f, size.height * fraction),
            end = Offset(size.width, size.height * fraction),
        )
    }
}

internal object CapsuleCustomIconEditorTestTags {
    const val Screen = "capsule_custom_icon_editor"
    const val Preview = "capsule_custom_icon_preview"
    const val ChooseImage = "capsule_custom_icon_choose_image"
    const val ChooseIconPack = "capsule_custom_icon_choose_icon_pack"
    const val Zoom = "capsule_custom_icon_zoom"
    const val Save = "capsule_custom_icon_save"
}

private val CapsuleIconCropSaver = listSaver<CapsuleIconCrop, Float>(
    save = { crop -> listOf(crop.zoom, crop.normalizedPanX, crop.normalizedPanY) },
    restore = { values ->
        CapsuleIconCrop(
            zoom = values[0],
            normalizedPanX = values[1],
            normalizedPanY = values[2],
        )
    },
)
