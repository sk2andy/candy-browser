package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Test

class GeckoViewInsetRulesTest {
    @Test
    fun `scrollable document inset keeps Gecko surface behind status bar`() {
        val layout = GeckoViewInsetRules.resolve(
            safeArea = GeckoViewInsets(left = 8, top = 96, right = 6, bottom = 34),
            forceNativeSafeArea = false,
            isFullscreenContent = false,
            isInsideSafeDrawingHost = false,
            useScrollableTopInset = true,
        )

        assertEquals(
            GeckoViewInsets(left = 8, top = 0, right = 6, bottom = 0),
            layout.margins,
        )
        assertEquals(
            GeckoViewInsets(left = 0, top = 0, right = 0, bottom = 34),
            layout.rendererSafeAreaOverride,
        )
    }

    @Test
    fun `status bar keeps page content below notification area`() {
        val layout = GeckoViewInsetRules.resolve(
            safeArea = GeckoViewInsets(left = 8, top = 96, right = 6, bottom = 0),
            forceNativeSafeArea = false,
            isFullscreenContent = false,
            isInsideSafeDrawingHost = false,
        )

        assertEquals(
            GeckoViewInsets(left = 8, top = 96, right = 6, bottom = 0),
            layout.margins,
        )
        assertEquals(GeckoViewInsets.Zero, layout.rendererSafeAreaOverride)
    }

    @Test
    fun `gesture navigation safe area reaches edge to edge renderer`() {
        val layout = GeckoViewInsetRules.resolve(
            safeArea = GeckoViewInsets(left = 0, top = 72, right = 0, bottom = 34),
            forceNativeSafeArea = false,
            isFullscreenContent = false,
            isInsideSafeDrawingHost = false,
        )

        assertEquals(
            GeckoViewInsets(left = 0, top = 72, right = 0, bottom = 0),
            layout.margins,
        )
        assertEquals(
            GeckoViewInsets(left = 0, top = 0, right = 0, bottom = 34),
            layout.rendererSafeAreaOverride,
        )
    }

    @Test
    fun `three button navigation also remains edge to edge`() {
        val layout = GeckoViewInsetRules.resolve(
            safeArea = GeckoViewInsets(left = 0, top = 72, right = 0, bottom = 48),
            forceNativeSafeArea = false,
            isFullscreenContent = false,
            isInsideSafeDrawingHost = false,
        )

        assertEquals(
            GeckoViewInsets(left = 0, top = 72, right = 0, bottom = 0),
            layout.margins,
        )
        assertEquals(
            GeckoViewInsets(left = 0, top = 0, right = 0, bottom = 48),
            layout.rendererSafeAreaOverride,
        )
    }

    @Test
    fun `forced safe area uses native margins and clears renderer insets`() {
        val safeArea = GeckoViewInsets(left = 8, top = 72, right = 6, bottom = 48)

        val layout = GeckoViewInsetRules.resolve(
            safeArea = safeArea,
            forceNativeSafeArea = true,
            isFullscreenContent = false,
            isInsideSafeDrawingHost = false,
        )

        assertEquals(safeArea, layout.margins)
        assertEquals(GeckoViewInsets.Zero, layout.rendererSafeAreaOverride)
    }

    @Test
    fun `forced native safe area rejects negative synthetic inset values`() {
        val layout = GeckoViewInsetRules.resolve(
            safeArea = GeckoViewInsets(left = -8, top = -1, right = -6, bottom = -48),
            forceNativeSafeArea = true,
            isFullscreenContent = false,
            isInsideSafeDrawingHost = false,
        )

        assertEquals(GeckoViewInsets.Zero, layout.margins)
        assertEquals(GeckoViewInsets.Zero, layout.rendererSafeAreaOverride)
    }

    @Test
    fun `fullscreen keeps renderer edge to edge despite site native override`() {
        val layout = GeckoViewInsetRules.resolve(
            safeArea = GeckoViewInsets(left = 18, top = 72, right = 6, bottom = 48),
            forceNativeSafeArea = true,
            isFullscreenContent = true,
            isInsideSafeDrawingHost = false,
        )

        assertEquals(GeckoViewInsets.Zero, layout.margins)
        assertEquals(null, layout.rendererSafeAreaOverride)
    }

    @Test
    fun `safe drawing host owns safe area without a second renderer inset`() {
        val layout = GeckoViewInsetRules.resolve(
            safeArea = GeckoViewInsets(left = 18, top = 72, right = 6, bottom = 48),
            forceNativeSafeArea = true,
            isFullscreenContent = true,
            isInsideSafeDrawingHost = true,
        )

        assertEquals(GeckoViewInsets.Zero, layout.margins)
        assertEquals(GeckoViewInsets.Zero, layout.rendererSafeAreaOverride)
    }
}
