package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
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
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsDropdownDivider
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDropdownInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openedMenuExposesSelectionAndSupportingTextWithNeutralSurfaces() {
        var selectionColor = Color.Unspecified
        var selected = 0
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                selectionColor = MaterialTheme.colorScheme.surfaceContainerLow
                var expanded by remember { mutableStateOf(true) }
                Box {
                    SettingsDropdown(expanded, onDismissRequest = { expanded = false }) {
                        SettingsDropdownItem(
                            label = "Selected option",
                            supportingText = "Existing explanation",
                            selected = true,
                            onClick = {},
                            modifier = Modifier.testTag("selected_menu_option"),
                        )
                        SettingsDropdownDivider()
                        SettingsDropdownItem("Other option", selected = false, onClick = {
                            selected = 1
                            expanded = false
                        })
                    }
                }
            }
        }
        val item = composeRule.onNodeWithTag("selected_menu_option")
        item.assertIsDisplayed().assertIsSelected().assertHeightIsAtLeast(48.dp)
            .assertTextContains("Existing explanation")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        val pixels = item.captureToImage().toPixelMap()
        val expected = selectionColor.toArgb()
        val highlighted = (0 until pixels.height).sumOf { y ->
            (0 until pixels.width).count { x -> pixels[x, y].toArgb() == expected }
        }
        assertTrue("Selected row retains the neutral menu surface", highlighted > pixels.width * pixels.height / 3)
        val otherPixels = composeRule.onNodeWithText("Other option").captureToImage().toPixelMap()
        val neutralPixels = (0 until otherPixels.height).sumOf { y ->
            (0 until otherPixels.width).count { x -> otherPixels[x, y].toArgb() == expected }
        }
        assertTrue("Unselected row uses the same surface", neutralPixels > otherPixels.width * otherPixels.height / 3)
        composeRule.onNodeWithText("Other option").performClick()
        composeRule.onAllNodes(isPopup()).assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(1, selected) }
    }

    @Test
    fun disabledChoiceCannotWriteAndBackDismissesWithoutSelection() {
        var writes = 0
        var dismissals = 0
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                var expanded by remember { mutableStateOf(true) }
                Box {
                    SettingsDropdown(expanded, onDismissRequest = {
                        dismissals++
                        expanded = false
                    }) {
                        SettingsDropdownItem(
                            "Unavailable option", selected = true, enabled = false,
                            onClick = { writes++ },
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("Unavailable option").assertIsNotEnabled().performClick()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitUntil(5_000) { dismissals == 1 }
        composeRule.onAllNodes(isPopup()).assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(0, writes) }
    }

    @Test
    fun openedLongMenuWrapsAtDoubleFontScaleAndLastOptionIsReachable() {
        val longLabel = "Keep existing choices readable with larger text"
        var selected = false
        composeRule.setContent {
            MaterialBrowserTheme(settings = AppearanceSettings()) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    Box(Modifier.width(320.dp)) {
                        SettingsDropdown(expanded = true, onDismissRequest = {}) {
                            SettingsDropdownItem(longLabel, selected = true, onClick = {})
                            repeat(25) { index ->
                                SettingsDropdownItem("Option $index", selected = false, onClick = {
                                    selected = index == 24
                                })
                            }
                        }
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(longLabel, useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val result = layouts.single()
        assertTrue(result.lineCount > 1)
        assertFalse(result.didOverflowHeight)
        (0 until result.lineCount).forEach { line ->
            assertTrue(result.getLineLeft(line) >= -1f)
            assertTrue(result.getLineRight(line) <= result.size.width + 1f)
        }
        (0 until result.lineCount).forEach { assertFalse(result.isLineEllipsized(it)) }
        assertEquals(longLabel.length, result.getLineEnd(result.lineCount - 1))
        composeRule.onNodeWithText("Option 24").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(selected) }
    }
}
