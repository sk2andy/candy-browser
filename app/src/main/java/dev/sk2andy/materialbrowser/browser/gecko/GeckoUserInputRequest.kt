package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import java.util.UUID
import org.json.JSONObject

/** Only a current, complete document response may confirm that a page has no user input. */
internal class GeckoUserInputRequest(private val handler: Handler) {
    private var requestId = 0L
    private var token = ""
    private var nonce = ""
    private var revision = -1L
    private var navigationGeneration = -1
    private var result: ((Boolean?) -> Unit)? = null
    private var timeout: Runnable? = null

    fun start(
        token: String,
        revision: Long,
        navigationGeneration: Int,
        post: (JSONObject) -> Unit,
        onResult: (Boolean?) -> Unit,
    ) {
        cancel()
        requestId = if (requestId >= MAX_SAFE_JAVASCRIPT_INTEGER) 1 else requestId + 1
        this.token = token
        nonce = UUID.randomUUID().toString()
        this.revision = revision
        this.navigationGeneration = navigationGeneration
        result = onResult
        timeout = Runnable { cancel() }.also { handler.postDelayed(it, TIMEOUT_MILLIS) }
        runCatching {
            post(
                JSONObject()
                    .put("type", "user-input-query")
                    .put("protocolVersion", CandyPrivacyHostContract.PROTOCOL_VERSION)
                    .put("token", token)
                    .put("revision", revision)
                    .put("navigationGeneration", navigationGeneration)
                    .put("requestId", requestId)
                    .put("nonce", nonce),
            )
        }.onFailure { cancel() }
    }

    fun accept(value: JSONObject) {
        if (
            result == null ||
            value.optString("token") != token ||
            value.optString("nonce") != nonce ||
            value.optLong("requestId", -1) != requestId ||
            value.optLong("revision", -1) != revision ||
            value.optInt("navigationGeneration", -1) != navigationGeneration
        ) return
        finish(value.opt("hasUserInput") as? Boolean)
    }

    fun cancel() = finish(null)

    private fun finish(hasUserInput: Boolean?) {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        val callback = result
        result = null
        callback?.invoke(hasUserInput)
    }

    private companion object {
        const val MAX_SAFE_JAVASCRIPT_INTEGER = 9_007_199_254_740_991L
        const val TIMEOUT_MILLIS = 5_000L
    }
}
