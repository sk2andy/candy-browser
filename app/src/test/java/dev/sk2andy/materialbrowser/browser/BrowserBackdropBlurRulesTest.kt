package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserBackdropBlurRulesTest {
    @Test
    fun `system WebView captures backdrop on every supported Android version`() {
        listOf(33, 34, 35, 36, 37).forEach { sdkInt ->
            assertEquals(
                BrowserBackdropBlurMode.ViewHierarchyCapture,
                BrowserBackdropBlurRules.mode(AndroidBrowserEngineKind.SystemWebView, sdkInt),
            )
        }
    }

    @Test
    fun `Gecko uses native surface blur only from Android 17`() {
        listOf(33, 34, 35, 36).forEach { sdkInt ->
            assertEquals(
                BrowserBackdropBlurMode.Unavailable,
                BrowserBackdropBlurRules.mode(AndroidBrowserEngineKind.GeckoView, sdkInt),
            )
        }
        assertEquals(
            BrowserBackdropBlurMode.NativeSurfaceRegion,
            BrowserBackdropBlurRules.mode(AndroidBrowserEngineKind.GeckoView, 37),
        )
    }

    @Test
    fun `window region maps and clamps to surface bounds`() {
        val region = requireNotNull(
            BrowserBackdropBlurRules.regionInWindow(
                leftPx = 20f,
                topPx = 780f,
                rightPx = 1_060f,
                bottomPx = 880f,
                cornerRadiusPx = 60f,
                blurRadiusPx = 24f,
            ),
        )

        assertEquals(
            BrowserSurfaceBackdropBlurRegion(
                leftPx = 0f,
                topPx = 756f,
                rightPx = 1_040f,
                bottomPx = 856f,
                cornerRadiusPx = 50f,
                blurRadiusPx = 24f,
            ),
            BrowserBackdropBlurRules.regionInSurface(
                region = region,
                surfaceLeftInWindowPx = 20,
                surfaceTopInWindowPx = 24,
                surfaceWidthPx = 1_040,
                surfaceHeightPx = 900,
            ),
        )
    }

    @Test
    fun `invalid or non-overlapping regions are rejected`() {
        assertNull(
            BrowserBackdropBlurRules.regionInWindow(
                leftPx = 10f,
                topPx = 10f,
                rightPx = 10f,
                bottomPx = 20f,
                cornerRadiusPx = 8f,
                blurRadiusPx = 12f,
            ),
        )
        val region = requireNotNull(
            BrowserBackdropBlurRules.regionInWindow(
                leftPx = 500f,
                topPx = 500f,
                rightPx = 600f,
                bottomPx = 600f,
                cornerRadiusPx = 8f,
                blurRadiusPx = 12f,
            ),
        )
        assertNull(
            BrowserBackdropBlurRules.regionInSurface(
                region = region,
                surfaceLeftInWindowPx = 0,
                surfaceTopInWindowPx = 0,
                surfaceWidthPx = 100,
                surfaceHeightPx = 100,
            ),
        )
    }
}
