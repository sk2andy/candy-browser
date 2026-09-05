@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import kotlin.math.roundToInt

@Composable
internal fun TabHeroLayer(
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    targetCornerRadius: Dp = 28.dp,
    targetFraction: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val targetCornerRadiusPx = with(density) { targetCornerRadius.toPx() }
    val heroClipPath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val fraction = targetFraction().coerceIn(0f, 1f)
                val width = rootWidthPx + (targetBounds.width - rootWidthPx) * fraction
                val height = rootHeightPx + (targetBounds.height - rootHeightPx) * fraction
                val scale = width / rootWidthPx
                val clipTop = (rootHeightPx - height / scale) * PREVIEW_CROP_TOP_FRACTION
                translationX = targetBounds.left * fraction
                translationY = targetBounds.top * fraction - clipTop * scale
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .drawWithContent {
                val fraction = targetFraction().coerceIn(0f, 1f)
                val width = rootWidthPx + (targetBounds.width - rootWidthPx) * fraction
                val height = rootHeightPx + (targetBounds.height - rootHeightPx) * fraction
                val scale = width / rootWidthPx
                val visibleHeight = height / scale
                val clipTop = (rootHeightPx - visibleHeight) * PREVIEW_CROP_TOP_FRACTION
                val cornerRadius = targetCornerRadiusPx * fraction / scale
                heroClipPath.reset()
                heroClipPath.addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = clipTop,
                        right = rootWidthPx,
                        bottom = clipTop + visibleHeight,
                        cornerRadius = CornerRadius(cornerRadius),
                    ),
                )
                clipPath(heroClipPath) { this@drawWithContent.drawContent() }
            }
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        content()
    }
}

