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
                isBrowserVisible = true,
                browserChromeOwnsIme = false,
                manuallyUnparked = false,
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
                isBrowserVisible = true,
                browserChromeOwnsIme = false,
                manuallyUnparked = false,
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
            isBrowserVisible = true,
            browserChromeOwnsIme = false,
            manuallyUnparked = false,
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
            isBrowserVisible = true,
            browserChromeOwnsIme = false,
            manuallyUnparked = false,
        )

        assertTrue(accepted)
        assertFalse(staleGeometry)
    }

    @Test
    fun `manual restoration rejects probes and late callbacks`() {
        assertFalse(
            AddressBarAutoDockRules.shouldProbe(
                dockingEnabled = true,
                addressBarDocked = false,
                selectedTabMatches = true,
                isHttpPage = true,
                isPrivatePage = false,
                hasViewportRect = true,
                isBrowserVisible = true,
                browserChromeOwnsIme = false,
                manuallyUnparked = true,
            ),
        )
        assertFalse(
            AddressBarAutoDockRules.isProbeContextCurrent(
                dockingEnabled = true,
                addressBarDocked = false,
                selectedTabMatches = true,
                sessionMatches = true,
                navigationMatches = true,
                urlMatches = true,
                viewportRectMatches = true,
                isPrivatePage = false,
                isBrowserVisible = true,
                browserChromeOwnsIme = false,
                manuallyUnparked = true,
            ),
        )
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

    @Test
    fun `paused browser and chrome owned ime reject probes and callbacks`() {
        for ((isBrowserVisible, browserChromeOwnsIme) in listOf(false to false, true to true)) {
            assertFalse(
                AddressBarAutoDockRules.shouldProbe(
                    dockingEnabled = true,
                    addressBarDocked = false,
                    selectedTabMatches = true,
                    isHttpPage = true,
                    isPrivatePage = false,
                    hasViewportRect = true,
                    isBrowserVisible = isBrowserVisible,
                    browserChromeOwnsIme = browserChromeOwnsIme,
                    manuallyUnparked = false,
                ),
            )
            assertFalse(
                AddressBarAutoDockRules.isProbeContextCurrent(
                    dockingEnabled = true,
                    addressBarDocked = false,
                    selectedTabMatches = true,
                    sessionMatches = true,
                    navigationMatches = true,
                    urlMatches = true,
                    viewportRectMatches = true,
                    isPrivatePage = false,
                    isBrowserVisible = isBrowserVisible,
                    browserChromeOwnsIme = browserChromeOwnsIme,
                    manuallyUnparked = false,
                ),
            )
        }
    }

    @Test
    fun `clear regular probe repeats without keyboard or focus`() {
        assertEquals(
            AddressBarAutoDockProbeRetry(TextInputOcclusionProbeMode.AllEditors, 2_000L, 0),
            AddressBarAutoDockRules.nextProbeRetry(
                mode = TextInputOcclusionProbeMode.AllEditors,
                result = TextInputOcclusionProbeResult.NoFocusedTextInput,
                completedRetryCount = 0,
            ),
        )
    }

    @Test
    fun `focused probes return to regular checks after focus loss or burst exhaustion`() {
        val expected = AddressBarAutoDockProbeRetry(
            mode = TextInputOcclusionProbeMode.AllEditors,
            delayMillis = 2_000L,
            completedRetryCount = 0,
        )
        assertEquals(
            expected,
            AddressBarAutoDockRules.nextProbeRetry(
                mode = TextInputOcclusionProbeMode.FocusedTextInput,
                result = TextInputOcclusionProbeResult.NoFocusedTextInput,
                completedRetryCount = 0,
            ),
        )
        assertEquals(
            expected,
            AddressBarAutoDockRules.nextProbeRetry(
                mode = TextInputOcclusionProbeMode.FocusedTextInput,
                result = TextInputOcclusionProbeResult.FocusedTextInputClear,
                completedRetryCount = 4,
            ),
        )
        assertEquals(
            AddressBarAutoDockProbeRetry(TextInputOcclusionProbeMode.FocusedTextInput, 150L, 1),
            AddressBarAutoDockRules.nextProbeRetry(
                mode = TextInputOcclusionProbeMode.FocusedTextInput,
                result = TextInputOcclusionProbeResult.FocusedTextInputClear,
                completedRetryCount = 0,
            ),
        )
    }

    @Test
    fun `occluded control terminates periodic probes`() {
        for (mode in TextInputOcclusionProbeMode.entries) {
            assertNull(
                AddressBarAutoDockRules.nextProbeRetry(
                    mode = mode,
                    result = TextInputOcclusionProbeResult.Occluded,
                    completedRetryCount = 0,
                ),
            )
        }
    }
}
