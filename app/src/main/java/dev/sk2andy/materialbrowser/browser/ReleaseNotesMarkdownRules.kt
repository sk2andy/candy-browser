package dev.sk2andy.materialbrowser.browser

internal data class ReleaseNotesDocument(
    val title: ReleaseNotesInline,
    val blocks: List<ReleaseNotesBlock>,
)

internal sealed interface ReleaseNotesBlock {
    data class Heading(val level: Int, val content: ReleaseNotesInline) : ReleaseNotesBlock
    data class Paragraph(val content: ReleaseNotesInline) : ReleaseNotesBlock
    data class ListItems(
        val ordered: Boolean,
        val items: List<ReleaseNotesInline>,
    ) : ReleaseNotesBlock
    data class Quote(val content: ReleaseNotesInline) : ReleaseNotesBlock
    data class Code(val language: String?, val content: String) : ReleaseNotesBlock
    data class Image(
        val altText: String,
        val sourceUrl: String,
        val assetPath: String,
    ) : ReleaseNotesBlock
    data object Divider : ReleaseNotesBlock
}

internal data class ReleaseNotesInline(
    val text: String,
    val decorations: List<ReleaseNotesDecoration> = emptyList(),
)

internal data class ReleaseNotesDecoration(
    val start: Int,
    val end: Int,
    val style: ReleaseNotesInlineStyle,
    val target: String? = null,
)

internal enum class ReleaseNotesInlineStyle {
    Bold,
    Italic,
    Code,
    Link,
}

internal object ReleaseNotesMarkdownRules {
    const val MAX_MARKDOWN_BYTES = 65_536
    private const val MAX_BLOCKS = 256
    private const val MAX_LINE_LENGTH = 8_192
    private const val MAX_IMAGES = 2
    private val headingPattern = Regex("""^(#{1,3})\s+(.+)$""")
    private val unorderedItemPattern = Regex("""^\s*[-+*]\s+(.+)$""")
    private val orderedItemPattern = Regex("""^\s*[1-9][0-9]{0,3}[.)]\s+(.+)$""")
    private val imagePattern = Regex("""^!\[([^]]+)]\(([^)]+)\)$""")
    private val dividerPattern = Regex("""^\s*(---+|___+|\*\*\*+)\s*$""")
    private val screenshotFilePattern =
        Regex("""[A-Za-z0-9][A-Za-z0-9._-]*\.(png|jpe?g|webp)""")

