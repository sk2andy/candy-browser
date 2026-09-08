package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarStaticOverlayRulesTest {
    @Test
    fun `overlay covers status bar and density scaled transparent buffer`() {
        assertEquals(
            StatusBarStaticOverlayGeometry(
                statusBarHeightPx = 72,
                overlayHeightPx = 88,
            ),
            StatusBarStaticOverlayRules.geometry(statusBarHeightPx = 72, density = 2f),
        )
    }

    @Test
    fun `hidden status bar creates no fade tail`() {
        assertEquals(
            StatusBarStaticOverlayGeometry(),
            StatusBarStaticOverlayRules.geometry(statusBarHeightPx = 0, density = 3f),
        )
    }

    @Test
    fun `invalid density creates no overlay`() {
        assertEquals(
            StatusBarStaticOverlayGeometry(),
            StatusBarStaticOverlayRules.geometry(statusBarHeightPx = 72, density = Float.NaN),
        )
    }
}
