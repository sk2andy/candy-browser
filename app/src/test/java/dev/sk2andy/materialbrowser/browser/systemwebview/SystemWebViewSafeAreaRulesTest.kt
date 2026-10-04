package dev.sk2andy.materialbrowser.browser.systemwebview

import dev.sk2andy.materialbrowser.browser.WebContentTopInsetTransitionState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsetLayout
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWebViewSafeAreaRulesTest {
    @Test
    fun `full CSS safe area starts with WebView milestone 144`() {
        assertFalse(SystemWebViewSafeAreaRules.supportsCssSafeAreaInsets("143.0.7499.1"))
        assertTrue(SystemWebViewSafeAreaRules.supportsCssSafeAreaInsets("144.0.7559.1"))
        assertTrue(SystemWebViewSafeAreaRules.supportsCssSafeAreaInsets("145.0.7632.218"))
    }

    @Test
    fun `missing or malformed provider version keeps Candy compatibility enabled`() {
        assertFalse(SystemWebViewSafeAreaRules.supportsCssSafeAreaInsets(null))
        assertFalse(SystemWebViewSafeAreaRules.supportsCssSafeAreaInsets("dev"))
        assertFalse(SystemWebViewSafeAreaRules.supportsCssSafeAreaInsets(""))
    }

    @Test
    fun `landscape cutouts reserve only assigned side edges and preserve top ownership`() {
        val layout = GeckoViewInsetLayout(
            margins = GeckoViewInsets(left = 4, top = 8, right = 2, bottom = 6),
            rendererSafeAreaOverride = GeckoViewInsets(left = 64, top = 120, right = 48, bottom = 32),
            scrollableTopInsetPx = 24,
            topInsetTransitionState = WebContentTopInsetTransitionState.WebContentHeader,
        )

        val result = SystemWebViewSafeAreaRules.withNativeCutoutMargins(
            layout = layout,
            cutout = GeckoViewInsets(left = 96, top = 120, right = 24, bottom = 0),
        )

        assertEquals(
            layout.copy(margins = GeckoViewInsets(left = 64, top = 8, right = 24, bottom = 6)),
            result,
        )
    }

    @Test
    fun `bottom cutout is bounded by assigned renderer edge rather than navigation inset`() {
        val layout = GeckoViewInsetLayout(
            margins = GeckoViewInsets(left = 0, top = 0, right = 0, bottom = 4),
            rendererSafeAreaOverride = GeckoViewInsets(left = 0, top = 96, right = 0, bottom = 80),
            scrollableTopInsetPx = 0,
        )

        for ((cutoutBottom, expectedBottom) in listOf(18 to 18, 200 to 80)) {
            val result = SystemWebViewSafeAreaRules.withNativeCutoutMargins(
                layout = layout,
                cutout = GeckoViewInsets(left = 0, top = 0, right = 0, bottom = cutoutBottom),
            )

            assertEquals(GeckoViewInsets(left = 0, top = 0, right = 0, bottom = expectedBottom), result.margins)
            assertEquals(layout.rendererSafeAreaOverride, result.rendererSafeAreaOverride)
        }
    }

    @Test
    fun `safe drawing host gains no additional cutout margin`() {
        val layout = GeckoViewInsetLayout(
            margins = GeckoViewInsets.Zero,
            rendererSafeAreaOverride = GeckoViewInsets.Zero,
            scrollableTopInsetPx = 0,
        )

        assertEquals(
            layout,
            SystemWebViewSafeAreaRules.withNativeCutoutMargins(
                layout = layout,
                cutout = GeckoViewInsets(left = 32, top = 96, right = 24, bottom = 18),
            ),
        )
    }

    @Test
    fun `fullscreen without renderer override retains original layout`() {
        val layout = GeckoViewInsetLayout(
            margins = GeckoViewInsets.Zero,
            rendererSafeAreaOverride = null,
            scrollableTopInsetPx = 0,
        )

        assertSame(
            layout,
            SystemWebViewSafeAreaRules.withNativeCutoutMargins(
                layout = layout,
                cutout = GeckoViewInsets(left = 32, top = 96, right = 24, bottom = 18),
            ),
        )
    }

    @Test
    fun `force native safe area retains existing margins without adding cutout twice`() {
        val layout = GeckoViewInsetLayout(
            margins = GeckoViewInsets(left = 32, top = 96, right = 24, bottom = 48),
            rendererSafeAreaOverride = GeckoViewInsets.Zero,
            scrollableTopInsetPx = 0,
        )

        assertEquals(
            layout,
            SystemWebViewSafeAreaRules.withNativeCutoutMargins(
                layout = layout,
                cutout = GeckoViewInsets(left = 32, top = 96, right = 24, bottom = 18),
            ),
        )
    }

    @Test
    fun `no cutout leaves navigation bar edge to edge`() {
        val layout = GeckoViewInsetLayout(
            margins = GeckoViewInsets.Zero,
            rendererSafeAreaOverride = GeckoViewInsets(left = 0, top = 96, right = 0, bottom = 48),
            scrollableTopInsetPx = 0,
        )

        assertEquals(
            layout,
            SystemWebViewSafeAreaRules.withNativeCutoutMargins(layout, GeckoViewInsets.Zero),
        )
    }

    @Test
    fun `top only cutout keeps shared CSS top ownership`() {
        val layout = GeckoViewInsetLayout(
            margins = GeckoViewInsets.Zero,
            rendererSafeAreaOverride = GeckoViewInsets(left = 0, top = 96, right = 0, bottom = 48),
            scrollableTopInsetPx = 0,
        )

        assertEquals(
            layout,
            SystemWebViewSafeAreaRules.withNativeCutoutMargins(
                layout = layout,
                cutout = GeckoViewInsets(left = 0, top = 96, right = 0, bottom = 0),
            ),
        )
    }
}
