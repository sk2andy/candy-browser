package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyDarkColors
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressSuggestionColorRulesTest {
    @Test
    fun `dark open tab and highlighted navigation avoid bright accent containers`() {
        val scheme = CandyDarkColors.copy(
            primaryContainer = Color.White,
            onPrimaryContainer = Color.Black,
            tertiaryContainer = Color.White,
            onTertiaryContainer = Color.Black,
        )
        val normal = AddressSuggestionColorRules.navigation(scheme, false, true)
        val highlighted = AddressSuggestionColorRules.navigation(scheme, true, true)

        listOf(normal, highlighted).forEach { colors ->
            assertTrue(colors.container.luminance() < 0.2f)
            assertTrue(contrastRatio(colors.container, colors.content) > 4.5f)
        }
        assertNotEquals(normal.container, highlighted.container)
    }

    @Test
    fun `light open tab keeps accent container and highlight takes precedence`() {
        val normal = AddressSuggestionColorRules.navigation(CandyLightColors, false, true)
        val highlighted = AddressSuggestionColorRules.navigation(CandyLightColors, true, true)

        assertEquals(Color(0xFFE8DEFF), normal.container)
        assertEquals(Color(0xFF21005D), normal.content)
        assertEquals(Color(0xFFFFD9E3), highlighted.container)
        assertEquals(Color(0xFF31101D), highlighted.content)
    }

    @Test
    fun `ordinary unhighlighted history stays transparent in either theme`() {
        listOf(CandyDarkColors, CandyLightColors).forEach { scheme ->
            val colors = AddressSuggestionColorRules.navigation(scheme, false, false)

            assertEquals(Color.Transparent, colors.container)
            assertEquals(scheme.onSurface, colors.content)
        }
    }

    private fun contrastRatio(background: Color, foreground: Color): Float =
        (maxOf(background.luminance(), foreground.luminance()) + 0.05f) /
            (minOf(background.luminance(), foreground.luminance()) + 0.05f)
}
