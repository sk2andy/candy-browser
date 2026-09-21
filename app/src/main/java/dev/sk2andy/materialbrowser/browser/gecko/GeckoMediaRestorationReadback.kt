package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler

/** Owns the bounded compositor readback after a request's DOM restoration acknowledgement. */
internal class GeckoMediaRestorationReadback(private val handler: Handler) {
    private class Request(
        val isCurrent: () -> Boolean,
        val onResult: (Boolean) -> Unit,
    ) {
        var timeout: Runnable? = null
    }

    private var pending: Request? = null

    fun start(
        isCurrent: () -> Boolean,
        capture: (onComplete: (Boolean) -> Unit) -> Unit,
        onResult: (Boolean) -> Unit,
    ) {
        cancel()
        val request = Request(isCurrent, onResult)
        pending = request
        if (!isCurrent()) {
            finish(request, false)
            return
        }
        request.timeout = Runnable { finish(request, false) }.also { timeout ->
            handler.postDelayed(timeout, READBACK_TIMEOUT_MILLIS)
        }
        capture { captured -> finish(request, captured) }
    }

    fun cancel() {
        pending?.let { finish(it, false) }
    }

    private fun finish(request: Request, captured: Boolean) {
        if (pending !== request) return
        pending = null
        request.timeout?.let(handler::removeCallbacks)
        request.onResult(captured && request.isCurrent())
    }

    private companion object {
        const val READBACK_TIMEOUT_MILLIS = 2_000L
    }
}
