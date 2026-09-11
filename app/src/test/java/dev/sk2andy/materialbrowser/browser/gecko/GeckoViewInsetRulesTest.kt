package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Test

class GeckoViewInsetRulesTest {
    @Test
    fun `engine safe area keeps Gecko surface behind system bars`() {
        val safeArea = GeckoViewInsets(left = 8, top = 96, right = 6, bottom = 34)
        val layout = GeckoViewInsetRules.resolve(
            safeArea = safeArea,
            forceNativeSafeArea = false,
            isFullscreenContent = false,
            isInsideSafeDrawingHost = false,
        )

        assertEquals(GeckoViewInsets.Zero, layout.margins)
        assertEquals(safeArea, layout.rendererSafeAreaOverride)
        assertEquals(safeArea.top, layout.scrollableTopInsetPx)
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
            GeckoViewInsets.Zero,
            layout.margins,
        )
        assertEquals(
            GeckoViewInsets(left = 0, top = 72, right = 0, bottom = 34),
            layout.rendererSafeAreaOverride,
        )
        assertEquals(72, layout.scrollableTopInsetPx)
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
            GeckoViewInsets.Zero,
            layout.margins,
        )
        assertEquals(
            GeckoViewInsets(left = 0, top = 72, right = 0, bottom = 48),
            layout.rendererSafeAreaOverride,
        )
        assertEquals(72, layout.scrollableTopInsetPx)
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
        assertEquals(0, layout.scrollableTopInsetPx)
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
        assertEquals(0, layout.scrollableTopInsetPx)
    }
}
