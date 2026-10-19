package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
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
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerMode
import dev.sk2andy.materialbrowser.browser.LinkLongPressAction
import dev.sk2andy.materialbrowser.browser.LinkPeekSwipeAction
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.shared.ui.settings.AppearanceSettingsSection
import dev.sk2andy.materialbrowser.shared.ui.settings.TabOverviewSelectionPage
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpandedSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun stackFolderHasAllThreeModesAndBackDoesNotWrite() {
        var mode by mutableStateOf(TabOverviewMode.Hero)
        var writes = 0
        var backs = 0
        show {
            TabOverviewSelectionPage(
                mode = mode,
                strings = androidTabOverviewSettingsStrings().copy(
                    overviewMode = stringResource(R.string.settings_tab_stack_folder_mode),
                ),
                backContentDescription = stringResource(R.string.action_back),
                onModeChanged = { mode = it; writes++ },
                onBack = { backs++ },
                testTagPrefix = "tab_stack_folder_option",
            )
        }
        TabOverviewMode.entries.forEach { candidate ->
            composeRule.onNodeWithTag("tab_stack_folder_option_${candidate.wireValue}")
                .performScrollTo().performClick().assertIsSelected().performClick()
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        composeRule.runOnIdle {
            assertEquals(TabOverviewMode.List, mode)
            assertEquals(2, writes)
            assertEquals(1, backs)
        }
    }

    @Test
    fun everyLongPressActionRemainsSelectableWithoutDuplicateWrites() {
        var action by mutableStateOf(LinkLongPressAction.LinkPeek)
        val writes = mutableListOf<LinkLongPressAction>()
        show {
            LinkLongPressSettingsPage(action, onActionChanged = { action = it; writes += it }, onBack = {})
        }
        LinkLongPressAction.entries.forEach { candidate ->
            composeRule.onNodeWithTag("gesture_choice_$candidate")
                .performScrollTo().performClick().assertIsSelected().performClick()
        }
        composeRule.runOnIdle {
            assertEquals(LinkLongPressAction.entries.filter { it != LinkLongPressAction.LinkPeek }, writes)
        }
    }

    @Test
    fun everySwipeActionRemainsSelectableAndDisabledChoiceCannotWrite() {
        var action by mutableStateOf(LinkPeekSwipeAction.OpenBackground)
        var enabled by mutableStateOf(true)
        val writes = mutableListOf<LinkPeekSwipeAction>()
        show {
            LinkPeekSwipeSettingsPage(action, enabled, onActionChanged = { action = it; writes += it }, onBack = {})
        }
        LinkPeekSwipeAction.entries.forEach { candidate ->
            composeRule.onNodeWithTag("gesture_choice_$candidate")
                .performScrollTo().performClick().assertIsSelected().performClick()
        }
        val before = writes.toList()
        composeRule.runOnIdle { enabled = false }
        LinkPeekSwipeAction.entries.forEach { candidate ->
            composeRule.onNodeWithTag("gesture_choice_$candidate")
                .performScrollTo().assertIsNotEnabled().performClick()
        }
        composeRule.runOnIdle {
            assertEquals(LinkPeekSwipeAction.entries.filter { it != LinkPeekSwipeAction.OpenBackground }, writes)
            assertEquals(before, writes)
        }
    }

    @Test
    fun allFivePlayerModesHaveExplanationsAndHonorEngineSupport() {
        var mode by mutableStateOf(InlineMediaPlayerMode.Disabled)
        var supported by mutableStateOf(true)
        val writes = mutableListOf<InlineMediaPlayerMode>()
        show {
            PlayerModeSettingsPage(mode, supported, onModeChanged = { mode = it; writes += it }, onBack = {})
        }
        InlineMediaPlayerMode.entries.forEach { candidate ->
            composeRule.onNodeWithTag("player_mode_${candidate.stableId}")
                .performScrollTo().performClick().assertIsSelected().performClick()
        }
        playerDescriptions.forEach { assertCompleteText(it) }
        composeRule.runOnIdle { supported = false }
        InlineMediaPlayerMode.entries.forEach { candidate ->
            composeRule.onNodeWithTag("player_mode_${candidate.stableId}")
                .performScrollTo().assertIsNotEnabled().performClick()
        }
        composeRule.runOnIdle {
            assertEquals(InlineMediaPlayerMode.entries.drop(1), writes)
        }
    }

    @Test
    fun privateAndForegroundBackgroundLabelsWrapAtDoubleFontScale() {
        show(largeText = true) {
            LinkLongPressSettingsPage(LinkLongPressAction.LinkPeek, onActionChanged = {}, onBack = {})
        }
        listOf(
            R.string.action_open_link_foreground_tab,
            R.string.action_open_link_background_tab,
            R.string.action_open_link_private_foreground_tab,
            R.string.action_open_link_private_background_tab,
            R.string.settings_gesture_normal_tabs_group,
            R.string.settings_gesture_private_tabs_group,
            R.string.settings_link_long_press_explanation,
        ).forEach { assertCompleteText(it) }
    }

    @Test
    fun playerExplanationsRemainCompleteAtDoubleFontScale() {
        show(largeText = true) {
            PlayerModeSettingsPage(InlineMediaPlayerMode.Automatic, true, onModeChanged = {}, onBack = {})
        }
        playerDescriptions.forEach { assertCompleteText(it) }
    }

    @Test
    fun surfacesKeepThreeDisabledSlidersAndTheirValuesAtDoubleFontScale() {
        val settings = AppearanceSettings(
            frostedTransparencyPercent = 70,
            frostedAddressBarTransparencyPercent = 50,
            frostedBlurPercent = 90,
        )
        var writes = 0
        show(largeText = true) {
            AppearanceSettingsPage(settings, section = AppearanceSettingsSection.Surfaces,
                onSettingsChanged = { writes++ }, onBack = {})
        }
        listOf(
            AppearanceSettingsTestTags.FrostedTransparency,
            AppearanceSettingsTestTags.FrostedAddressBarTransparency,
            AppearanceSettingsTestTags.FrostedBlur,
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        }
        listOf(R.string.settings_frosted_transparency, R.string.settings_frosted_address_bar_transparency,
            R.string.settings_frosted_blur, R.string.settings_shape_style).forEach { assertCompleteText(it) }
        composeRule.runOnIdle { assertEquals(0, writes) }
    }

    @Test
    fun colorsRemainReadableInALargeDisplayLayout() {
        composeRule.setContent {
            MaterialBrowserTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.width(600.dp)) {
                        AppearanceSettingsPage(AppearanceSettings(), section = AppearanceSettingsSection.Colors,
                            onSettingsChanged = {}, onBack = {})
                    }
                }
            }
        }
        listOf(R.string.appearance_mode_system, R.string.appearance_mode_dark,
            R.string.color_palette_candy, R.string.color_palette_neutral).forEach { assertCompleteText(it) }
    }

    private fun show(largeText: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            MaterialBrowserTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,
                    fontScale = if (largeText) 2f else 1f)) {
                    Box(Modifier.width(320.dp)) { content() }
                }
            }
        }
    }

    private fun assertCompleteText(resource: Int) {
        val label = context.getString(resource)
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(label, useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val result = layouts.single()
        assertFalse(label, result.didOverflowHeight)
        (0 until result.lineCount).forEach { line ->
            assertFalse(label, result.isLineEllipsized(line))
            assertTrue(label, result.getLineLeft(line) >= -1f)
            assertTrue(label, result.getLineRight(line) <= result.size.width + 1f)
        }
        assertEquals(label.length, result.getLineEnd(result.lineCount - 1))
    }

    private val playerDescriptions = listOf(
        R.string.settings_player_disabled_explanation,
        R.string.settings_player_button_fullscreen_explanation,
        R.string.settings_player_button_inline_explanation,
        R.string.settings_player_fullscreen_explanation,
        R.string.settings_player_automatic_explanation,
    )
}
