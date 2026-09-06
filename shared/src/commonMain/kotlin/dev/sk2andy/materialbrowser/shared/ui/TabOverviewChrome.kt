package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object TabOverviewChromeTestTags {
    const val Root = "tab_overview_root"
    const val Background = "tab_overview_background"
    const val HeroPager = "tab_overview_hero_pager"
    const val Grid = "tab_overview_grid"
    const val List = "tab_overview_list"
    const val Bar = "tab_overview_address_bar"
    const val NewTab = "tab_overview_new_tab"
    const val More = "tab_overview_more"
    const val PinnedTabsJump = "tab_overview_pinned_tabs_jump"
    const val Settings = "tab_overview_settings"
}

@Composable
fun TabOverviewBottomChrome(
    visible: Boolean,
    enabled: Boolean,
    onNewTab: () -> Unit,
    onMore: () -> Unit,
    containerColor: Color,
    tonalElevation: Dp,
    shadowElevation: Dp,
    bottomInset: Dp = 0.dp,
    newTabIcon: @Composable () -> Unit,
    moreIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingAction: @Composable BoxScope.() -> Unit = {},
    trailingAction: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp + bottomInset)
            .padding(start = 4.dp, end = 4.dp, bottom = bottomInset),
        contentAlignment = Alignment.Center,
    ) {
        leadingAction()
        trailingAction()
        Surface(
            modifier = Modifier
                .width(112.dp)
                .height(56.dp)
                .testTag(TabOverviewChromeTestTags.Bar)
                .graphicsLayer {
                    alpha = if (visible) 1f else 0f
                }
                .then(
                    if (visible) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics { }
                    },
                ),
            shape = MaterialTheme.shapes.extraLarge,
            color = containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
        ) {
            OverviewAddressBarContent(
                onNewTab = onNewTab,
                onMore = onMore,
                enabled = enabled,
                newTabIcon = newTabIcon,
                moreIcon = moreIcon,
            )
        }
    }
}

@Composable
fun OverviewAddressBarContent(
    onNewTab: () -> Unit,
    onMore: () -> Unit,
    enabled: Boolean = true,
    newTabIcon: @Composable () -> Unit,
    moreIcon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onNewTab,
            enabled = enabled,
            modifier = Modifier.testTag(TabOverviewChromeTestTags.NewTab),
        ) {
            newTabIcon()
        }
        IconButton(
            onClick = onMore,
            enabled = enabled,
            modifier = Modifier.testTag(TabOverviewChromeTestTags.More),
        ) {
            moreIcon()
        }
    }
}
