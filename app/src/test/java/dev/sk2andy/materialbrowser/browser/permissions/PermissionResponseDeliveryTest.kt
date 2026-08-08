package dev.sk2andy.materialbrowser.browser.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionResponseDeliveryTest {
    @Test
    fun webViewCancellationDropsRequestWithoutDenyingIt() {
        var grants = 0
        var denials = 0
        val delivery = PermissionResponseDelivery(
            grantCallback = { grants++ },
            denyCallback = { denials++ },
        )

        assertTrue(delivery.drop())
        assertFalse(delivery.deny())
        assertFalse(delivery.grant(setOf(SitePermission.Camera)))
        assertEquals(0, grants)
        assertEquals(0, denials)
    }

    @Test
    fun appCancellationDeniesExactlyOnce() {
        var denials = 0
        val delivery = PermissionResponseDelivery(
            grantCallback = {},
            denyCallback = { denials++ },
        )

        assertTrue(delivery.deny())
        assertFalse(delivery.deny())
        assertFalse(delivery.drop())
        assertEquals(1, denials)
    }
}