@Composable
internal fun TabCardHeroContent(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    favorites: List<FavoriteEntry>,
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: FloatState,
    targetFraction: () -> Float,
) {
    val previewLayout = TabOverviewHeroRules.cardPreviewLayout(
        rootWidthPx = rootWidthPx,
        rootHeightPx = rootHeightPx,
        targetWidthPx = targetBounds.width,
        targetHeightPx = targetBounds.height,
        cropTopFraction = PREVIEW_CROP_TOP_FRACTION,
    )
    Box(Modifier.fillMaxSize()) {
        if (tab.url == BLANK_URL && !tab.isIncognito) {
            FullscreenTabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                favorites = favorites,
                rootHeightPx = rootHeightPx,
                previewTopInsetPx = previewTopInsetPx,
                bottomBarTopPx = bottomBarTopPx,
                blankFavoritesAlpha = {
                    TabOverviewHeroRules.blankFavoritesAlpha(targetFraction())
                },
            )
        } else if (tab.isIncognito) {
            FullscreenTabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                favorites = favorites,
                rootHeightPx = rootHeightPx,
                previewTopInsetPx = previewTopInsetPx,
                bottomBarTopPx = bottomBarTopPx,
            )
        } else {
            Layout(
                content = {
                    TabPreviewContent(
                        tab = tab,
                        preview = preview,
                        favicon = favicon,
                        favorites = favorites,
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .clipToBounds(),
            ) { measurables, constraints ->
                val capturedHeightPx = preview
                    ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
                    ?.let { bitmap -> rootWidthPx * bitmap.height / bitmap.width }
                val startLayout = TabSwitchPreviewLayoutRules.resolve(
                    rootHeightPx = rootHeightPx,
                    previewTopInsetPx = previewTopInsetPx,
                    bottomBarTopPx = bottomBarTopPx.floatValue,
                    capturedHeightPx = capturedHeightPx,
                )
                val frame = TabOverviewHeroRules.cardPreviewFrame(
                    startTopPx = startLayout.topInsetPx,
                    startHeightPx = startLayout.visibleHeightPx,
                    targetLayout = previewLayout,
                    targetFraction = targetFraction(),
                )
                val frameHeight = frame.sourceHeightPx
                    .roundToInt()
                    .coerceAtLeast(1)
                val previewPlaceable = measurables.single().measure(
                    Constraints.fixed(
                        width = constraints.maxWidth,
                        height = frameHeight,
                    ),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    previewPlaceable.placeRelative(
                        x = 0,
                        y = frame.sourceTopPx.roundToInt(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TabListHeroContent(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    favorites: List<FavoriteEntry>,
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: FloatState,
    targetFraction: () -> Float,
) {
    val density = LocalDensity.current
    val targetScale = (targetBounds.width / rootWidthPx).coerceAtLeast(0.01f)
    val sourceRowHeight = with(density) { (targetBounds.height / targetScale).toDp() }
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = 1f - TabOverviewHeroRules.compactChromeAlpha(targetFraction())
                },
        ) {
            FullscreenTabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                favorites = favorites,
                rootHeightPx = rootHeightPx,
                previewTopInsetPx = previewTopInsetPx,
                bottomBarTopPx = bottomBarTopPx,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(sourceRowHeight)
                .graphicsLayer {
                    val fraction = targetFraction().coerceIn(0f, 1f)
                    val width = rootWidthPx + (targetBounds.width - rootWidthPx) * fraction
                    val height = rootHeightPx + (targetBounds.height - rootHeightPx) * fraction
                    val scale = width / rootWidthPx
                    val visibleHeight = height / scale
                    translationY = (rootHeightPx - visibleHeight) * PREVIEW_CROP_TOP_FRACTION
                    alpha = TabOverviewHeroRules.compactChromeAlpha(fraction)
                },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabFavicon(tab = tab, favicon = favicon, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            displayTabTitle(tab),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (tab.url == BLANK_URL) {
                                stringResource(R.string.new_tab_title)
                            } else {
                                AddressResolver.displayText(tab.url)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (tab.isPinned) {
                        Icon(
                            painter = painterResource(R.drawable.ic_push_pin),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 15.dp)
                                .size(20.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TabTitleRow(
    tab: BrowserTab,
    favicon: Bitmap?,
    contentColor: Color,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.graphicsLayer { this.alpha = alpha() },
        shape = MaterialTheme.shapes.extraLarge,
        color = browserChromeColor(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            frostedAlpha = 0.88f,
        ),
        contentColor = contentColor,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tab.isIncognito) {
                Icon(
                    painter = painterResource(R.drawable.ic_incognito_outline),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor,
                )
            } else if (tab.url == BLANK_URL) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground_art),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.Unspecified,
                )
            } else if (favicon != null && !favicon.isRecycled) {
                Image(
                    bitmap = favicon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Surface(
                    modifier = Modifier.size(22.dp),
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            displayTabTitle(tab).take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                displayTabTitle(tab),
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
            )
            if (tab.isPinned) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_push_pin),
                    contentDescription = stringResource(R.string.cd_pinned_tab),
                    modifier = Modifier.size(18.dp),
                    tint = contentColor,
                )
            }
        }
    }
}

@Composable
internal fun GridTabPreviewChrome(
    tab: BrowserTab,
    favicon: Bitmap?,
    interactionsEnabled: Boolean,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    Box(modifier) {
        TabTitleRow(
            tab = tab,
            favicon = favicon,
            contentColor = contentColor,
            alpha = { 1f },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = 8.dp,
                    top = 8.dp,
                    end = if (tab.isPinned) 8.dp else 60.dp,
                )
                .testTag(SnoozeTestTags.overviewTitle(tab.id)),
        )
        if (!tab.isPinned) {
            IconButton(
                onClick = onClose ?: {},
                enabled = interactionsEnabled && onClose != null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(48.dp)
                    .testTag(SnoozeTestTags.overviewClose(tab.id)),
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = browserChromeColor(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        frostedAlpha = 0.88f,
                    ),
                    contentColor = contentColor,
                    shadowElevation = 2.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = onClose?.let {
                                stringResource(
                                    R.string.cd_close_named_tab,
                                    displayTabTitle(tab),
                                )
                            },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun displayTabTitle(tab: BrowserTab): String =
    if (tab.url == BLANK_URL || tab.title.isBlank()) {
        stringResource(R.string.new_tab_title)
    } else {
        tab.title
    }

