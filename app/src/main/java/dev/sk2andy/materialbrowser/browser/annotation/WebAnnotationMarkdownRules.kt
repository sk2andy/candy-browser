package dev.sk2andy.materialbrowser.browser.annotation

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

internal sealed interface WebAnnotationMarkdownBlock {
    data class Heading(val level: Int, val content: WebAnnotationMarkdownInline) : WebAnnotationMarkdownBlock
    data class Paragraph(val content: WebAnnotationMarkdownInline) : WebAnnotationMarkdownBlock
    data class ListItem(val marker: String, val content: WebAnnotationMarkdownInline) : WebAnnotationMarkdownBlock
    data class Quote(val content: WebAnnotationMarkdownInline) : WebAnnotationMarkdownBlock
    data class Code(val content: String) : WebAnnotationMarkdownBlock
    data object Divider : WebAnnotationMarkdownBlock
}

internal data class WebAnnotationMarkdownInline(
    val text: String,
    val decorations: List<WebAnnotationMarkdownDecoration> = emptyList(),
)

internal data class WebAnnotationMarkdownDecoration(
    val start: Int,
    val end: Int,
    val style: WebAnnotationMarkdownStyle,
    val target: String? = null,
)

internal enum class WebAnnotationMarkdownStyle { Bold, Italic, Code, Link }

internal object WebAnnotationMarkdownRules {
    private val headingPattern = Regex("""^(#{1,6})\s+(.+)$""")
    private val itemPattern = Regex("""^\s*([-+*]|[0-9]{1,4}[.)])\s+(.+)$""")
    private val dividerPattern = Regex("""^\s*(---+|___+|\*\*\*+)\s*$""")

