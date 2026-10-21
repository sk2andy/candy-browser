package dev.sk2andy.materialbrowser.browser.annotation

import dev.sk2andy.materialbrowser.browser.integration.ProcessTextRules

internal enum class WebTextSelectionAction(val maximumCharacters: Int = 8_000) {
    Highlight,
    Translate,
    ReadAloud(ProcessTextRules.MAX_SPEECH_CHARACTERS),
}
