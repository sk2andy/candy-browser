package dev.sk2andy.materialbrowser.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchItem
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchKind
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSearchSettingsPageInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val item = DeviceSearchItem(
        id = "f:bookmark-one",
        title = "Shared bookmark",
        sourceUrl = "https://example.com",
        profileId = "candy",
        kind = DeviceSearchKind.Bookmark,
    )

    @Test
    fun selectionAndGlobalConsentAreSeparateUserActions() {
        var settings by mutableStateOf(DeviceSearchSettings(selectedIds = setOf(item.id)))
        composeRule.setContent {
            MaterialTheme {
                DeviceSearchSettingsPage(
                    settings = settings,
                    items = listOf(item),
                    profiles = listOf(BrowserProfile("candy", "🍬")),
                    busy = false,
                    failed = false,
                    onSettingsChanged = { settings = it },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(DeviceSearchTestTags.item(item.id))
            .performScrollTo()
            .assertIsOn()
            .assertIsNotEnabled()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(setOf(item.id), settings.selectedIds)
            assertEquals(false, settings.enabled)
        }
        composeRule.onNodeWithTag(DeviceSearchTestTags.Enabled).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(true, settings.enabled) }
        composeRule.onNodeWithTag(DeviceSearchTestTags.item(item.id))
            .performScrollTo()
            .assertIsOn()
            .performClick()
            .assertIsOff()
        composeRule.runOnIdle { assertEquals(emptySet<String>(), settings.selectedIds) }
        composeRule.onNodeWithTag(DeviceSearchTestTags.item(item.id)).performClick().assertIsOn()
        composeRule.onNodeWithTag(DeviceSearchTestTags.Enabled).performScrollTo().performClick()
        composeRule.onNodeWithTag(DeviceSearchTestTags.item(item.id))
            .performScrollTo()
            .assertIsOn()
            .assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(setOf(item.id), settings.selectedIds)
            assertEquals(false, settings.enabled)
        }
    }

    @Test
    fun indexUpdatesDisableItemChangesUntilCompletion() {
        composeRule.setContent {
            MaterialTheme {
                DeviceSearchSettingsPage(
                    settings = DeviceSearchSettings(),
                    items = listOf(item),
                    profiles = listOf(BrowserProfile("candy", "🍬")),
                    busy = true,
                    failed = false,
                    onSettingsChanged = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(DeviceSearchTestTags.item(item.id)).performScrollTo().assertIsNotEnabled()
    }
}
