package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.shared.ui.settings.AppearanceSettingsSection
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderArtwork
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Captures real parent-page composables, without a browser engine or persisted user state. */
@RunWith(AndroidJUnit4::class)
class SettingsHeaderArtworkInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun themeScreensRetainArtworkWithoutPaperSeams() {
        var settings by mutableStateOf(AppearanceSettings(animationsEnabled = false))
        var artwork by mutableStateOf(SettingsHeaderArtwork.Appearance)
        var pageColor = Color.Unspecified
        var primaryColor = Color.Unspecified
        composeRule.setContent {
            MaterialBrowserTheme(settings = settings) {
                val colors = MaterialTheme.colorScheme
                SideEffect {
                    pageColor = colors.surfaceContainerLow
                    primaryColor = colors.primary
                }
                key(artwork) { ParentPage(artwork, settings) }
            }
        }

        val evidence = StringBuilder("screen,palette,mode,width,height\n")
        for (kind in SettingsHeaderArtwork.entries) {
            composeRule.runOnIdle { artwork = kind }
            var firstHeader: PixelMap? = null
            for (palette in BrowserColorPalette.entries) {
                for (mode in listOf(
                    BrowserAppearanceMode.Light,
                    BrowserAppearanceMode.Dark,
                    BrowserAppearanceMode.Amoled,
                )) {
                    composeRule.runOnIdle {
                        settings = settings.copy(colorPalette = palette, appearanceMode = mode)
                    }
                    composeRule.waitForIdle()
                    val node = composeRule.onNodeWithTag(tag(kind))
                        .assertIsDisplayed()
                        .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
                    val header = node.captureToImage().toPixelMap()
                    assertPaperMatchesPage(header, pageColor)
                    assertTonalArtwork(header, pageColor, primaryColor)
                    if (firstHeader == null) {
                        firstHeader = header
                    } else if (palette == BrowserColorPalette.Candy && mode == BrowserAppearanceMode.Dark) {
                        assertTrue("The live theme must recolor the header", differs(firstHeader, header))
                    }
                    val screen = composeRule.onRoot().captureToImage()
                    val name = "${kind.name.lowercase()}_${palette.name.lowercase()}_${mode.name.lowercase()}"
                    saveImage("$name.png", screen)
                    evidence.append("$kind,$palette,$mode,${screen.width},${screen.height}\n")
                }
            }
        }
        File(evidenceDirectory(), "captures.csv").writeText(evidence.toString())
    }

    @Test
    fun narrowLargeTextKeepsHeaderAndSettingsReachable() {
        var artwork by mutableStateOf(SettingsHeaderArtwork.Appearance)
        var settings by mutableStateOf(AppearanceSettings(
            appearanceMode = BrowserAppearanceMode.Amoled,
            colorPalette = BrowserColorPalette.Candy,
            animationsEnabled = false,
        ))
        var autoplayBlocked by mutableStateOf(false)
        var profilesEnabled by mutableStateOf(true)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MaterialBrowserTheme(settings = settings) {
                    Box(Modifier.width(320.dp)) {
                        key(artwork) {
                            ParentPage(
                                artwork = artwork,
                                settings = settings,
                                onSettingsChanged = { settings = it },
                                autoplayBlocked = autoplayBlocked,
                                onAutoplayChanged = { autoplayBlocked = it },
                                profilesEnabled = profilesEnabled,
                                onProfilesChanged = { profilesEnabled = it },
                            )
                        }
                    }
                }
            }
        }
        for (kind in SettingsHeaderArtwork.entries) {
            composeRule.runOnIdle { artwork = kind }
            composeRule.onNodeWithTag(tag(kind)).assertIsDisplayed()
            saveImage("${kind.name.lowercase()}_narrow_200_percent.png", composeRule.onRoot().captureToImage())
            when (kind) {
                SettingsHeaderArtwork.Appearance -> {
                    composeRule.onNodeWithTag(AppearanceSettingsTestTags.Animations)
                        .performScrollTo().performClick()
                    composeRule.runOnIdle { assertTrue(settings.animationsEnabled) }
                }
                SettingsHeaderArtwork.Tabs -> {
                    composeRule.onNodeWithText(context.getString(R.string.settings_profiles_title))
                        .performScrollTo().performClick()
                    composeRule.runOnIdle { assertTrue(!profilesEnabled) }
                }
                SettingsHeaderArtwork.Player -> {
                    composeRule.onNodeWithTag(PlayerSettingsTestTags.VideoAutoplay)
                        .performScrollTo().performClick()
                    composeRule.runOnIdle { assertTrue(autoplayBlocked) }
                }
            }
        }
    }

    @Test
    fun gesturesPageDoesNotGainTabsArtwork() {
        composeRule.setContent {
            MaterialBrowserTheme {
                TabsPage(section = TabSettingsSection.Gestures)
            }
        }
        composeRule.onNodeWithTag(tag(SettingsHeaderArtwork.Tabs)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.settings_home_gestures_title))
            .assertIsDisplayed()
    }

    @Composable
    private fun ParentPage(
        artwork: SettingsHeaderArtwork,
        settings: AppearanceSettings,
        onSettingsChanged: (AppearanceSettings) -> Unit = {},
        autoplayBlocked: Boolean = false,
        onAutoplayChanged: (Boolean) -> Unit = {},
        profilesEnabled: Boolean = true,
        onProfilesChanged: (Boolean) -> Unit = {},
    ) {
        when (artwork) {
            SettingsHeaderArtwork.Appearance -> AppearanceSettingsPage(
                settings = settings,
                section = AppearanceSettingsSection.Theme,
                navigationContent = { AppearanceSubpageLinks {} },
                onSettingsChanged = onSettingsChanged,
                onBack = {},
            )
            SettingsHeaderArtwork.Tabs -> TabsPage(
                profilesEnabled = profilesEnabled,
                onProfilesChanged = onProfilesChanged,
            )
            SettingsHeaderArtwork.Player -> PlayerSettingsPage(
                isVideoAutoplayBlocked = autoplayBlocked,
                isVideoAutoplayBlockingSupported = true,
                onVideoAutoplayBlockedChanged = onAutoplayChanged,
                onBack = {},
            )
        }
    }

    @Composable
    private fun TabsPage(
        section: TabSettingsSection = TabSettingsSection.Tabs,
        profilesEnabled: Boolean = true,
        onProfilesChanged: (Boolean) -> Unit = {},
    ) {
        TabsAndGesturesSettingsPage(
            section = section,
            inactiveTabLifetime = InactiveTabLifetime.Never,
            residentTabLimit = 10,
            tabOverviewMode = TabOverviewMode.Grid,
            tabStackFolderMode = TabOverviewMode.Grid,
            tabListStartsAtBottom = false,
            automaticTabSortingEnabled = false,
            dismissResistancePercent = 40,
            profilesEnabled = profilesEnabled,
            isAddressBarDockingEnabled = true,
            onInactiveTabLifetimeChanged = {},
            onResidentTabLimitChanged = {},
            onTabOverviewModeChanged = {},
            onTabStackFolderModeChanged = {},
            onTabListStartsAtBottomChanged = {},
            onAutomaticTabSortingEnabledChanged = {},
            onDismissResistancePercentChanged = {},
            onProfilesEnabledChanged = onProfilesChanged,
            onAddressBarDockingEnabledChanged = {},
            onAddressBarActions = {},
            onBack = {},
        )
    }

    private fun assertPaperMatchesPage(pixels: PixelMap, page: Color) {
        for (x in 2 until pixels.width - 2 step 7) {
            assertColorNear(page, pixels[x, 2])
            assertColorNear(page, pixels[x, pixels.height - 3])
        }
        for (y in 2 until pixels.height - 2 step 7) {
            assertColorNear(page, pixels[2, y])
            assertColorNear(page, pixels[pixels.width - 3, y])
        }
    }

    private fun assertTonalArtwork(pixels: PixelMap, page: Color, ink: Color) {
        val contrast = distance(page, ink)
        var darkDetails = 0
        var midtones = 0
        for (y in 0 until pixels.height step 4) {
            for (x in 0 until pixels.width step 4) {
                val pixel = pixels[x, y]
                val amount = distance(page, pixel) / contrast
                if (amount > 0.7f) darkDetails++
                if (amount in 0.15f..0.65f) midtones++
                if (amount > 0.15f) {
                    val tone = amount.coerceIn(0f, 1f)
                    assertColorNear(
                        Color(
                            red = page.red + (ink.red - page.red) * tone,
                            green = page.green + (ink.green - page.green) * tone,
                            blue = page.blue + (ink.blue - page.blue) * tone,
                        ),
                        pixel,
                    )
                }
            }
        }
        assertTrue("The original dark shapes must remain visible", darkDetails > 20)
        assertTrue("A flat tint must not erase the original intermediate tones", midtones > 20)
    }

    private fun differs(first: PixelMap, second: PixelMap): Boolean {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        return (0 until first.height step 8).any { y ->
            (0 until first.width step 8).any { x -> distance(first[x, y], second[x, y]) > 0.1f }
        }
    }

    private fun assertColorNear(expected: Color, actual: Color) {
        assertTrue("Theme color mismatch: expected $expected, got $actual", distance(expected, actual) <= 2f / 255f)
        assertEquals(1f, actual.alpha, 0.01f)
    }

    private fun distance(first: Color, second: Color): Float = max(
        max(abs(first.red - second.red), abs(first.green - second.green)),
        abs(first.blue - second.blue),
    )

    private fun tag(artwork: SettingsHeaderArtwork): String =
        "settings_header_artwork_${artwork.name.lowercase()}"

    private fun evidenceDirectory(): File =
        File(requireNotNull(context.getExternalFilesDir(null)), "settings-header-artwork").also {
            assertTrue(it.exists() || it.mkdirs())
        }

    private fun saveImage(name: String, image: ImageBitmap) {
        File(evidenceDirectory(), name).outputStream().use {
            assertTrue(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
