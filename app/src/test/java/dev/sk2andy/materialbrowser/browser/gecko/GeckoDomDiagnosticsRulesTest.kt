package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoDomDiagnosticsRulesTest {
    @Test
    fun `only enabled idle unambiguous regular target permits probe`() {
        assertNull(GeckoDomDiagnosticsRules.requestRejection(true, false, 1, false))
        assertEquals("disabled", GeckoDomDiagnosticsRules.requestRejection(false, true, 0, true))
        assertEquals("private_session", GeckoDomDiagnosticsRules.requestRejection(true, true, 1, false))
        assertEquals("target_unavailable", GeckoDomDiagnosticsRules.requestRejection(true, false, 0, false))
        assertEquals("ambiguous_target", GeckoDomDiagnosticsRules.requestRejection(true, false, 2, false))
        assertEquals("busy", GeckoDomDiagnosticsRules.requestRejection(true, false, 1, true))
    }

    @Test
    fun `publication rejects stale private empty or oversized snapshots`() {
        assertTrue(GeckoDomDiagnosticsRules.canPublish(1, 1, false, 24_576))
        assertFalse(GeckoDomDiagnosticsRules.canPublish(1, 2, false, 100))
        assertFalse(GeckoDomDiagnosticsRules.canPublish(1, 1, true, 100))
        assertFalse(GeckoDomDiagnosticsRules.canPublish(1, 1, false, 1))
        assertFalse(GeckoDomDiagnosticsRules.canPublish(1, 1, false, 24_577))
    }
}
