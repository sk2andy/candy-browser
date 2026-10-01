package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyDarkColors
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankTabColorRulesTest {
    @Test
    fun `dark blank tabs stay dark when accent and inverse roles are white`() {
        val colors = BlankTabColorRules.resolve(
            CandyDarkColors.copy(
                primary = Color.White,
                primaryContainer = Color.White,
                inverseSurface = Color.White,
                inverseOnSurface = Color.Black,
            ),
        )

        listOf(
            colors.regularBackground,
            colors.incognitoBackground,
            colors.regularHero,
            colors.incognitoHero,
        ).forEach { assertTrue(it.luminance() < 0.2f) }
        assertTrue(colors.incognitoContent.luminance() > 0.7f)
    }

    @Test
    fun `light blank tabs retain accent and inverse mode colors`() {
        val colors = BlankTabColorRules.resolve(CandyLightColors)

        assertEquals(Color(0xFFE8DEFF), colors.regularBackground)
        assertEquals(Color(0xFF6548C5), colors.regularHero)
        assertEquals(CandyLightColors.inverseSurface, colors.incognitoBackground)
        assertEquals(CandyLightColors.inverseSurface, colors.incognitoHero)
        assertEquals(CandyLightColors.inverseOnSurface, colors.incognitoContent)
    }
}
