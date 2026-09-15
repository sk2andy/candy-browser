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
    fun `result requires unchanged page session and chrome geometry`() {
        val accepted = AddressBarAutoDockRules.shouldApplyResult(
            occluded = true,
            dockingEnabled = true,
            addressBarDocked = false,
            selectedTabMatches = true,
            sessionMatches = true,
            navigationMatches = true,
            urlMatches = true,
            viewportRectMatches = true,
            isPrivatePage = false,
        )
        val staleGeometry = AddressBarAutoDockRules.shouldApplyResult(
            occluded = true,
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
}
