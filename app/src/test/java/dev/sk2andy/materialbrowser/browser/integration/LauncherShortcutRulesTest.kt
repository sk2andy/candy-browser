package dev.sk2andy.materialbrowser.browser.integration

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherShortcutRulesTest {
    @Test
    fun `fixed shortcut actions resolve without profile state`() {
        assertEquals(
            LauncherShortcutTarget.NewTab,
            resolve(LauncherShortcutRules.ACTION_NEW_TAB),
        )
        assertEquals(
            LauncherShortcutTarget.NewPrivateTab,
            resolve(LauncherShortcutRules.ACTION_NEW_PRIVATE_TAB),
        )
    }

    @Test
    fun `profile shortcut resolves only for enabled available profile`() {
        assertEquals(
            LauncherShortcutTarget.Profile("work"),
            resolve(
                action = LauncherShortcutRules.ACTION_OPEN_PROFILE,
                profileId = "work",
                availableProfileIds = setOf("candy", "work"),
            ),
        )
        assertNull(
            resolve(
                action = LauncherShortcutRules.ACTION_OPEN_PROFILE,
                profileId = "deleted",
                availableProfileIds = setOf("candy", "work"),
            ),
        )
        assertNull(
            resolve(
                action = LauncherShortcutRules.ACTION_OPEN_PROFILE,
                profileId = "work",
                availableProfileIds = setOf("candy", "work"),
                profilesEnabled = false,
            ),
        )
    }

    @Test
    fun `recent profiles exclude active profile and sort by regular tab use`() {
        val profiles = listOf(
            BrowserProfile("candy", "🍬"),
            BrowserProfile("work", "💼"),
            BrowserProfile("travel", "🧳"),
        )
        val tabs = listOf(
            tab(id = "candy", profileId = "candy", lastAccessedAt = 100L),
            tab(id = "work-old", profileId = "work", lastAccessedAt = 20L),
            tab(id = "work-new", profileId = "work", lastAccessedAt = 80L),
            tab(id = "travel", profileId = "travel", lastAccessedAt = 60L),
        )

        val state = LauncherShortcutRules.state(
            profiles = profiles,
            tabs = tabs,
            activeProfileId = "candy",
            profilesEnabled = true,
        )

        assertEquals(
            listOf(
                LauncherProfileShortcut("work", "💼"),
                LauncherProfileShortcut("travel", "🧳"),
            ),
            state.recentProfiles,
        )
    }

    @Test
    fun `private activity does not change recent profile order`() {
        val profiles = listOf(
            BrowserProfile("candy", "🍬"),
            BrowserProfile("work", "💼"),
            BrowserProfile("travel", "🧳"),
        )
        val tabs = listOf(
            tab(id = "work", profileId = "work", lastAccessedAt = 80L),
            tab(id = "travel", profileId = "travel", lastAccessedAt = 60L),
            tab(
                id = "travel-private",
                profileId = "travel",
                lastAccessedAt = 100L,
                isIncognito = true,
            ),
        )

        val state = LauncherShortcutRules.state(
            profiles = profiles,
            tabs = tabs,
            activeProfileId = "candy",
            profilesEnabled = true,
        )

        assertEquals(listOf("work", "travel"), state.recentProfiles.map { it.profileId })
    }

    @Test
    fun `disabled profiles publish no profile shortcuts`() {
        val state = LauncherShortcutRules.state(
            profiles = listOf(
                BrowserProfile("candy", "🍬"),
                BrowserProfile("work", "💼"),
            ),
            tabs = listOf(tab(id = "work", profileId = "work", lastAccessedAt = 10L)),
            activeProfileId = "candy",
            profilesEnabled = false,
        )

        assertEquals(emptyList<LauncherProfileShortcut>(), state.recentProfiles)
    }

    @Test
    fun `recent profiles exclude synced profiles and stay capped at two`() {
        val state = LauncherShortcutRules.state(
            profiles = listOf(
                BrowserProfile("candy", "🍬"),
                BrowserProfile("work", "💼"),
                BrowserProfile("travel", "🧳"),
                BrowserProfile("games", "🎮"),
                BrowserProfile("synced", "📱", syncedDeviceId = "device"),
            ),
            tabs = listOf(
                tab(id = "work", profileId = "work", lastAccessedAt = 40L),
                tab(id = "travel", profileId = "travel", lastAccessedAt = 30L),
                tab(id = "games", profileId = "games", lastAccessedAt = 20L),
                tab(id = "synced", profileId = "synced", lastAccessedAt = 50L),
            ),
            activeProfileId = "candy",
            profilesEnabled = true,
        )

        assertEquals(listOf("work", "travel"), state.recentProfiles.map { it.profileId })
    }

    @Test
    fun `private shortcut keeps local profile or falls back from synced profile`() {
        val profiles = listOf(
            BrowserProfile("candy", "🍬"),
            BrowserProfile("synced", "📱", syncedDeviceId = "device"),
        )

        assertEquals(
            "candy",
            LauncherShortcutRules.privateTargetProfileId(
                profiles = profiles,
                activeProfileId = "candy",
                profileIsolationSupported = true,
            ),
        )
        assertEquals(
            "candy",
            LauncherShortcutRules.privateTargetProfileId(
                profiles = profiles,
                activeProfileId = "synced",
                profileIsolationSupported = true,
            ),
        )
        assertNull(
            LauncherShortcutRules.privateTargetProfileId(
                profiles = profiles,
                activeProfileId = "synced",
                profileIsolationSupported = false,
            ),
        )
    }

    private fun resolve(
        action: String?,
        profileId: String? = null,
        availableProfileIds: Set<String> = emptySet(),
        profilesEnabled: Boolean = true,
    ) = LauncherShortcutRules.resolve(
        action = action,
        profileId = profileId,
        availableProfileIds = availableProfileIds,
        profilesEnabled = profilesEnabled,
    )

    private fun tab(
        id: String,
        profileId: String,
        lastAccessedAt: Long,
        isIncognito: Boolean = false,
    ) = BrowserTab(
        id = id,
        lastAccessedAt = lastAccessedAt,
        profileId = profileId,
        isIncognito = isIncognito,
    )
}
