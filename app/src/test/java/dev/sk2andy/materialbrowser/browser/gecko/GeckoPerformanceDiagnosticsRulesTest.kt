package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoPerformanceDiagnosticsRulesTest {
    @Test
    fun `normal runtime permits idle recording`() {
        assertNull(GeckoPerformanceDiagnosticsRules.startRejection(true, true, false, false))
    }

    @Test
    fun `disabled builds reject recording before runtime checks`() {
        assertEquals("disabled", GeckoPerformanceDiagnosticsRules.startRejection(false, false, true, true))
    }

    @Test
    fun `private sessions reject recording even with active runtime`() {
        assertEquals("private_session", GeckoPerformanceDiagnosticsRules.startRejection(true, true, true, false))
    }

    @Test
    fun `missing runtime and pending export reject new recording`() {
        assertEquals("runtime_unavailable", GeckoPerformanceDiagnosticsRules.startRejection(true, false, false, false))
        assertEquals("busy", GeckoPerformanceDiagnosticsRules.startRejection(true, true, false, true))
    }

    @Test
    fun `discard invalidates stale stop and export generations`() {
        assertFalse(GeckoPerformanceDiagnosticsRules.canPublish(1, 2, false, 100))
        assertTrue(GeckoPerformanceDiagnosticsRules.canPublish(2, 2, false, 100))
    }

    @Test
    fun `private session invalidates completed normal capture`() {
        assertFalse(GeckoPerformanceDiagnosticsRules.canPublish(2, 2, true, 100))
    }

    @Test
    fun `profile sizes include limit but reject empty and oversized results`() {
        assertFalse(GeckoPerformanceDiagnosticsRules.canPublish(2, 2, false, 0))
        assertFalse(GeckoPerformanceDiagnosticsRules.canPublish(2, 2, false, 1))
        assertTrue(GeckoPerformanceDiagnosticsRules.canPublish(2, 2, false, 67_108_864))
        assertFalse(GeckoPerformanceDiagnosticsRules.canPublish(2, 2, false, 67_108_865))
    }

    @Test
    fun `only gzip payload header passes export validation`() {
        assertTrue(GeckoPerformanceDiagnosticsRules.hasGzipHeader(byteArrayOf(0x1f, 0x8b.toByte(), 0)))
        assertFalse(GeckoPerformanceDiagnosticsRules.hasGzipHeader(byteArrayOf()))
        assertFalse(GeckoPerformanceDiagnosticsRules.hasGzipHeader(byteArrayOf(0x1f)))
        assertFalse(GeckoPerformanceDiagnosticsRules.hasGzipHeader("{}".toByteArray()))
    }

    @Test
    fun `capture duration remains bounded at two minutes`() {
        assertEquals(120_000L, GeckoPerformanceDiagnosticsRules.MAX_CAPTURE_DURATION_MS)
    }

    @Test
    fun `failed and oversized exports produce bounded safe error states`() {
        assertEquals("error", GeckoPerformanceDiagnosticsRules.profileRejection(0, false))
        assertEquals("error", GeckoPerformanceDiagnosticsRules.profileRejection(100, false))
        assertEquals("profile_too_large", GeckoPerformanceDiagnosticsRules.profileRejection(67_108_865, true))
        assertNull(GeckoPerformanceDiagnosticsRules.profileRejection(67_108_864, true))
    }
}
