package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
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
