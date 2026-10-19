package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetContentLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuPresentation
import dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuContentLayoutSettingsPage
import dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuSelectionTestTags
import dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuStyleSettingsPage
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenuPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun menuStyleSelectionStaysOnPageAndReselectionDoesNotWrite() {
        var layout by mutableStateOf(BrowserMenuLayout.Default.copy(presentation = BrowserMenuPresentation.Floating))
        var writes = 0
        var backs = 0
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                BrowserMenuStyleSettingsPage(
                    layout = layout,
                    resources = AndroidBrowserMenuSettingsResources,
                    backContentDescription = AndroidBrowserMenuSettingsResources.back(),
                    onBack = { backs++ },
                    onPresentationChanged = {
                        writes++
                        layout = layout.copy(presentation = it)
                    },
                )
            }
        }
        val floating = composeRule.onNodeWithTag(BrowserMenuSelectionTestTags.presentation(BrowserMenuPresentation.Floating))
        floating.performScrollTo().assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        composeRule.runOnIdle { assertEquals(0, writes) }
        val sheet = composeRule.onNodeWithTag(BrowserMenuSelectionTestTags.presentation(BrowserMenuPresentation.BottomSheet))
        sheet.performScrollTo().performClick()
        sheet.assertIsDisplayed().assertIsSelected().performClick()
        composeRule.runOnIdle {
            assertEquals(1, writes)
            assertEquals(0, backs)
            assertEquals(BrowserMenuPresentation.BottomSheet, layout.presentation)
        }
        headerBack()
        composeRule.runOnIdle {
            assertEquals(1, backs)
            assertEquals(1, writes)
        }
    }

    @Test
    fun contentLayoutBackAndReselectionKeepValueThenExplicitChoiceUpdatesOnce() {
        var layout by mutableStateOf(BrowserMenuLayout.Default.copy(
            presentation = BrowserMenuPresentation.BottomSheet,
            bottomSheetContentLayout = BrowserMenuBottomSheetContentLayout.Scroll,
        ))
        var writes = 0
        var backs = 0
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                BrowserMenuContentLayoutSettingsPage(
                    layout = layout,
                    resources = AndroidBrowserMenuSettingsResources,
                    backContentDescription = AndroidBrowserMenuSettingsResources.back(),
                    onBack = { backs++ },
                    onContentLayoutChanged = {
                        writes++
                        layout = layout.copy(bottomSheetContentLayout = it)
                    },
                )
            }
        }
        val scroll = composeRule.onNodeWithTag(BrowserMenuSelectionTestTags.contentLayout(BrowserMenuBottomSheetContentLayout.Scroll))
        scroll.performScrollTo().assertIsSelected().performClick()
        headerBack()
        composeRule.runOnIdle {
            assertEquals(1, backs)
            assertEquals(0, writes)
            assertEquals(BrowserMenuBottomSheetContentLayout.Scroll, layout.bottomSheetContentLayout)
        }
        val pages = composeRule.onNodeWithTag(BrowserMenuSelectionTestTags.contentLayout(BrowserMenuBottomSheetContentLayout.SwipePages))
        pages.performScrollTo().performClick()
        pages.assertIsDisplayed().assertIsSelected().performClick()
        composeRule.runOnIdle {
            assertEquals(1, writes)
            assertEquals(1, backs)
        }
    }

    @Test
    fun directContentLayoutPageWithFloatingStyleRetainsDisabledSelection() {
        val layout = BrowserMenuLayout.Default.copy(
            presentation = BrowserMenuPresentation.Floating,
            bottomSheetContentLayout = BrowserMenuBottomSheetContentLayout.SwipePages,
        )
        var writes = 0
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                BrowserMenuContentLayoutSettingsPage(
                    layout = layout,
                    resources = AndroidBrowserMenuSettingsResources,
                    backContentDescription = AndroidBrowserMenuSettingsResources.back(),
                    onBack = {},
                    onContentLayoutChanged = { writes++ },
                )
            }
        }
        BrowserMenuBottomSheetContentLayout.entries.forEach { value ->
            val option = composeRule.onNodeWithTag(BrowserMenuSelectionTestTags.contentLayout(value))
            option.performScrollTo().assertIsDisplayed().assertIsNotEnabled().performClick()
            if (value == layout.bottomSheetContentLayout) option.assertIsSelected()
        }
        composeRule.runOnIdle { assertEquals(0, writes) }
    }

    private fun headerBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performScrollTo().performClick()
    }
}
