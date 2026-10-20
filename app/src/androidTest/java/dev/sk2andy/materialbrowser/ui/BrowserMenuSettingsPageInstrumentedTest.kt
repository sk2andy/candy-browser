package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetContentLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetToolbarRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayoutRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuPresentation
import dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuStyleSettingsPage
import dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuContentLayoutSettingsPage
import dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuSettingsTestTags
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMenuSettingsPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statusBarEntryAvailabilityPreservesSavedMenuLocation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val savedLayout = BrowserMenuLayoutRules.update(
            BrowserMenuLayout.Default.copy(presentation = BrowserMenuPresentation.BottomSheet),
            BrowserMenuEntry.StatusBarScroll,
            BrowserMenuLocation.Nowhere,
        )
        var enabled by mutableStateOf(false)
        var writes = 0
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                TestMenuSettingsHost(
                    layout = savedLayout,
                    statusBarScrollEnabled = enabled,
                    onLocationChanged = { _, _ -> writes++ },
                    onBack = {},
                )
            }
        }
        val entry = composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.entry(BrowserMenuEntry.StatusBarScroll))
        entry.assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.entry(BrowserMenuEntry.OpenFirefoxExtensions))
            .assertDoesNotExist()
        composeRule.runOnIdle { enabled = true }
        entry.performScrollTo().assertIsDisplayed()
        composeRule.onNode(
            hasText(context.getString(R.string.settings_menu_location_nowhere)) and
                hasAnyAncestor(hasTestTag(BrowserMenuSettingsTestTags.entry(BrowserMenuEntry.StatusBarScroll))),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.runOnIdle { enabled = false }
        entry.assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(0, writes)
            assertEquals(BrowserMenuLocation.Nowhere,
                BrowserMenuLayoutRules.location(savedLayout, BrowserMenuEntry.StatusBarScroll))
        }
    }

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
                TestMenuSettingsHost(
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
    fun floatingMenuKeepsAllFiveToolbarSlotsVisibleDisabledAndPreservesDefaults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var currentLayout = BrowserMenuLayout.Default.copy(presentation = BrowserMenuPresentation.Floating)
        composeRule.setContent {
            var layout by remember { mutableStateOf(currentLayout) }
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                TestMenuSettingsHost(
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

        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.ContentLayout)
            .assertIsNotEnabled().performClick()
        (0..4).forEach { index ->
            composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(index))
                .performScrollTo().assertIsDisplayed().assertIsNotEnabled().performClick()
        }
        composeRule.onAllNodes(isPopup()).assertCountEquals(0)
        composeRule.onNodeWithTag("settings_selection_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.Presentation)
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_menu_presentation_bottom_sheet))
            .performScrollTo().performClick()
        headerBack()
        (0..4).forEach { index ->
            composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(index))
                .performScrollTo().assertIsDisplayed().assertIsEnabled()
        }
        composeRule.runOnIdle {
            assertEquals(BrowserMenuBottomSheetToolbarRules.DefaultEntries, currentLayout.bottomSheetToolbarEntries)
        }
    }

    @Test
    fun changingPresentationRetainsCustomToolbarValuesAndDisabledControlsEmitNoWrites() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entries = listOf(
            BrowserMenuEntry.Share,
            BrowserMenuEntry.DesktopView,
            null,
            BrowserMenuEntry.Favorite,
            BrowserMenuEntry.Pin,
        )
        var currentLayout = BrowserMenuLayout.Default.copy(
            presentation = BrowserMenuPresentation.BottomSheet,
            bottomSheetContentLayout = BrowserMenuBottomSheetContentLayout.SwipePages,
            bottomSheetToolbarEntries = entries,
        )
        var dependentWrites = 0
        composeRule.setContent {
            var layout by remember { mutableStateOf(currentLayout) }
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                TestMenuSettingsHost(
                    layout = layout,
                    onLocationChanged = { _, _ -> dependentWrites++ },
                    onPresentationChanged = {
                        layout = layout.copy(presentation = it)
                        currentLayout = layout
                    },
                    onBottomSheetContentLayoutChanged = { dependentWrites++ },
                    onBottomSheetToolbarEntriesChanged = { dependentWrites++ },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.Presentation).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_menu_presentation_floating))
            .performScrollTo().performClick()
        headerBack()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.ContentLayout)
            .assertTextContains(context.getString(R.string.settings_menu_bottom_sheet_layout_pages))
            .assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(0))
            .performScrollTo().assertTextContains(context.getString(R.string.action_share))
            .assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(2))
            .performScrollTo().assertTextContains(context.getString(R.string.settings_menu_bottom_sheet_toolbar_empty))
            .assertIsNotEnabled().performClick()
        composeRule.onAllNodes(isPopup()).assertCountEquals(0)
        composeRule.onNodeWithTag("settings_selection_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.Presentation)
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_menu_presentation_bottom_sheet))
            .performScrollTo().performClick()
        headerBack()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(0))
            .performScrollTo().assertIsEnabled().assertTextContains(context.getString(R.string.action_share))
        composeRule.runOnIdle {
            assertEquals(entries, currentLayout.bottomSheetToolbarEntries)
            assertEquals(BrowserMenuBottomSheetContentLayout.SwipePages, currentLayout.bottomSheetContentLayout)
            assertEquals(0, dependentWrites)
        }
    }

    @Test
    fun groupedMenuLabelsWrapAtDoubleFontScaleAndEveryToolbarSlotRemainsReachable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    Box(Modifier.width(320.dp)) {
                        TestMenuSettingsHost(
                            layout = BrowserMenuLayout.Default,
                            onLocationChanged = { _, _ -> },
                            onBack = {},
                        )
                    }
                }
            }
        }
        val labels = listOf(
            context.getString(R.string.settings_menu_actions_title),
            context.getString(R.string.settings_menu_presentation_title),
            context.getString(R.string.settings_menu_bottom_sheet_layout_title),
        ) + (1..5).map { context.getString(R.string.settings_menu_bottom_sheet_toolbar_slot, it) }
        labels.forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(label, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.size)
            val result = layouts.single()
            assertTrue(label, result.lineCount > 0)
            // Semantics may retain the paragraph's wider measure constraint for short labels.
            // Check the actual lines, including ellipsis and complete text, instead of that width.
            val linesFit = (0 until result.lineCount).all { line ->
                result.getLineLeft(line) >= -1f && result.getLineRight(line) <= result.size.width + 1f &&
                    result.getLineTop(line) >= -1f && result.getLineBottom(line) <= result.size.height + 1f
            }
            if (!linesFit || result.didOverflowHeight) {
                val directory = File(context.getExternalFilesDir(null), "settings-combined-evidence").apply { mkdirs() }
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .takeScreenshot(File(directory, "menu-text-overflow.png"))
            }
            val diagnostic =
                "$label: size=${result.size}, constraints=${result.layoutInput.constraints}, " +
                    "width=${result.didOverflowWidth}, height=${result.didOverflowHeight}, " +
                    "paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}, " +
                    "lineBounds=${(0 until result.lineCount).map { result.getLineLeft(it) to result.getLineRight(it) }}"
            assertTrue(diagnostic, linesFit)
            assertFalse(diagnostic, result.didOverflowHeight)
            (0 until result.lineCount).forEach { line -> assertFalse(label, result.isLineEllipsized(line)) }
            assertEquals(label, label.length, result.getLineEnd(result.lineCount - 1))
        }
        (0..4).forEach { index ->
            composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(index))
                .performScrollTo().assertIsDisplayed()
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
                TestMenuSettingsHost(
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
            .performScrollTo().performClick()
        headerBack()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.ContentLayout).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_menu_bottom_sheet_layout_pages))
            .performScrollTo().performClick()
        headerBack()

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
                TestMenuSettingsHost(
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
        composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.location(BrowserMenuLocation.Nowhere))
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_selection_dialog").assertDoesNotExist()

        composeRule.runOnIdle {
            assertEquals(
                BrowserMenuLocation.Nowhere,
                BrowserMenuLayoutRules.location(currentLayout, BrowserMenuEntry.Share),
            )
        }
    }

    @Test
    fun focusedSelectorsPreserveChoicesAndBothBackActionsReturnWithoutWrites() {
        var layout by mutableStateOf(BrowserMenuLayout.Default.copy(presentation = BrowserMenuPresentation.BottomSheet))
        var writes = 0
        var parentBacks = 0
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                TestMenuSettingsHost(
                    layout = layout,
                    onLocationChanged = { entry, location ->
                        layout = BrowserMenuLayoutRules.update(layout, entry, location)
                        writes++
                    },
                    onBottomSheetToolbarEntriesChanged = { entries ->
                        layout = layout.copy(bottomSheetToolbarEntries = entries)
                        writes++
                    },
                    onBack = { parentBacks++ },
                )
            }
        }
        val dialog = composeRule.onNodeWithTag("settings_selection_dialog")
        fun openSlot() {
            composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(0))
                .performScrollTo().performClick()
            dialog.assertIsDisplayed()
        }
        openSlot()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarEntry(BrowserMenuEntry.Back))
            .assertIsSelected()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarEntry(BrowserMenuEntry.StatusBarScroll))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarEntry(BrowserMenuEntry.OpenFirefoxExtensions))
            .assertDoesNotExist()
        composeRule.onNode(hasTestTag("settings_page_back") and hasAnyAncestor(hasTestTag("settings_selection_dialog")))
            .performScrollTo().performClick()
        dialog.assertDoesNotExist()
        openSlot()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitForIdle()
        dialog.assertDoesNotExist()
        openSlot()
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarEntry(BrowserMenuEntry.Back))
            .performScrollTo().performClick()
        dialog.assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(0, writes)
            assertEquals(0, parentBacks)
        }
        composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.entry(BrowserMenuEntry.Reload))
            .performScrollTo().performClick()
        composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.location(BrowserMenuLocation.Tab)).assertIsSelected()
        composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.location(BrowserMenuLocation.TabSwitcher)).assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.location(BrowserMenuLocation.Both)).assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.location(BrowserMenuLocation.Nowhere))
            .performScrollTo().performClick()
        dialog.assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.entry(BrowserMenuEntry.Reload))
            .performScrollTo().performClick()
        composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.location(BrowserMenuLocation.Nowhere)).assertIsSelected()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitForIdle()
        dialog.assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, writes)
            assertEquals(0, parentBacks)
            assertEquals(BrowserMenuLocation.Nowhere, BrowserMenuLayoutRules.location(layout, BrowserMenuEntry.Reload))
        }
        for (location in BrowserMenuLocation.entries) {
            composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.entry(BrowserMenuEntry.Share))
                .performScrollTo().performClick()
            BrowserMenuLocation.entries.forEach { available ->
                composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.location(available)).assertExists()
            }
            composeRule.onNodeWithTag(BrowserMenuSettingsTestTags.location(location))
                .performScrollTo().performClick()
            dialog.assertDoesNotExist()
            composeRule.runOnIdle {
                assertEquals(location, BrowserMenuLayoutRules.location(layout, BrowserMenuEntry.Share))
            }
        }
    }

    private fun headerBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performScrollTo().performClick()
    }

    private fun chooseToolbarEntry(index: Int, label: String) {
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(index))
            .performScrollTo().performClick()
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag("settings_selection_dialog")))
            .performScrollTo().performClick()
    }
}

