package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

internal sealed interface FavoriteBookmarkFileResult {
    data class Loaded(val source: String) : FavoriteBookmarkFileResult
    data object Empty : FavoriteBookmarkFileResult
    data object TooLarge : FavoriteBookmarkFileResult
    data object InvalidUtf8 : FavoriteBookmarkFileResult
    data object Unreadable : FavoriteBookmarkFileResult
}

internal object FavoriteBookmarkImportReader {
    const val MAX_FILE_BYTES = 5 * 1_024 * 1_024

    fun read(input: InputStream): FavoriteBookmarkFileResult = runCatching {
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (output.size() + count > MAX_FILE_BYTES) {
                    return FavoriteBookmarkFileResult.TooLarge
                }
                output.write(buffer, 0, count)
            }
            if (output.size() == 0) return FavoriteBookmarkFileResult.Empty
            val source = runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(output.toByteArray()))
                    .toString()
            }.getOrElse { return FavoriteBookmarkFileResult.InvalidUtf8 }
            FavoriteBookmarkFileResult.Loaded(source)
        }
    }.getOrDefault(FavoriteBookmarkFileResult.Unreadable)

    private const val BUFFER_BYTES = 8 * 1_024
}

internal sealed interface FavoriteBookmarkParseResult {
    data class Parsed(val favorites: List<FavoriteEntry>) : FavoriteBookmarkParseResult
    data object InvalidFormat : FavoriteBookmarkParseResult
}

internal data class FavoriteBookmarkMergeResult(
    val favorites: List<FavoriteEntry>,
    val importedCount: Int,
    val skippedCount: Int,
    val limitReached: Boolean,
)

internal object FavoriteBookmarkImportRules {
    private const val FORMAT_MARKER = "NETSCAPE-Bookmark-file-1"
    private const val MAX_TITLE_CHARS = 512
    private const val MAX_URL_CHARS = 8 * 1_024
    private val tagPattern = Regex("""<[^>]*>""")
    private val whitespacePattern = Regex("""\s+""")
    private val entityPattern = Regex("""&(#(?:x[0-9a-f]+|[0-9]+)|[a-z]+);""", RegexOption.IGNORE_CASE)

    fun parse(
        source: String,
        importedAtMillis: Long,
    ): FavoriteBookmarkParseResult {
        if (!source.contains(FORMAT_MARKER, ignoreCase = true)) {
            return FavoriteBookmarkParseResult.InvalidFormat
        }
        val favorites = anchors(source)
            .mapNotNull { anchor ->
                val attributes = anchor.attributes
                val rawUrl = attribute(attributes, "href") ?: return@mapNotNull null
                val url = decodeHtml(rawUrl).trim()
                if (url.isEmpty() || url.length > MAX_URL_CHARS) return@mapNotNull null
                val title = decodeHtml(tagPattern.replace(anchor.content, " "))
                    .replace('\u00a0', ' ')
                    .let { value -> whitespacePattern.replace(value.trim(), " ") }
                    .take(MAX_TITLE_CHARS)
                FavoriteEntry(
                    url = url,
                    title = title,
                    addedAt = importedAt(attributes) ?: importedAtMillis,
                )
            }
            .toList()
        return FavoriteBookmarkParseResult.Parsed(favorites)
    }

    private fun anchors(source: String): Sequence<HtmlAnchor> = sequence {
        var cursor = 0
        while (cursor < source.length) {
            val tagStart = source.indexOf('<', startIndex = cursor)
            if (tagStart < 0) break
            when {
                source.startsWith("<!--", tagStart) -> {
                    val commentEnd = source.indexOf("-->", startIndex = tagStart + 4)
                    cursor = if (commentEnd < 0) source.length else commentEnd + 3
                }
                source.isOpeningTag(tagStart, "script") ||
                    source.isOpeningTag(tagStart, "style") -> {
                    val tagName = if (source.isOpeningTag(tagStart, "script")) {
                        "script"
                    } else {
                        "style"
                    }
                    val closeStart = source.findClosingTag(
                        name = tagName,
                        startIndex = tagStart + tagName.length + 1,
                    )
                    val closeEnd = closeStart.takeIf { it >= 0 }
                        ?.let { source.tagEnd(it) }
                    cursor = closeEnd?.plus(1) ?: source.length
                }
                source.isOpeningTag(tagStart, "a") -> {
                    val openEnd = source.tagEnd(tagStart)
                    if (openEnd == null) {
                        cursor = tagStart + 2
                        continue
                    }
                    val boundary = source.findNextAnchorBoundary(openEnd + 1) ?: break
                    if (boundary.kind == AnchorBoundaryKind.Open) {
                        cursor = boundary.index
                        continue
                    }
                    val closeStart = boundary.index
                    val closeEnd = source.tagEnd(closeStart)
                    if (closeEnd == null) {
                        cursor = closeStart + 3
                        continue
                    }
                    yield(
                        HtmlAnchor(
                            attributes = source.substring(tagStart + 2, openEnd),
                            content = source.substring(openEnd + 1, closeStart),
                        ),
                    )
                    cursor = closeEnd + 1
                }
                else -> cursor = tagStart + 1
            }
        }
    }

