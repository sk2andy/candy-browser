@file:OptIn(ExperimentalFoundationApi::class)

package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.LocalCandyMotionScheme
import kotlinx.coroutines.flow.first

internal data class WideAddressTabItem(
    val id: String,
    val title: String,
    val favicon: Bitmap?,
    val canClose: Boolean,
)

internal object WideAddressTabStripTestTags {
    const val Strip = "wide_address_tab_strip"
    const val TabPrefix = "wide_address_tab_"
    const val ClosePrefix = "wide_address_tab_close_"
    const val FaviconPrefix = "wide_address_tab_favicon_"
    const val FallbackPrefix = "wide_address_tab_fallback_"
}

@Composable
internal fun WideAddressTabStrip(
    tabs: List<WideAddressTabItem>,
    selectedTabId: String,
    onTabClick: (String) -> Unit,
    onCurrentTabClick: () -> Unit,
    onCurrentTabLongPress: () -> Unit,
    currentTabLongPressEnabled: Boolean,
    currentTabLongPressLabel: String,
    onCloseTab: (String) -> Unit,
    interactionEnabled: Boolean = true,
    scrollState: LazyListState? = null,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)
    val selectedTab = tabs.getOrNull(selectedIndex)?.takeIf { it.id == selectedTabId }
    var selectedWidthPx by remember(selectedTabId) { mutableIntStateOf(0) }
    val rememberedListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val listState = scrollState ?: rememberedListState

    LaunchedEffect(
        selectedTabId,
        tabs.map(WideAddressTabItem::id),
        selectedTab?.title,
        selectedTab?.canClose,
        selectedWidthPx,
    ) {
        val index = tabs.indexOfFirst { it.id == selectedTabId }
        if (index < 0) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            layout.totalItemsCount >= tabs.size &&
                layout.viewportEndOffset > layout.viewportStartOffset
        }.first { it }
        val layout = listState.layoutInfo
        val visible = layout.visibleItemsInfo.firstOrNull { it.index == index }
        if (
            visible == null ||
            visible.offset < layout.viewportStartOffset ||
            visible.offset + visible.size > layout.viewportEndOffset
        ) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag(WideAddressTabStripTestTags.Strip)
            .then(if (interactionEnabled) Modifier else Modifier.clearAndSetSemantics { }),
        userScrollEnabled = interactionEnabled,
        contentPadding = PaddingValues(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tabs, key = WideAddressTabItem::id) { tab ->
            WideAddressTab(
                tab = tab,
                selected = tab.id == selectedTabId,
                onClick = {
                    if (tab.id == selectedTabId) onCurrentTabClick() else onTabClick(tab.id)
                },
                onLongPress = onCurrentTabLongPress,
                longPressEnabled = currentTabLongPressEnabled,
                longPressLabel = currentTabLongPressLabel,
                onClose = { onCloseTab(tab.id) },
                interactionEnabled = interactionEnabled,
                onSelectedWidthChanged = { selectedWidthPx = it },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun WideAddressTab(
    tab: WideAddressTabItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    longPressEnabled: Boolean,
    longPressLabel: String,
    onClose: () -> Unit,
    interactionEnabled: Boolean,
    onSelectedWidthChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = LocalCandyMotionScheme.current
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(motionScheme.addressBarActionExpandMillis),
        label = "Wide address tab background",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(motionScheme.addressBarActionExpandMillis),
        label = "Wide address tab content",
    )
    val title = tab.title.ifBlank { stringResource(R.string.new_tab_title) }

    Row(
        modifier = modifier
            .widthIn(min = 128.dp, max = 216.dp)
            .height(44.dp)
            .onSizeChanged { if (selected) onSelectedWidthChanged(it.width) }
            .clip(CircleShape)
            .background(containerColor)
            .combinedClickable(
                enabled = interactionEnabled,
                role = Role.Tab,
                onClick = onClick,
                onLongClick = onLongPress.takeIf { selected && longPressEnabled },
                onLongClickLabel = longPressLabel.takeIf { selected && longPressEnabled },
            )
            .semantics { this.selected = selected }
            .testTag(WideAddressTabStripTestTags.TabPrefix + tab.id)
            .padding(start = 10.dp, end = if (tab.canClose) 2.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tab.favicon != null && !tab.favicon.isRecycled) {
            Image(
                bitmap = tab.favicon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .testTag(WideAddressTabStripTestTags.FaviconPrefix + tab.id),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(contentColor)
                    .testTag(WideAddressTabStripTestTags.FallbackPrefix + tab.id),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.take(1).uppercase(),
                    color = containerColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.widthIn(min = 48.dp, max = 132.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
        if (tab.canClose) {
            IconButton(
                onClick = onClose,
                enabled = interactionEnabled,
                modifier = Modifier
                    .size(36.dp)
                    .testTag(WideAddressTabStripTestTags.ClosePrefix + tab.id),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "${stringResource(R.string.cd_close_tab)}: $title",
                    modifier = Modifier.size(18.dp),
                    tint = contentColor,
                )
            }
        }
    }
}
