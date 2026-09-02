package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun scrollBarSwitchUpdatesSetting() {
        var enabled by mutableStateOf(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserSettingsPage(
                    pageTranslationProvider = PageTranslationProvider.Google,
                    isFullImmersiveModeEnabled = false,
                    isStartupAnimationEnabled = true,
                    isScrollBarEnabled = enabled,
                    isVideoAutoplayBlocked = false,
                    isVideoAutoplayBlockingSupported = true,
                    isDefaultBrowser = false,
                    onFullImmersiveModeEnabledChanged = {},
                    onStartupAnimationEnabledChanged = {},
                    onScrollBarEnabledChanged = { enabled = it },
                    onVideoAutoplayBlockedChanged = {},
                    onPageTranslationProviderChanged = {},
                    onOpenDefaultBrowserSettings = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(BrowserSettingsTestTags.ScrollBar).performClick()

        assertTrue(enabled)
    }

    @Test
    fun startupAnimationSwitchUpdatesSetting() {
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserSettingsPage(
                    pageTranslationProvider = PageTranslationProvider.Google,
                    isFullImmersiveModeEnabled = false,
                    isStartupAnimationEnabled = enabled,
                    isScrollBarEnabled = false,
                    isVideoAutoplayBlocked = false,
                    isVideoAutoplayBlockingSupported = true,
                    isDefaultBrowser = false,
                    onFullImmersiveModeEnabledChanged = {},
                    onStartupAnimationEnabledChanged = { enabled = it },
                    onScrollBarEnabledChanged = {},
                    onVideoAutoplayBlockedChanged = {},
                    onPageTranslationProviderChanged = {},
                    onOpenDefaultBrowserSettings = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(BrowserSettingsTestTags.StartupAnimation).performClick()

        assertFalse(enabled)
    }

    @Test
    fun translationProviderChoiceUpdatesSetting() {
        var provider by mutableStateOf(PageTranslationProvider.Google)
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserSettingsPage(
                    pageTranslationProvider = provider,
                    isFullImmersiveModeEnabled = false,
                    isStartupAnimationEnabled = true,
                    isScrollBarEnabled = false,
                    isVideoAutoplayBlocked = false,
                    isVideoAutoplayBlockingSupported = true,
                    isDefaultBrowser = false,
                    onFullImmersiveModeEnabledChanged = {},
                    onStartupAnimationEnabledChanged = {},
                    onScrollBarEnabledChanged = {},
                    onVideoAutoplayBlockedChanged = {},
                    onPageTranslationProviderChanged = { provider = it },
                    onOpenDefaultBrowserSettings = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(BrowserSettingsTestTags.TranslationProvider).performClick()
        composeRule.onNode(
            hasText(PageTranslationProvider.Google.displayName) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).assertExists()
        composeRule.onNode(
            hasText(PageTranslationProvider.Kagi.displayName) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, false),
        ).assertExists()
        composeRule.onNodeWithText(PageTranslationProvider.Kagi.displayName).performClick()

        assertEquals(PageTranslationProvider.Kagi, provider)
        composeRule.onNodeWithText(
            context.getString(R.string.settings_translation_provider_kagi_summary),
        ).assertIsDisplayed()
    }
}
