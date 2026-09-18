package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.shared.browser.AddressBarLongPressAction
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarLongPressSettingsPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun groupedCatalogShowsEveryActionAndUpdatesSelection() {
        var selected by mutableStateOf(AddressBarLongPressAction.OpenReader)
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                AddressBarLongPressSettingsPage(
                    selectedAction = selected,
                    onActionSelected = { selected = it },
                    onBack = {},
                )
            }
        }

        AddressBarLongPressAction.entries.forEach { action ->
            composeRule.onNodeWithTag(AddressBarLongPressSettingsTestTags.action(action))
                .performScrollTo()
                .assertIsDisplayed()
        }

        composeRule.onNodeWithTag(
            AddressBarLongPressSettingsTestTags.action(AddressBarLongPressAction.CopyUrl),
        ).performScrollTo().performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(AddressBarLongPressAction.CopyUrl, selected)
        }
    }
}
