package dev.sk2andy.materialbrowser.browser.gecko

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.UiThread
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime

/** Bridges GeckoView WebAuthn requests to the currently active browser Activity. */
internal class GeckoWebAuthnActivityDelegate(
    private val launch: (PendingIntent) -> Unit,
) : GeckoRuntime.ActivityDelegate {
    private var pendingResult: GeckoResult<Intent>? = null
    private var closed = false

    @UiThread
    override fun onStartActivityForResult(intent: PendingIntent): GeckoResult<Intent> {
        if (closed) return failedResult("WebAuthn activity host is closed")
        if (pendingResult != null) return failedResult("WebAuthn activity is already pending")

        val result = GeckoResult<Intent>()
        pendingResult = result
        runCatching { launch(intent) }
            .onFailure { failure -> completeExceptionally(result, failure) }
        return result
    }

    @UiThread
    fun onActivityResult(resultCode: Int, data: Intent?) {
        val result = pendingResult ?: return
        pendingResult = null
        if (resultCode == Activity.RESULT_OK && data != null) {
            result.complete(data)
        } else {
            result.completeExceptionally(
                IllegalStateException("WebAuthn activity did not return a successful result"),
            )
        }
    }

    @UiThread
    fun close() {
        if (closed) return
        closed = true
        pendingResult?.let { result ->
            pendingResult = null
            result.completeExceptionally(IllegalStateException("WebAuthn activity host was closed"))
        }
    }

    private fun completeExceptionally(result: GeckoResult<Intent>, failure: Throwable) {
        if (pendingResult !== result) return
        pendingResult = null
        result.completeExceptionally(failure)
    }

    private fun failedResult(message: String): GeckoResult<Intent> =
        GeckoResult.fromException(IllegalStateException(message))
}
