package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuAction
import dev.sk2andy.materialbrowser.shared.ui.TabActionsMenuLabel
import dev.sk2andy.materialbrowser.shared.ui.TabActionsMenuResources

internal object AndroidTabActionsMenuResources : TabActionsMenuResources {
    @Composable
    override fun text(label: TabActionsMenuLabel): String = androidx.compose.ui.res.stringResource(
        when (label) {
            TabActionsMenuLabel.Title -> R.string.tab_actions_title
            TabActionsMenuLabel.Favorite -> R.string.action_favorite
            TabActionsMenuLabel.AddFavorite -> R.string.action_add_favorite
            TabActionsMenuLabel.RemoveFavorite -> R.string.action_remove_favorite
            TabActionsMenuLabel.PinTab -> R.string.action_pin_tab
            TabActionsMenuLabel.UnpinTab -> R.string.action_remove_pin
            TabActionsMenuLabel.PageGroup -> R.string.browser_menu_page_group
            TabActionsMenuLabel.Share -> R.string.action_share
            TabActionsMenuLabel.OpenExternal -> R.string.action_open_in_app
            TabActionsMenuLabel.Print -> R.string.action_print
            TabActionsMenuLabel.MuteDomain -> R.string.action_mute_domain
            TabActionsMenuLabel.CandyTrail -> R.string.action_open_candy_trail
            TabActionsMenuLabel.AddSiteCapsule -> R.string.action_add_site_capsule
            TabActionsMenuLabel.Summarize -> R.string.action_summarize
            TabActionsMenuLabel.Snooze -> R.string.action_snooze_tab
            TabActionsMenuLabel.SnoozeUnavailablePrivate -> R.string.snooze_unavailable_private
            TabActionsMenuLabel.MoveToProfile -> R.string.action_move_tab_to_profile
            TabActionsMenuLabel.CloseAllTabs -> R.string.action_close_all_tabs
            TabActionsMenuLabel.CloseAllTabsPinnedSupportingText ->
                R.string.close_all_tabs_pinned_supporting_text
        },
    )

    @Composable
    override fun icon(
        action: BrowserFeatureMenuAction,
        selected: Boolean,
        modifier: Modifier,
    ) {
        val drawable = when (action) {
            BrowserFeatureMenuAction.ToggleFavorite -> if (selected) {
                R.drawable.ic_symbol_favorite_filled
            } else {
                R.drawable.ic_symbol_favorite
            }
            BrowserFeatureMenuAction.TogglePinned -> R.drawable.ic_push_pin
            BrowserFeatureMenuAction.Share -> R.drawable.ic_symbol_share
            BrowserFeatureMenuAction.OpenExternal -> R.drawable.ic_symbol_open_in_new
            BrowserFeatureMenuAction.Print -> R.drawable.ic_symbol_print
            BrowserFeatureMenuAction.ToggleDomainMute -> R.drawable.ic_symbol_volume_off
            BrowserFeatureMenuAction.OpenCandyTrail -> R.drawable.ic_symbol_route
            BrowserFeatureMenuAction.AddSiteCapsule -> R.drawable.ic_symbol_add_to_home_screen
            BrowserFeatureMenuAction.Summarize -> R.drawable.ic_symbol_auto_awesome
            BrowserFeatureMenuAction.SnoozeTab -> R.drawable.ic_snooze
            BrowserFeatureMenuAction.CloseTab -> R.drawable.ic_delete_outline
            else -> R.drawable.ic_symbol_open_in_new
        }
        Icon(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = modifier,
        )
    }
}
