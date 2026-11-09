package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.HomePageSettings
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePageSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun independentUrlsSaveAndCancelDoesNotChangeSettings() {
        var settings by mutableStateOf(HomePageSettings())
        composeRule.setContent {
            MaterialBrowserTheme {
                HomePageSettingsSection(settings) { settings = it }
            }
        }
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Open).performClick()
        composeRule.onNodeWithTag(HomePageSettingsTestTags.HomeUrl)
            .performTextReplacement("https://home.example/dashboard")
        composeRule.onNodeWithTag(HomePageSettingsTestTags.NewTabUrl)
            .performTextReplacement("https://tabs.example/start")
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Save).performClick()
        composeRule.runOnIdle {
            assertEquals("https://home.example/dashboard", settings.homeUrl)
            assertEquals("https://tabs.example/start", settings.newTabUrl)
        }
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Open).performClick()
        composeRule.onNodeWithTag(HomePageSettingsTestTags.HomeUrl)
            .performTextReplacement("https://discard.example/")
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Cancel).performClick()
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Open).performClick()
        composeRule.onNodeWithTag(HomePageSettingsTestTags.HomeUrl)
            .assertTextContains("https://home.example/dashboard")
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "issue350-settings.png")
        checkNotNull(instrumentation.uiAutomation.takeScreenshot()).let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test
    fun systemBackDiscardsDraft() {
        var settings by mutableStateOf(HomePageSettings(homeUrl = "https://keep.example/"))
        composeRule.setContent {
            MaterialBrowserTheme { HomePageSettingsSection(settings) { settings = it } }
        }
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Open).performClick()
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Reset).performScrollTo().performClick()
        androidx.test.espresso.Espresso.pressBack()
        composeRule.runOnIdle { assertEquals("https://keep.example/", settings.homeUrl) }
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Open).performClick()
        composeRule.onNodeWithTag(HomePageSettingsTestTags.HomeUrl).assertTextContains("https://keep.example/")
    }

    @Test
    fun invalidSchemeCannotSaveAndCopyAndResetRequireSave() {
        var settings by mutableStateOf(HomePageSettings(homeUrl = "https://home.example/"))
        composeRule.setContent {
            MaterialBrowserTheme {
                HomePageSettingsSection(settings) { settings = it }
            }
        }
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Open).performClick()
        composeRule.onNodeWithTag(HomePageSettingsTestTags.NewTabUrl)
            .performTextReplacement("javascript:alert(1)")
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Save).assertIsNotEnabled()
        composeRule.onNodeWithTag(HomePageSettingsTestTags.CopyHome).performScrollTo().performClick()
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Save).performClick()
        composeRule.runOnIdle { assertEquals(settings.homeUrl, settings.newTabUrl) }
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Open).performClick()
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Reset).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("https://home.example/", settings.homeUrl) }
        composeRule.onNodeWithTag(HomePageSettingsTestTags.Save).performClick()
        composeRule.runOnIdle { assertEquals(HomePageSettings(), settings) }
    }
}
