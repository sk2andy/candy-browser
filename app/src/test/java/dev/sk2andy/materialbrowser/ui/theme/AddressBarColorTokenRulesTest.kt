package dev.sk2andy.materialbrowser.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.sk2andy.materialbrowser.data.BrowserAddressBarColorPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBarColorTokenRulesTest {
    @Test
    fun `theme preset preserves every Material color role`() {
        val colors = resolve(BrowserAddressBarColorPreset.Theme)

        assertEquals(THEME_CONTAINER, colors.containerColor)
        assertEquals(THEME_FIELD, colors.fieldContainerColor)
        assertEquals(THEME_CONTENT, colors.contentColor)
        assertEquals(THEME_SECONDARY_CONTENT, colors.secondaryContentColor)
        assertEquals(THEME_OUTLINE, colors.outlineColor)
        assertEquals(THEME_OUTLINE_VARIANT, colors.outlineVariantColor)
        assertEquals(THEME_ACCENT, colors.accentColor)
        assertEquals(THEME_ON_ACCENT, colors.onAccentColor)
    }

    @Test
    fun `presets replace only address colors and derive a distinct field`() {
        listOf(
            BrowserAddressBarColorPreset.Dimmed,
            BrowserAddressBarColorPreset.Graphite,
            BrowserAddressBarColorPreset.Black,
        ).forEach { preset ->
            val colors = resolve(preset)

            assertNotEquals(THEME_CONTAINER, colors.containerColor)
            assertNotEquals(colors.containerColor, colors.fieldContainerColor)
        }
    }

    @Test
    fun `custom light and dark colors choose readable monochrome content`() {
        val light = resolve(
            preset = BrowserAddressBarColorPreset.Custom,
            customColorArgb = 0xFFF5F5F5,
        )
        val dark = resolve(
            preset = BrowserAddressBarColorPreset.Custom,
            customColorArgb = 0xFF101114,
        )

        assertEquals(Color.Black, light.contentColor)
        assertEquals(Color.White, dark.contentColor)
        assertEquals(light.contentColor, light.accentColor)
        assertEquals(dark.contentColor, dark.accentColor)
    }

    @Test
    fun `invalid custom color falls back exactly to theme`() {
        assertEquals(
            resolve(BrowserAddressBarColorPreset.Theme),
            resolve(
                preset = BrowserAddressBarColorPreset.Custom,
                customColorArgb = null,
            ),
        )
    }

    @Test
    fun `frosted override finds contrast safe alpha for either website backdrop`() {
        val base = resolve(BrowserAddressBarColorPreset.Black)
        val safeAlpha = AddressBarColorTokenRules.minimumBackdropSafeAlpha(
            background = base.containerColor,
            content = base.contentColor,
        )
        val overlay = base.containerColor.copy(alpha = safeAlpha)

        assertTrue(safeAlpha > 0.2f)
        assertTrue(contrastRatio(overlay.compositeOver(Color.Black), base.contentColor) >= 4.5f)
        assertTrue(contrastRatio(overlay.compositeOver(Color.White), base.contentColor) >= 4.5f)
    }

    private fun resolve(
        preset: BrowserAddressBarColorPreset,
        customColorArgb: Long? = null,
    ): AddressBarResolvedColors = AddressBarColorTokenRules.resolve(
        preset = preset,
        customColorArgb = customColorArgb,
        themeContainerColor = THEME_CONTAINER,
        themeFieldContainerColor = THEME_FIELD,
        themeContentColor = THEME_CONTENT,
        themeSecondaryContentColor = THEME_SECONDARY_CONTENT,
        themeOutlineColor = THEME_OUTLINE,
        themeOutlineVariantColor = THEME_OUTLINE_VARIANT,
        themeAccentColor = THEME_ACCENT,
        themeOnAccentColor = THEME_ON_ACCENT,
    )

    private fun contrastRatio(
        first: Color,
        second: Color,
    ): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private companion object {
        val THEME_CONTAINER = Color(0xFFEAE0E5)
        val THEME_FIELD = Color(0xFFFFF8FB)
        val THEME_CONTENT = Color(0xFF201A1D)
        val THEME_SECONDARY_CONTENT = Color(0xFF514347)
        val THEME_OUTLINE = Color(0xFF837377)
        val THEME_OUTLINE_VARIANT = Color(0xFFD5C2C6)
        val THEME_ACCENT = Color(0xFF6548C5)
        val THEME_ON_ACCENT = Color.White
    }
}