/** Test-only navigation keeps the same explicit selection and Back flow as the production router. */
@Composable
private fun TestMenuSettingsHost(
    layout: BrowserMenuLayout,
    statusBarScrollEnabled: Boolean = false,
    onLocationChanged: (BrowserMenuEntry, BrowserMenuLocation) -> Unit,
    onBack: () -> Unit,
    onPresentationChanged: (BrowserMenuPresentation) -> Unit = {},
    onBottomSheetContentLayoutChanged: (BrowserMenuBottomSheetContentLayout) -> Unit = {},
    onBottomSheetToolbarEntriesChanged: (List<BrowserMenuEntry?>) -> Unit = {},
) {
    var destination by remember { mutableStateOf(SettingsDestination.MenuActions) }
    when (destination) {
        SettingsDestination.MenuStyle ->
            BrowserMenuStyleSettingsPage(
                layout = layout,
                resources = AndroidBrowserMenuSettingsResources,
                backContentDescription = AndroidBrowserMenuSettingsResources.back(),
                onBack = { destination = SettingsDestination.MenuActions },
                onPresentationChanged = onPresentationChanged,
            )
        SettingsDestination.MenuContentLayout ->
            BrowserMenuContentLayoutSettingsPage(
                layout = layout,
                resources = AndroidBrowserMenuSettingsResources,
                backContentDescription = AndroidBrowserMenuSettingsResources.back(),
                onBack = { destination = SettingsDestination.MenuActions },
                onContentLayoutChanged = onBottomSheetContentLayoutChanged,
            )
        else -> BrowserMenuSettingsPage(
            layout = layout,
            statusBarScrollEnabled = statusBarScrollEnabled,
            onLocationChanged = onLocationChanged,
            onBack = onBack,
            onPresentationChanged = onPresentationChanged,
            onBottomSheetContentLayoutChanged = onBottomSheetContentLayoutChanged,
            onBottomSheetToolbarEntriesChanged = onBottomSheetToolbarEntriesChanged,
            onOpenPresentation = { destination = SettingsDestination.MenuStyle },
            onOpenContentLayout = { destination = SettingsDestination.MenuContentLayout },
        )
    }
}
