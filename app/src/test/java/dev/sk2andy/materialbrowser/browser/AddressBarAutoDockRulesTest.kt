package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBarAutoDockRulesTest {
    @Test
    fun `visible page ime requests a fresh probe`() {
        assertTrue(
            AddressBarAutoDockRules.shouldProbeForImeState(
                isImeVisible = true,
                browserChromeOwnsIme = false,
            ),
        )
        assertFalse(
            AddressBarAutoDockRules.shouldProbeForImeState(
                isImeVisible = false,
                browserChromeOwnsIme = false,
            ),
        )
        assertFalse(
            AddressBarAutoDockRules.shouldProbeForImeState(
                isImeVisible = true,
                browserChromeOwnsIme = true,
            ),
        )
    }

    @Test
    fun `viewport rect normalizes current address bar bounds`() {
        assertEquals(
            BrowserViewportRect(
                leftFraction = 0.1f,
                topFraction = 0.8f,
                rightFraction = 0.9f,
                bottomFraction = 0.9f,
            ),
            AddressBarAutoDockRules.viewportRect(
                leftPx = 100f,
                topPx = 800f,
                rightPx = 900f,
                bottomPx = 900f,
                viewportWidthPx = 1_000f,
                viewportHeightPx = 1_000f,
            ),
        )
    }

    @Test
    fun `viewport rect rejects invalid or empty bounds`() {
        assertNull(
            AddressBarAutoDockRules.viewportRect(
                leftPx = Float.NaN,
                topPx = 0f,
                rightPx = 10f,
                bottomPx = 10f,
                viewportWidthPx = 100f,
                viewportHeightPx = 100f,
            ),
        )
        assertNull(
            AddressBarAutoDockRules.viewportRect(
                leftPx = 20f,
                topPx = 20f,
                rightPx = 10f,
                bottomPx = 10f,
                viewportWidthPx = 100f,
                viewportHeightPx = 100f,
            ),
        )
    }

    @Test
    fun `probe requires selected regular web page and undocked address bar`() {
        assertTrue(
            AddressBarAutoDockRules.shouldProbe(
                dockingEnabled = true,
                addressBarDocked = false,
                selectedTabMatches = true,
                isHttpPage = true,
                isPrivatePage = false,
                hasViewportRect = true,
            ),
        )
        assertFalse(
            AddressBarAutoDockRules.shouldProbe(
                dockingEnabled = true,
                addressBarDocked = false,
                selectedTabMatches = true,
                isHttpPage = true,
                isPrivatePage = true,
                hasViewportRect = true,
            ),
        )
    }

    @Test
    fun `probe requires unchanged page session and chrome geometry`() {
        val accepted = AddressBarAutoDockRules.isProbeContextCurrent(
            dockingEnabled = true,
            addressBarDocked = false,
            selectedTabMatches = true,
            sessionMatches = true,
            navigationMatches = true,
            urlMatches = true,
            viewportRectMatches = true,
            isPrivatePage = false,
        )
        val staleGeometry = AddressBarAutoDockRules.isProbeContextCurrent(
            dockingEnabled = true,
            addressBarDocked = false,
            selectedTabMatches = true,
            sessionMatches = true,
            navigationMatches = true,
            urlMatches = true,
            viewportRectMatches = false,
            isPrivatePage = false,
        )

        assertTrue(accepted)
        assertFalse(staleGeometry)
    }

    @Test
    fun `focused probe burst has four bounded retries`() {
        assertEquals(150L, AddressBarAutoDockRules.focusedProbeRetryDelayMillis(0))
        assertEquals(200L, AddressBarAutoDockRules.focusedProbeRetryDelayMillis(1))
        assertEquals(350L, AddressBarAutoDockRules.focusedProbeRetryDelayMillis(2))
        assertEquals(500L, AddressBarAutoDockRules.focusedProbeRetryDelayMillis(3))
        assertNull(AddressBarAutoDockRules.focusedProbeRetryDelayMillis(4))
    }

    @Test
    fun `focused probe burst stops after focus loss or overlap`() {
        assertTrue(
            AddressBarAutoDockRules.shouldRetryFocusedProbe(
                TextInputOcclusionProbeResult.FocusedTextInputClear,
            ),
        )
        assertFalse(
            AddressBarAutoDockRules.shouldRetryFocusedProbe(
                TextInputOcclusionProbeResult.NoFocusedTextInput,
            ),
        )
        assertFalse(
            AddressBarAutoDockRules.shouldRetryFocusedProbe(
                TextInputOcclusionProbeResult.Occluded,
            ),
        )
    }

    @Test
    fun `unknown probe result fails closed`() {
        assertEquals(
            TextInputOcclusionProbeResult.NoFocusedTextInput,
            TextInputOcclusionProbeResult.fromWireValue(99),
        )
    }
}
