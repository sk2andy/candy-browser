@file:OptIn(ExperimentalTestApi::class)

package dev.sk2andy.materialbrowser.ui

import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.SearchShortcut
import dev.sk2andy.materialbrowser.browser.SearchShortcutRules
import dev.sk2andy.materialbrowser.shared.ui.AddressBarFieldContent
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressSearchShortcutChipInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var latestValue = TextFieldValue()
    private var editorView: View? = null
    private var submittedAddress: String? = null

    @Test
    fun chipHidesPrefixAndTypingSubmitsShortcutWithQuery() {
        setEditor("")
        composeRule.onNodeWithTag(EditorTag).performClick().performTextInput("!gh")
        composeRule.onNodeWithTag(AddressSearchShortcutTestTags.Chip).assertDoesNotExist()
        composeRule.onNodeWithTag(EditorTag).performTextInput(" ")

        composeRule.onNodeWithTag(AddressSearchShortcutTestTags.Chip).assertIsDisplayed()
        composeRule.onNodeWithText(Placeholder).assertIsDisplayed()
        composeRule.onNodeWithTag(EditorTag).performClick().performTextInput("compose")

        val results = mutableListOf<TextLayoutResult>()
        val getLayout = composeRule.onNodeWithTag(EditorTag).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
        assertTrue(getLayout.action?.invoke(results) == true)
        assertEquals("compose", results.single().layoutInput.text.text)

        composeRule.onNodeWithTag(EditorTag).performImeAction()
        composeRule.runOnIdle { assertEquals("!gh compose", submittedAddress) }
    }

    @Test
    fun hardwareBackspaceRemovesShortcutWhenQueryEmpty() {
        setEditor("!gh ")

        composeRule.onNodeWithTag(EditorTag).performClick().performKeyInput {
            pressKey(Key.Backspace)
        }

        composeRule.runOnIdle { assertEquals("", latestValue.text) }
        composeRule.onNodeWithTag(AddressSearchShortcutTestTags.Chip).assertDoesNotExist()
    }

    @Test
    fun hardwareBackspaceDeletesQueryBeforeShortcut() {
        setEditor("!gh a")

        composeRule.onNodeWithTag(EditorTag).performClick().performKeyInput {
            pressKey(Key.MoveEnd)
            pressKey(Key.Backspace)
        }

        composeRule.runOnIdle { assertEquals("!gh ", latestValue.text) }
    }

    @Test
    fun imeBackspaceRemovesShortcutWhenQueryEmpty() {
        setEditor("!gh ")
        composeRule.onNodeWithTag(EditorTag).performClick()

        composeRule.runOnIdle {
            val connection = editorView?.onCreateInputConnection(EditorInfo())
            assertNotNull(connection)
            assertTrue(connection!!.deleteSurroundingText(1, 0))
        }

        composeRule.runOnIdle { assertEquals("", latestValue.text) }
        composeRule.onNodeWithTag(AddressSearchShortcutTestTags.Chip).assertDoesNotExist()
    }

    @Test
    fun hardwareBackspaceAtQueryStartPreservesShortcutAndQuery() {
        setEditor("!gh compose")

        composeRule.onNodeWithTag(EditorTag)
            .performClick()
            .performTextInputSelection(TextRange(4))
        composeRule.onNodeWithTag(EditorTag).performKeyInput { pressKey(Key.Backspace) }

        composeRule.runOnIdle {
            assertEquals("!gh compose", latestValue.text)
            assertEquals(TextRange(4), latestValue.selection)
        }
        composeRule.onNodeWithTag(AddressSearchShortcutTestTags.Chip).assertIsDisplayed()
    }

    @Test
    fun imeBackspaceAtQueryStartPreservesShortcutAndQuery() {
        setEditor("!gh compose")
        composeRule.onNodeWithTag(EditorTag)
            .performClick()
            .performTextInputSelection(TextRange(4))

        composeRule.runOnIdle {
            val connection = editorView?.onCreateInputConnection(EditorInfo())
            assertNotNull(connection)
            assertTrue(connection!!.deleteSurroundingText(1, 0))
        }

        composeRule.runOnIdle {
            assertEquals("!gh compose", latestValue.text)
            assertEquals(TextRange(4), latestValue.selection)
        }
        composeRule.onNodeWithTag(AddressSearchShortcutTestTags.Chip).assertIsDisplayed()
    }

    @Test
    fun chipRemoveActionPreservesQuery() {
        setEditor("!gh compose")
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.onNodeWithTag(AddressSearchShortcutTestTags.Chip)
            .assertContentDescriptionEquals(
                context.getString(R.string.search_shortcut_chip_remove, "gh"),
            )
            .performClick()

        composeRule.runOnIdle { assertEquals("compose", latestValue.text) }
        composeRule.onNodeWithTag(AddressSearchShortcutTestTags.Chip).assertDoesNotExist()
    }

    private fun setEditor(initialText: String) {
        latestValue = TextFieldValue(initialText, selection = TextRange(initialText.length))
        composeRule.setContent {
            var value by remember { mutableStateOf(latestValue) }
            val view = LocalView.current
            val match = SearchShortcutRules.match(value.text, Shortcuts)
            SideEffect { editorView = view }
            MaterialBrowserTheme {
                AddressBarFieldContent(
                    editing = true,
                    editValue = value,
                    onEditValueChange = {
                        value = AddressSearchShortcutRules.edit(value, it, Shortcuts)
                        latestValue = value
                    },
                    ghostCompletion = null,
                    placeholder = Placeholder,
                    displayText = "",
                    onSubmitAddress = { submittedAddress = it },
                    modifier = Modifier.width(360.dp),
                    editorModifier = Modifier.testTag(EditorTag),
                    hiddenPrefixLength = match?.prefix?.length ?: 0,
                    editorLeadingContent = {
                        AddressSearchShortcutChip(
                            keyword = match?.shortcut?.keyword,
                            onRemove = {
                                value = AddressSearchShortcutRules.edit(
                                    value,
                                    TextFieldValue(match?.query.orEmpty()),
                                    Shortcuts,
                                )
                                latestValue = value
                            },
                        )
                    },
                )
            }
        }
    }

    private companion object {
        const val EditorTag = "search_shortcut_editor"
        const val Placeholder = "Search or enter an address"
        val Shortcuts = listOf(SearchShortcut("gh", "https://github.com/search?q=%s"))
    }
}
