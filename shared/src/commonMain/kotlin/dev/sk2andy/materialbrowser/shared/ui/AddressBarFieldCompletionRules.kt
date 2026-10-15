package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.ui.text.input.TextFieldValue

object AddressBarFieldCompletionRules {
    fun submissionText(editValue: TextFieldValue, ghostCompletion: String?): String {
        val input = editValue.text
        if (
            input.isEmpty() ||
            !editValue.selection.collapsed ||
            editValue.selection.end != input.length
        ) {
            return input
        }
        return ghostCompletion?.takeIf { completion ->
            completion.length > input.length && completion.startsWith(input, ignoreCase = true)
        } ?: input
    }
}
