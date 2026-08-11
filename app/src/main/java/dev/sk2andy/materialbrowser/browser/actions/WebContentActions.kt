package dev.sk2andy.materialbrowser.browser.actions

import android.webkit.WebView
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
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
        referrer: String? = null,
    ): WebContentAction.DownloadImage? = imageUrl?.let { url ->
        BrowserDownloadRequestFactory.create(
            url = url,
            mimeType = imageMimeType(url),
            userAgent = userAgent,
            cookies = cookies,
            referrer = referrer,
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

    private fun safeHttpUrl(value: String?): String? = BrowserUriPolicy.normalizeHttpUrl(value)
}

@Stable
class WebContentActionState {
    internal var revision = 0L
        private set

    var target by mutableStateOf<WebContentTarget?>(null)
        private set

    var lastDownload by mutableStateOf<DownloadActionResult?>(null)
        private set

    var addressBarPulseNonce by mutableIntStateOf(0)
        private set

    var linkPeekProgress by mutableFloatStateOf(0f)
        private set

    var isLinkPeekArmed by mutableStateOf(false)
        private set

    var isLinkPeekCommitting by mutableStateOf(false)
        private set

    var linkPeekNewTabPulseNonce by mutableIntStateOf(0)
        private set

    val isVisible: Boolean
        get() = target != null

    val isLinkPeekVisible: Boolean
        get() = target?.linkUrl != null

    fun show(target: WebContentTarget) {
        revision++
        val sameLink = this.target?.linkUrl != null && this.target?.linkUrl == target.linkUrl
        this.target = target
        if (!sameLink) {
            isLinkPeekCommitting = false
            updateLinkPeek(progress = 0f, armed = false)
        }
    }

    fun dismiss() {
        revision++
        target = null
        isLinkPeekCommitting = false
        updateLinkPeek(progress = 0f, armed = false)
    }

    fun updateLinkPeek(progress: Float, armed: Boolean) {
        if (isLinkPeekCommitting) return
        linkPeekProgress = progress.coerceIn(0f, 1f)
        isLinkPeekArmed = armed
    }

    fun startLinkPeekCommit() {
        if (target?.linkUrl == null || isLinkPeekCommitting) return
        isLinkPeekCommitting = true
        linkPeekProgress = 1f
        isLinkPeekArmed = true
    }

    fun requestLinkPeekNewTabPulse() {
        linkPeekNewTabPulseNonce++
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

    data class HandedOff(
        val fileName: String,
        val appName: String,
    ) : DownloadActionResult

    data class Failed(val message: String) : DownloadActionResult
}
