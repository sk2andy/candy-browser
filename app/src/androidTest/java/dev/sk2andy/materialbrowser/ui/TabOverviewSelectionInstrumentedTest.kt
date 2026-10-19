package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.shared.ui.settings.TabOverviewSelectionPage
import dev.sk2andy.materialbrowser.shared.ui.settings.TabOverviewSelectionTestTags
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabOverviewSelectionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allExistingModesAreRadioChoicesAndBackDoesNotChangeSelection() {
        var mode by mutableStateOf(TabOverviewMode.Hero)
        var writes = 0
        var backs = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                TabOverviewSelectionPage(
                    mode, androidTabOverviewSettingsStrings(), stringResource(R.string.action_back),
                    onModeChanged = { mode = it; writes++ }, onBack = { backs++ },
                )
            }
        }
        TabOverviewMode.entries.forEach { candidate ->
            composeRule.onNodeWithTag(TabOverviewSelectionTestTags.option(candidate))
                .performScrollTo().assertIsDisplayed().performClick().assertIsSelected().performClick()
        }
        composeRule.onAllNodes(isPopup()).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.action_back),
        ).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(TabOverviewMode.List, mode)
            assertEquals(2, writes)
            assertEquals(1, backs)
        }
    }

    @Test
    fun radioLabelsRemainCompleteAt320DpAndDoubleFontScale() {
        composeRule.setContent {
            MaterialBrowserTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Box(Modifier.width(320.dp)) {
                        TabOverviewSelectionPage(
                            TabOverviewMode.Grid, androidTabOverviewSettingsStrings(),
                            stringResource(R.string.action_back), onModeChanged = {}, onBack = {},
                        )
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(R.string.tab_overview_mode_hero, R.string.tab_overview_mode_grid, R.string.tab_overview_mode_list)
            .forEach { labelId ->
                val label = context.getString(labelId)
                val layouts = mutableListOf<TextLayoutResult>()
                composeRule.onNodeWithText(label, useUnmergedTree = true)
                    .performScrollTo().assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val result = layouts.single()
                assertFalse(result.didOverflowHeight)
                (0 until result.lineCount).forEach { line ->
                    assertFalse(result.isLineEllipsized(line))
                    assertTrue(result.getLineLeft(line) >= -1f)
                    assertTrue(result.getLineRight(line) <= result.size.width + 1f)
                }
                assertEquals(label.length, result.getLineEnd(result.lineCount - 1))
            }
    }
}
