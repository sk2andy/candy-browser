package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class PopupNavigationRulesTest {
    private val pending = PendingPopupNavigation(
        openerTabId = "opener",
        openerUrl = "https://stream.example/watch",
        profileId = "private-profile",
        isIncognito = true,
        sitePaused = false,
        hadUserGesture = true,
    )

    @Test
    fun `non web targets keep pending decision`() {
        assertEquals(
            PopupNavigationDecision.KeepPending,
            decide("about:blank", shouldBlock = true),
        )
    }

    @Test
    fun `enabled matching popup is blocked`() {
        assertEquals(
            PopupNavigationDecision.Block,
            decide("https://ads.example/click", shouldBlock = true),
        )
    }

    @Test
    fun `disabled or paused protection allows popup`() {
        assertEquals(
            PopupNavigationDecision.Allow,
            decide("https://ads.example/click", enabled = false, shouldBlock = true),
        )
        assertEquals(
            PopupNavigationDecision.Allow,
            PopupNavigationRules.decide(
                pending.copy(sitePaused = true),
                "https://ads.example/click",
                blockerEnabled = true,
            ) { _, _ -> true },
        )
    }

    private fun decide(
        targetUrl: String,
        enabled: Boolean = true,
        shouldBlock: Boolean,
    ): PopupNavigationDecision = PopupNavigationRules.decide(
        pending = pending,
        targetUrl = targetUrl,
        blockerEnabled = enabled,
    ) { _, _ -> shouldBlock }
}
