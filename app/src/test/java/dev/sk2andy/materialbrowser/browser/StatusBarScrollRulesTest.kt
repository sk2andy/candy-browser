package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarScrollRulesTest {
    @Test
    fun targetWidthPercentageIsClampedToSupportedRange() {
        assertEquals(10, StatusBarScrollRules.targetWidthPercent(0))
        assertEquals(20, StatusBarScrollRules.targetWidthPercent(20))
        assertEquals(50, StatusBarScrollRules.targetWidthPercent(200))
    }

    @Test
    fun targetWidthScalesWithAvailableScreenWidthAcrossOrientationsAndResolutions() {
        assertEquals(108, StatusBarScrollRules.targetWidthPx(540, 20))
        assertEquals(216, StatusBarScrollRules.targetWidthPx(1080, 20))
        assertEquals(192, StatusBarScrollRules.targetWidthPx(1920, 10))
        assertEquals(960, StatusBarScrollRules.targetWidthPx(1920, 50))
        assertEquals(1, StatusBarScrollRules.targetWidthPx(0, 20))
    }

    @Test
    fun previewRemainsAvailableFromSettingsEvenWhenForegroundStateIsStale() {
        assertTrue(StatusBarScrollRules.shouldShowOverlay(true, false, false, true))
        assertTrue(StatusBarScrollRules.shouldShowOverlay(true, true, true, false))
        assertFalse(StatusBarScrollRules.shouldShowOverlay(true, false, false, false))
        assertFalse(StatusBarScrollRules.shouldShowOverlay(false, true, true, true))
    }

    @Test
    fun domainOverridesAreCanonicalScopedAndReversible() {
        val disabled = StatusBarScrollRules.withEnabledState(emptySet(), "EXAMPLE.COM", false)
        assertFalse(StatusBarScrollRules.isEnabled("https://www.example.com/page", disabled))
        assertTrue(StatusBarScrollRules.isEnabled("https://other.example.org", emptySet()))
        assertEquals(setOf("example.com"), disabled)
        assertEquals(
            emptySet<String>(),
            StatusBarScrollRules.withEnabledState(disabled, "example.com", true),
        )
        assertFalse(StatusBarScrollRules.isEnabled("file:///tmp/page", disabled))
    }
}
