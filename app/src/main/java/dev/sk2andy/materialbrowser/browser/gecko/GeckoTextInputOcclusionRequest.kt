package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import dev.sk2andy.materialbrowser.browser.BrowserViewportRect
import org.json.JSONObject

internal class GeckoTextInputOcclusionRequest(private val handler: Handler) {
    private var requestId = 0L
    private var revision = -1L
    private var navigationGeneration = -1
    private var result: ((Boolean) -> Unit)? = null
    private var timeout: Runnable? = null

    fun start(
        token: String,
        revision: Long,
        navigationGeneration: Int,
        viewportRect: BrowserViewportRect,
        post: (JSONObject) -> Unit,
        onResult: (Boolean) -> Unit,
    ) {
        cancel()
        requestId = if (requestId >= MAX_SAFE_JAVASCRIPT_INTEGER) 1 else requestId + 1
        this.revision = revision
        this.navigationGeneration = navigationGeneration
        result = onResult
        timeout = Runnable { finish(false) }.also { pending ->
            handler.postDelayed(pending, TIMEOUT_MILLIS)
        }
        runCatching {
            post(
                JSONObject()
                    .put("type", "text-input-occlusion-probe")
                    .put("protocolVersion", CandyPrivacyHostContract.PROTOCOL_VERSION)
                    .put("token", token)
                    .put("revision", revision)
                    .put("navigationGeneration", navigationGeneration)
                    .put("requestId", requestId)
                    .put(
                        "viewportRect",
                        JSONObject()
                            .put("left", viewportRect.leftFraction)
                            .put("top", viewportRect.topFraction)
                            .put("right", viewportRect.rightFraction)
                            .put("bottom", viewportRect.bottomFraction),
                    ),
            )
        }.onFailure { finish(false) }
    }

    fun accept(value: JSONObject) {
        if (
            value.optLong("requestId", -1) != requestId ||
            value.optLong("revision", -1) != revision ||
            value.optInt("navigationGeneration", -1) != navigationGeneration
        ) {
            return
        }
        finish(value.optBoolean("occluded", false))
    }

    fun cancel() = finish(false)

    private fun finish(occluded: Boolean) {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        val callback = result
        result = null
        callback?.invoke(occluded)
    }

    private companion object {
        const val MAX_SAFE_JAVASCRIPT_INTEGER = 9_007_199_254_740_991L
        const val TIMEOUT_MILLIS = 5_000L
    }
}
