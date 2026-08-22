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
            decide("about:blank", filterDecision = PopupFilterDecision.Block),
        )
    }

    @Test
    fun `enabled matching popup is blocked`() {
        assertEquals(
            PopupNavigationDecision.BlockListed,
            decide("https://ads.example/click", filterDecision = PopupFilterDecision.Block),
        )
    }

    @Test
    fun `cross site popup without listed rule requires user confirmation`() {
        assertEquals(
            PopupNavigationDecision.BlockCrossSite,
            decide("https://outside.example/click"),
        )
        assertEquals(
            PopupNavigationDecision.AllowSameSite,
            decide("https://login.stream.example/account"),
        )
    }

    @Test
    fun `listed popup allow overrides cross site protection`() {
        assertEquals(
            PopupNavigationDecision.AllowListed,
            decide(
                "https://outside.example/login",
                filterDecision = PopupFilterDecision.Allow,
            ),
        )
    }

    @Test
    fun `disabled or paused protection allows popup`() {
        assertEquals(
            PopupNavigationDecision.Allow,
            decide(
                "https://ads.example/click",
                enabled = false,
                filterDecision = PopupFilterDecision.Block,
            ),
        )
        assertEquals(
            PopupNavigationDecision.Allow,
            PopupNavigationRules.decide(
                pending.copy(sitePaused = true),
                "https://ads.example/click",
                blockerEnabled = true,
            ) { _, _ -> PopupFilterDecision.Block },
        )
    }

    private fun decide(
        targetUrl: String,
        enabled: Boolean = true,
        filterDecision: PopupFilterDecision = PopupFilterDecision.NoMatch,
    ): PopupNavigationDecision = PopupNavigationRules.decide(
        pending = pending,
        targetUrl = targetUrl,
        blockerEnabled = enabled,
    ) { _, _ -> filterDecision }
}
