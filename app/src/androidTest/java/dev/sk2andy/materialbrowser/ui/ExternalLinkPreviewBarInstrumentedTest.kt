package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ExternalLinkPreviewState
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalLinkPreviewBarInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactBarRoutesOpenProfileAndOverflowActions() {
        var opened = false
        var selectedProfileId: String? = null
        var shared = false
        val profiles = listOf(
            BrowserProfile(id = "default", emoji = "🍬"),
            BrowserProfile(id = "work", emoji = "💼"),
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                ExternalLinkPreviewBar(
                    state = ExternalLinkPreviewState(
                        sessionId = 1,
                        generation = 0,
                        currentUrl = "https://example.com/path",
                        targetProfileId = "default",
                    ),
                    profiles = profiles,
                    isDesktopView = false,
                    backdropSource = null,
                    rootBottomInWindowPx = 0,
                    onDismissPreview = {},
                    onOpenInCandy = { opened = true },
                    onSelectProfile = { selectedProfileId = it },
                    onShare = { shared = true },
                    onCopyLink = {},
                    onFindInPage = {},
                    onDesktopViewChange = {},
                )
            }
        }

        composeRule.onNodeWithTag(ExternalLinkPreviewTestTags.Open)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(ExternalLinkPreviewTestTags.Profile).performClick()
        composeRule.onNodeWithTag(ExternalLinkPreviewTestTags.profile("work")).performClick()
        composeRule.onNodeWithTag(ExternalLinkPreviewTestTags.Overflow).performClick()
        composeRule.onNodeWithTag(ExternalLinkPreviewTestTags.Share).performClick()

        assertTrue(opened)
        assertEquals("work", selectedProfileId)
        assertTrue(shared)
    }

    @Test
    fun compactBarHidesProfileSelectionWhenOnlyOneProfileExists() {
        composeRule.setContent {
            MaterialBrowserTheme {
                ExternalLinkPreviewBar(
                    state = ExternalLinkPreviewState(
                        sessionId = 1,
                        generation = 0,
                        currentUrl = "https://example.com/path",
                        targetProfileId = "default",
                    ),
                    profiles = listOf(BrowserProfile(id = "default", emoji = "🍬")),
                    isDesktopView = false,
                    backdropSource = null,
                    rootBottomInWindowPx = 0,
                    onDismissPreview = {},
                    onOpenInCandy = {},
                    onSelectProfile = {},
                    onShare = {},
                    onCopyLink = {},
                    onFindInPage = {},
                    onDesktopViewChange = {},
                )
            }
        }

        composeRule.onNodeWithTag(ExternalLinkPreviewTestTags.Open).assertIsEnabled()
        composeRule.onNodeWithTag(ExternalLinkPreviewTestTags.Profile).assertDoesNotExist()
    }

    @Test
    fun compactBarKeepsBackdropAndTransparencyAcrossRecomposition() {
        val firstBackdrop = object : CandyChromeBackdropSource {}
        val secondBackdrop = object : CandyChromeBackdropSource {}
        val observedBackdrop = AtomicReference<CandyChromeBackdropSource?>()
        val observedAlpha = AtomicReference<Float>()
        var backdrop by mutableStateOf<CandyChromeBackdropSource?>(firstBackdrop)
        var appearance by mutableStateOf(
            AppearanceSettings(
                surfaceStyle = BrowserSurfaceStyle.Frosted,
                frostedAddressBarTransparencyPercent = 20,
            ),
        )

        composeRule.setContent {
            CandyTheme(
                settings = appearance,
                chromeSurfaceRenderer = CandyChromeSurfaceRenderer {
                        source,
                        tokens,
                        modifier,
                        _,
                        _,
                        _,
                        _,
                        content,
                    ->
                    observedBackdrop.set(source)
                    observedAlpha.set(tokens.containerColor.alpha)
                    Box(modifier = modifier) { content() }
                },
            ) {
                ExternalLinkPreviewBar(
                    state = ExternalLinkPreviewState(
                        sessionId = 1,
                        generation = 0,
                        currentUrl = "https://example.com/path",
                        targetProfileId = "default",
                    ),
                    profiles = listOf(BrowserProfile(id = "default", emoji = "🍬")),
                    isDesktopView = false,
                    backdropSource = backdrop,
                    rootBottomInWindowPx = 0,
                    onDismissPreview = {},
                    onOpenInCandy = {},
                    onSelectProfile = {},
                    onShare = {},
                    onCopyLink = {},
                    onFindInPage = {},
                    onDesktopViewChange = {},
                )
            }
        }
        composeRule.waitForIdle()

        val firstAlpha = requireNotNull(observedAlpha.get())
        assertSame(firstBackdrop, observedBackdrop.get())

        composeRule.runOnUiThread {
            backdrop = secondBackdrop
            appearance = appearance.copy(frostedAddressBarTransparencyPercent = 70)
        }
        composeRule.waitForIdle()

        assertSame(secondBackdrop, observedBackdrop.get())
        assertNotEquals(firstAlpha, observedAlpha.get())
    }
}
