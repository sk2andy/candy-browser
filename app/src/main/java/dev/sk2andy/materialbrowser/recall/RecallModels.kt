package dev.sk2andy.materialbrowser.recall

import dev.sk2andy.materialbrowser.data.CanonicalWebUrl
import java.text.Normalizer
import java.util.Locale

internal data class RecallDocument(
    val profileId: String,
    val url: String,
    val title: String,
    val text: String,
    val visitedAt: Long,
)

data class RecallMatch(
    val profileId: String,
    val url: String,
    val title: String,
    val excerpt: String,
    val visitedAt: Long,
    val score: Double,
)

internal data class RecallExtractionIdentity(
    val tabId: String,
    val profileId: String,
    val url: String,
    val navigationGeneration: Int,
)

internal object RecallRules {
    const val MAX_DOCUMENT_CHARS = 64_000
    const val MAX_TITLE_CHARS = 512
    const val MAX_PROFILE_ID_CHARS = 128
    const val MAX_URL_CHARS = 4_096
    const val MAX_QUERY_CHARS = 160
    const val MAX_ENTRIES = 250
    const val MAX_ADDRESS_RESULTS = 2
    const val MAX_HISTORY_RESULTS = 50
    const val MAX_COMMAND_RESULTS = 20
    const val MAX_EXCERPT_CHARS = 320
    const val EXPLICIT_COMMAND = ">recall"

    fun sanitizeDocument(document: RecallDocument): RecallDocument? {
        val profileId = document.profileId.trim().takeIf { candidate ->
            candidate.isNotEmpty() && candidate.length <= MAX_PROFILE_ID_CHARS
        } ?: return null
        val url = canonicalUrl(document.url) ?: return null
        val text = normalizeWhitespace(document.text).take(MAX_DOCUMENT_CHARS)
        if (text.isBlank()) return null
        val title = normalizeWhitespace(document.title)
            .take(MAX_TITLE_CHARS)
            .ifBlank { url }
        return document.copy(
            profileId = profileId,
            url = url,
            title = title,
            text = text,
        )
    }

    fun canonicalUrl(url: String): String? = CanonicalWebUrl.key(url)
        ?.takeIf { candidate -> candidate.length <= MAX_URL_CHARS }

    fun addressQuery(input: String): String? {
        if (isExplicitCommand(input)) return null
        val query = normalizedQuery(input) ?: return null
        return query.takeIf { meaningfulTerms(it).size >= 2 }
    }

    fun explicitQuery(input: String): String? {
        val trimmed = input.trimStart()
        if (!isExplicitCommand(trimmed)) return null
        return normalizedQuery(trimmed.drop(EXPLICIT_COMMAND.length))
    }

    fun historyQuery(input: String): String? = normalizedQuery(input)

    fun isExplicitCommand(input: String): Boolean {
        val trimmed = input.trimStart()
        if (!trimmed.startsWith(EXPLICIT_COMMAND, ignoreCase = true)) return false
        return trimmed.length == EXPLICIT_COMMAND.length ||
            trimmed[EXPLICIT_COMMAND.length].isWhitespace()
    }

    fun matchExpression(query: String): String? {
        val terms = meaningfulTerms(query).take(MAX_QUERY_TERMS)
        if (terms.isEmpty()) return null
        return terms.joinToString(" ") { term -> "$term*" }
    }

    fun isCurrent(
        expected: RecallExtractionIdentity,
        actual: RecallExtractionIdentity?,
        isActivityStarted: Boolean,
        enabled: Boolean,
        isPrivate: Boolean,
        webViewMatches: Boolean,
    ): Boolean =
        isActivityStarted && enabled && !isPrivate && webViewMatches && expected == actual

    internal fun meaningfulTerms(query: String): List<String> = normalizedSearchText(query)
        .split(NON_WORD)
        .asSequence()
        .filter { term -> term.length >= MIN_TERM_CHARS && term.any(Char::isLetterOrDigit) }
        .distinct()
        .toList()

    private fun normalizedQuery(input: String): String? {
        val normalized = normalizeWhitespace(input).take(MAX_QUERY_CHARS)
        return normalized.takeIf { meaningfulTerms(it).isNotEmpty() }
    }

    private fun normalizeWhitespace(value: String): String = value
        .replace(CONTROL_CHARACTERS, " ")
        .replace(WHITESPACE, " ")
        .trim()

    private fun normalizedSearchText(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT),
        Normalizer.Form.NFD,
    ).replace(COMBINING_MARKS, "")

    private val CONTROL_CHARACTERS = "[\\p{Cc}\\p{Cf}]".toRegex()
    private val WHITESPACE = "\\s+".toRegex()
    private val COMBINING_MARKS = "\\p{M}+".toRegex()
    private val NON_WORD = "[^\\p{L}\\p{N}]+".toRegex()
    private const val MIN_TERM_CHARS = 2
    private const val MAX_QUERY_TERMS = 12
}
