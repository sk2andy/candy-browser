package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import org.json.JSONArray
import org.json.JSONObject

/** Limits the document payload crossing either browser engine's extraction bridge. */
internal object PageTextExtraction {
    const val MAX_TEXT_CHARS = 64_000
    const val MAX_PAYLOAD_CHARS = 150_000

    fun fromJavascriptResult(result: String?): String? {
        if (result == null || result.length > MAX_PAYLOAD_CHARS || result == "null") return null
        return runCatching {
            validate(JSONObject(JSONArray("[$result]").getString(0)))
        }.getOrNull()
    }

    fun validate(payload: JSONObject?): String? {
        payload ?: return null
        if (payload.length() != 3) return null
        val sourceUrl = payload.opt("sourceUrl") as? String ?: return null
        val title = payload.opt("title") as? String ?: return null
        val text = payload.opt("text") as? String ?: return null
        if (sourceUrl.length !in 1..8_192 || BrowserUriPolicy.normalizeHttpUrl(sourceUrl) == null ||
            title.length > 1_024 ||
            text.length !in 1..MAX_TEXT_CHARS || text.isBlank()
        ) return null
        return payload.toString().takeIf { it.length <= MAX_PAYLOAD_CHARS }
    }
}
