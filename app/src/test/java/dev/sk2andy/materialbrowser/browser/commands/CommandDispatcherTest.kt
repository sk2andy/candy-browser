package dev.sk2andy.materialbrowser.browser.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDispatcherTest {
    @Test
    fun `dispatch routes every fixed execution id`() {
        val actions = RecordingActions()
        val expected = listOf(
            BrowserCommandKind.ClearCacheAndReload to "cache",
            BrowserCommandKind.ClearCookiesAndReload to "cookies",
            BrowserCommandKind.Reload to "reload",
            BrowserCommandKind.StopLoading to "stop",
            BrowserCommandKind.PinTab to "pin:true",
            BrowserCommandKind.UnpinTab to "pin:false",
            BrowserCommandKind.CloseDuplicateTabs to "duplicates",
            BrowserCommandKind.NewRegularTab to "new:false",
            BrowserCommandKind.NewIncognitoTab to "new:true",
            BrowserCommandKind.OpenSettings to "settings",
        )

        expected.forEach { (kind, event) ->
            assertTrue(CommandDispatcher.dispatch(BrowserCommand(checkNotNull(kind.executionId), kind), actions))
            assertEquals(event, actions.events.last())
        }
    }

    @Test
    fun `dispatch preserves dynamic profile target`() {
        val actions = RecordingActions()

        CommandDispatcher.dispatch(
            BrowserCommand("move-tab-to-profile:work-42", BrowserCommandKind.MoveTabToProfile, "work-42"),
            actions,
        )
        CommandDispatcher.dispatch(
            BrowserCommand("switch-profile:travel-9", BrowserCommandKind.SwitchProfile, "travel-9"),
            actions,
        )

        assertEquals(listOf("move:work-42", "switch:travel-9"), actions.events)
    }

    @Test
    fun `dynamic command without target never dispatches`() {
        val actions = RecordingActions()

        assertFalse(
            CommandDispatcher.dispatch(
                BrowserCommand("switch-profile:", BrowserCommandKind.SwitchProfile),
                actions,
            ),
        )
        assertTrue(actions.events.isEmpty())
    }

    private class RecordingActions : CommandActions {
        val events = mutableListOf<String>()
        override fun clearCacheAndReload() = record("cache")
        override fun clearCookiesAndReload() = record("cookies")
        override fun reload() = record("reload")
        override fun stopLoading() = record("stop")
        override fun setSelectedTabPinned(isPinned: Boolean) = record("pin:$isPinned")
        override fun closeDuplicateTabs(confirmedTabIds: List<String>) = record("duplicates")
        override fun moveSelectedTabToProfile(profileId: String) = record("move:$profileId")
        override fun switchProfile(profileId: String) = record("switch:$profileId")
        override fun createTab(isIncognito: Boolean) = record("new:$isIncognito")
        override fun openSettings() = record("settings")
        private fun record(event: String): Boolean {
            events += event
            return true
        }
    }
}
