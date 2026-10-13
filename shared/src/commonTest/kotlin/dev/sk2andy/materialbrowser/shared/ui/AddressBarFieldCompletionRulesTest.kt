package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class AddressBarFieldCompletionRulesTest {
    @Test
    fun submitAcceptsMatchingDomainCompletion() {
        assertEquals(
            "github.com",
            AddressBarFieldCompletionRules.submissionText(
                TextFieldValue("Git", selection = TextRange(3)),
                "github.com",
            ),
        )
    }

    @Test
    fun submitKeepsInputWithoutMatchingCompletion() {
        val input = TextFieldValue("candy", selection = TextRange(5))
        assertEquals("candy", AddressBarFieldCompletionRules.submissionText(input, null))
        assertEquals("candy", AddressBarFieldCompletionRules.submissionText(input, "github.com"))
        assertEquals("candy", AddressBarFieldCompletionRules.submissionText(input, "candy"))
        assertEquals("candy", AddressBarFieldCompletionRules.submissionText(input, "can"))
    }

    @Test
    fun submitKeepsInputWhenCursorMovedOrTextSelected() {
        assertEquals(
            "git",
            AddressBarFieldCompletionRules.submissionText(
                TextFieldValue("git", selection = TextRange(1)),
                "github.com",
            ),
        )
        assertEquals(
            "git",
            AddressBarFieldCompletionRules.submissionText(
                TextFieldValue("git", selection = TextRange(0, 3)),
                "github.com",
            ),
        )
    }

    @Test
    fun submitNeverCompletesEmptyInput() {
        assertEquals(
            "",
            AddressBarFieldCompletionRules.submissionText(TextFieldValue(), "github.com"),
        )
    }
}
