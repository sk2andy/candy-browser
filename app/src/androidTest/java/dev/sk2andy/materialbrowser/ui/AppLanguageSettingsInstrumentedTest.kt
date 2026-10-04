package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppLanguagePreferences
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun traditionalChineseSelectionKeepsItsScriptAndCanSwitchToSimplified() {
        val simplified = Locale.forLanguageTag("zh-Hans")
        val traditional = Locale.forLanguageTag("zh-Hant")
        var languageTag by mutableStateOf("zh-Hant")
        composeRule.setContent {
            MaterialBrowserTheme {
                AppLanguageSettings(
                    languageTag = languageTag,
                    supportedLocales = listOf(simplified, traditional),
                    onLanguageChanged = { languageTag = it },
                )
            }
        }

        composeRule.onNodeWithText(traditional.getDisplayName(traditional)).assertIsDisplayed()
        composeRule.onNodeWithTag(BrowserSettingsTestTags.AppLanguage).performClick()
        composeRule.onNodeWithText(simplified.getDisplayName(simplified)).performClick()
        assertEquals("zh-Hans", languageTag)
        composeRule.onNodeWithText(simplified.getDisplayName(simplified)).assertIsDisplayed()
    }

    @Test
    fun longLanguageMenuCanScrollToVietnamese() {
        val vietnamese = Locale.forLanguageTag("vi")
        var languageTag by mutableStateOf("en")
        composeRule.setContent {
            MaterialBrowserTheme {
                AppLanguageSettings(
                    languageTag = languageTag,
                    supportedLocales = AppLanguagePreferences(
                        InstrumentationRegistry.getInstrumentation().targetContext,
                    ).supportedLocales,
                    onLanguageChanged = { languageTag = it },
                )
            }
        }

        composeRule.onNodeWithTag(BrowserSettingsTestTags.AppLanguage).performClick()
        composeRule.onNodeWithText(vietnamese.getDisplayName(vietnamese))
            .performScrollTo()
            .performClick()
        assertEquals("vi", languageTag)
        composeRule.onNodeWithText(vietnamese.getDisplayName(vietnamese)).assertIsDisplayed()
    }

    @Test
    fun russianNativeNameCanBeSelectedFromSupportedLanguages() {
        var languageTag by mutableStateOf("en")
        composeRule.setContent {
            MaterialBrowserTheme {
                AppLanguageSettings(
                    languageTag = languageTag,
                    supportedLocales = AppLanguagePreferences(
                        InstrumentationRegistry.getInstrumentation().targetContext,
                    ).supportedLocales,
                    onLanguageChanged = { languageTag = it },
                )
            }
        }

        composeRule.onNodeWithTag(BrowserSettingsTestTags.AppLanguage).performClick()
        composeRule.onNodeWithText("русский").performScrollTo().performClick()
        assertEquals("ru", languageTag)
        composeRule.onNodeWithText("русский").assertIsDisplayed()
    }

    @Test
    fun nativeLanguageNamesSelectPolishCzechAndDeviceDefault() {
        var languageTag by mutableStateOf("")
        composeRule.setContent {
            MaterialBrowserTheme {
                AppLanguageSettings(
                    languageTag = languageTag,
                    supportedLocales = listOf(Locale.forLanguageTag("pl"), Locale.forLanguageTag("cs")),
                    onLanguageChanged = { languageTag = it },
                )
            }
        }

        composeRule.onNodeWithTag(BrowserSettingsTestTags.AppLanguage).performClick()
        composeRule.onNodeWithText("polski").performClick()
        assertEquals("pl", languageTag)
        composeRule.onNodeWithText("polski").assertIsDisplayed()
        composeRule.onNodeWithTag(BrowserSettingsTestTags.AppLanguage).performClick()
        composeRule.onNodeWithText("čeština").performClick()
        assertEquals("cs", languageTag)
        composeRule.onNodeWithText("čeština").assertIsDisplayed()
        composeRule.onNodeWithTag(BrowserSettingsTestTags.AppLanguage).performClick()
        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getString(R.string.settings_app_language_system),
        ).performClick()
        assertEquals("", languageTag)
        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getString(R.string.settings_app_language_system),
        ).assertIsDisplayed()
    }
}
