package dev.sk2andy.materialbrowser.browser.actions

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequestFactory

/** Engine-neutral target presented by Candy's page-content action surface. */
data class WebContentTarget(
    val linkUrl: String? = null,
    val imageUrl: String? = null,
) {
    val canOpenLinkInBackground: Boolean
        get() = linkUrl != null

    val canDownloadImage: Boolean
        get() = imageUrl != null

    val canDownloadLink: Boolean
        get() = linkUrl != null

    fun openLinkInBackgroundAction(): WebContentAction.OpenLinkInBackground? =
        linkUrl?.let { WebContentAction.OpenLinkInBackground(it) }

    fun downloadLinkAction(
        userAgent: String? = null,
        cookies: String? = null,
        referrer: String? = null,
    ): WebContentAction.DownloadLink? = linkUrl?.let { url ->
        BrowserDownloadRequestFactory.create(
            url = url,
            userAgent = userAgent,
            cookies = cookies,
            referrer = referrer,
        )?.let { WebContentAction.DownloadLink(it) }
    }

    fun downloadImageAction(
        userAgent: String? = null,
        cookies: String? = null,
        referrer: String? = null,
    ): WebContentAction.DownloadImage? = imageUrl?.let { url ->
        BrowserDownloadRequestFactory.create(
            url = url,
            userAgent = userAgent,
            cookies = cookies,
            referrer = referrer,
        )?.let { WebContentAction.DownloadImage(it) }
    }
}

/** Semantic kind of content under a browser-engine context-menu gesture. */
internal enum class BrowserContentTargetKind {
    None,
    Image,
    Video,
    Audio,
}

/**
 * Engine-neutral mapping from context-menu payloads to Candy's existing content-action target.
 * Only HTTP(S) values cross this boundary; media sources other than images are not presented as
 * image downloads.
 */
internal object BrowserContentTargetRules {
    fun resolve(
        kind: BrowserContentTargetKind,
        linkUrl: String?,
        sourceUrl: String?,
    ): WebContentTarget? {
        val safeLinkUrl = BrowserUriPolicy.normalizeHttpUrl(linkUrl)
        val safeImageUrl = sourceUrl
            ?.takeIf { kind == BrowserContentTargetKind.Image }
            ?.let(BrowserUriPolicy::normalizeHttpUrl)
        return WebContentTarget(
            linkUrl = safeLinkUrl,
            imageUrl = safeImageUrl,
        ).takeIf { target -> target.linkUrl != null || target.imageUrl != null }
    }
}

/** Browser-engine event edge for a user long-press on actionable page content. */
internal fun interface BrowserContentTargetListener {
    fun onLongPress(target: WebContentTarget)
}
