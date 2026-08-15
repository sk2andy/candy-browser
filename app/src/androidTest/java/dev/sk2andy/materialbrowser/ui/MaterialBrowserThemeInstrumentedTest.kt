package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.ui.theme.BrowserChromeSurfaceRole
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialBrowserThemeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun translucentSurfaceInheritsThemeAwareContentColor() {
        val expected = AtomicReference<Color>()
        val actual = AtomicReference<Color>()

        composeRule.setContent {
            MaterialBrowserTheme {
                expected.set(MaterialTheme.colorScheme.onSurface)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                ) {
                    actual.set(LocalContentColor.current)
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(expected.get(), actual.get())
    }

    @Test
    fun amoledUsesBlackRootSurfaces() {
        val surface = AtomicReference<Color>()
        val background = AtomicReference<Color>()

        composeRule.setContent {
            MaterialBrowserTheme(
                settings = AppearanceSettings(
                    appearanceMode = BrowserAppearanceMode.Amoled,
                    surfaceStyle = BrowserSurfaceStyle.Frosted,
                ),
            ) {
                surface.set(MaterialTheme.colorScheme.surface)
                background.set(MaterialTheme.colorScheme.background)
            }
        }
        composeRule.waitForIdle()

        assertEquals(Color.Black, surface.get())
        assertEquals(Color.Black, background.get())
    }

    @Test
    fun palettesAndShapesProduceDistinctThemeTokens() {
        val candyPrimary = AtomicReference<Color>()
        val neutralPrimary = AtomicReference<Color>()
        val angularShape = AtomicReference<Any>()
        val extraRoundedShape = AtomicReference<Any>()

        composeRule.setContent {
            MaterialBrowserTheme(
                settings = AppearanceSettings(
                    appearanceMode = BrowserAppearanceMode.Light,
                    colorPalette = BrowserColorPalette.Candy,
                    shapeStyle = BrowserShapeStyle.Angular,
                ),
            ) {
                candyPrimary.set(MaterialTheme.colorScheme.primary)
                angularShape.set(MaterialTheme.shapes.large)
            }
            MaterialBrowserTheme(
                settings = AppearanceSettings(
                    appearanceMode = BrowserAppearanceMode.Light,
                    colorPalette = BrowserColorPalette.Neutral,
                    shapeStyle = BrowserShapeStyle.ExtraRounded,
                ),
            ) {
                neutralPrimary.set(MaterialTheme.colorScheme.primary)
                extraRoundedShape.set(MaterialTheme.shapes.large)
            }
        }
        composeRule.waitForIdle()

        assertNotEquals(candyPrimary.get(), neutralPrimary.get())
        assertNotEquals(angularShape.get(), extraRoundedShape.get())
    }

    @Test
    fun frostedChromeIsTranslucentExceptInAmoledMode() {
        val frosted = AtomicReference<Color>()
        val amoled = AtomicReference<Color>()

        composeRule.setContent {
            MaterialBrowserTheme(
                settings = AppearanceSettings(surfaceStyle = BrowserSurfaceStyle.Frosted),
            ) {
                frosted.set(browserChromeColor(Color.Red))
            }
            MaterialBrowserTheme(
                settings = AppearanceSettings(
                    appearanceMode = BrowserAppearanceMode.Amoled,
                    surfaceStyle = BrowserSurfaceStyle.Frosted,
                ),
            ) {
                amoled.set(browserChromeColor(Color.Red))
            }
        }
        composeRule.waitForIdle()

        assertEquals(0.82f, frosted.get().alpha, 0.001f)
        assertEquals(1f, amoled.get().alpha, 0f)
    }

    @Test
    fun frostedGeneralAndAddressBarTransparencyAreIndependent() {
        val general = AtomicReference<Color>()
        val addressBar = AtomicReference<Color>()

        composeRule.setContent {
            MaterialBrowserTheme(
                settings = AppearanceSettings(
                    surfaceStyle = BrowserSurfaceStyle.Frosted,
                    frostedTransparencyPercent = 70,
                    frostedAddressBarTransparencyPercent = 50,
                ),
            ) {
                general.set(browserChromeColor(Color.Red))
                addressBar.set(
                    browserChromeColor(
                        color = Color.Red,
                        role = BrowserChromeSurfaceRole.AddressBar,
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(0.41f, general.get().alpha, 0.005f)
        assertEquals(0.683f, addressBar.get().alpha, 0.005f)
    }
}
