package dev.sk2andy.materialbrowser.browser.integration

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class CandySearchWidgetRulesTest {
    @Test
    fun `widget shows first two local profile icons in stored order`() {
        val state = CandySearchWidgetRules.state(
            profiles = listOf(
                BrowserProfile("candy", "🍬"),
                BrowserProfile("work", "💼"),
                BrowserProfile("travel", "🧳"),
                BrowserProfile("synced", "📱", syncedDeviceId = "device"),
            ),
            profilesEnabled = true,
        )

        assertEquals(
            listOf(
                CandySearchWidgetProfile("candy", "🍬"),
                CandySearchWidgetProfile("work", "💼"),
            ),
            state.profiles,
        )
    }

    @Test
    fun `widget hides profile controls when profiles are disabled`() {
        val state = CandySearchWidgetRules.state(
            profiles = listOf(BrowserProfile("candy", "🍬")),
            profilesEnabled = false,
        )

        assertEquals(emptyList<CandySearchWidgetProfile>(), state.profiles)
    }

    @Test
    fun `widget publishes compact medium and large responsive size tiers`() {
        assertEquals(
            listOf(
                CandySearchWidgetSize(140, 52, CandySearchWidgetLayout.Compact),
                CandySearchWidgetSize(200, 52, CandySearchWidgetLayout.Medium),
                CandySearchWidgetSize(270, 52, CandySearchWidgetLayout.Large),
            ),
            CandySearchWidgetRules.responsiveSizes,
        )
    }
}
