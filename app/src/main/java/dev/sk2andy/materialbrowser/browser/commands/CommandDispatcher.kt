package dev.sk2andy.materialbrowser.browser.commands

interface CommandActions {
    fun clearCacheAndReload(): Boolean
    fun clearCookiesAndReload(): Boolean
    fun reload(): Boolean
    fun stopLoading(): Boolean
    fun setSelectedTabPinned(isPinned: Boolean): Boolean
    fun closeDuplicateTabs(confirmedTabIds: List<String>): Boolean
    fun moveSelectedTabToProfile(profileId: String): Boolean
    fun switchProfile(profileId: String): Boolean
    fun createTab(isIncognito: Boolean): Boolean
    fun openSettings(): Boolean
}

object CommandDispatcher {
    fun dispatch(command: BrowserCommand, actions: CommandActions): Boolean {
        return when (command.kind) {
            BrowserCommandKind.ClearCacheAndReload -> actions.clearCacheAndReload()
            BrowserCommandKind.ClearCookiesAndReload -> actions.clearCookiesAndReload()
            BrowserCommandKind.Reload -> actions.reload()
            BrowserCommandKind.StopLoading -> actions.stopLoading()
            BrowserCommandKind.PinTab -> actions.setSelectedTabPinned(true)
            BrowserCommandKind.UnpinTab -> actions.setSelectedTabPinned(false)
            BrowserCommandKind.CloseDuplicateTabs -> actions.closeDuplicateTabs(command.duplicateTabIds)
            BrowserCommandKind.MoveTabToProfile -> {
                actions.moveSelectedTabToProfile(command.targetProfileId ?: return false)
            }
            BrowserCommandKind.SwitchProfile -> {
                actions.switchProfile(command.targetProfileId ?: return false)
            }
            BrowserCommandKind.NewRegularTab -> actions.createTab(isIncognito = false)
            BrowserCommandKind.NewIncognitoTab -> actions.createTab(isIncognito = true)
            BrowserCommandKind.OpenSettings -> actions.openSettings()
        }
    }
}
