package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetContentLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetToolbarRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayoutRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuPresentation
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
    fun toolbarChoicesIncludeCommandsTogglesAndEmptySlotsAndActivateSelectedHiddenActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var currentLayout = BrowserMenuLayoutRules.update(
            BrowserMenuLayoutRules.update(
                BrowserMenuLayout.Default.copy(
                    presentation = BrowserMenuPresentation.BottomSheet,
                    bottomSheetContentLayout = BrowserMenuBottomSheetContentLayout.SwipePages,
                ),
                BrowserMenuEntry.Print,
                BrowserMenuLocation.Nowhere,
            ),
            BrowserMenuEntry.Share,
            BrowserMenuLocation.Nowhere,
        )
        composeRule.setContent {
            var layout by remember { mutableStateOf(currentLayout) }
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                BrowserMenuSettingsPage(
                    layout = layout,
                    onLocationChanged = { entry, location ->
                        layout = BrowserMenuLayoutRules.update(layout, entry, location)
                        currentLayout = layout
                    },
                    onBottomSheetToolbarEntriesChanged = { entries ->
                        layout = layout.copy(bottomSheetToolbarEntries = entries)
                        currentLayout = layout
                    },
                    onBack = {},
                )
            }
        }

        chooseToolbarEntry(0, context.getString(R.string.action_share))
        chooseToolbarEntry(1, context.getString(R.string.action_desktop_view))
        chooseToolbarEntry(2, context.getString(R.string.settings_menu_bottom_sheet_toolbar_empty))

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    BrowserMenuEntry.Share,
                    BrowserMenuEntry.DesktopView,
                    null,
                    BrowserMenuEntry.Favorite,
                    BrowserMenuEntry.Pin,
                ),
                currentLayout.bottomSheetToolbarEntries,
            )
            assertEquals(BrowserMenuPresentation.BottomSheet, currentLayout.presentation)
            assertEquals(BrowserMenuBottomSheetContentLayout.SwipePages, currentLayout.bottomSheetContentLayout)
            assertEquals(
                BrowserMenuLocation.Tab,
                BrowserMenuLayoutRules.location(currentLayout, BrowserMenuEntry.Share),
            )
            assertEquals(
                BrowserMenuLocation.Nowhere,
                BrowserMenuLayoutRules.location(currentLayout, BrowserMenuEntry.Print),
            )
        }
    }

    @Test
    fun toolbarConfigurationAppearsOnlyForBottomSheetAndPreservesDefaults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var currentLayout = BrowserMenuLayout.Default.copy(presentation = BrowserMenuPresentation.Floating)
        composeRule.setContent {
            var layout by remember { mutableStateOf(currentLayout) }
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                BrowserMenuSettingsPage(
                    layout = layout,
                    onLocationChanged = { _, _ -> },
                    onPresentationChanged = { presentation ->
                        layout = layout.copy(presentation = presentation)
                        currentLayout = layout
                    },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(0))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.Presentation).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_menu_presentation_bottom_sheet))
            .performClick()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(0))
            .performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(BrowserMenuBottomSheetToolbarRules.DefaultEntries, currentLayout.bottomSheetToolbarEntries)
        }
    }

    @Test
    fun presentationAndSectionChoicesPreserveCustomActionLocations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var currentLayout = BrowserMenuLayoutRules.update(
            BrowserMenuLayout.Default.copy(presentation = BrowserMenuPresentation.Floating),
            BrowserMenuEntry.Share,
            BrowserMenuLocation.Nowhere,
        )
        composeRule.setContent {
            var layout by remember { mutableStateOf(currentLayout) }
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                BrowserMenuSettingsPage(
                    layout = layout,
                    onLocationChanged = { _, _ -> },
                    onPresentationChanged = { presentation ->
                        layout = layout.copy(presentation = presentation)
                        currentLayout = layout
                    },
                    onBottomSheetContentLayoutChanged = { contentLayout ->
                        layout = layout.copy(bottomSheetContentLayout = contentLayout)
                        currentLayout = layout
                    },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.Presentation).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_menu_presentation_bottom_sheet))
            .performClick()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.ContentLayout).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_menu_bottom_sheet_layout_pages))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(BrowserMenuPresentation.BottomSheet, currentLayout.presentation)
            assertEquals(BrowserMenuBottomSheetContentLayout.SwipePages, currentLayout.bottomSheetContentLayout)
            assertEquals(
                BrowserMenuLocation.Nowhere,
                BrowserMenuLayoutRules.location(currentLayout, BrowserMenuEntry.Share),
            )
        }
    }

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

    private fun chooseToolbarEntry(index: Int, label: String) {
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(index))
            .performScrollTo().performClick()
        composeRule.onNode(hasText(label) and hasAnyAncestor(isPopup()))
            .performScrollTo().performClick()
    }
}
