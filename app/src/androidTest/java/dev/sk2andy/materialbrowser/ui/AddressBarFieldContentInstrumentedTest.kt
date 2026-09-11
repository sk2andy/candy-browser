package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.shared.ui.AddressBarFieldContent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarFieldContentInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displayTextDoesNotClipAtLargeFontScale() {
        setAddressBarContent(editing = false)

        assertTextDoesNotOverflow(
            composeRule.onNodeWithText(DisplayText, useUnmergedTree = true),
        )
    }

    @Test
    fun editorTextDoesNotClipAtLargeFontScale() {
        setAddressBarContent(editing = true)

        assertTextDoesNotOverflow(
            composeRule.onNodeWithText(DisplayText, useUnmergedTree = true),
        )
    }

    @Test
    fun fieldUsesRequestedHeightWithoutBoundedHost() {
        setAddressBarContent(
            editing = false,
            modifier = Modifier.testTag(FieldTag),
            fieldHeight = 44.dp,
        )

        composeRule.onNodeWithTag(FieldTag).assertHeightIsEqualTo(44.dp)
    }

    private fun setAddressBarContent(
        editing: Boolean,
        modifier: Modifier = Modifier.size(width = 320.dp, height = 48.dp),
        fieldHeight: Dp = 48.dp,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.3f),
            ) {
                MaterialTheme {
                    AddressBarFieldContent(
                        editing = editing,
                        editValue = TextFieldValue(DisplayText),
                        onEditValueChange = {},
                        ghostCompletion = null,
                        placeholder = "Search or enter an address",
                        displayText = DisplayText,
                        onSubmitAddress = {},
                        submissionText = { text, _ -> text },
                        modifier = modifier,
                        fieldHeight = fieldHeight,
                    )
                }
            }
        }
    }

    private fun assertTextDoesNotOverflow(node: SemanticsNodeInteraction) {
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        val getTextLayoutResult = node
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]

        assertTrue(getTextLayoutResult.action?.invoke(textLayoutResults) == true)
        assertFalse(textLayoutResults.single().didOverflowHeight)
    }

    private companion object {
        const val DisplayText = "google.com"
        const val FieldTag = "address_bar_field"
    }
}
