package dev.sk2andy.materialbrowser.browser.annotation

internal data class WebAnnotationEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

internal enum class WebAnnotationFormat {
    Bold,
    Italic,
    Heading,
    BulletList,
    Link,
    Code,
}

internal object WebAnnotationEditorRules {
    fun apply(
        text: String,
        start: Int,
        end: Int,
        format: WebAnnotationFormat,
    ): WebAnnotationEdit {
        val selectionStart = start.coerceIn(0, text.length)
        val selectionEnd = end.coerceIn(0, text.length)
        val original = WebAnnotationEdit(text, selectionStart, selectionEnd)
        if (text.length > WebAnnotationRules.MAX_NOTE_CHARS) return original
        val first = minOf(selectionStart, selectionEnd)
        val last = maxOf(selectionStart, selectionEnd)
        val formatted = when (format) {
            WebAnnotationFormat.Bold -> wrap(original, first, last, "**")
            WebAnnotationFormat.Italic -> wrap(original, first, last, "*")
            WebAnnotationFormat.Code -> wrap(original, first, last, "`")
            WebAnnotationFormat.Heading -> prefixLines(original, first, last, "## ")
            WebAnnotationFormat.BulletList -> prefixLines(original, first, last, "- ")
            WebAnnotationFormat.Link -> {
                val replacement = "[${text.substring(first, last)}]($LINK_DESTINATION)"
                val destinationStart = last + 3
                WebAnnotationEdit(
                    text = text.replaceRange(first, last, replacement),
                    selectionStart = destinationStart,
                    selectionEnd = destinationStart + LINK_DESTINATION.length,
                )
            }
        }
        return formatted.takeIf { it.text.length <= WebAnnotationRules.MAX_NOTE_CHARS } ?: original
    }

    private fun wrap(
        original: WebAnnotationEdit,
        first: Int,
        last: Int,
        marker: String,
    ): WebAnnotationEdit = original.copy(
        text = original.text.replaceRange(first, last, "$marker${original.text.substring(first, last)}$marker"),
        selectionStart = original.selectionStart + marker.length,
        selectionEnd = original.selectionEnd + marker.length,
    )

    private fun prefixLines(
        original: WebAnnotationEdit,
        first: Int,
        last: Int,
        prefix: String,
    ): WebAnnotationEdit {
        val text = original.text
        val firstLine = text.lastIndexOf('\n', startIndex = first - 1) + 1
        val lastCharacter = if (first == last) last else last - 1
        val lastLine = text.lastIndexOf('\n', startIndex = lastCharacter - 1) + 1
        val lineStarts = mutableListOf(firstLine)
        var newline = text.indexOf('\n', startIndex = firstLine)
        while (newline >= 0 && newline + 1 <= lastLine) {
            lineStarts += newline + 1
            newline = text.indexOf('\n', startIndex = newline + 1)
        }
        val formatted = buildString {
            var copiedUntil = 0
            lineStarts.forEach { lineStart ->
                append(text, copiedUntil, lineStart)
                append(prefix)
                copiedUntil = lineStart
            }
            append(text, copiedUntil, text.length)
        }
        return WebAnnotationEdit(
            text = formatted,
            selectionStart = original.selectionStart + prefix.length * lineStarts.count { it <= original.selectionStart },
            selectionEnd = original.selectionEnd + prefix.length * lineStarts.count { it <= original.selectionEnd },
        )
    }

    private const val LINK_DESTINATION = "https://"
}
