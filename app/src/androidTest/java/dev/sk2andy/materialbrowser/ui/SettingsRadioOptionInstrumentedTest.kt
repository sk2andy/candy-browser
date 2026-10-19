package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsRadioOption
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyDarkColors
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyLightColors
import dev.sk2andy.materialbrowser.shared.ui.theme.NeutralDarkColors
import dev.sk2andy.materialbrowser.shared.ui.theme.NeutralLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRadioOptionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedIndicatorContrastsWithCapturedDynamicLightSurface() {
        val colors = lightColorScheme(
            primary = Color(0xFF2A4174),
            tertiaryContainer = Color(0xFF8A6A89),
            onTertiaryContainer = Color.White,
        )
        assertTrue(contrast(colors.primary, colors.tertiaryContainer) < 3f)
        assertSelectedIndicatorContrast(colors)
    }

    @Test
    fun selectedIndicatorContrastsWithCandyLightSurface() =
        assertSelectedIndicatorContrast(CandyLightColors)

    @Test
    fun selectedIndicatorContrastsWithCandyDarkSurface() =
        assertSelectedIndicatorContrast(CandyDarkColors)

    @Test
    fun selectedIndicatorContrastsWithNeutralLightSurface() =
        assertSelectedIndicatorContrast(NeutralLightColors)

    @Test
    fun selectedIndicatorContrastsWithNeutralDarkSurface() =
        assertSelectedIndicatorContrast(NeutralDarkColors)

    @Test
    fun selectedIndicatorContrastsWithDeviceDynamicLightSurface() =
        assertSelectedIndicatorContrast(
            dynamicLightColorScheme(InstrumentationRegistry.getInstrumentation().targetContext),
        )

    @Test
    fun selectedIndicatorContrastsWithDeviceDynamicDarkSurface() =
        assertSelectedIndicatorContrast(
            dynamicDarkColorScheme(InstrumentationRegistry.getInstrumentation().targetContext),
        )

    private fun assertSelectedIndicatorContrast(colors: ColorScheme) {
        var indicatorCenterX = 0
        composeRule.setContent {
            MaterialTheme(colorScheme = colors) {
                indicatorCenterX = with(LocalDensity.current) { 30.dp.roundToPx() }
                Column(Modifier.width(320.dp)) {
                    SettingsRadioOption(
                        label = "Selected option",
                        selected = true,
                        onClick = {},
                        modifier = Modifier.testTag("selected_radio_option"),
                    )
                    SettingsRadioOption(
                        label = "Other option",
                        selected = false,
                        onClick = {},
                        modifier = Modifier.testTag("unselected_radio_option"),
                    )
                }
            }
        }
        val option = composeRule.onNodeWithTag("selected_radio_option").assertIsSelected()
        val pixels = option.captureToImage().toPixelMap()
        val indicator = pixels[indicatorCenterX, pixels.height / 2]
        val background = pixels[pixels.width - indicatorCenterX, pixels.height / 2]
        assertEquals(colors.primary.toArgb(), indicator.toArgb())
        assertEquals(colors.surfaceContainerLowest.toArgb(), background.toArgb())
        val unselected = composeRule.onNodeWithTag("unselected_radio_option").assertIsNotSelected()
            .captureToImage().toPixelMap()
        assertEquals(background.toArgb(), unselected[unselected.width - indicatorCenterX, unselected.height / 2].toArgb())
        val ratio = contrast(indicator, background)
        assertTrue("Selected radio contrast $ratio must be at least 3:1", ratio >= 3f)
    }

    private fun contrast(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
