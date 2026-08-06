package dev.sk2andy.materialbrowser.browser.commands

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandDispatcherTest {
    @Test
    fun `dispatch routes every fixed execution id`() {
        val actions = RecordingActions()
        val expected = listOf(
            Triple(
                BrowserCommandKind.ClearCacheAndReload,
                "cache",
                CommandResult.CacheClearedAndReloaded,
            ),
            Triple(BrowserCommandKind.Reload, "reload", CommandResult.Reloaded),
            Triple(BrowserCommandKind.StopLoading, "stop", CommandResult.LoadingStopped),
            Triple(BrowserCommandKind.PinTab, "pin:true", CommandResult.TabPinned),
            Triple(BrowserCommandKind.UnpinTab, "pin:false", CommandResult.TabUnpinned),
            Triple(
                BrowserCommandKind.CloseDuplicateTabs,
                "duplicates:2",
                CommandResult.DuplicateTabsClosed(2),
            ),
            Triple(BrowserCommandKind.NewRegularTab, "new:false", CommandResult.RegularTabCreated),
            Triple(BrowserCommandKind.NewIncognitoTab, "new:true", CommandResult.IncognitoTabCreated),
            Triple(BrowserCommandKind.OpenSettings, "settings", CommandResult.SettingsOpened),
        )

        expected.forEach { (kind, event, result) ->
            val command = BrowserCommand(
                executionId = checkNotNull(kind.executionId),
                kind = kind,
                duplicateTabIds = if (kind == BrowserCommandKind.CloseDuplicateTabs) {
                    listOf("duplicate-1", "duplicate-2")
                } else {
                    emptyList()
                },
            )
            assertEquals(
                CommandDispatchOutcome.Succeeded(result),
                CommandDispatcher.dispatch(command, actions),
            )
            assertEquals(event, actions.events.last())
        }
    }

    @Test
    fun `cookie command reports success only after async completion`() {
        val actions = RecordingActions()
        val completedOutcomes = mutableListOf<CommandDispatchOutcome>()
        val command = BrowserCommand(
            checkNotNull(BrowserCommandKind.ClearCookiesAndReload.executionId),
            BrowserCommandKind.ClearCookiesAndReload,
        )

        val startedOutcome = CommandDispatcher.dispatch(
            command = command,
            actions = actions,
            onPendingOutcome = completedOutcomes::add,
        )

        assertEquals(
            CommandDispatchOutcome.Pending(BrowserCommandKind.ClearCookiesAndReload),
            startedOutcome,
        )
        assertEquals(emptyList<CommandDispatchOutcome>(), completedOutcomes)

        actions.completeCookies(completed = true)

        assertEquals(
            listOf(
                CommandDispatchOutcome.Succeeded(CommandResult.CookiesClearedAndReloaded),
            ),
            completedOutcomes,
        )
    }

    @Test
    fun `cookie command reports rejection when async completion fails`() {
        val actions = RecordingActions()
        val completedOutcomes = mutableListOf<CommandDispatchOutcome>()
        val command = BrowserCommand(
            checkNotNull(BrowserCommandKind.ClearCookiesAndReload.executionId),
            BrowserCommandKind.ClearCookiesAndReload,
        )

        CommandDispatcher.dispatch(command, actions, completedOutcomes::add)
        actions.completeCookies(completed = false)

        assertEquals(
            listOf(CommandDispatchOutcome.Rejected(BrowserCommandKind.ClearCookiesAndReload)),
            completedOutcomes,
        )
    }

    @Test
    fun `dispatch preserves dynamic profile target`() {
        val actions = RecordingActions()

        val moveOutcome = CommandDispatcher.dispatch(
            BrowserCommand(
                "move-tab-to-profile:work-42",
                BrowserCommandKind.MoveTabToProfile,
                "work-42",
                "2 · 💼",
            ),
            actions,
        )
        val switchOutcome = CommandDispatcher.dispatch(
            BrowserCommand(
                "switch-profile:travel-9",
                BrowserCommandKind.SwitchProfile,
                "travel-9",
                "3 · ✈️",
            ),
            actions,
        )

        assertEquals(listOf("move:work-42", "switch:travel-9"), actions.events)
        assertEquals(
            CommandDispatchOutcome.Succeeded(CommandResult.TabMoved("2 · 💼")),
            moveOutcome,
        )
        assertEquals(
            CommandDispatchOutcome.Succeeded(CommandResult.ProfileSwitched("3 · ✈️")),
            switchOutcome,
        )
    }

    @Test
    fun `dynamic command without target never dispatches`() {
        val actions = RecordingActions()

        assertEquals(
            CommandDispatchOutcome.Rejected(BrowserCommandKind.SwitchProfile),
            CommandDispatcher.dispatch(
                BrowserCommand("switch-profile:", BrowserCommandKind.SwitchProfile),
                actions,
            ),
        )
        assertEquals(emptyList<String>(), actions.events)
    }

    @Test
    fun `failed action produces typed rejection`() {
        val actions = RecordingActions(succeeds = false)

        val outcome = CommandDispatcher.dispatch(
            BrowserCommand("reload", BrowserCommandKind.Reload),
            actions,
        )

        assertEquals(CommandDispatchOutcome.Rejected(BrowserCommandKind.Reload), outcome)
        assertEquals(listOf("reload"), actions.events)
    }

    private class RecordingActions(private val succeeds: Boolean = true) : CommandActions {
        val events = mutableListOf<String>()
        private var cookieCompletion: ((Boolean) -> Unit)? = null
        override fun clearCacheAndReload() = record("cache")
        override fun clearCookiesAndReload(onComplete: (Boolean) -> Unit): Boolean {
            events += "cookies"
            if (succeeds) cookieCompletion = onComplete
            return succeeds
        }
        override fun reload() = record("reload")
        override fun stopLoading() = record("stop")
        override fun setSelectedTabPinned(isPinned: Boolean) = record("pin:$isPinned")
        override fun closeDuplicateTabs(confirmedTabIds: List<String>): Int {
            events += "duplicates:${confirmedTabIds.size}"
            return if (succeeds) confirmedTabIds.size else 0
        }
        override fun moveSelectedTabToProfile(profileId: String) = record("move:$profileId")
        override fun switchProfile(profileId: String) = record("switch:$profileId")
        override fun createTab(isIncognito: Boolean) = record("new:$isIncognito")
        override fun openSettings() = record("settings")
        fun completeCookies(completed: Boolean) {
            checkNotNull(cookieCompletion).invoke(completed)
            cookieCompletion = null
        }
        private fun record(event: String): Boolean {
            events += event
            return succeeds
        }
    }
}