    fun parse(markdown: String, versionName: String): ReleaseNotesDocument? = runCatching {
        require(markdown.toByteArray(Charsets.UTF_8).size in 1..MAX_MARKDOWN_BYTES)
        require(versionName.matches(Regex("""[0-9]+(\.[0-9]+){1,2}(-[0-9A-Za-z.-]+)?""")))
        val lines = markdown
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
        require(lines.all { line -> line.length <= MAX_LINE_LENGTH })
        val blocks = mutableListOf<ReleaseNotesBlock>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                index++
                continue
            }
            val headingMatch = headingPattern.matchEntire(line)
            if (headingMatch != null) {
                blocks += ReleaseNotesBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    content = parseInline(headingMatch.groupValues[2]),
                )
                index++
                continue
            }
            val imageMatch = imagePattern.matchEntire(line)
            if (imageMatch != null) {
                val altText = imageMatch.groupValues[1].trim()
                val sourceUrl = imageMatch.groupValues[2]
                val prefix = "https://raw.githubusercontent.com/sk2andy/candy-browser/" +
                    "v$versionName/docs/screenshots/"
                val fileName = sourceUrl.removePrefix(prefix)
                require(altText.isNotEmpty() && sourceUrl.startsWith(prefix))
                require(fileName.matches(screenshotFilePattern))
                blocks += ReleaseNotesBlock.Image(
                    altText = altText,
                    sourceUrl = sourceUrl,
                    assetPath = "release-notes-images/$fileName",
                )
                index++
                continue
            }
            if (line.startsWith("```")) {
                val language = line.removePrefix("```").trim().takeIf(String::isNotEmpty)
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && lines[index] != "```") code += lines[index++]
                require(index < lines.size)
                index++
                blocks += ReleaseNotesBlock.Code(language = language, content = code.joinToString("\n"))
                continue
            }
            if (unorderedItemPattern.matches(line)) {
                val items = mutableListOf<ReleaseNotesInline>()
                while (index < lines.size) {
                    val match = unorderedItemPattern.matchEntire(lines[index]) ?: break
                    items += parseInline(match.groupValues[1])
                    index++
                }
                blocks += ReleaseNotesBlock.ListItems(ordered = false, items = items)
                continue
            }
            if (orderedItemPattern.matches(line)) {
                val items = mutableListOf<ReleaseNotesInline>()
                while (index < lines.size) {
                    val match = orderedItemPattern.matchEntire(lines[index]) ?: break
                    items += parseInline(match.groupValues[1])
                    index++
                }
                blocks += ReleaseNotesBlock.ListItems(ordered = true, items = items)
                continue
            }
            if (line.startsWith(">")) {
                val quote = mutableListOf<String>()
                while (index < lines.size && lines[index].startsWith(">")) {
                    quote += lines[index++].removePrefix(">").trimStart()
                }
                blocks += ReleaseNotesBlock.Quote(parseInline(quote.joinToString(" ")))
                continue
            }
            if (dividerPattern.matches(line)) {
                blocks += ReleaseNotesBlock.Divider
                index++
                continue
            }
            val paragraph = mutableListOf<String>()
            while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines[index])) {
                paragraph += lines[index++]
            }
            require(paragraph.isNotEmpty())
            blocks += ReleaseNotesBlock.Paragraph(parseInline(paragraph.joinToString(" ")))
        }
        require(blocks.size <= MAX_BLOCKS)
        require(blocks.count { block -> block is ReleaseNotesBlock.Image } <= MAX_IMAGES)
        val first = requireNotNull(blocks.firstOrNull() as? ReleaseNotesBlock.Heading)
        require(first.level == 1 && first.content.text.isNotBlank())
        ReleaseNotesDocument(title = first.content, blocks = blocks)
    }.getOrNull()

    fun parseInline(markdown: String): ReleaseNotesInline {
        val text = StringBuilder(markdown.length)
        val decorations = mutableListOf<ReleaseNotesDecoration>()
        var index = 0
        while (index < markdown.length) {
            if (markdown[index] == '\\' && index + 1 < markdown.length) {
                text.append(markdown[index + 1])
                index += 2
                continue
            }
            if (markdown[index] == '[') {
                val labelEnd = markdown.indexOf("](", startIndex = index + 1)
                val targetEnd = if (labelEnd >= 0) markdown.indexOf(')', startIndex = labelEnd + 2) else -1
                if (labelEnd > index + 1 && targetEnd > labelEnd + 2) {
                    val label = markdown.substring(index + 1, labelEnd)
                    val target = markdown.substring(labelEnd + 2, targetEnd)
                    if (target.startsWith("https://")) {
                        val start = text.length
                        text.append(label)
                        decorations += ReleaseNotesDecoration(
                            start = start,
                            end = text.length,
                            style = ReleaseNotesInlineStyle.Link,
                            target = target,
                        )
                        index = targetEnd + 1
                        continue
                    }
                }
            }
            val matched = when {
                markdown.startsWith("**", index) -> appendDelimited(
                    markdown = markdown,
                    startIndex = index,
                    delimiter = "**",
                    style = ReleaseNotesInlineStyle.Bold,
                    output = text,
                    decorations = decorations,
                )
                markdown[index] == '`' -> appendDelimited(
                    markdown = markdown,
                    startIndex = index,
                    delimiter = "`",
                    style = ReleaseNotesInlineStyle.Code,
                    output = text,
                    decorations = decorations,
                )
                markdown[index] == '*' || markdown[index] == '_' -> appendDelimited(
                    markdown = markdown,
                    startIndex = index,
                    delimiter = markdown[index].toString(),
                    style = ReleaseNotesInlineStyle.Italic,
                    output = text,
                    decorations = decorations,
                )
                else -> null
            }
            if (matched != null) {
                index = matched
            } else {
                text.append(markdown[index])
                index++
            }
        }
        return ReleaseNotesInline(text = text.toString(), decorations = decorations)
    }

    private fun appendDelimited(
        markdown: String,
        startIndex: Int,
        delimiter: String,
        style: ReleaseNotesInlineStyle,
        output: StringBuilder,
        decorations: MutableList<ReleaseNotesDecoration>,
    ): Int? {
        val contentStart = startIndex + delimiter.length
        val contentEnd = markdown.indexOf(delimiter, startIndex = contentStart)
        if (contentEnd <= contentStart) return null
        val rangeStart = output.length
        output.append(markdown, contentStart, contentEnd)
        decorations += ReleaseNotesDecoration(
            start = rangeStart,
            end = output.length,
            style = style,
        )
        return contentEnd + delimiter.length
    }

    private fun startsBlock(line: String): Boolean =
        headingPattern.matches(line) ||
            imagePattern.matches(line) ||
            line.startsWith("```") ||
            unorderedItemPattern.matches(line) ||
            orderedItemPattern.matches(line) ||
            line.startsWith(">") ||
            dividerPattern.matches(line)
}
