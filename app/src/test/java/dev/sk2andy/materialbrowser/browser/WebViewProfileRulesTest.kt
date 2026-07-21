package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewProfileRulesTest {
    private val shared = BrowserProfile(id = "shared", emoji = "🍬")
    private val isolated = BrowserProfile(
        id = "arbeit/é",
        emoji = "💼",
        isolationEnabled = true,
    )

    @Test
    fun `stable isolated name is injective and independent of emoji`() {
        val first = WebViewProfileRules.isolatedProfileName(isolated.id)
        val sameIdDifferentEmoji = WebViewProfileRules.isolatedProfileName(
            isolated.copy(emoji = "🧪").id,
        )
        val different = WebViewProfileRules.isolatedProfileName("arbeit/e")

        assertEquals("candy_profile_v1_6172626569742fc3a9", first)
        assertEquals(first, sameIdDifferentEmoji)
        assertNotEquals(first, different)
        assertTrue(WebViewProfileRules.isManagedIsolatedProfileName(first))
        assertFalse(WebViewProfileRules.isManagedIsolatedProfileName(DEFAULT_STORAGE_KEY))
    }

    @Test
    fun `assignment gates isolation on provider support`() {
        val tab = BrowserTab(id = "tab", lastAccessedAt = 1L, profileId = isolated.id)

        assertEquals(
            WebViewProfileAssignment.Isolated(
                WebViewProfileRules.isolatedProfileName(isolated.id),
            ),
            WebViewProfileRules.assignment(tab, listOf(shared, isolated), true),
        )
        assertEquals(
            WebViewProfileAssignment.Default,
            WebViewProfileRules.assignment(tab, listOf(shared, isolated), false),
        )
        assertEquals(
            WebViewProfileAssignment.Default,
            WebViewProfileRules.assignment(
                tab.copy(profileId = shared.id),
                listOf(shared, isolated),
                true,
            ),
        )
    }

    @Test
    fun `incognito wins over browser profile isolation and uses session name`() {
        val incognito = BrowserTab(
            id = "private",
            lastAccessedAt = 1L,
            profileId = isolated.id,
            isIncognito = true,
        )

        assertEquals(
            WebViewProfileAssignment.Incognito("private-session"),
            WebViewProfileRules.assignment(
                tab = incognito,
                profiles = listOf(isolated),
                multiProfileSupported = true,
                incognitoProfileName = "private-session",
            ),
        )
    }

    @Test
    fun `storage switch recreates regular tabs only`() {
        val tabs = listOf(
            BrowserTab(id = "regular", lastAccessedAt = 1L, profileId = isolated.id),
            BrowserTab(
                id = "private",
                lastAccessedAt = 2L,
                profileId = isolated.id,
                isIncognito = true,
            ),
            BrowserTab(id = "other", lastAccessedAt = 3L, profileId = shared.id),
        )

        assertEquals(
            setOf("regular"),
            WebViewProfileRules.regularTabIdsForStorageChange(tabs, isolated.id),
        )
        assertEquals(
            setOf("regular", "private"),
            WebViewProfileRules.tabIdsForProfileDeletion(tabs, isolated.id),
        )
    }

    @Test
    fun `profile deletion move preserves tab state while changing owner`() {
        val source = BrowserTab(
            id = "tab",
            lastAccessedAt = 42L,
            profileId = isolated.id,
            isPinned = true,
            title = "Example",
            url = "https://example.com/path",
        )
        val untouched = BrowserTab(id = "other", lastAccessedAt = 3L, profileId = shared.id)

        val moved = WebViewProfileRules.moveTabs(
            tabs = listOf(source, untouched),
            sourceProfileId = isolated.id,
            targetProfileId = shared.id,
        )

        assertEquals(source.copy(profileId = shared.id), moved.first())
        assertEquals(untouched, moved.last())
    }

    @Test
    fun `visible history URL preserves state for storage recreation`() {
        val tab = BrowserTab(
            id = "spa",
            lastAccessedAt = 7L,
            profileId = isolated.id,
            isPinned = true,
            title = "Single page app",
            url = "https://example.com/start",
        )

        val updated = WebViewProfileRules.withVisibleUrl(
            tab,
            "https://example.com/account#security",
        )

        assertEquals("https://example.com/account#security", updated.url)
        assertEquals(tab.copy(url = updated.url), updated)
        assertEquals(updated, WebViewProfileRules.withVisibleUrl(updated, null))
    }

    @Test
    fun `last WebView removal releases only unused non-default contexts`() {
        val assignments = linkedMapOf(
            "shared" to DEFAULT_STORAGE_KEY,
            "isolated-a" to "profile-a",
            "isolated-b" to "profile-a",
            "private" to "incognito-session",
        )

        assertEquals(
            emptySet<String>(),
            WebViewProfileRules.storageKeysLosingLastWebView(assignments, setOf("isolated-a")),
        )
        assertEquals(
            setOf("profile-a", "incognito-session"),
            WebViewProfileRules.storageKeysLosingLastWebView(
                assignments,
                setOf("isolated-a", "isolated-b", "private", "shared"),
            ),
        )
    }
}
