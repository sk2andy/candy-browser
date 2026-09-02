package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDescriptor
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncedProfileRuntimeRulesTest {
    @Test
    fun runtimeProfileCarriesRemoteIdentityWithoutIsolation() {
        val result = SyncedProfileRuntimeRules.runtimeProfile(profile(), "💻")

        assertEquals("synced:desktop", result.id)
        assertEquals("desktop", result.syncedDeviceId)
        assertEquals("Workstation", result.syncedDisplayName)
        assertEquals("computer", result.syncedIconCatalogId)
        assertEquals("💻", result.syncedIconEmoji)
        assertTrue(result.isSynced)
        assertFalse(result.isolationEnabled)
    }

    @Test
    fun reconcilePreservesRuntimeIdentityAndReportsRemoteNavigationAndClose() {
        val existing = listOf(
            BrowserTab(
                id = "runtime-kept",
                lastAccessedAt = 10,
                profileId = "synced:desktop",
                url = "https://old.example/",
                syncCandyId = "kept",
            ),
            BrowserTab(
                id = "runtime-closed",
                lastAccessedAt = 11,
                profileId = "synced:desktop",
                url = "https://closed.example/",
                syncCandyId = "closed",
            ),
        )
        val remote = profile(
            tabs = listOf(
                tab("new", 0, "https://new.example/"),
                tab("kept", 1, "https://changed.example/"),
            ),
        )

        val result = SyncedProfileRuntimeRules.reconcile(remote, existing, nowMillis = 100)

        assertEquals(listOf("new", "kept"), result.tabs.map { it.syncCandyId })
        assertEquals("runtime-kept", result.tabs.last().id)
        assertEquals(setOf("runtime-closed"), result.removedRuntimeTabIds)
        assertEquals(
            listOf(SyncedTabNavigation("runtime-kept", "https://changed.example/")),
            result.navigations,
        )
    }

    @Test
    fun outboundExcludesPrivateBlankAndInternalUrls() {
        val base = BrowserTab(
            id = "runtime",
            lastAccessedAt = 1,
            profileId = "synced:desktop",
            syncCandyId = "candy-id",
        )

        assertNull(SyncedProfileRuntimeRules.outboundTab(base, 0, base.id))
        assertNull(
            SyncedProfileRuntimeRules.outboundTab(
                base.copy(url = "https://example.com", isIncognito = true),
                0,
                base.id,
            ),
        )
        assertNull(
            SyncedProfileRuntimeRules.outboundTab(
                base.copy(url = "file:///secret"),
                0,
                base.id,
            ),
        )
    }

    @Test
    fun outboundPreservesStableCandyIdentityAndPosition() {
        val browserTab = BrowserTab(
            id = "runtime",
            lastAccessedAt = 1,
            profileId = "synced:desktop",
            isPinned = true,
            title = "Candy",
            url = "https://example.com/path",
            syncCandyId = "stable-candy-id",
        )

        val result = requireNotNull(
            SyncedProfileRuntimeRules.outboundTab(browserTab, 3, browserTab.id),
        )

        assertEquals("stable-candy-id", result.candyId)
        assertEquals(3, result.index)
        assertTrue(result.active)
        assertTrue(result.pinned)
    }

    @Test
    fun reconcileRetainsBlankAndDurablyPendingLocalTabs() {
        val blank = BrowserTab(
            id = "blank-runtime",
            lastAccessedAt = 5,
            profileId = "synced:desktop",
            syncCandyId = "blank-candy",
        )
        val pending = blank.copy(
            id = "pending-runtime",
            url = "https://pending.example/",
            syncCandyId = "pending-candy",
        )

        val result = SyncedProfileRuntimeRules.reconcile(
            profile = profile(),
            existingTabs = listOf(blank, pending),
            nowMillis = 10,
            locallyPendingCandyIds = setOf("pending-candy"),
        )

        assertEquals(
            listOf("blank-candy", "pending-candy"),
            result.tabs.map(BrowserTab::syncCandyId),
        )
        assertTrue(result.removedRuntimeTabIds.isEmpty())
    }

    private fun profile(tabs: List<SyncTab> = emptyList()) = SyncProfile(
        deviceId = "desktop",
        displayName = "Workstation",
        icon = SyncDeviceIconDescriptor("computer", 312),
        revision = 4,
        tabs = tabs,
        lastSeenAt = "2026-09-02T10:00:00Z",
    )

    private fun tab(candyId: String, index: Int, url: String) = SyncTab(
        candyId = candyId,
        windowId = 0,
        index = index,
        groupId = null,
        active = index == 0,
        pinned = false,
        title = candyId,
        url = url,
    )
}
