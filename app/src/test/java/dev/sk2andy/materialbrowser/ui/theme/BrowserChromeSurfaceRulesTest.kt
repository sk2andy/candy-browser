package dev.sk2andy.materialbrowser.ui.theme

import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserChromeSurfaceRulesTest {
    @Test
    fun `clear stays opaque and uses strongest elevation`() {
        val specification = BrowserChromeSurfaceRules.resolve(
            surfaceStyle = BrowserSurfaceStyle.Clear,
            appearanceMode = BrowserAppearanceMode.Light,
            darkColors = false,
            frostedTransparencyPercent = AppearanceSettings.DEFAULT_FROSTED_TRANSPARENCY_PERCENT,
            frostedBlurPercent = AppearanceSettings.DEFAULT_FROSTED_BLUR_PERCENT,
        )

        assertEquals(1f, specification.containerAlpha)
        assertEquals(0f, specification.primaryTintFraction)
        assertEquals(14, specification.shadowElevationDp)
        assertFalse(specification.backdropBlurEnabled)
    }

    @Test
    fun `frosted is translucent and blurs its backdrop`() {
        val specification = BrowserChromeSurfaceRules.resolve(
            surfaceStyle = BrowserSurfaceStyle.Frosted,
            appearanceMode = BrowserAppearanceMode.Light,
            darkColors = false,
            frostedTransparencyPercent = 40,
            frostedBlurPercent = 60,
        )

        assertTrue(specification.containerAlpha < 1f)
        assertTrue(specification.blurRadiusPx > 0f)
        assertTrue(specification.backdropBlurEnabled)
    }

    @Test
    fun `amoled keeps frosted chrome opaque and unblurred`() {
        val specification = BrowserChromeSurfaceRules.resolve(
            surfaceStyle = BrowserSurfaceStyle.Frosted,
            appearanceMode = BrowserAppearanceMode.Amoled,
            darkColors = true,
            frostedTransparencyPercent = 80,
            frostedBlurPercent = 100,
        )

        assertEquals(1f, specification.containerAlpha)
        assertEquals(0f, specification.blurRadiusPx)
        assertFalse(specification.backdropBlurEnabled)
    }

    @Test
    fun `frosted controls are bounded and zero blur disables backdrop`() {
        val transparentWithoutBlur = BrowserChromeSurfaceRules.resolve(
            surfaceStyle = BrowserSurfaceStyle.Frosted,
            appearanceMode = BrowserAppearanceMode.Light,
            darkColors = false,
            frostedTransparencyPercent = 200,
            frostedBlurPercent = -1,
        )
        val opaqueWithMaximumBlur = BrowserChromeSurfaceRules.resolve(
            surfaceStyle = BrowserSurfaceStyle.Frosted,
            appearanceMode = BrowserAppearanceMode.Dark,
            darkColors = true,
            frostedTransparencyPercent = -1,
            frostedBlurPercent = 200,
        )

        assertEquals(0.2f, transparentWithoutBlur.containerAlpha, 0.001f)
        assertEquals(0f, transparentWithoutBlur.blurRadiusPx)
        assertFalse(transparentWithoutBlur.backdropBlurEnabled)
        assertEquals(1f, opaqueWithMaximumBlur.containerAlpha)
        assertEquals(36f, opaqueWithMaximumBlur.blurRadiusPx)
        assertFalse(opaqueWithMaximumBlur.backdropBlurEnabled)
    }

    @Test
    fun `liquid glass uses native material tokens independent of material surface choice`() {
        val specification = BrowserChromeSurfaceRules.resolve(
            designLanguage = CandyDesignLanguage.LiquidGlass,
            surfaceStyle = BrowserSurfaceStyle.Clear,
            appearanceMode = BrowserAppearanceMode.Light,
            darkColors = false,
            frostedTransparencyPercent = 40,
            frostedBlurPercent = 60,
        )

        assertEquals(CandyChromeTreatment.PlatformNative, specification.treatment)
        assertEquals(0, specification.tonalElevationDp)
        assertTrue(specification.backdropBlurEnabled)
        assertEquals(
            28,
            BrowserChromeSurfaceRules.cornerRadius(
                designLanguage = CandyDesignLanguage.LiquidGlass,
                shapeStyle = BrowserShapeStyle.Angular,
            ),
        )
    }

    @Test
    fun `amoled disables liquid glass and keeps opaque material fallback`() {
        val specification = BrowserChromeSurfaceRules.resolve(
            designLanguage = CandyDesignLanguage.LiquidGlass,
            surfaceStyle = BrowserSurfaceStyle.Frosted,
            appearanceMode = BrowserAppearanceMode.Amoled,
            darkColors = true,
            frostedTransparencyPercent = 80,
            frostedBlurPercent = 100,
        )

        assertEquals(CandyChromeTreatment.Opaque, specification.treatment)
        assertEquals(1f, specification.containerAlpha)
        assertFalse(specification.backdropBlurEnabled)
    }
}
