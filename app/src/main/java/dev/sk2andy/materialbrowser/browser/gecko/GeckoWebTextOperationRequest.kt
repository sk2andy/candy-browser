package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import java.util.UUID
import org.json.JSONObject

internal class GeckoWebTextOperationRequest(private val handler: Handler) {
    private var id: String? = null
    private var callback: ((Boolean) -> Unit)? = null
    private var timeout: Runnable? = null

    fun start(payload: String, post: (JSONObject) -> Unit, onComplete: (Boolean) -> Unit) {
        cancel()
        id = UUID.randomUUID().toString()
        callback = onComplete
        timeout = Runnable { cancel() }.also { handler.postDelayed(it, 5_000L) }
        runCatching {
            require(payload.length <= 2_000_000)
            post(JSONObject().put("type", "web-text-operation").put("requestId", id)
                .put("payload", JSONObject(payload)))
        }.onFailure { cancel() }
    }

    fun accept(value: JSONObject) {
        if (id == value.optString("requestId")) finish(value.optBoolean("applied"))
    }

    fun cancel() = finish(false)

    private fun finish(applied: Boolean) {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        id = null
        val pending = callback
        callback = null
        pending?.invoke(applied)
    }
}
