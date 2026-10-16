package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import dev.sk2andy.materialbrowser.browser.PageTextExtraction
import java.util.UUID
import org.json.JSONObject

/** Owns one document-bound extraction and clears it on navigation, disconnect or timeout. */
internal class GeckoTextExtractionRequest(private val handler: Handler) {
    private var requestId: String? = null
    private var revision = -1L
    private var navigationGeneration = -1
    private var result: ((String?) -> Unit)? = null
    private var timeout: Runnable? = null

    fun start(
        revision: Long,
        navigationGeneration: Int,
        post: (JSONObject) -> Unit,
        onResult: (String?) -> Unit,
    ) {
        cancel()
        requestId = UUID.randomUUID().toString()
        this.revision = revision
        this.navigationGeneration = navigationGeneration
        result = onResult
        timeout = Runnable { cancel() }.also { handler.postDelayed(it, TIMEOUT_MILLIS) }
        runCatching {
            post(JSONObject()
                .put("type", "text-extract")
                .put("requestId", requestId)
                .put("revision", revision)
                .put("navigationGeneration", navigationGeneration))
        }.onFailure { cancel() }
    }

    fun accept(value: JSONObject) {
        if (result == null || value.optString("requestId") != requestId ||
            value.optLong("revision", -1) != revision ||
            value.optInt("navigationGeneration", -1) != navigationGeneration
        ) return
        finish(PageTextExtraction.validate(value.optJSONObject("payload")))
    }

    fun cancel() = finish(null)

    private fun finish(payload: String?) {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        requestId = null
        val callback = result
        result = null
        callback?.invoke(payload)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
