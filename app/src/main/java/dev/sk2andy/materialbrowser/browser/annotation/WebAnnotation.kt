package dev.sk2andy.materialbrowser.browser.annotation

import dev.sk2andy.materialbrowser.data.CanonicalWebUrl
import dev.sk2andy.materialbrowser.recall.RecallMatch
import dev.sk2andy.materialbrowser.recall.RecallRules
import java.util.Locale

internal data class WebAnnotation(
    val id: String,
    val profileId: String,
    val url: String,
    val title: String,
    val quote: String,
    val note: String,
    val createdAt: Long,
)

internal object WebAnnotationRules {
    const val MAX_ENTRIES = 200
    const val MAX_QUOTE_CHARS = 8_000
    const val MAX_NOTE_CHARS = 4_000

    fun sanitize(value: WebAnnotation): WebAnnotation? {
        if (value.id.length !in 1..64 || value.profileId.isBlank() || value.profileId.length > 128 ||
            value.url.length > 4_096 || value.createdAt < 0 || value.quote.isBlank() ||
            value.quote.length > MAX_QUOTE_CHARS || value.note.length > MAX_NOTE_CHARS) return null
        val url = CanonicalWebUrl.key(value.url) ?: return null
        return value.copy(url = url, title = value.title.take(512), note = value.note.trim())
    }

    fun search(
        values: List<WebAnnotation>,
        profiles: Set<String>,
        query: String,
        limit: Int,
    ): List<RecallMatch> {
        val terms = RecallRules.meaningfulTerms(query).take(12)
        if (terms.isEmpty()) return emptyList()
        return values.asSequence().filter { it.profileId in profiles }.filter { annotation ->
            val text = "${annotation.title} ${annotation.quote} ${annotation.note}".lowercase(Locale.ROOT)
            terms.all(text::contains)
        }.sortedByDescending(WebAnnotation::createdAt).take(limit.coerceIn(0, 50)).map {
            RecallMatch(
                profileId = it.profileId,
                url = it.url,
                title = it.title,
                excerpt = "${it.quote} — ${it.note}".trimEnd(' ', '—').take(320),
                visitedAt = it.createdAt,
                score = 1.0,
            )
        }.toList()
    }
}
