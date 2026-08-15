package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
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

        composeRule.onNodeWithTag(AppearanceSettingsTestTags.ShapeStyle).performClick()
        composeRule.onNodeWithText(context.getString(R.string.shape_style_angular)).performClick()
        assertEquals(
            AppearanceSettings(
                appearanceMode = BrowserAppearanceMode.Dark,
                colorPalette = BrowserColorPalette.Candy,
                surfaceStyle = BrowserSurfaceStyle.Frosted,
                shapeStyle = BrowserShapeStyle.Angular,
                frostedTransparencyPercent = 70,
                frostedAddressBarTransparencyPercent = 50,
                frostedBlurPercent = 90,
            ),
            settings,
        )
    }
}
