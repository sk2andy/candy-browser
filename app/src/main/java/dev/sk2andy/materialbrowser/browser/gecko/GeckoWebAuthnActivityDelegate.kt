package dev.sk2andy.materialbrowser.browser.gecko

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.UiThread
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime

/** Bridges GeckoView WebAuthn requests to the currently active browser Activity. */
internal class GeckoWebAuthnActivityDelegate(
    private val onPendingChanged: (Boolean) -> Unit = {},
    private val isPendingRequestCurrent: () -> Boolean = { true },
    private val launch: (PendingIntent) -> Unit,
) : GeckoRuntime.ActivityDelegate {
    private var pendingResult: GeckoResult<Intent>? = null
    private var pendingActivityResult: ActivityResult? = null
    private var hostResumed = false
    private var closed = false

    @UiThread
    override fun onStartActivityForResult(intent: PendingIntent): GeckoResult<Intent> {
        if (closed) return failedResult("WebAuthn activity host is closed")
        if (pendingResult != null) return failedResult("WebAuthn activity is already pending")

        val result = GeckoResult<Intent>()
        pendingResult = result
        onPendingChanged(true)
        runCatching { launch(intent) }
            .onFailure { failure -> completeExceptionally(result, failure) }
        return result
    }

    @UiThread
    fun onActivityResult(resultCode: Int, data: Intent?) {
        if (pendingResult == null || closed) return
        pendingActivityResult = ActivityResult(resultCode, data)
        deliverPendingResult()
    }

    @UiThread
    fun onHostResumed() {
        if (closed) return
        hostResumed = true
        deliverPendingResult()
    }

    @UiThread
    fun onHostPaused() {
        hostResumed = false
    }

    @UiThread
    fun close() {
        if (closed) return
        closed = true
        hostResumed = false
        pendingActivityResult = null
        pendingResult?.let { result ->
            pendingResult = null
            result.completeExceptionally(IllegalStateException("WebAuthn activity host was closed"))
            onPendingChanged(false)
        }
    }

    private fun deliverPendingResult() {
        when (
            GeckoWebAuthnDeliveryRules.decide(
                hasPendingRequest = pendingResult != null,
                hasActivityResult = pendingActivityResult != null,
                isHostResumed = hostResumed,
                isHostClosed = closed,
                isPendingRequestCurrent = isPendingRequestCurrent(),
            )
        ) {
            GeckoWebAuthnDeliveryDecision.Wait,
            GeckoWebAuthnDeliveryDecision.Ignore,
            -> return
            GeckoWebAuthnDeliveryDecision.Reject -> {
                pendingResult?.let { result ->
                    completeExceptionally(
                        result,
                        IllegalStateException("WebAuthn activity result is stale"),
                    )
                }
                return
            }
            GeckoWebAuthnDeliveryDecision.Complete -> Unit
        }
        val result = requireNotNull(pendingResult)
        val activityResult = requireNotNull(pendingActivityResult)
        pendingResult = null
        pendingActivityResult = null
        onPendingChanged(false)
        if (activityResult.resultCode == Activity.RESULT_OK) {
            result.complete(activityResult.data)
        } else {
            result.completeExceptionally(
                IllegalStateException("WebAuthn activity did not return a successful result"),
            )
        }
    }

    private fun completeExceptionally(result: GeckoResult<Intent>, failure: Throwable) {
        if (pendingResult !== result) return
        pendingResult = null
        pendingActivityResult = null
        onPendingChanged(false)
        result.completeExceptionally(failure)
    }

    private fun failedResult(message: String): GeckoResult<Intent> =
        GeckoResult.fromException(IllegalStateException(message))

    private data class ActivityResult(
        val resultCode: Int,
        val data: Intent?,
    )
}
