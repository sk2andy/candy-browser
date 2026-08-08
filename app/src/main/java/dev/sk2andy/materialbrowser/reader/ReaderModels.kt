package dev.sk2andy.materialbrowser.reader

import java.net.URI

enum class ReaderBlockKind { Heading, Paragraph, Quote, ListItem }

data class ReaderLink(
    val label: String,
    val url: String,
)

data class ReaderBlock(
    val kind: ReaderBlockKind,
    val text: String,
    val level: Int = 0,
    val links: List<ReaderLink> = emptyList(),
)

data class ReaderDocument(
    val title: String,
    val sourceUrl: String,
    val siteName: String,
    val blocks: List<ReaderBlock>,
) {
    val speechText: String
        get() = buildString {
            append(title)
            blocks.forEach { block ->
                append("\n\n")
                append(block.text)
            }
        }
}

data class ReaderExtractionPayload(
    val title: String?,
    val sourceUrl: String?,
    val siteName: String?,
    val blocks: List<ReaderExtractionBlock>,
)

data class ReaderExtractionBlock(
    val kind: String?,
    val text: String?,
    val level: Int = 0,
    val links: List<ReaderExtractionLink> = emptyList(),
)

data class ReaderExtractionLink(
    val label: String?,
    val url: String?,
)

sealed interface ReaderExtractionResult {
    data class Success(val document: ReaderDocument) : ReaderExtractionResult
    data class Failure(val reason: ReaderExtractionFailure) : ReaderExtractionResult
}

enum class ReaderExtractionFailure { UnsupportedPage, EmptyArticle, InvalidResponse }

object ReaderExtractionContract {
    const val MAX_BLOCKS = 600
    const val MAX_BLOCK_CHARS = 12_000
    const val MAX_TOTAL_CHARS = 500_000
    private const val MAX_TITLE_CHARS = 500
    private const val MAX_LINKS_PER_BLOCK = 40
    private const val MAX_TOTAL_LINKS = 500

    fun sanitize(payload: ReaderExtractionPayload): ReaderExtractionResult {
        val sourceUrl = safeHttpUrl(payload.sourceUrl)
            ?: return ReaderExtractionResult.Failure(ReaderExtractionFailure.UnsupportedPage)
        var remainingChars = MAX_TOTAL_CHARS
        var remainingLinks = MAX_TOTAL_LINKS
        val blocks = buildList {
            payload.blocks.take(MAX_BLOCKS).forEach { raw ->
                if (remainingChars <= 0) return@forEach
                val text = normalize(raw.text, minOf(MAX_BLOCK_CHARS, remainingChars))
                if (text.isEmpty()) return@forEach
                val kind = when (raw.kind?.lowercase()) {
                    "heading" -> ReaderBlockKind.Heading
                    "quote" -> ReaderBlockKind.Quote
                    "listitem" -> ReaderBlockKind.ListItem
                    else -> ReaderBlockKind.Paragraph
                }
                val links = raw.links.take(minOf(MAX_LINKS_PER_BLOCK, remainingLinks)).mapNotNull { link ->
                    val url = safeHttpUrl(link.url) ?: return@mapNotNull null
                    val label = normalize(link.label, 300).ifEmpty { url }
                    ReaderLink(label = label, url = url)
                }.distinctBy(ReaderLink::url)
                remainingLinks -= links.size
                add(
                    ReaderBlock(
                        kind = kind,
                        text = text,
                        level = if (kind == ReaderBlockKind.Heading) raw.level.coerceIn(1, 6) else 0,
                        links = links,
                    ),
                )
                remainingChars -= text.length
            }
        }
        val readableChars = blocks.sumOf { block -> block.text.length }
        if (blocks.isEmpty() || readableChars < 80) {
            return ReaderExtractionResult.Failure(ReaderExtractionFailure.EmptyArticle)
        }
        val host = runCatching { URI(sourceUrl).host.orEmpty().removePrefix("www.") }
            .getOrDefault("")
        return ReaderExtractionResult.Success(
            ReaderDocument(
                title = normalize(payload.title, MAX_TITLE_CHARS).ifEmpty {
                    blocks.firstOrNull { it.kind == ReaderBlockKind.Heading }?.text
                        ?: host
                },
                sourceUrl = sourceUrl,
                siteName = normalize(payload.siteName, 200).ifEmpty { host },
                blocks = blocks,
            ),
        )
    }

    fun safeHttpUrl(value: String?): String? {
        val candidate = value?.trim()?.takeIf { it.length <= 2_048 } ?: return null
        return runCatching {
            val uri = URI(candidate)
            candidate.takeIf {
                (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null
            }
        }.getOrNull()
    }

    private fun normalize(value: String?, maxChars: Int): String = value.orEmpty()
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]"), " ")
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .replace(Regex(" *\\n+ *"), "\n")
        .trim()
        .take(maxChars)
        .trim()
}
