@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.FavoriteEntry

@Composable
internal fun CompactTabList(
    listState: LazyListState,
    tabs: List<BrowserTab>,
    startsAtBottom: Boolean,
    visible: Boolean,
    selectedTabId: String,
    initialTabId: String,
    favicons: Map<String, Bitmap>,
    heroProgress: () -> Float,
    heroCompleted: Boolean,
    heroVisible: Boolean,
    exitHeroTabId: String?,
    interactionsEnabled: Boolean,
    reorderSessionId: String?,
    reorderDraggedTabId: String?,
    reorderTranslation: (String) -> Offset,
    reorderPointerInRoot: Offset?,
    reorderCanScrollBackward: Boolean,
    reorderCanScrollForward: Boolean,
    onReorderAutoScroll: (Float) -> Unit,
    onRowBounds: (BrowserTab, Rect) -> Unit,
    onRowBoundsDisposed: (BrowserTab, Rect?) -> Unit,
    onSelect: (BrowserTab, Rect) -> Unit,
    onCloseTab: (BrowserTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)
    var listBounds by remember { mutableStateOf<Rect?>(null) }
    TabReorderEdgeAutoScroll(
        sessionId = reorderSessionId,
        pointerInRoot = reorderPointerInRoot,
        viewportBounds = listBounds,
        orientation = Orientation.Vertical,
        canScrollBackward = listState.canScrollBackward && reorderCanScrollBackward,
        canScrollForward = listState.canScrollForward && reorderCanScrollForward,
        scrollBy = { delta -> listState.scrollBy(delta) },
        onScrolled = onReorderAutoScroll,
    )
    LaunchedEffect(visible, initialTabId, selectedTabId, tabs.size, startsAtBottom) {
        if (!visible || tabs.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        val targetIndex = if (startsAtBottom) tabs.lastIndex else selectedIndex
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
            listState.scrollToItem(targetIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag(TabOverviewChromeTestTags.List)
            .onGloballyPositioned { listBounds = it.boundsInRoot() },
        userScrollEnabled = interactionsEnabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(
            space = 8.dp,
            alignment = if (startsAtBottom) Alignment.Bottom else Alignment.Top,
        ),
    ) {
        itemsIndexed(
            items = tabs,
            key = { _, tab -> tab.id },
            contentType = { _, _ -> "tab-list-row" },
        ) { _, tab ->
            CompactListTabItem(
                tab = tab,
                favicon = favicons[tab.id],
                selected = tab.id == selectedTabId,
                initial = tab.id == initialTabId,
                heroProgress = heroProgress,
                heroCompleted = heroCompleted,
                heroVisible = heroVisible,
                exitTarget = tab.id == exitHeroTabId,
                interactionsEnabled = interactionsEnabled,
                onBounds = { bounds -> onRowBounds(tab, bounds) },
                onBoundsDisposed = { bounds -> onRowBoundsDisposed(tab, bounds) },
                onSelect = { bounds -> onSelect(tab, bounds) },
                onClose = { onCloseTab(tab) },
                modifier = Modifier
                    .then(
                        if (reorderSessionId == null) Modifier.animateItem() else Modifier,
                    )
                    .zIndex(if (reorderDraggedTabId == tab.id) 6f else 0f)
                    .tabReorderVisualMotion(
                        sessionId = reorderSessionId,
                        isDragged = reorderDraggedTabId == tab.id,
                        targetOffset = reorderTranslation(tab.id),
                    )
                    .testTag(SnoozeTestTags.overviewTab(tab.id)),
            )
        }
    }
}

@Composable
private fun CompactListTabItem(
    tab: BrowserTab,
    favicon: Bitmap?,
    selected: Boolean,
    initial: Boolean,
    heroProgress: () -> Float,
    heroCompleted: Boolean,
    heroVisible: Boolean,
    exitTarget: Boolean,
    interactionsEnabled: Boolean,
    onBounds: (Rect) -> Unit,
    onBoundsDisposed: (Rect?) -> Unit,
    onSelect: (Rect) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val boundsHolder = remember(tab.id) { TabBoundsHolder() }
    DisposableEffect(tab.id) {
        onDispose { onBoundsDisposed(boundsHolder.bounds) }
    }
    val realRowVisible = TabOverviewHeroRules.isCardVisible(
        isInitialCard = initial,
        progress = if (heroCompleted) 1f else 0f,
        isExitTarget = exitTarget,
    )
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                alpha = when {
                    !realRowVisible || (initial && heroVisible) -> 0f
                    initial -> 1f
                    else -> TabOverviewHeroRules.neighborAlpha(heroProgress())
                }
                translationY = if (initial) 0f else {
                    (1f - TabOverviewHeroRules.neighborAlpha(heroProgress())) * 18f
                }
            }
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                boundsHolder.bounds = bounds
                onBounds(bounds)
            }
            .clickable(
                enabled = interactionsEnabled,
                role = Role.Button,
                onClick = { boundsHolder.bounds?.let(onSelect) },
            )
            .semantics { this.selected = selected },
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
        },
        shadowElevation = 0.dp,
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
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
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
                        contentDescription = stringResource(R.string.cd_pinned_tab),
                        modifier = Modifier
                            .padding(horizontal = 15.dp)
                            .size(20.dp),
                    )
                } else {
                    IconButton(
                        onClick = onClose,
                        enabled = interactionsEnabled,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(
                                R.string.cd_close_named_tab,
                                displayTabTitle(tab),
                            ),
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TabFavicon(
    tab: BrowserTab,
    favicon: Bitmap?,
    size: Dp,
) {
    if (tab.isIncognito) {
        Icon(
            painter = painterResource(R.drawable.ic_incognito_outline),
            contentDescription = null,
            modifier = Modifier.size(size),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    } else if (tab.url == BLANK_URL) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground_art),
            contentDescription = null,
            modifier = Modifier.size(size),
            tint = Color.Unspecified,
        )
    } else if (favicon != null && !favicon.isRecycled) {
        Image(
            bitmap = favicon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Surface(
            modifier = Modifier.size(size),
            shape = RoundedCornerShape(size * 0.28f),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    displayTabTitle(tab).take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
internal fun TabPreviewContent(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    favorites: List<FavoriteEntry> = emptyList(),
) {
    when {
        tab.isIncognito -> IncognitoTabPlaceholder()
        tab.url == BLANK_URL -> BlankTabPreview(
            favorites = favorites,
            favoritesAlpha = { 0f },
        )
        preview != null && !preview.isRecycled -> Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(
                horizontalBias = 0f,
                verticalBias = PREVIEW_CROP_TOP_FRACTION * 2f - 1f,
            ),
        )
        else -> TabPreviewPlaceholder(title = displayTabTitle(tab), favicon = favicon)
    }
}

