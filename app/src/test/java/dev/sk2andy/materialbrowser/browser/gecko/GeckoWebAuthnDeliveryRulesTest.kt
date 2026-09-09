package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Test

class GeckoWebAuthnDeliveryRulesTest {
    @Test
    fun `activity result waits until browser host resumes`() {
        assertEquals(
            GeckoWebAuthnDeliveryDecision.Wait,
            GeckoWebAuthnDeliveryRules.decide(
                hasPendingRequest = true,
                hasActivityResult = true,
                isHostResumed = false,
                isHostClosed = false,
                isPendingRequestCurrent = true,
            ),
        )
        assertEquals(
            GeckoWebAuthnDeliveryDecision.Complete,
            GeckoWebAuthnDeliveryRules.decide(
                hasPendingRequest = true,
                hasActivityResult = true,
                isHostResumed = true,
                isHostClosed = false,
                isPendingRequestCurrent = true,
            ),
        )
    }

    @Test
    fun `closed host and completed request never deliver`() {
        assertEquals(
            GeckoWebAuthnDeliveryDecision.Ignore,
            GeckoWebAuthnDeliveryRules.decide(
                hasPendingRequest = true,
                hasActivityResult = true,
                isHostResumed = true,
                isHostClosed = true,
                isPendingRequestCurrent = true,
            ),
        )
        assertEquals(
            GeckoWebAuthnDeliveryDecision.Ignore,
            GeckoWebAuthnDeliveryRules.decide(
                hasPendingRequest = false,
                hasActivityResult = true,
                isHostResumed = true,
                isHostClosed = false,
                isPendingRequestCurrent = true,
            ),
        )
    }

    @Test
    fun `stale tab session or navigation rejects pending result`() {
        assertEquals(
            GeckoWebAuthnDeliveryDecision.Reject,
            GeckoWebAuthnDeliveryRules.decide(
                hasPendingRequest = true,
                hasActivityResult = true,
                isHostResumed = true,
                isHostClosed = false,
                isPendingRequestCurrent = false,
            ),
        )
    }
}
