package dev.sk2andy.materialbrowser.browser.actions

import android.webkit.WebView
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequest
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequestFactory
import java.net.URI

data class WebContentTarget(
    val linkUrl: String? = null,
    val imageUrl: String? = null,
) {
    val canOpenLinkInBackground: Boolean
        get() = linkUrl != null

    val canDownloadImage: Boolean
        get() = imageUrl != null

    fun openLinkInBackgroundAction(): WebContentAction.OpenLinkInBackground? =
        linkUrl?.let { WebContentAction.OpenLinkInBackground(it) }

    fun downloadImageAction(
        userAgent: String? = null,
        cookies: String? = null,
    ): WebContentAction.DownloadImage? = imageUrl?.let { url ->
        BrowserDownloadRequestFactory.create(
            url = url,
            mimeType = imageMimeType(url),
            userAgent = userAgent,
            cookies = cookies,
        )?.let { WebContentAction.DownloadImage(it) }
    }

    private fun imageMimeType(url: String): String? = when (
        runCatching { URI(url).path.substringAfterLast('.').lowercase() }.getOrNull()
    ) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "svg" -> "image/svg+xml"
        else -> null
    }
}

sealed interface WebContentAction {
    data class OpenLinkInBackground(val url: String) : WebContentAction

    data class DownloadImage(val request: BrowserDownloadRequest) : WebContentAction
}

object WebViewHitTestResolver {
    fun supports(hitType: Int): Boolean = hitType == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
        hitType == WebView.HitTestResult.IMAGE_TYPE ||
        hitType == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE

    fun resolve(
        hitType: Int,
        extra: String?,
        focusedLinkUrl: String? = null,
        focusedImageUrl: String? = null,
    ): WebContentTarget? {
        val directUrl = safeHttpUrl(extra)
        val focusedLink = safeHttpUrl(focusedLinkUrl)
        val focusedImage = safeHttpUrl(focusedImageUrl)
        val target = when (hitType) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> WebContentTarget(
                linkUrl = focusedLink ?: directUrl,
            )

            WebView.HitTestResult.IMAGE_TYPE -> WebContentTarget(
                imageUrl = focusedImage ?: directUrl,
            )

            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> WebContentTarget(
                linkUrl = focusedLink,
                imageUrl = focusedImage ?: directUrl,
            )

            else -> null
        }
        return target?.takeIf { it.linkUrl != null || it.imageUrl != null }
    }

    private fun safeHttpUrl(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val uri = URI(value.trim())
            value.trim().takeIf {
                (uri.scheme.equals("http", ignoreCase = true) ||
                    uri.scheme.equals("https", ignoreCase = true)) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null
            }
        }.getOrNull()
    }
}

@Stable
class WebContentActionState {
    var target by mutableStateOf<WebContentTarget?>(null)
        private set

    var lastDownload by mutableStateOf<DownloadActionResult?>(null)
        private set

    var addressBarPulseNonce by mutableIntStateOf(0)
        private set

    val isVisible: Boolean
        get() = target != null

    fun show(target: WebContentTarget) {
        this.target = target
    }

    fun dismiss() {
        target = null
    }

    fun reportDownload(result: DownloadActionResult) {
        lastDownload = result
    }

    fun consumeDownloadResult() {
        lastDownload = null
    }

    fun requestAddressBarPulse() {
        addressBarPulseNonce++
    }
}

sealed interface DownloadActionResult {
    data class Enqueued(
        val id: Long,
        val fileName: String,
    ) : DownloadActionResult

    data class Failed(val message: String) : DownloadActionResult
}