    fun parse(note: String): List<WebAnnotationMarkdownBlock> {
        val lines = note.take(WebAnnotationRules.MAX_NOTE_CHARS)
            .replace("\r\n", "\n").replace('\r', '\n').lines()
        val blocks = mutableListOf<WebAnnotationMarkdownBlock>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                index++
                continue
            }
            val heading = headingPattern.matchEntire(line)
            val item = itemPattern.matchEntire(line)
            when {
                line.startsWith("```") -> {
                    val closing = (index + 1 until lines.size).firstOrNull { lines[it] == "```" }
                    if (closing == null) {
                        blocks += WebAnnotationMarkdownBlock.Paragraph(WebAnnotationMarkdownInline(line))
                        index++
                    } else {
                        blocks += WebAnnotationMarkdownBlock.Code(lines.subList(index + 1, closing).joinToString("\n"))
                        index = closing + 1
                    }
                }
                heading != null -> {
                    blocks += WebAnnotationMarkdownBlock.Heading(
                        level = heading.groupValues[1].length,
                        content = parseInline(heading.groupValues[2]),
                    )
                    index++
                }
                dividerPattern.matches(line) -> {
                    blocks += WebAnnotationMarkdownBlock.Divider
                    index++
                }
                item != null -> {
                    val marker = item.groupValues[1].let { if (it.length == 1) "•" else it }
                    blocks += WebAnnotationMarkdownBlock.ListItem(marker, parseInline(item.groupValues[2]))
                    index++
                }
                line.startsWith(">") -> {
                    val quote = mutableListOf<String>()
                    while (index < lines.size && lines[index].startsWith(">")) {
                        quote += lines[index++].removePrefix(">").trimStart()
                    }
                    blocks += WebAnnotationMarkdownBlock.Quote(parseInline(quote.joinToString(" ")))
                }
                else -> {
                    val paragraph = mutableListOf(line)
                    index++
                    while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines[index])) {
                        paragraph += lines[index++]
                    }
                    blocks += WebAnnotationMarkdownBlock.Paragraph(parseInline(paragraph.joinToString("\n")))
                }
            }
        }
        return blocks
    }

    fun parseInline(markdown: String): WebAnnotationMarkdownInline = parseInline(markdown, depth = 0)

    fun plainText(note: String): String = parse(note).mapNotNull { block ->
        when (block) {
            is WebAnnotationMarkdownBlock.Heading -> block.content.text
            is WebAnnotationMarkdownBlock.Paragraph -> block.content.text
            is WebAnnotationMarkdownBlock.ListItem -> "${block.marker} ${block.content.text}"
            is WebAnnotationMarkdownBlock.Quote -> block.content.text
            is WebAnnotationMarkdownBlock.Code -> block.content
            WebAnnotationMarkdownBlock.Divider -> null
        }
    }.joinToString("\n")

    private fun parseInline(markdown: String, depth: Int): WebAnnotationMarkdownInline {
        if (depth >= 8) return WebAnnotationMarkdownInline(markdown)
        val text = StringBuilder(markdown.length)
        val decorations = mutableListOf<WebAnnotationMarkdownDecoration>()
        fun append(content: WebAnnotationMarkdownInline) {
            val offset = text.length
            text.append(content.text)
            decorations += content.decorations.map { it.copy(start = it.start + offset, end = it.end + offset) }
        }
        var index = 0
        while (index < markdown.length) {
            if (markdown[index] == '\\' && index + 1 < markdown.length) {
                text.append(markdown[index + 1])
                index += 2
                continue
            }
            if (markdown[index] == '[' && (index == 0 || markdown[index - 1] != '!')) {
                val labelEnd = markdown.indexOf("](", startIndex = index + 1)
                val targetStart = labelEnd + 2
                val targetEnd = if (labelEnd >= 0) linkEnd(markdown, targetStart) else -1
                if (labelEnd > index + 1 && targetEnd > targetStart) {
                    val target = BrowserUriPolicy.normalizeHttpUrl(markdown.substring(targetStart, targetEnd))
                    if (target != null) {
                        val start = text.length
                        append(parseInline(markdown.substring(index + 1, labelEnd), depth + 1))
                        decorations += WebAnnotationMarkdownDecoration(start, text.length, WebAnnotationMarkdownStyle.Link, target)
                        index = targetEnd + 1
                        continue
                    }
                }
            }
            val delimiter = delimiterAt(markdown, index)
            val end = delimiter?.let { delimiterEnd(markdown, index, it, depth) } ?: -1
            if (delimiter != null && end > index + delimiter.length) {
                val content = markdown.substring(index + delimiter.length, end)
                val start = text.length
                val style = when (delimiter) {
                    "**", "__", "***", "___" -> WebAnnotationMarkdownStyle.Bold
                    "`" -> WebAnnotationMarkdownStyle.Code
                    else -> WebAnnotationMarkdownStyle.Italic
                }
                append(if (style == WebAnnotationMarkdownStyle.Code) WebAnnotationMarkdownInline(content) else parseInline(content, depth + 1))
                decorations += WebAnnotationMarkdownDecoration(start, text.length, style)
                if (delimiter.length == 3) {
                    decorations += WebAnnotationMarkdownDecoration(start, text.length, WebAnnotationMarkdownStyle.Italic)
                }
                index = end + delimiter.length
            } else {
                text.append(markdown[index++])
            }
        }
        return WebAnnotationMarkdownInline(text.toString(), decorations)
    }

    private fun delimiterAt(markdown: String, index: Int): String? = when {
        markdown.startsWith("***", index) -> "***"
        markdown.startsWith("___", index) -> "___"
        markdown.startsWith("**", index) -> "**"
        markdown.startsWith("__", index) -> "__"
        markdown[index] in "*_`" -> markdown[index].toString()
        else -> null
    }

    private fun delimiterEnd(markdown: String, start: Int, delimiter: String, depth: Int): Int {
        if (depth >= 8) return -1
        var index = start + delimiter.length
        while (index < markdown.length) {
            if (markdown[index] == '\\' && delimiter != "`") {
                index += 2
                continue
            }
            val nested = if (delimiter != "`") {
                delimiterAt(markdown, index)?.takeIf { candidate ->
                    markdown.getOrNull(index + candidate.length)?.isWhitespace() == false
                }
            } else null
            // A paired inner emphasis consumes its closing characters before the outer one.
            // In **bold *italic***, the first star of the final run closes italic.
            if (nested != null && (nested.first() != delimiter.first() || nested.length > delimiter.length)) {
                val end = delimiterEnd(markdown, index, nested, depth + 1)
                if (end > index + nested.length) {
                    index = end + nested.length
                    continue
                }
            }
            if (markdown.startsWith(delimiter, index)) return index
            if (nested != null) {
                val end = delimiterEnd(markdown, index, nested, depth + 1)
                if (end > index + nested.length) {
                    index = end + nested.length
                    continue
                }
            }
            index++
        }
        return -1
    }

    private fun linkEnd(markdown: String, start: Int): Int {
        var nested = 0
        for (index in start until markdown.length) {
            when (markdown[index]) {
                '(' -> nested++
                ')' -> if (nested == 0) return index else nested--
            }
        }
        return -1
    }

    private fun startsBlock(line: String): Boolean = headingPattern.matches(line) ||
        itemPattern.matches(line) || dividerPattern.matches(line) || line.startsWith("```") || line.startsWith(">")
}
