package dev.sk2andy.materialbrowser.browser.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivePermissionLedgerTest {
    private val site = PermissionSiteKey("profile-a", "https://example.com")

    @Test
    fun parallelGrantsAreUnionedAndCanceledByExactToken() {
        val firstRequest = Any()
        val secondRequest = Any()
        val ledger = ActivePermissionLedger()
        ledger.record(
            firstRequest,
            ActivePermissionGrant("tab-a", site, setOf(SitePermission.Camera)),
        )
        ledger.record(
            secondRequest,
            ActivePermissionGrant("tab-a", site, setOf(SitePermission.Microphone)),
        )

        assertEquals(
            setOf(SitePermission.Camera, SitePermission.Microphone),
            ledger.permissions("tab-a", site),
        )
        assertTrue(ledger.drop(secondRequest))
        assertEquals(setOf(SitePermission.Camera), ledger.permissions("tab-a", site))
        assertFalse(ledger.drop(secondRequest))
    }

    @Test
    fun unrelatedDeniedOrOldRequestCannotEraseActiveGrant() {
        val activeRequest = Any()
        val unrelatedRequest = Any()
        val ledger = ActivePermissionLedger()
        ledger.record(
            activeRequest,
            ActivePermissionGrant("tab-a", site, setOf(SitePermission.Camera)),
        )

        assertFalse(ledger.drop(unrelatedRequest))
        assertTrue(ledger.has("tab-a", site, SitePermission.Camera))
        assertFalse(ledger.has("tab-b", site, SitePermission.Camera))
    }

    @Test
    fun tabCloseRemovesOnlyThatTabsGrants() {
        val firstRequest = Any()
        val secondRequest = Any()
        val ledger = ActivePermissionLedger()
        ledger.record(
            firstRequest,
            ActivePermissionGrant("tab-a", site, setOf(SitePermission.Camera)),
        )
        ledger.record(
            secondRequest,
            ActivePermissionGrant("tab-b", site, setOf(SitePermission.Microphone)),
        )

        assertTrue(ledger.dropTab("tab-a"))
        assertTrue(ledger.has("tab-b", site, SitePermission.Microphone))
        assertFalse(ledger.has("tab-a", site, SitePermission.Camera))
    }
}
