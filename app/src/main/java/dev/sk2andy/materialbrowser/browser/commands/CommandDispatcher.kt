package dev.sk2andy.materialbrowser.browser.commands

interface CommandActions {
    fun clearCacheAndReload(): Boolean
    fun clearCookiesAndReload(onComplete: (Boolean) -> Unit): Boolean
    fun reload(): Boolean
    fun stopLoading(): Boolean
    fun setSelectedTabPinned(isPinned: Boolean): Boolean
    fun closeDuplicateTabs(confirmedTabIds: List<String>): Int
    fun moveSelectedTabToProfile(profileId: String): Boolean
    fun switchProfile(profileId: String): Boolean
    fun createTab(isIncognito: Boolean): Boolean
    fun openSettings(): Boolean
}

object CommandDispatcher {
    fun dispatch(
        command: BrowserCommand,
        actions: CommandActions,
        onPendingOutcome: (CommandDispatchOutcome) -> Unit = {},
    ): CommandDispatchOutcome {
        return when (command.kind) {
            BrowserCommandKind.ClearCacheAndReload -> outcome(
                command,
                actions.clearCacheAndReload(),
                CommandResult.CacheClearedAndReloaded,
            )
            BrowserCommandKind.ClearCookiesAndReload -> {
                val started = actions.clearCookiesAndReload { completed ->
                    onPendingOutcome(
                        outcome(
                            command,
                            completed,
                            CommandResult.CookiesClearedAndReloaded,
                        ),
                    )
                }
                if (started) {
                    CommandDispatchOutcome.Pending(command.kind)
                } else {
                    CommandDispatchOutcome.Rejected(command.kind)
                }
            }
            BrowserCommandKind.Reload -> outcome(command, actions.reload(), CommandResult.Reloaded)
            BrowserCommandKind.StopLoading -> outcome(
                command,
                actions.stopLoading(),
                CommandResult.LoadingStopped,
            )
            BrowserCommandKind.PinTab -> outcome(
                command,
                actions.setSelectedTabPinned(true),
                CommandResult.TabPinned,
            )
            BrowserCommandKind.UnpinTab -> outcome(
                command,
                actions.setSelectedTabPinned(false),
                CommandResult.TabUnpinned,
            )
            BrowserCommandKind.CloseDuplicateTabs -> {
                val closedCount = actions.closeDuplicateTabs(command.duplicateTabIds)
                outcome(
                    command,
                    closedCount > 0,
                    CommandResult.DuplicateTabsClosed(closedCount),
                )
            }
            BrowserCommandKind.MoveTabToProfile -> {
                val profileId = command.targetProfileId
                    ?: return CommandDispatchOutcome.Rejected(command.kind)
                outcome(
                    command,
                    actions.moveSelectedTabToProfile(profileId),
                    CommandResult.TabMoved(command.targetProfileLabel),
                )
            }
            BrowserCommandKind.SwitchProfile -> {
                val profileId = command.targetProfileId
                    ?: return CommandDispatchOutcome.Rejected(command.kind)
                outcome(
                    command,
                    actions.switchProfile(profileId),
                    CommandResult.ProfileSwitched(command.targetProfileLabel),
                )
            }
            BrowserCommandKind.NewRegularTab -> outcome(
                command,
                actions.createTab(isIncognito = false),
                CommandResult.RegularTabCreated,
            )
            BrowserCommandKind.NewIncognitoTab -> outcome(
                command,
                actions.createTab(isIncognito = true),
                CommandResult.IncognitoTabCreated,
            )
            BrowserCommandKind.OpenSettings -> outcome(
                command,
                actions.openSettings(),
                CommandResult.SettingsOpened,
            )
        }
    }

    private fun outcome(
        command: BrowserCommand,
        succeeded: Boolean,
        result: CommandResult,
    ): CommandDispatchOutcome = if (succeeded) {
        CommandDispatchOutcome.Succeeded(result)
    } else {
        CommandDispatchOutcome.Rejected(command.kind)
    }
}
