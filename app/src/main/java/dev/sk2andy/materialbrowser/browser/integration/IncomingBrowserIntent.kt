package dev.sk2andy.materialbrowser.browser.integration

import android.content.Intent

data class IncomingBrowserRequest(val url: String)

object IncomingBrowserIntent {
    fun from(intent: Intent): IncomingBrowserRequest? {
        val url = when (intent.action) {
            Intent.ACTION_VIEW -> BrowserUriPolicy.normalizeHttpUrl(intent.dataString)
            Intent.ACTION_SEND -> sharedWebUrl(intent)
            else -> null
        } ?: return null
        return IncomingBrowserRequest(url)
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
