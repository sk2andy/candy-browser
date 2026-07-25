package dev.sk2andy.materialbrowser.browser.commands

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserCommandRegistryTest {
    @Test
    fun `registry uses state-specific reload and pin commands`() {
        val regular = BrowserCommandRegistry.commands(context())
        val busyPinned = BrowserCommandRegistry.commands(
            context(selectedTab = tab().copy(isLoading = true, isPinned = true)),
        )

        assertTrue(regular.any { it.kind == BrowserCommandKind.Reload })
        assertTrue(regular.any { it.kind == BrowserCommandKind.PinTab })
        assertFalse(regular.any { it.kind == BrowserCommandKind.StopLoading })
        assertTrue(busyPinned.any { it.kind == BrowserCommandKind.StopLoading })
        assertTrue(busyPinned.any { it.kind == BrowserCommandKind.UnpinTab })
    }

    @Test
    fun `registry creates stable move and switch targets for every other profile`() {
        val commands = BrowserCommandRegistry.commands(
            context(
                profiles = listOf(
                    BrowserProfile("home", "🏠"),
                    BrowserProfile("work", "💼"),
                    BrowserProfile("travel", "✈️"),
                ),
            ),
        )

        assertEquals(
            listOf("move-tab-to-profile:work", "move-tab-to-profile:travel"),
            commands.filter { it.kind == BrowserCommandKind.MoveTabToProfile }
                .map(BrowserCommand::executionId),
        )
        assertEquals(
            listOf("switch-profile:work", "switch-profile:travel"),
            commands.filter { it.kind == BrowserCommandKind.SwitchProfile }
                .map(BrowserCommand::executionId),
        )
        assertEquals(
            listOf("2 · 💼", "3 · ✈️"),
            commands.filter { it.kind == BrowserCommandKind.SwitchProfile }
                .map(BrowserCommand::targetProfileLabel),
        )
    }

    @Test
    fun `duplicate confirmation appears only when multiple tabs close`() {
        val one = BrowserCommandRegistry.commands(context(duplicateTabIds = listOf("one")))
            .single { it.kind == BrowserCommandKind.CloseDuplicateTabs }
        val several = BrowserCommandRegistry.commands(
            context(duplicateTabIds = listOf("one", "two", "three")),
        )
            .single { it.kind == BrowserCommandKind.CloseDuplicateTabs }

        assertEquals(CommandConfirmation.None, one.confirmation)
        assertEquals(CommandConfirmation.CloseMultipleDuplicates, several.confirmation)
    }

    @Test
    fun `registry hides impossible tab creation and move commands`() {
        val commands = BrowserCommandRegistry.commands(
            context(canCreateTab = false, canMoveSelectedTab = false),
        )

        assertFalse(commands.any { it.kind == BrowserCommandKind.NewRegularTab })
        assertFalse(commands.any { it.kind == BrowserCommandKind.NewIncognitoTab })
        assertFalse(commands.any { it.kind == BrowserCommandKind.MoveTabToProfile })
        assertTrue(commands.any { it.kind == BrowserCommandKind.SwitchProfile })
    }

    @Test
    fun `registry hides incognito command when private storage is unsupported`() {
        val commands = BrowserCommandRegistry.commands(
            context(canCreateTab = true, canCreateIncognitoTab = false),
        )

        assertTrue(commands.any { it.kind == BrowserCommandKind.NewRegularTab })
        assertFalse(commands.any { it.kind == BrowserCommandKind.NewIncognitoTab })
    }

    @Test
    fun `maximum profile registry exposes unique targets even with repeated emoji`() {
        val profiles = (1..12).map {
            BrowserProfile(if (it == 1) "home" else "profile-$it", "🍬")
        }

        val commands = BrowserCommandRegistry.commands(context(profiles = profiles))
        val targeted = commands.filter { it.targetProfileId != null }

        assertEquals(22, targeted.size)
        assertEquals(22, targeted.map(BrowserCommand::executionId).distinct().size)
        assertEquals(11, targeted.map(BrowserCommand::targetProfileLabel).distinct().size)
    }

    private fun context(
        selectedTab: BrowserTab = tab(),
        profiles: List<BrowserProfile> = listOf(
            BrowserProfile("home", "🏠"),
            BrowserProfile("work", "💼"),
        ),
        duplicateTabIds: List<String> = emptyList(),
        canCreateTab: Boolean = true,
        canCreateIncognitoTab: Boolean = canCreateTab,
        canMoveSelectedTab: Boolean = true,
        hasLoadedPage: Boolean = true,
        canClearCookies: Boolean = true,
    ) = CommandContext(
        selectedTab = selectedTab,
        profiles = profiles,
        activeProfileId = "home",
        duplicateTabIds = duplicateTabIds,
        canCreateTab = canCreateTab,
        canCreateIncognitoTab = canCreateIncognitoTab,
        canMoveSelectedTab = canMoveSelectedTab,
        hasLoadedPage = hasLoadedPage,
        canClearCookies = canClearCookies,
    )

    private fun tab() = BrowserTab(id = "tab", lastAccessedAt = 1)
}
