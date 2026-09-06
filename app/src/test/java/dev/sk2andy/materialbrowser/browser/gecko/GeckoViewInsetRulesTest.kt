package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Test

class GeckoViewInsetRulesTest {
    @Test
    fun `status and cutout safe area reach edge to edge renderer`() {
        val layout = GeckoViewInsetRules.resolve(
            safeArea = GeckoViewInsets(left = 8, top = 96, right = 6, bottom = 0),
            forceNativeSafeArea = false,
        )

        assertEquals(GeckoViewInsets(left = 0, top = 0, right = 0, bottom = 0), layout.margins)
        assertEquals(0, layout.bottomContentClippingPx)
    }

    @Test
    fun `gesture navigation safe area reaches edge to edge renderer`() {
        val layout = GeckoViewInsetRules.resolve(
            safeArea = GeckoViewInsets(left = 0, top = 72, right = 0, bottom = 34),
            forceNativeSafeArea = false,
        )

        assertEquals(GeckoViewInsets(left = 0, top = 0, right = 0, bottom = 0), layout.margins)
        assertEquals(34, layout.bottomContentClippingPx)
    }

    @Test
    fun `three button navigation also remains edge to edge`() {
        val layout = GeckoViewInsetRules.resolve(
            safeArea = GeckoViewInsets(left = 0, top = 72, right = 0, bottom = 48),
            forceNativeSafeArea = false,
        )

        assertEquals(GeckoViewInsets(left = 0, top = 0, right = 0, bottom = 0), layout.margins)
        assertEquals(48, layout.bottomContentClippingPx)
    }

    @Test
    fun `forced safe area uses native margins and clears renderer insets`() {
        val safeArea = GeckoViewInsets(left = 8, top = 72, right = 6, bottom = 48)

        val layout = GeckoViewInsetRules.resolve(
            safeArea = safeArea,
            forceNativeSafeArea = true,
        )

        assertEquals(safeArea, layout.margins)
        assertEquals(0, layout.bottomContentClippingPx)
    }
}
