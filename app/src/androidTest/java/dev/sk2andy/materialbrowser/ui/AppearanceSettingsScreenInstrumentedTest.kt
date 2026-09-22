package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAddressBarColorPreset
import dev.sk2andy.materialbrowser.data.BrowserAddressBarStyle
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun eachAppearanceChoiceUpdatesOnlyItsSetting() {
        var settings by mutableStateOf(AppearanceSettings())
        composeRule.setContent {
            MaterialBrowserTheme(settings = settings) {
                AppearanceSettingsPage(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AppearanceMode).performClick()
        composeRule.onNodeWithText(context.getString(R.string.appearance_mode_dark)).performClick()
        assertEquals(BrowserAppearanceMode.Dark, settings.appearanceMode)

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.Animations).performClick()
        assertFalse(settings.animationsEnabled)

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.ForceDarkWebsites).performClick()
        assertTrue(settings.forceDarkWebsites)

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.WebContentFontSize)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(150f)
            }
        assertEquals(150, settings.webContentFontSizePercent)

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.ColorPalette).performClick()
        composeRule.onNodeWithText(context.getString(R.string.color_palette_candy)).performClick()
        assertEquals(BrowserColorPalette.Candy, settings.colorPalette)

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.SurfaceStyle).performClick()
        composeRule.onNodeWithTag(AppearanceSettingsTestTags.FrostedTransparency)
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.surface_style_frosted)).performClick()
        assertEquals(BrowserSurfaceStyle.Frosted, settings.surfaceStyle)
        composeRule.onNodeWithTag(AppearanceSettingsTestTags.FrostedTransparency)
            .assertExists()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(70f)
            }
        composeRule.onNodeWithTag(AppearanceSettingsTestTags.FrostedAddressBarTransparency)
            .assertExists()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(50f)
            }
        composeRule.onNodeWithTag(AppearanceSettingsTestTags.FrostedBlur)
            .assertExists()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(90f)
            }
        composeRule.onNodeWithText(
            context.getString(R.string.settings_frosted_blur_summary_gecko),
        ).assertExists()

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.ShapeStyle)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.shape_style_angular)).performClick()

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AddressBarStyle)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.address_bar_style_segmented))
            .performClick()
        assertEquals(
            AppearanceSettings(
                appearanceMode = BrowserAppearanceMode.Dark,
                animationsEnabled = false,
                forceDarkWebsites = true,
                webContentFontSizePercent = 150,
                colorPalette = BrowserColorPalette.Candy,
                surfaceStyle = BrowserSurfaceStyle.Frosted,
                shapeStyle = BrowserShapeStyle.Angular,
                addressBarStyle = BrowserAddressBarStyle.Segmented,
                frostedTransparencyPercent = 70,
                frostedAddressBarTransparencyPercent = 50,
                frostedBlurPercent = 90,
            ),
            settings,
        )
    }

    @Test
    fun forceDarkWebsitesIsDisabledWhenEngineDoesNotSupportIt() {
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                AppearanceSettingsPage(
                    settings = AppearanceSettings(),
                    onSettingsChanged = {},
                    onBack = {},
                    forceDarkWebsitesAvailable = false,
                )
            }
        }

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.ForceDarkWebsites)
            .assertIsNotEnabled()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_force_dark_websites_system_webview_only),
        ).assertExists()
    }

    @Test
    fun frostedSummaryDescribesSystemWebViewSupportFromAndroid13() {
        composeRule.setContent {
            MaterialBrowserTheme(
                settings = AppearanceSettings(surfaceStyle = BrowserSurfaceStyle.Frosted),
            ) {
                AppearanceSettingsPage(
                    settings = AppearanceSettings(surfaceStyle = BrowserSurfaceStyle.Frosted),
                    onSettingsChanged = {},
                    onBack = {},
                    browserEngineKind = AndroidBrowserEngineKind.SystemWebView,
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.settings_frosted_blur_summary_system_webview),
        ).assertExists()
    }

    @Test
    fun addressBarColorPresetCanBeResetToTheme() {
        var settings by mutableStateOf(AppearanceSettings())
        composeRule.setContent {
            MaterialBrowserTheme(settings = settings) {
                AppearanceSettingsPage(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AddressBarColor)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.address_bar_color_graphite))
            .performClick()
        assertEquals(BrowserAddressBarColorPreset.Graphite, settings.addressBarColorPreset)

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AddressBarColorReset)
            .performClick()

        assertEquals(BrowserAddressBarColorPreset.Theme, settings.addressBarColorPreset)
        assertEquals("", settings.addressBarCustomColorHex)
        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AddressBarColorReset)
            .assertIsNotEnabled()
    }

    @Test
    fun customAddressBarColorRejectsInvalidHexAndSavesNormalizedColor() {
        var settings by mutableStateOf(AppearanceSettings())
        composeRule.setContent {
            MaterialBrowserTheme(settings = settings) {
                AppearanceSettingsPage(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AddressBarColor)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.address_bar_color_custom))
            .performClick()
        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AddressBarCustomColorSave)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AddressBarCustomColor)
            .performTextInput("#12xz")
        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AddressBarCustomColorSave)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AddressBarCustomColor)
            .performTextClearance()
        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AddressBarCustomColor)
            .performTextInput("#1a2b3c")
        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AddressBarCustomColorSave)
            .assertIsEnabled()
            .performClick()

        assertEquals(BrowserAddressBarColorPreset.Custom, settings.addressBarColorPreset)
        assertEquals("#1A2B3C", settings.addressBarCustomColorHex)
    }
}
