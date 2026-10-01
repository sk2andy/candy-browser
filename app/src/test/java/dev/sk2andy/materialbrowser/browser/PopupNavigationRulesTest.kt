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
    fun `preopened blank popup gets time for asynchronous checkout navigation`() {
        assertEquals(30_000L, PopupNavigationRules.pendingTimeoutMillis(preopenedBlank = true))
        assertEquals(5_000L, PopupNavigationRules.pendingTimeoutMillis(preopenedBlank = false))
    }

    @Test
    fun `non web targets keep pending decision`() {
        assertEquals(
            PopupNavigationDecision.KeepPending,
            decide("about:blank", filterDecision = PopupFilterDecision.Block),
        )
    }

    @Test
    fun `local web popup leaves pending state without requiring a filter domain`() {
        listOf("http://127.0.0.1:8080/page", "http://[::1]:8080/page", "http://localhost/page")
            .forEach { url ->
                assertEquals(PopupNavigationDecision.Allow, decide(url, enabled = false))
                assertEquals(PopupNavigationDecision.Allow, decide(url))
            }
        assertEquals(
            PopupNavigationDecision.AllowSameSite,
            PopupNavigationRules.decide(
                pending.copy(openerUrl = "http://127.0.0.1:8080/opener"),
                "http://127.0.0.1:8080/page",
                blockerEnabled = true,
            ) { _, _ -> PopupFilterDecision.NoMatch },
        )
    }

    @Test
    fun `invalid web popup stays pending`() {
        listOf("https:///missing-host", "javascript:alert(1)", "https://example.com/\u0000")
            .forEach { url ->
                assertEquals(PopupNavigationDecision.KeepPending, decide(url, enabled = false))
            }
    }

    @Test
    fun `enabled matching popup is blocked`() {
        assertEquals(
            PopupNavigationDecision.BlockListed,
            decide("https://ads.example/click", filterDecision = PopupFilterDecision.Block),
        )
    }

    @Test
    fun `cross site user gesture without listed rule is allowed`() {
        assertEquals(
            PopupNavigationDecision.Allow,
            decide("https://outside.example/click"),
        )
        assertEquals(
            PopupNavigationDecision.AllowSameSite,
            decide("https://login.stream.example/account"),
        )
    }

    @Test
    fun `listed popup allow is honored`() {
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
