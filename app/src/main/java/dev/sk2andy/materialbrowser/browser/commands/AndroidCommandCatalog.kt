package dev.sk2andy.materialbrowser.browser.commands

import android.content.Context
import dev.sk2andy.materialbrowser.R

class AndroidCommandCatalog(private val context: Context) {
    fun localize(
        commands: List<BrowserCommand>,
        cookieScope: CommandCookieScope,
    ): List<CommandSuggestion> = commands.map { command ->
        CommandSuggestion(
            command = command,
            name = context.getString(command.nameResource()),
            effect = command.effectText(cookieScope),
        )
    }

    private fun BrowserCommand.nameResource(): Int = when (kind) {
        BrowserCommandKind.ClearCacheAndReload -> R.string.command_clear_cache_reload_name
        BrowserCommandKind.ClearCookiesAndReload -> R.string.command_delete_cookies_reload_name
        BrowserCommandKind.Reload -> R.string.action_reload
        BrowserCommandKind.StopLoading -> R.string.action_stop_loading
        BrowserCommandKind.PinTab -> R.string.action_pin_tab
        BrowserCommandKind.UnpinTab -> R.string.action_remove_pin
        BrowserCommandKind.CloseDuplicateTabs -> R.string.command_close_duplicates_name
        BrowserCommandKind.MoveTabToProfile -> R.string.action_move_tab_to_profile
        BrowserCommandKind.SwitchProfile -> R.string.command_switch_profile_name
        BrowserCommandKind.NewRegularTab -> R.string.command_new_regular_tab_name
        BrowserCommandKind.NewIncognitoTab -> R.string.command_new_incognito_tab_name
        BrowserCommandKind.OpenSettings -> R.string.command_open_settings_name
    }

    private fun BrowserCommand.effectText(cookieScope: CommandCookieScope): String = when (kind) {
        BrowserCommandKind.ClearCacheAndReload ->
            context.getString(R.string.command_clear_cache_reload_effect)
        BrowserCommandKind.ClearCookiesAndReload -> context.getString(
            when (cookieScope) {
                CommandCookieScope.SharedRegularProfile ->
                    R.string.command_delete_cookies_reload_effect_regular
                CommandCookieScope.PrivateProfile ->
                    R.string.command_delete_cookies_reload_effect_private
                CommandCookieScope.AllWebViews ->
                    R.string.command_delete_cookies_reload_effect_all
            },
        )
        BrowserCommandKind.Reload -> context.getString(R.string.command_reload_effect)
        BrowserCommandKind.StopLoading -> context.getString(R.string.command_stop_loading_effect)
        BrowserCommandKind.PinTab -> context.getString(R.string.command_pin_tab_effect)
        BrowserCommandKind.UnpinTab -> context.getString(R.string.command_unpin_tab_effect)
        BrowserCommandKind.CloseDuplicateTabs -> context.resources.getQuantityString(
            R.plurals.command_close_duplicates_effect,
            duplicateCount,
            duplicateCount,
        )
        BrowserCommandKind.MoveTabToProfile -> context.getString(
            R.string.command_move_tab_effect,
            targetProfileLabel,
        )
        BrowserCommandKind.SwitchProfile -> context.getString(
            R.string.command_switch_profile_effect,
            targetProfileLabel,
        )
        BrowserCommandKind.NewRegularTab -> context.getString(R.string.command_new_regular_tab_effect)
        BrowserCommandKind.NewIncognitoTab ->
            context.getString(R.string.command_new_incognito_tab_effect)
        BrowserCommandKind.OpenSettings -> context.getString(R.string.command_open_settings_effect)
    }
}
