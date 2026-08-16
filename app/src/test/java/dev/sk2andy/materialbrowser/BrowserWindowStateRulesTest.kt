package dev.sk2andy.materialbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserWindowStateRulesTest {
    @Test
    fun `web fullscreen overrides tab overview portrait lock`() {
        val state = BrowserWindowStateRules.resolve(
            isWebContentFullscreen = true,
            isBrowserFullscreen = false,
            isTabOverviewPortraitLocked = true,
        )

        assertTrue(state.isImmersive)
        assertEquals(BrowserRequestedOrientation.Sensor, state.requestedOrientation)
    }

    @Test
    fun `tab overview stays portrait after web fullscreen exits`() {
        val state = BrowserWindowStateRules.resolve(
            isWebContentFullscreen = false,
            isBrowserFullscreen = false,
            isTabOverviewPortraitLocked = true,
        )

        assertFalse(state.isImmersive)
        assertEquals(BrowserRequestedOrientation.Portrait, state.requestedOrientation)
    }

    @Test
    fun `browser fullscreen keeps immersive bars without locking orientation`() {
        val state = BrowserWindowStateRules.resolve(
            isWebContentFullscreen = false,
            isBrowserFullscreen = true,
            isTabOverviewPortraitLocked = false,
        )

        assertTrue(state.isImmersive)
        assertEquals(BrowserRequestedOrientation.Unspecified, state.requestedOrientation)
    }
}
