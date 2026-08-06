package dev.sk2andy.materialbrowser.browser.commands

sealed interface CommandResult {
    data object CacheClearedAndReloaded : CommandResult
    data object CookiesClearedAndReloaded : CommandResult
    data object Reloaded : CommandResult
    data object LoadingStopped : CommandResult
    data object TabPinned : CommandResult
    data object TabUnpinned : CommandResult
    data class DuplicateTabsClosed(val count: Int) : CommandResult
    data class TabMoved(val targetProfileLabel: String?) : CommandResult
    data class ProfileSwitched(val targetProfileLabel: String?) : CommandResult
    data object RegularTabCreated : CommandResult
    data object IncognitoTabCreated : CommandResult
    data object SettingsOpened : CommandResult
}

sealed interface CommandDispatchOutcome {
    data class Pending(val kind: BrowserCommandKind) : CommandDispatchOutcome
    data class Succeeded(val result: CommandResult) : CommandDispatchOutcome
    data class Rejected(val kind: BrowserCommandKind) : CommandDispatchOutcome
}
