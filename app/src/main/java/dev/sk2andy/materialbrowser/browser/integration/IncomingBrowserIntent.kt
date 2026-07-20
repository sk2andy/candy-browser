package dev.sk2andy.materialbrowser.browser.integration

import android.content.Intent

data class IncomingBrowserRequest(val url: String)

object IncomingBrowserIntent {
    fun from(intent: Intent): IncomingBrowserRequest? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val url = BrowserUriPolicy.normalizeHttpUrl(intent.dataString) ?: return null
        return IncomingBrowserRequest(url)
    }
}
