package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.shared.ui.theme.CandyDarkColors
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressEditorDarkThemeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun systemDarkMaterialYouKeepsRegularAndPrivateEditorHeroDark() {
        var privateMode by mutableStateOf(false)
        composeRule.setContent {
            // Run with system night mode enabled, matching the reported System appearance.
            assertTrue(isSystemInDarkTheme())
            MaterialBrowserTheme(
                settings = AppearanceSettings(
                    appearanceMode = BrowserAppearanceMode.System,
                    colorPalette = BrowserColorPalette.Dynamic,
                ),
            ) {
                EditorBackdrop(privateMode)
            }
        }

        assertHeroDark()
        composeRule.runOnIdle { privateMode = true }
        assertHeroDark()
    }

    @Test
    fun amoledEditorHeroStaysDarkInBothTabModes() {
        var privateMode by mutableStateOf(false)
        composeRule.setContent {
            MaterialBrowserTheme(
                settings = AppearanceSettings(
                    appearanceMode = BrowserAppearanceMode.Amoled,
                    colorPalette = BrowserColorPalette.Dynamic,
                ),
            ) {
                EditorBackdrop(privateMode)
            }
        }

        assertHeroDark()
        composeRule.runOnIdle { privateMode = true }
        assertHeroDark()
    }

    @Test
    fun brightDynamicAccentRolesCannotTurnDarkEditorOrSwitchSuggestionWhite() {
        composeRule.setContent {
            MaterialTheme(
                colorScheme = CandyDarkColors.copy(
                    primary = Color.White,
                    primaryContainer = Color.White,
                    onPrimaryContainer = Color.Black,
                    tertiaryContainer = Color.White,
                    onTertiaryContainer = Color.Black,
                    inverseSurface = Color.White,
                ),
            ) {
                Box(Modifier.fillMaxSize()) {
                    EditorBackdrop(privateMode = false)
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                        NavigationSuggestionRow(
                            suggestion = openTabSuggestion,
                            highlighted = true,
                            onHighlight = {},
                            onClick = {},
                            onFill = {},
                        )
                    }
                }
            }
        }

        assertHeroDark()
        assertSuggestionDark()
    }

    @Test
    fun materialYouSwitchSuggestionStaysDarkAndKeepsSeparateOpenAndFillActions() {
        var highlighted by mutableStateOf(false)
        val opens = AtomicInteger()
        val fills = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme(
                settings = AppearanceSettings(
                    appearanceMode = BrowserAppearanceMode.Dark,
                    colorPalette = BrowserColorPalette.Dynamic,
                ),
            ) {
                NavigationSuggestionRow(
                    suggestion = openTabSuggestion,
                    highlighted = highlighted,
                    onHighlight = { highlighted = true },
                    onClick = opens::incrementAndGet,
                    onFill = fills::incrementAndGet,
                )
            }
        }

        assertSuggestionDark()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.cd_fill_address_suggestion, openTabSuggestion.url),
        ).assertHasClickAction().performClick()
        assertEquals(0, opens.get())
        assertEquals(1, fills.get())
        composeRule.onNodeWithTag(AddressSuggestionTestTags.navigationRow(openTabSuggestion.url))
            .assertHasClickAction().performClick()
        assertEquals(1, opens.get())
        assertEquals(1, fills.get())
        assertSuggestionDark()
    }

    @Test
    fun privateNewTabHeroUsesDarkSurfaceWithReadablePrivateIcon() {
        composeRule.setContent {
            MaterialBrowserTheme(
                settings = AppearanceSettings(
                    appearanceMode = BrowserAppearanceMode.Dark,
                    colorPalette = BrowserColorPalette.Dynamic,
                ),
            ) {
                NewTabPage(
                    favorites = emptyList(),
                    incognito = true,
                    modeProgress = 1f,
                    revealOriginInRoot = Offset.Zero,
                    onSearch = {},
                    onFavorite = {},
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pixels = composeRule
            .onNodeWithContentDescription(context.getString(R.string.cd_open_search))
            .assertIsDisplayed().captureToImage().toPixelMap()
        val background = pixels[pixels.width / 10, pixels.height / 2]
        assertTrue("Private new-tab hero is light: $background", background.luminance() < 0.2f)
        val brightPixels = (pixels.width / 4 until pixels.width * 3 / 4).sumOf { x ->
            (pixels.height / 4 until pixels.height * 3 / 4).count { y ->
                pixels[x, y].luminance() > 0.6f
            }
        }
        assertTrue("Private icon must remain visible", brightPixels > pixels.width * pixels.height / 100)
    }

    @Composable
    private fun EditorBackdrop(privateMode: Boolean) {
        AddressEditorBackdrop(
            showStartContent = true,
            modeProgress = if (privateMode) 1f else 0f,
            revealOriginInRoot = Offset.Zero,
            wallpaper = null,
            onDismiss = {},
        )
    }

    private fun assertHeroDark() {
        val pixels = composeRule.onNodeWithTag(AddressEditorTestTags.Hero, useUnmergedTree = true)
            .assertIsDisplayed().captureToImage().toPixelMap()
        val background = pixels[pixels.width / 10, pixels.height / 2]
        assertTrue("Editor hero is light: $background", background.luminance() < 0.2f)
    }

    private fun assertSuggestionDark() {
        val pixels = composeRule
            .onNodeWithTag(AddressSuggestionTestTags.navigationRow(openTabSuggestion.url))
            .assertIsDisplayed().captureToImage().toPixelMap()
        val background = pixels[pixels.width / 2, pixels.height / 16]
        assertTrue("Switch-to-tab suggestion is light: $background", background.luminance() < 0.2f)
    }

    private val openTabSuggestion = AddressSuggestion(
        url = "https://google.com/",
        title = "Google Search",
        openTabId = "existing-tab",
    )
}
