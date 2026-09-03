package dev.sk2andy.materialbrowser.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTabRulesTest {
    private val first = tab("first", 0, pinned = false)
    private val second = tab("second", 1, pinned = true)
    private val profile = SyncProfile(
        deviceId = "device-1",
        displayName = "Phone",
        icon = SyncDeviceIconDescriptor("phone", 127),
        revision = 4,
        tabs = listOf(first, second),
        lastSeenAt = "2026-09-02T10:00:00Z",
    )

    @Test
    fun `private and internal tabs never become outbound payloads`() {
        assertNull(SyncTabRules.outboundTab(first, isPrivate = true))
        assertNull(SyncTabRules.outboundTab(first.copy(url = "file:///secret"), isPrivate = false))
        assertNull(SyncTabRules.outboundTab(first.copy(url = "about:blank"), isPrivate = false))
        assertEquals(first, SyncTabRules.outboundTab(first, isPrivate = false))
    }

    @Test
    fun `synced profile supports navigation close open reorder and pin`() {
        val navigated = applied(
            SyncTabRules.apply(
                profile,
                SyncPendingMutation.Navigate("m1", "device-1", "first", "Next", "https://next.example/"),
            ),
        )
        assertEquals("https://next.example/", navigated.tabs.first().url)

        val closed = applied(SyncTabRules.apply(profile, SyncPendingMutation.Close("m2", "device-1", "first")))
        assertEquals(listOf("second"), closed.tabs.map(SyncTab::candyId))
        assertEquals(0, closed.tabs.single().index)
        assertTrue(closed.tabs.single().active)

        val opened = applied(
            SyncTabRules.apply(profile, SyncPendingMutation.Open("m3", "device-1", tab("third", 2))),
        )
        assertEquals(listOf("first", "second", "third"), opened.tabs.map(SyncTab::candyId))

        val activeOpened = applied(
            SyncTabRules.apply(
                profile,
                SyncPendingMutation.Open("m-active", "device-1", tab("third", 2).copy(active = true)),
            ),
        )
        assertEquals(listOf("third"), activeOpened.tabs.filter(SyncTab::active).map(SyncTab::candyId))

        val reordered = applied(
            SyncTabRules.apply(profile, SyncPendingMutation.Reorder("m4", "device-1", listOf("second", "first"))),
        )
        assertEquals(listOf("second", "first"), reordered.tabs.map(SyncTab::candyId))
        assertEquals(listOf(0, 1), reordered.tabs.map(SyncTab::index))

        val pinned = applied(
            SyncTabRules.apply(profile, SyncPendingMutation.SetPinned("m5", "device-1", "first", true)),
        )
        assertTrue(pinned.tabs.first().pinned)
    }

    @Test
    fun `reorder rejects missing duplicate and foreign tab identities`() {
        assertEquals(
            SyncMutationResult.InvalidTab,
            SyncTabRules.apply(profile, SyncPendingMutation.Reorder("m1", "device-1", listOf("first"))),
        )
        assertEquals(
            SyncMutationResult.InvalidTab,
            SyncTabRules.apply(profile, SyncPendingMutation.Reorder("m2", "device-1", listOf("first", "first"))),
        )
        assertEquals(
            SyncMutationResult.InvalidTab,
            SyncTabRules.apply(profile, SyncPendingMutation.Reorder("m3", "device-1", listOf("first", "other"))),
        )
    }

    private fun applied(result: SyncMutationResult): SyncProfile =
        (result as SyncMutationResult.Applied).profile

    private fun tab(id: String, index: Int, pinned: Boolean = false) = SyncTab(
        candyId = id,
        windowId = 0,
        index = index,
        groupId = null,
        active = index == 0,
        pinned = pinned,
        title = id,
        url = "https://example.com/$id",
    )
}
