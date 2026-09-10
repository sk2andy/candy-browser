package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserConnectivityRulesTest {
    @Test
    fun `network is online only when internet and validation are available`() {
        assertTrue(
            BrowserConnectivityRules.isOnline(
                hasInternetCapability = true,
                hasValidatedCapability = true,
            ),
        )
        assertFalse(
            BrowserConnectivityRules.isOnline(
                hasInternetCapability = true,
                hasValidatedCapability = false,
            ),
        )
        assertFalse(
            BrowserConnectivityRules.isOnline(
                hasInternetCapability = false,
                hasValidatedCapability = true,
            ),
        )
        assertFalse(
            BrowserConnectivityRules.isOnline(
                hasInternetCapability = false,
                hasValidatedCapability = false,
            ),
        )
    }

    @Test
    fun `first observed status is delivered`() {
        assertEquals(
            false,
            BrowserConnectivityRules.statusToDeliver(
                lastDeliveredStatus = null,
                currentStatus = false,
            ),
        )
    }

    @Test
    fun `unchanged status is not delivered again`() {
        assertNull(
            BrowserConnectivityRules.statusToDeliver(
                lastDeliveredStatus = true,
                currentStatus = true,
            ),
        )
        assertNull(
            BrowserConnectivityRules.statusToDeliver(
                lastDeliveredStatus = false,
                currentStatus = false,
            ),
        )
    }

    @Test
    fun `changed status is delivered`() {
        assertEquals(
            true,
            BrowserConnectivityRules.statusToDeliver(
                lastDeliveredStatus = false,
                currentStatus = true,
            ),
        )
        assertEquals(
            false,
            BrowserConnectivityRules.statusToDeliver(
                lastDeliveredStatus = true,
                currentStatus = false,
            ),
        )
    }
}
