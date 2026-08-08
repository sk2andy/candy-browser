package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeRestoreRulesTest {
    private val profiles = listOf(
        BrowserProfile("candy", "🍬"),
        BrowserProfile("work", "💼"),
    )

    @Test
    fun `due tab restores once with profile and pin metadata`() {
        val snoozed = snoozedTab("due", wakeAt = 90L, profileId = "work", pinned = true)

        val first = restore(emptyList(), listOf(snoozed), now = 100L)
        val second = restore(first.tabs, listOf(snoozed), now = 100L)

        assertEquals(listOf("due"), first.tabs.map { it.id })
        assertEquals("work", first.tabs.single().profileId)
        assertEquals(100L, first.tabs.single().lastAccessedAt)
        assertTrue(first.tabs.single().isPinned)
        assertEquals(setOf("due"), first.completedTabIds)
        assertEquals(listOf("due"), second.tabs.map { it.id })
        assertEquals(setOf("due"), second.completedTabIds)
    }

    @Test
    fun `future tab remains pending`() {
        val result = restore(emptyList(), listOf(snoozedTab("future", 101L)), now = 100L)

        assertTrue(result.tabs.isEmpty())
        assertTrue(result.completedTabIds.isEmpty())
    }

    @Test
    fun `missing profile falls back to active profile`() {
        val result = restore(
            emptyList(),
            listOf(snoozedTab("due", 90L, profileId = "deleted")),
            now = 100L,
        )

        assertEquals("work", result.tabs.single().profileId)
    }

    @Test
    fun `full tab list leaves due tab pending`() {
        val result = SnoozeRestoreRules.restoreDue(
            tabs = listOf(BrowserTab("open", 1L)),
            snoozedTabs = listOf(snoozedTab("due", 90L)),
            profiles = profiles,
            activeProfileId = "candy",
            nowMillis = 100L,
            maxTabs = 1,
        )

        assertEquals(listOf("open"), result.tabs.map { it.id })
        assertTrue(result.completedTabIds.isEmpty())
    }

    @Test
    fun `private tab is rejected and discarded by restore boundary`() {
        val privateTab = snoozedTab("private", 90L).copy(
            tab = BrowserTab("private", 1L, isIncognito = true),
        )

        assertFalse(SnoozeRules.canSnooze(privateTab.tab, 100L, 90L))
        val result = restore(emptyList(), listOf(privateTab), now = 100L)
        assertTrue(result.tabs.isEmpty())
        assertEquals(setOf("private"), result.completedTabIds)
    }

    @Test
    fun `snooze requires future wake time`() {
        val tab = BrowserTab("regular", 1L)

        assertFalse(SnoozeRules.canSnooze(tab, wakeAtMillis = 100L, nowMillis = 100L))
        assertTrue(SnoozeRules.canSnooze(tab, wakeAtMillis = 101L, nowMillis = 100L))
    }

    @Test
    fun `scheduler retries overdue tabs without busy loop`() {
        val overdue = snoozedTab("overdue", wakeAt = 90L)

        assertEquals(1_000L + 15 * 60 * 1_000L, SnoozeScheduleRules.nextTriggerAt(listOf(overdue), 1_000L))
        assertEquals(null, SnoozeScheduleRules.nextTriggerAt(emptyList(), 1_000L))
    }

    @Test
    fun `reschedule reorders tabs and rejects non-future time`() {
        val first = snoozedTab("first", 200L)
        val second = snoozedTab("second", 300L)

        val rescheduled = SnoozeMutationRules.rescheduled(
            listOf(first, second),
            tabId = "second",
            wakeAtMillis = 150L,
            nowMillis = 100L,
        )

        assertEquals(listOf("second", "first"), rescheduled?.map { it.tab.id })
        assertEquals(
            null,
            SnoozeMutationRules.rescheduled(listOf(first), "first", 100L, 100L),
        )
    }

    @Test
    fun `delete removes only requested snooze and missing id is no-op`() {
        val first = snoozedTab("first", 200L)
        val second = snoozedTab("second", 300L)

        assertEquals(
            listOf("second"),
            SnoozeMutationRules.deleted(listOf(first, second), "first")?.map { it.tab.id },
        )
        assertEquals(null, SnoozeMutationRules.deleted(listOf(first), "missing"))
    }

    @Test
    fun `startup restore keeps original snapshot when atomic persistence fails`() {
        val originalTab = BrowserTab("active", 1L)
        val pending = snoozedTab("due", 90L)
        val restoredTab = pending.tab.copy(lastAccessedAt = 100L)

        val snapshot = SnoozeRestoreRules.settleStartupRestore(
            originalTabs = listOf(originalTab),
            originalSnoozedTabs = listOf(pending),
            restoredTabs = listOf(originalTab, restoredTab),
            remainingSnoozedTabs = emptyList(),
            snapshotPersisted = false,
        )

        assertEquals(listOf("active"), snapshot.tabs.map { it.id })
        assertEquals(listOf("due"), snapshot.snoozedTabs.map { it.tab.id })
    }

    @Test
    fun `startup restore publishes restored snapshot after atomic persistence`() {
        val originalTab = BrowserTab("active", 1L)
        val pending = snoozedTab("due", 90L)
        val restoredTab = pending.tab.copy(lastAccessedAt = 100L)

        val snapshot = SnoozeRestoreRules.settleStartupRestore(
            originalTabs = listOf(originalTab),
            originalSnoozedTabs = listOf(pending),
            restoredTabs = listOf(originalTab, restoredTab),
            remainingSnoozedTabs = emptyList(),
            snapshotPersisted = true,
        )

        assertEquals(listOf("active", "due"), snapshot.tabs.map { it.id })
        assertTrue(snapshot.snoozedTabs.isEmpty())
    }

    @Test
    fun `undo background snooze restores position without changing selection`() {
        val first = BrowserTab("first", 1L)
        val snoozed = snoozedTab("background", wakeAt = 1_000L)
        val third = BrowserTab("third", 1L)
        val token = SnoozeUndoToken(
            tabId = snoozed.tab.id,
            appliedSnoozedTab = snoozed,
            originalIndex = 1,
            originalSelectedTabId = first.id,
            selectedTabIdAfterSnooze = first.id,
            replacementTabId = null,
            touchedTabBefore = null,
            touchedTabAfter = null,
        )

        val result = SnoozeUndoRules.undo(
            tabs = listOf(first, third),
            selectedTabId = first.id,
            snoozedTabs = listOf(snoozed),
            token = token,
            maxTabs = 12,
        )

        assertEquals(listOf("first", "background", "third"), result?.tabs?.map { it.id })
        assertEquals("first", result?.selectedTabId)
        assertEquals(snoozed.tab.lastAccessedAt, result?.restoredTab?.lastAccessedAt)
        assertTrue(result?.snoozedTabs?.isEmpty() == true)
    }

    @Test
    fun `undo last selected tab removes synthetic blank and restores selection`() {
        val snoozed = snoozedTab("only", wakeAt = 1_000L)
        val replacement = BrowserTab("replacement", 10L)
        val token = SnoozeUndoToken(
            tabId = snoozed.tab.id,
            appliedSnoozedTab = snoozed,
            originalIndex = 0,
            originalSelectedTabId = snoozed.tab.id,
            selectedTabIdAfterSnooze = replacement.id,
            replacementTabId = replacement.id,
            touchedTabBefore = replacement,
            touchedTabAfter = replacement,
        )

        val result = SnoozeUndoRules.undo(
            tabs = listOf(replacement),
            selectedTabId = replacement.id,
            snoozedTabs = listOf(snoozed),
            token = token,
            maxTabs = 12,
        )

        assertEquals(listOf("only"), result?.tabs?.map { it.id })
        assertEquals("only", result?.selectedTabId)
        assertEquals("replacement", result?.removedReplacementTabId)
    }

    @Test
    fun `undo token does not remove a snooze changed after snackbar appeared`() {
        val snoozed = snoozedTab("changed", wakeAt = 1_000L)
        val token = SnoozeUndoToken(
            tabId = snoozed.tab.id,
            appliedSnoozedTab = snoozed,
            originalIndex = 0,
            originalSelectedTabId = "active",
            selectedTabIdAfterSnooze = "active",
            replacementTabId = null,
            touchedTabBefore = null,
            touchedTabAfter = null,
        )

        val result = SnoozeUndoRules.undo(
            tabs = listOf(BrowserTab("active", 1L)),
            selectedTabId = "active",
            snoozedTabs = listOf(snoozed.copy(wakeAtMillis = 2_000L)),
            token = token,
            maxTabs = 12,
        )

        assertEquals(null, result)
    }

    private fun restore(
        tabs: List<BrowserTab>,
        snoozed: List<SnoozedTab>,
        now: Long,
    ) = SnoozeRestoreRules.restoreDue(
        tabs = tabs,
        snoozedTabs = snoozed,
        profiles = profiles,
        activeProfileId = "work",
        nowMillis = now,
        maxTabs = 12,
    )

    private fun snoozedTab(
        id: String,
        wakeAt: Long,
        profileId: String = "candy",
        pinned: Boolean = false,
    ) = SnoozedTab(
        tab = BrowserTab(id, 1L, profileId = profileId, isPinned = pinned),
        wakeAtMillis = wakeAt,
        createdAtMillis = 1L,
    )
}
