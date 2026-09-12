package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.browser.BrowserTab

@Composable
fun CompactTabList(
    listState: LazyListState,
    tabs: List<BrowserTab>,
    startsAtBottom: Boolean,
    visible: Boolean,
    selectedTabId: String,
    initialTabId: String,
    visuals: @Composable (BrowserTab) -> TabOverviewHeroVisuals,
    heroProgress: () -> Float,
    heroCompleted: Boolean,
    heroVisible: Boolean,
    exitHeroTabId: String?,
    interactionsEnabled: Boolean,
    reorderSessionId: String?,
    reorderDraggedTabId: String?,
    reorderModifier: @Composable (BrowserTab) -> Modifier,
    reorderAutoScroll: @Composable (Rect?) -> Unit,
    onRowBounds: (BrowserTab, Rect) -> Unit,
    onRowBoundsDisposed: (BrowserTab, Rect?) -> Unit,
    onSelect: (BrowserTab, Rect) -> Unit,
    onCloseTab: (BrowserTab) -> Unit,
    listTestTag: String,
    tabTestTag: (BrowserTab) -> String,
    topPadding: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)
    var listBounds by remember { mutableStateOf<Rect?>(null) }
    reorderAutoScroll(listBounds)
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
            .testTag(listTestTag)
            .onGloballyPositioned { listBounds = it.boundsInRoot() },
        userScrollEnabled = interactionsEnabled,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = topPadding,
            end = 16.dp,
            bottom = 8.dp,
        ),
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
                visuals = visuals(tab),
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
                    .then(reorderModifier(tab))
                    .testTag(tabTestTag(tab)),
            )
        }
    }
}

@Composable
private fun CompactListTabItem(
    tab: BrowserTab,
    visuals: TabOverviewHeroVisuals,
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
                modifier = Modifier.fillMaxSize().padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visuals.favicon(36.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        visuals.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    )
                    Text(
                        visuals.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (tab.isPinned) {
                    visuals.pinnedIcon(
                        Modifier.padding(horizontal = 15.dp).size(20.dp),
                        MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    IconButton(
                        onClick = onClose,
                        enabled = interactionsEnabled,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(visuals.closeTestTag),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = visuals.closeContentDescription,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
        }
    }
}

class TabBoundsHolder {
    var bounds: Rect? = null
}