    private fun String.isOpeningTag(startIndex: Int, name: String): Boolean {
        if (!regionMatches(startIndex + 1, name, 0, name.length, ignoreCase = true)) return false
        val boundaryIndex = startIndex + name.length + 1
        return boundaryIndex >= length ||
            this[boundaryIndex].isWhitespace() ||
            this[boundaryIndex] == '>' ||
            this[boundaryIndex] == '/'
    }

    private fun String.findClosingTag(name: String, startIndex: Int): Int {
        var candidate = indexOf("</$name", startIndex, ignoreCase = true)
        while (candidate >= 0) {
            val boundaryIndex = candidate + name.length + 2
            if (
                boundaryIndex >= length ||
                this[boundaryIndex].isWhitespace() ||
                this[boundaryIndex] == '>'
            ) {
                return candidate
            }
            candidate = indexOf("</$name", boundaryIndex, ignoreCase = true)
        }
        return -1
    }

    private fun String.findNextAnchorBoundary(startIndex: Int): AnchorBoundary? {
        var candidate = indexOf('<', startIndex)
        while (candidate >= 0) {
            when {
                isOpeningTag(candidate, "a") -> return AnchorBoundary(
                    index = candidate,
                    kind = AnchorBoundaryKind.Open,
                )
                isClosingTag(candidate, "a") -> return AnchorBoundary(
                    index = candidate,
                    kind = AnchorBoundaryKind.Close,
                )
                else -> candidate = indexOf('<', candidate + 1)
            }
        }
        return null
    }

    private fun String.isClosingTag(startIndex: Int, name: String): Boolean {
        if (startIndex + 1 >= length || this[startIndex + 1] != '/') return false
        if (!regionMatches(startIndex + 2, name, 0, name.length, ignoreCase = true)) return false
        val boundaryIndex = startIndex + name.length + 2
        return boundaryIndex >= length ||
            this[boundaryIndex].isWhitespace() ||
            this[boundaryIndex] == '>'
    }

    private fun String.tagEnd(startIndex: Int): Int? {
        var quote: Char? = null
        for (index in startIndex until length) {
            val character = this[index]
            when {
                quote != null && character == quote -> quote = null
                quote == null && (character == '\'' || character == '"') -> quote = character
                quote == null && character == '>' -> return index
            }
        }
        return null
    }

    fun merge(
        current: List<FavoriteEntry>,
        imported: List<FavoriteEntry>,
        limit: Int = BrowsingLibraryRules.MAX_FAVORITES,
    ): FavoriteBookmarkMergeResult {
        val safeLimit = limit.coerceAtLeast(0)
        val existingKeys = current.mapNotNullTo(mutableSetOf()) { entry ->
            CanonicalWebUrl.key(entry.url)
        }
        val additions = mutableListOf<FavoriteEntry>()
        var skippedCount = 0
        var limitReached = false
        imported.forEach { entry ->
            val safeUrl = BrowserUriPolicy.normalizeHttpUrl(entry.url)
            val key = safeUrl?.let(CanonicalWebUrl::key)
            if (key == null || !existingKeys.add(key)) {
                skippedCount++
                return@forEach
            }
            if (current.size + additions.size >= safeLimit) {
                existingKeys.remove(key)
                skippedCount++
                limitReached = true
                return@forEach
            }
            additions += entry.copy(
                url = safeUrl,
                title = safeTitle(entry),
            )
        }
        return FavoriteBookmarkMergeResult(
            favorites = additions + current,
            importedCount = additions.size,
            skippedCount = skippedCount,
            limitReached = limitReached,
        )
    }

    private fun attribute(attributes: String, name: String): String? {
        val pattern = Regex(
            pattern = """(?:^|\s)$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
            option = RegexOption.IGNORE_CASE,
        )
        val match = pattern.find(attributes) ?: return null
        return match.groupValues.drop(1).firstOrNull(String::isNotEmpty)
    }

    private fun importedAt(attributes: String): Long? {
        val seconds = attribute(attributes, "add_date")?.toLongOrNull()
            ?.takeIf { it >= 0L }
            ?: return null
        return runCatching { Math.multiplyExact(seconds, 1_000L) }.getOrNull()
    }

    private fun safeTitle(entry: FavoriteEntry): String = entry.title.trim().ifEmpty {
        runCatching { URI(entry.url).host?.removePrefix("www.") }
            .getOrNull()
            .orEmpty()
            .ifEmpty { entry.url }
    }

    private fun decodeHtml(value: String): String = entityPattern.replace(value) { match ->
        val entity = match.groupValues[1]
        when (entity.lowercase(Locale.ROOT)) {
            "amp" -> "&"
            "apos" -> "'"
            "gt" -> ">"
            "lt" -> "<"
            "nbsp" -> "\u00a0"
            "quot" -> "\""
            else -> decodeNumericEntity(entity) ?: match.value
        }
    }

    private fun decodeNumericEntity(entity: String): String? {
        if (!entity.startsWith('#')) return null
        val isHex = entity.startsWith("#x", ignoreCase = true)
        val codePoint = entity.drop(if (isHex) 2 else 1)
            .toIntOrNull(if (isHex) 16 else 10)
            ?: return null
        if (!Character.isValidCodePoint(codePoint)) return null
        return String(Character.toChars(codePoint))
    }

    private data class HtmlAnchor(
        val attributes: String,
        val content: String,
    )

    private data class AnchorBoundary(
        val index: Int,
        val kind: AnchorBoundaryKind,
    )

    private enum class AnchorBoundaryKind {
        Open,
        Close,
    }
}
