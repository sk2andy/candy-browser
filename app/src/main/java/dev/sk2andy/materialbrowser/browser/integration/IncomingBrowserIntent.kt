package dev.sk2andy.materialbrowser.browser.integration

import android.content.Intent

data class IncomingBrowserRequest(
    val url: String,
    val kind: IncomingBrowserRequestKind,
)

enum class IncomingBrowserRequestKind {
    View,
    Share,
}

object IncomingBrowserIntent {
    fun from(intent: Intent): IncomingBrowserRequest? {
        val kind = when (intent.action) {
            Intent.ACTION_VIEW -> IncomingBrowserRequestKind.View
            Intent.ACTION_SEND -> IncomingBrowserRequestKind.Share
            else -> null
        } ?: return null
        val url = when (kind) {
            IncomingBrowserRequestKind.View -> BrowserUriPolicy.normalizeHttpUrl(intent.dataString)
            IncomingBrowserRequestKind.Share -> sharedWebUrl(intent)
        } ?: return null
        return IncomingBrowserRequest(url = url, kind = kind)
    }

    private fun sharedWebUrl(intent: Intent): String? {
        if (
            !intent.type.equals("text/plain", ignoreCase = true) &&
            !intent.type.equals("text/html", ignoreCase = true)
        ) {
            return null
        }
        val sharedText = runCatching {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        }.getOrNull() ?: return null
        if (sharedText.length > MAX_SHARED_TEXT_LENGTH) return null
        return BrowserUriPolicy.normalizeHttpUrl(sharedText.toString())
    }

    private const val MAX_SHARED_TEXT_LENGTH = 32_768
}
