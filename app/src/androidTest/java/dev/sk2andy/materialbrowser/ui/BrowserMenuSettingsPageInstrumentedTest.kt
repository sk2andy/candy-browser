package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayoutRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation
import dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuSettingsTestTags
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMenuSettingsPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun locationChoiceUpdatesSharedLayoutImmediately() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var currentLayout = BrowserMenuLayout.Default
        composeRule.setContent {
            var layout by remember { mutableStateOf(currentLayout) }
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                BrowserMenuSettingsPage(
                    layout = layout,
                    onLocationChanged = { entry, location ->
                        layout = BrowserMenuLayoutRules.update(layout, entry, location)
                        currentLayout = layout
                    },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(
            BrowserMenuSettingsTestTags.entry(BrowserMenuEntry.Share),
        ).performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_menu_location_nowhere),
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(
                BrowserMenuLocation.Nowhere,
                BrowserMenuLayoutRules.location(currentLayout, BrowserMenuEntry.Share),
            )
        }
    }
}
