package dev.sk2andy.materialbrowser.ui

internal object AddressEditorCompletionRules {
    fun submissionText(input: String, ghostCompletion: String?): String =
        ghostCompletion?.takeIf { completion ->
            completion.length > input.length && completion.startsWith(input, ignoreCase = true)
        } ?: input
}
