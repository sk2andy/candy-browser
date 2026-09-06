package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeckoSessionStateSnapshotRulesTest {
    @Test
    fun `regular same tab and profile restores a supported snapshot`() {
        val snapshot = GeckoSessionStateSnapshotRules.forPersistence(
            tabId = "tab-1",
            profileId = "work",
            isPrivate = false,
            encodedState = "{state}",
        )

        assertEquals(
            GeckoSessionStateRestoreDecision.Restore(requireNotNull(snapshot)),
            GeckoSessionStateSnapshotRules.restoreDecision(
                snapshot = snapshot,
                tabId = "tab-1",
                profileId = "work",
                isPrivate = false,
            ),
        )
    }

    @Test
    fun `private state never enters persistence or restore`() {
        assertNull(
            GeckoSessionStateSnapshotRules.forPersistence(
                tabId = "tab-1",
                profileId = "work",
                isPrivate = true,
                encodedState = "{state}",
            ),
        )

        assertEquals(
            GeckoSessionStateRestoreDecision.PrivateTab,
            GeckoSessionStateSnapshotRules.restoreDecision(
                snapshot = GeckoSessionStateSnapshot(
                    tabId = "tab-1",
                    profileId = "work",
                    encodedState = "{state}",
                ),
                tabId = "tab-1",
                profileId = "work",
                isPrivate = true,
            ),
        )
    }

    @Test
    fun `stale profile version and corrupt payload are rejected distinctly`() {
        val base = GeckoSessionStateSnapshot(
            tabId = "tab-1",
            profileId = "work",
            encodedState = "{state}",
        )

        assertEquals(
            GeckoSessionStateRestoreDecision.StaleTab,
            GeckoSessionStateSnapshotRules.restoreDecision(
                snapshot = base,
                tabId = "tab-2",
                profileId = "work",
                isPrivate = false,
            ),
        )
        assertEquals(
            GeckoSessionStateRestoreDecision.ProfileMismatch,
            GeckoSessionStateSnapshotRules.restoreDecision(
                snapshot = base,
                tabId = "tab-1",
                profileId = "personal",
                isPrivate = false,
            ),
        )
        assertEquals(
            GeckoSessionStateRestoreDecision.UnsupportedVersion,
            GeckoSessionStateSnapshotRules.restoreDecision(
                snapshot = base.copy(formatVersion = 2),
                tabId = "tab-1",
                profileId = "work",
                isPrivate = false,
            ),
        )
        assertEquals(
            GeckoSessionStateRestoreDecision.Malformed,
            GeckoSessionStateSnapshotRules.restoreDecision(
                snapshot = base.copy(encodedState = ""),
                tabId = "tab-1",
                profileId = "work",
                isPrivate = false,
            ),
        )
    }
}
