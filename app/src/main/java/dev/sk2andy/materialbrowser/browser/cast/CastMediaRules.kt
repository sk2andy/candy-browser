package dev.sk2andy.materialbrowser.browser.cast

import dev.sk2andy.materialbrowser.browser.WebMediaKind
import dev.sk2andy.materialbrowser.browser.WebMediaState
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import java.net.URI

internal data class CastMediaSource(
    val url: String,
    val contentType: String,
    val title: String,
    val origin: String,
    val posterUrl: String?,
    val startPositionMillis: Long,
)

internal data class CastMediaIdentity(
    val tabId: String,
    val navigationGeneration: Int,
    val documentId: String,
    val mediaId: String,
    val origin: String,
)

internal data class CastMediaCandidate(
    val identity: CastMediaIdentity,
    val source: CastMediaSource,
)

internal object CastMediaRules {
    fun source(
        state: WebMediaState?,
        isPrivate: Boolean,
        isSelectedTab: Boolean,
    ): CastMediaSource? {
        if (state == null || isPrivate || !isSelectedTab || state.kind != WebMediaKind.Video) {
            return null
        }
        val url = BrowserUriPolicy.normalizeHttpUrl(state.sourceUrl) ?: return null
        val contentType = normalizedContentType(state.contentType)
            ?: inferredContentType(url)
            ?: return null
        val posterUrl = BrowserUriPolicy.normalizeHttpUrl(state.posterUrl)
        return CastMediaSource(
            url = url,
            contentType = contentType,
            title = state.title,
            origin = state.origin,
            posterUrl = posterUrl,
            startPositionMillis = state.currentPositionMillis.coerceAtLeast(0L),
        )
    }

    private fun normalizedContentType(value: String?): String? {
        val normalized = value
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        return normalized.takeIf(SUPPORTED_CONTENT_TYPES::contains)
    }

    private fun inferredContentType(url: String): String? {
        val path = runCatching { URI(url).path.lowercase() }.getOrNull() ?: return null
        return when {
            path.endsWith(".m3u8") -> "application/x-mpegurl"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            else -> null
        }
    }

    private val SUPPORTED_CONTENT_TYPES = setOf(
        "application/dash+xml",
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "video/mp4",
        "video/webm",
    )
}
