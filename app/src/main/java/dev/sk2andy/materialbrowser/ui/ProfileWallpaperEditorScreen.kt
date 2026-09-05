@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import dev.sk2andy.materialbrowser.browser.ProfileWallpaper
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperRules
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperTarget
import kotlin.math.roundToInt

internal data class ProfileWallpaperRuntime(
    val bitmap: ImageBitmap,
    val wallpaper: ProfileWallpaper,
)

internal val LocalProfileWallpaper = staticCompositionLocalOf<ProfileWallpaperRuntime?> { null }

@Composable
internal fun ProfileWallpaperEditorScreen(
    bitmap: ImageBitmap?,
    wallpaperTarget: ProfileWallpaperTarget = ProfileWallpaperTarget.NewTab,
    targetAspectRatio: Float = 9f / 16f,
    initialWallpaper: ProfileWallpaper,
    hasStoredWallpaper: Boolean,
    loading: Boolean,
    errorMessage: String?,
    onChooseImage: () -> Unit,
    onSave: (ProfileWallpaper) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var wallpaper by remember(bitmap, initialWallpaper) {
        mutableStateOf(ProfileWallpaperRules.sanitize(initialWallpaper))
    }
    var viewportSize by remember(bitmap) { mutableStateOf(IntSize.Zero) }
    BackHandler(enabled = loading) {}
    Scaffold(
        modifier = Modifier.testTag(ProfileWallpaperEditorTestTags.Screen),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (wallpaperTarget) {
                                ProfileWallpaperTarget.NewTab -> {
                                    R.string.profile_new_tab_wallpaper_title
                                }
                                ProfileWallpaperTarget.TabSwitcher -> {
                                    R.string.profile_tab_switcher_wallpaper_title
                                }
                            },
                        ),
                    )
                },
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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    val safeTargetAspectRatio = targetAspectRatio
                        .takeIf { it.isFinite() && it > 0f }
                        ?: (9f / 16f)
                    val availableAspectRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
                    val previewWidth = if (availableAspectRatio > safeTargetAspectRatio) {
                        maxHeight * safeTargetAspectRatio
                    } else {
                        maxWidth
                    }
                    val previewHeight = if (availableAspectRatio > safeTargetAspectRatio) {
                        maxHeight
                    } else {
                        maxWidth / safeTargetAspectRatio
                    }
                    Box(
                        modifier = Modifier
                            .size(previewWidth, previewHeight)
                            .onSizeChanged { viewportSize = it }
                            .then(
                                if (loading) {
                                    Modifier
                                } else {
                                    Modifier.pointerInput(bitmap, viewportSize) {
                                        detectTransformGestures { centroid, pan, zoom, _ ->
                                            wallpaper = ProfileWallpaperRules.transformed(
                                                wallpaper = wallpaper,
                                                zoomChange = zoom,
                                                panX = pan.x,
                                                panY = pan.y,
                                                centroidX = centroid.x,
                                                centroidY = centroid.y,
                                                imageWidth = bitmap.width.toFloat(),
                                                imageHeight = bitmap.height.toFloat(),
                                                viewportWidth = viewportSize.width.toFloat(),
                                                viewportHeight = viewportSize.height.toFloat(),
                                            )
                                        }
                                    }
                                },
                            ),
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawProfileWallpaper(bitmap, wallpaper)
                            drawCropGrid()
                        }
                        Text(
                            text = stringResource(R.string.profile_wallpaper_gesture_hint),
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
                } else if (loading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = onChooseImage,
                        modifier = Modifier.testTag(ProfileWallpaperEditorTestTags.ChooseImage),
                    ) {
                        Text(stringResource(R.string.profile_wallpaper_choose_image))
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
                            text = stringResource(R.string.profile_wallpaper_zoom),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Slider(
                            value = wallpaper.zoom,
                            enabled = !loading,
                            onValueChange = { zoom ->
                                val currentZoom = wallpaper.zoom.coerceAtLeast(
                                    ProfileWallpaperRules.MIN_ZOOM,
                                )
                                wallpaper = ProfileWallpaperRules.transformed(
                                    wallpaper = wallpaper,
                                    zoomChange = zoom / currentZoom,
                                    panX = 0f,
                                    panY = 0f,
                                    centroidX = viewportSize.width / 2f,
                                    centroidY = viewportSize.height / 2f,
                                    imageWidth = bitmap.width.toFloat(),
                                    imageHeight = bitmap.height.toFloat(),
                                    viewportWidth = viewportSize.width.toFloat(),
                                    viewportHeight = viewportSize.height.toFloat(),
                                )
                            },
                            valueRange = ProfileWallpaperRules.MIN_ZOOM..
                                ProfileWallpaperRules.MAX_ZOOM,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(ProfileWallpaperEditorTestTags.Zoom),
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
                                Text(stringResource(R.string.profile_wallpaper_replace))
                            }
                            Button(
                                onClick = { onSave(wallpaper) },
                                enabled = !loading,
                                modifier = Modifier.testTag(ProfileWallpaperEditorTestTags.Save),
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
                    if (hasStoredWallpaper) {
                        TextButton(
                            onClick = onRemove,
                            enabled = !loading,
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag(ProfileWallpaperEditorTestTags.Remove),
                        ) {
                            Text(
                                stringResource(R.string.profile_wallpaper_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun DrawScope.drawProfileWallpaper(
    bitmap: ImageBitmap,
    wallpaper: ProfileWallpaper,
    scrimAlpha: Float = 0f,
) {
    val layout = ProfileWallpaperRules.layout(
        imageWidth = bitmap.width.toFloat(),
        imageHeight = bitmap.height.toFloat(),
        viewportWidth = size.width,
        viewportHeight = size.height,
        wallpaper = wallpaper,
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
        if (scrimAlpha > 0f) {
            drawRect(Color.Black.copy(alpha = scrimAlpha.coerceIn(0f, 1f)))
        }
    }
}

private fun DrawScope.drawCropGrid() {
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

internal object ProfileWallpaperEditorTestTags {
    const val Screen = "profile_wallpaper_editor"
    const val ChooseImage = "profile_wallpaper_choose_image"
    const val Zoom = "profile_wallpaper_zoom"
    const val Save = "profile_wallpaper_save"
    const val Remove = "profile_wallpaper_remove"
}
