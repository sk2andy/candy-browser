package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import org.json.JSONObject

/** Native bridge edge: never accepts page-provided code or a shell-provided selector. */
internal class GeckoDomProbeRequest(private val handler: Handler) {
    private var requestId = 0L
    private var revision = -1L
    private var navigationGeneration = -1L
    private var result: ((String?) -> Unit)? = null
    private var timeout: Runnable? = null

    fun start(
        token: String,
        revision: Long,
        navigationGeneration: Long,
        post: (JSONObject) -> Unit,
        onResult: (String?) -> Unit,
    ) {
        cancel()
        requestId = if (requestId >= 9_007_199_254_740_991L) 1 else requestId + 1
        this.revision = revision
        this.navigationGeneration = navigationGeneration
        result = onResult
        timeout = Runnable { cancel() }.also { handler.postDelayed(it, 10_000) }
        runCatching {
            post(
                JSONObject()
                    .put("type", "dom-probe")
                    .put("protocolVersion", CandyPrivacyHostContract.PROTOCOL_VERSION)
                    .put("token", token)
                    .put("revision", revision)
                    .put("navigationGeneration", navigationGeneration)
                    .put("requestId", requestId),
            )
        }.onFailure { cancel() }
    }

    fun accept(value: JSONObject) {
        if (value.optLong("requestId", -1) != requestId ||
            value.optLong("revision", -1) != revision ||
            value.optLong("navigationGeneration", -1) != navigationGeneration
        ) return
        val payload = value.optJSONObject("payload")?.let(GeckoDomProbePayload::sanitize)
        finish(payload)
    }

    fun cancel() = finish(null)

    private fun finish(payload: String?) {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        val callback = result
        result = null
        callback?.invoke(payload)
    }
}
