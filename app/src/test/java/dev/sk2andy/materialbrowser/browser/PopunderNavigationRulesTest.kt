package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class PopunderNavigationRulesTest {
    private val pending = PendingPopunderNavigation(
        openerTabId = "opener",
        popupTabId = "child",
        originalOpenerUrl = "https://movies.example/watch",
        createdAtMillis = 1_000L,
        sitePaused = false,
    )

    @Test
    fun `both navigation event orders reach same blocking decision`() {
        val childFirst = PopunderNavigationRules.withOpenerTarget(
            PopunderNavigationRules.withChildUrl(pending, "https://movies.example/watch"),
            "https://ads.example/landing",
        )
        val openerFirst = PopunderNavigationRules.withChildUrl(
            PopunderNavigationRules.withOpenerTarget(pending, "https://ads.example/landing"),
            "https://movies.example/watch",
        )

        assertEquals(PopunderNavigationDecision.Block, decide(childFirst))
        assertEquals(PopunderNavigationDecision.Block, decide(openerFirst))
    }

    @Test
    fun `stable opener timeout pause and allow never block`() {
        val stable = pending.copy(
            childUrl = "https://movies.example/watch",
            openerTargetUrl = "https://movies.example/watch#section",
        )
        val complete = stable.copy(openerTargetUrl = "https://ads.example/landing")

        assertEquals(PopunderNavigationDecision.KeepPending, decide(stable))
        assertEquals(PopunderNavigationDecision.Allow, decide(complete, nowMillis = 6_001L))
        assertEquals(PopunderNavigationDecision.Allow, decide(complete.copy(sitePaused = true)))
        assertEquals(
            PopunderNavigationDecision.Allow,
            decide(complete, filterDecision = PopupFilterDecision.Allow),
        )
    }

    @Test
    fun `incomplete pair stays pending`() {
        assertEquals(PopunderNavigationDecision.KeepPending, decide(pending))
        assertEquals(
            PopunderNavigationDecision.KeepPending,
            decide(pending.copy(childUrl = "https://movies.example/watch")),
        )
    }

    @Test
    fun `scheme query and same site redirects stay pending`() {
        val child = "https://movies.example/watch"
        listOf(
            "http://movies.example/watch",
            "https://movies.example/watch?canonical=1",
            "https://www.movies.example/elsewhere",
        ).forEach { target ->
            assertEquals(
                PopunderNavigationDecision.KeepPending,
                decide(pending.copy(childUrl = child, openerTargetUrl = target)),
            )
        }
    }

    @Test
    fun `unmatched intermediate redirect remains eligible for final block`() {
        val intermediate = pending.copy(
            childUrl = "https://movies.example/watch",
            openerTargetUrl = "https://tracker.example/redirect",
        )
        assertEquals(
            PopunderNavigationDecision.KeepPending,
            decide(intermediate, filterDecision = PopupFilterDecision.NoMatch),
        )
        assertEquals(
            PopunderNavigationDecision.Block,
            decide(intermediate.copy(openerTargetUrl = "https://ads.example/landing")),
        )
    }

    private fun decide(
        value: PendingPopunderNavigation,
        nowMillis: Long = 2_000L,
        filterDecision: PopupFilterDecision = PopupFilterDecision.Block,
    ): PopunderNavigationDecision = PopunderNavigationRules.decide(
        pending = value,
        nowMillis = nowMillis,
        blockerEnabled = true,
    ) { _, _ -> filterDecision }
}
