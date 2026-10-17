package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import java.util.UUID
import org.json.JSONObject

internal class GeckoFormDraftRestoreRequest(private val handler: Handler) {
    private var id: String? = null
    private var callback: ((Boolean) -> Unit)? = null
    private var timeout: Runnable? = null

    fun start(payload: String, post: (JSONObject) -> Unit, onComplete: (Boolean) -> Unit) {
        cancel()
        val requestId = UUID.randomUUID().toString()
        id = requestId
        callback = onComplete
        timeout = Runnable { cancel() }.also { handler.postDelayed(it, 5_000L) }
        runCatching {
            post(JSONObject().put("type", "form-draft-restore").put("requestId", requestId)
                .put("payload", JSONObject(payload)))
        }.onFailure { cancel() }
    }

    fun accept(value: JSONObject) {
        if (id == value.optString("requestId")) finish(value.optBoolean("restored"))
    }

    fun cancel() = finish(false)

    private fun finish(restored: Boolean) {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        id = null
        val pending = callback
        callback = null
        pending?.invoke(restored)
    }
}
