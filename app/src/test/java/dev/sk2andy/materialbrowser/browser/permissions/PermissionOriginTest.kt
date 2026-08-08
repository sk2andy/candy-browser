package dev.sk2andy.materialbrowser.browser.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionOriginTest {
    @Test
    fun normalizesCasePathAndDefaultPortToOrigin() {
        assertEquals(
            "https://example.com",
            PermissionOrigin.normalize(" HTTPS://Example.COM:443/camera?x=1#fragment "),
        )
        assertEquals(
            "http://example.com:8080",
            PermissionOrigin.normalize("http://EXAMPLE.com:8080/path"),
        )
    }

    @Test
    fun rejectsCredentialsNonWebSchemesAndMalformedValues() {
        assertNull(PermissionOrigin.normalize("https://user@example.com"))
        assertNull(PermissionOrigin.normalize("javascript:alert(1)"))
        assertNull(PermissionOrigin.normalize("https://example.com\n.evil.test"))
        assertNull(PermissionOrigin.normalize("https:///missing-host"))
    }

    @Test
    fun onlyHttpsAndLoopbackHttpAreTrustworthy() {
        assertTrue(PermissionOrigin.isPotentiallyTrustworthy("https://example.com"))
        assertTrue(PermissionOrigin.isPotentiallyTrustworthy("http://localhost:8080"))
        assertTrue(PermissionOrigin.isPotentiallyTrustworthy("http://127.0.0.1"))
        assertTrue(PermissionOrigin.isPotentiallyTrustworthy("http://[::1]:8080"))
        assertFalse(PermissionOrigin.isPotentiallyTrustworthy("http://example.com"))
    }
}
