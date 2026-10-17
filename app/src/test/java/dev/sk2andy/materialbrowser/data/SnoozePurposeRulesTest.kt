package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.isFreshBlankTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SnoozePurposeRulesTest {
    @Test
    fun `purpose normalizes whitespace and bounds persisted text`() {
        assertEquals("Check returns", SnoozePurposeRules.normalize("  Check\n\treturns\u0000 "))
        assertEquals("", SnoozePurposeRules.normalize(" \n\t"))
        assertEquals(240, SnoozePurposeRules.normalize("x".repeat(1_000)).length)
        val normalized = SnoozePurposeRules.normalize(" Discuss   with Lisa ")
        assertEquals(normalized, SnoozePurposeRules.normalize(normalized))
    }

    @Test
    fun `editing purpose preserves due time and other tabs and accepts clearing`() {
        val pending = SnoozedTab(BrowserTab("pending", 1L), 200L, 1L)
        val other = SnoozedTab(BrowserTab("other", 1L), 300L, 1L)
        val updated = requireNotNull(SnoozeMutationRules.withPurpose(listOf(pending, other), "pending", " Check returns "))

        assertEquals("Check returns", updated.first().tab.snoozePurpose)
        assertEquals(200L, updated.first().wakeAtMillis)
        assertEquals(other, updated.last())
        assertEquals("", SnoozeMutationRules.withPurpose(updated, "pending", "")?.first()?.tab?.snoozePurpose)
        assertNull(SnoozeMutationRules.withPurpose(updated, "missing", "Do something"))
    }

    @Test
    fun `reschedule and wake preserve purpose`() {
        val pending = SnoozedTab(BrowserTab("pending", 1L, snoozePurpose = "Check returns"), 200L, 1L)
        val updated = requireNotNull(SnoozeMutationRules.rescheduled(listOf(pending), "pending", 300L, 100L))
        val result = SnoozeRestoreRules.restoreDue(
            tabs = emptyList(),
            snoozedTabs = updated,
            profiles = listOf(BrowserProfile("default", "🍬")),
            activeProfileId = "default",
            nowMillis = 400L,
            maxTabs = 10,
        )

        assertEquals("Check returns", updated.single().tab.snoozePurpose)
        assertEquals("Check returns", result.tabs.single().snoozePurpose)
        assertFalse(result.tabs.single().isFreshBlankTab)
    }

    @Test
    fun `failed startup restore retains pending purpose for retry`() {
        val pending = SnoozedTab(BrowserTab("pending", 1L, snoozePurpose = "Check returns"), 200L, 1L)
        val snapshot = SnoozeRestoreRules.settleStartupRestore(
            originalTabs = emptyList(),
            originalSnoozedTabs = listOf(pending),
            restoredTabs = listOf(pending.tab),
            remainingSnoozedTabs = emptyList(),
            snapshotPersisted = false,
        )

        assertEquals(emptyList<BrowserTab>(), snapshot.tabs)
        assertEquals("Check returns", snapshot.snoozedTabs.single().tab.snoozePurpose)
    }

    @Test
    fun `undo keeps purpose and rejects stale token after purpose edit`() {
        val pending = SnoozedTab(BrowserTab("pending", 1L, snoozePurpose = "Discuss with Lisa"), 200L, 1L)
        val token = SnoozeUndoToken(
            tabId = "pending",
            appliedSnoozedTab = pending,
            originalIndex = 0,
            originalSelectedTabId = "pending",
            selectedTabIdAfterSnooze = "other",
            replacementTabId = null,
            touchedTabBefore = null,
            touchedTabAfter = null,
        )
        val result = SnoozeUndoRules.undo(
            tabs = listOf(BrowserTab("other", 1L)),
            selectedTabId = "other",
            snoozedTabs = listOf(pending),
            token = token,
            maxTabs = 10,
        )

        assertEquals("Discuss with Lisa", result?.restoredTab?.snoozePurpose)
        val changedPurpose = requireNotNull(
            SnoozeMutationRules.withPurpose(listOf(pending), "pending", "Different purpose"),
        )
        assertNull(
            SnoozeUndoRules.undo(
                tabs = listOf(BrowserTab("other", 1L)),
                selectedTabId = "other",
                snoozedTabs = changedPurpose,
                token = token,
                maxTabs = 10,
            ),
        )
    }
}
