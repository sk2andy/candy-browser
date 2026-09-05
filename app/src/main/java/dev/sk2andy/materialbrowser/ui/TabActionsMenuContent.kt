package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R

@Composable
internal fun TabActionsMenuContent(
    pageSubtitle: String,
    canToggleFavorite: Boolean,
    isFavorite: Boolean,
    isPinned: Boolean,
    canUsePageActions: Boolean,
    canToggleDomainMute: Boolean,
    isDomainMuted: Boolean,
    canAddSiteCapsule: Boolean,
    canSnooze: Boolean,
    canCloseAllTabs: Boolean,
    hasPinnedTabs: Boolean,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onPrint: () -> Unit,
    onDomainMutedChange: (Boolean) -> Unit,
    onOpenCandyTrail: () -> Unit,
    onAddSiteCapsule: () -> Unit,
    onSummarize: () -> Unit,
    onSnooze: () -> Unit,
    onCloseAllTabs: () -> Unit,
    modifier: Modifier = Modifier,
    compactToolbar: Boolean = false,
    profileContent: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val outerCorners = MaterialTheme.shapes.medium
    val innerCorners = MaterialTheme.shapes.extraSmall
    val firstItemShape = RoundedCornerShape(
        topStart = outerCorners.topStart,
        topEnd = outerCorners.topEnd,
        bottomEnd = innerCorners.bottomEnd,
        bottomStart = innerCorners.bottomStart,
    )
    val lastItemShape = RoundedCornerShape(
        topStart = innerCorners.topStart,
        topEnd = innerCorners.topEnd,
        bottomEnd = outerCorners.bottomEnd,
        bottomStart = outerCorners.bottomStart,
    )
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.tab_actions_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = pageSubtitle,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TabActionsMenuTestTags.Toolbar),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MenuToolbarAction(
                label = stringResource(R.string.action_favorite),
                iconRes = if (isFavorite) {
                    R.drawable.ic_symbol_favorite_filled
                } else {
                    R.drawable.ic_symbol_favorite
                },
                accessibilityLabel = stringResource(
                    if (isFavorite) R.string.action_remove_favorite
                    else R.string.action_add_favorite,
                ),
                enabled = canToggleFavorite,
                selected = isFavorite,
                horizontalContent = !compactToolbar,
                onClick = onToggleFavorite,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TabActionsMenuTestTags.Favorite),
            )
            MenuToolbarAction(
                label = stringResource(
                    if (isPinned) R.string.action_remove_pin else R.string.action_pin_tab,
                ),
                iconRes = R.drawable.ic_push_pin,
                accessibilityLabel = stringResource(
                    if (isPinned) R.string.action_remove_pin else R.string.action_pin_tab,
                ),
                selected = isPinned,
                horizontalContent = !compactToolbar,
                onClick = onTogglePinned,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TabActionsMenuTestTags.Pin),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.browser_menu_page_group),
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier.testTag(TabActionsMenuTestTags.PageGroup),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MenuRow(
                label = stringResource(R.string.action_share),
                iconRes = R.drawable.ic_symbol_share,
                enabled = canUsePageActions,
                shape = firstItemShape,
                onClick = onShare,
            )
            MenuRow(
                label = stringResource(R.string.action_open_in_app),
                iconRes = R.drawable.ic_symbol_open_in_new,
                enabled = canUsePageActions,
                shape = innerCorners,
                onClick = onOpenExternal,
            )
            MenuRow(
                label = stringResource(R.string.action_print),
                iconRes = R.drawable.ic_symbol_print,
                enabled = canUsePageActions,
                shape = innerCorners,
                onClick = onPrint,
            )
            DomainMuteMenuItem(
                enabled = canToggleDomainMute,
                muted = isDomainMuted,
                onMutedChange = onDomainMutedChange,
                shape = lastItemShape,
            )
        }

        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.testTag(TabActionsMenuTestTags.CandyGroup),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MenuRow(
                label = stringResource(R.string.action_open_candy_trail),
                iconRes = R.drawable.ic_symbol_route,
                enabled = canUsePageActions,
                shape = firstItemShape,
                modifier = Modifier.testTag(TabActionsMenuTestTags.Trail),
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                onClick = onOpenCandyTrail,
            )
            MenuRow(
                label = stringResource(R.string.action_add_site_capsule),
                iconRes = R.drawable.ic_symbol_add_to_home_screen,
                enabled = canAddSiteCapsule,
                shape = innerCorners,
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                onClick = onAddSiteCapsule,
            )
            MenuRow(
                label = stringResource(R.string.action_summarize),
                iconRes = R.drawable.ic_symbol_auto_awesome,
                enabled = canUsePageActions,
                shape = innerCorners,
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                onClick = onSummarize,
            )
            MenuRow(
                label = stringResource(R.string.action_snooze_tab),
                iconRes = R.drawable.ic_snooze,
                enabled = canSnooze,
                shape = lastItemShape,
                modifier = Modifier.testTag(SnoozeTestTags.TabActionsSnooze),
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                supportingText = if (canSnooze) {
                    null
                } else {
                    stringResource(R.string.snooze_unavailable_private)
                },
                onClick = onSnooze,
            )
        }
        profileContent()
        Spacer(Modifier.height(8.dp))
        MenuRow(
            label = stringResource(R.string.action_close_all_tabs),
            iconRes = R.drawable.ic_delete_outline,
            enabled = canCloseAllTabs,
            shape = outerCorners,
            modifier = Modifier.testTag(TabActionsMenuTestTags.CloseAllTabs),
            containerColor = colors.errorContainer,
            contentColor = colors.onErrorContainer,
            supportingText = if (hasPinnedTabs) {
                stringResource(R.string.close_all_tabs_pinned_supporting_text)
            } else {
                null
            },
            onClick = onCloseAllTabs,
        )
    }
}

