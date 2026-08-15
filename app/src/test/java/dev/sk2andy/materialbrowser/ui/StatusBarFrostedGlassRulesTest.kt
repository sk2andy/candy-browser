package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarFrostedGlassRulesTest {
    @Test
    fun `overlay covers status bar and density scaled transparent buffer`() {
        assertEquals(
            StatusBarFrostedGlassGeometry(
                statusBarHeightPx = 72,
                blurRadiusPx = 14f,
                overlayHeightPx = 88,
            ),
            StatusBarFrostedGlassRules.geometry(statusBarHeightPx = 72, density = 2f),
        )
    }

    @Test
    fun `hidden status bar creates no fade tail`() {
        assertEquals(
            StatusBarFrostedGlassGeometry(),
            StatusBarFrostedGlassRules.geometry(statusBarHeightPx = 0, density = 3f),
        )
    }

    @Test
    fun `invalid density creates no overlay`() {
        assertEquals(
            StatusBarFrostedGlassGeometry(),
            StatusBarFrostedGlassRules.geometry(statusBarHeightPx = 72, density = Float.NaN),
        )
    }
}
