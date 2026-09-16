package dev.sk2andy.materialbrowser.browser.gecko

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Keeps Gecko's activity-result and process-runtime wiring outside the engine-neutral activity. */
internal class GeckoActivityIntegration(
    context: Context,
    launch: (PendingIntent) -> Unit,
    onPendingChanged: (Boolean) -> Unit,
    isPendingRequestCurrent: () -> Boolean,
) : AutoCloseable {
    private val delegate = GeckoWebAuthnActivityDelegate(
        launch = launch,
        onPendingChanged = onPendingChanged,
        isPendingRequestCurrent = isPendingRequestCurrent,
    )

    init {
        GeckoRuntimeOwner.bindWebAuthnActivityDelegate(
            context = context.applicationContext,
            delegate = delegate,
        )
    }

    fun onActivityResult(resultCode: Int, data: Intent?) {
        delegate.onActivityResult(resultCode, data)
    }

    fun onHostPaused() {
        delegate.onHostPaused()
    }

    fun onHostResumed() {
        delegate.onHostResumed()
    }

    override fun close() {
        GeckoRuntimeOwner.unbindWebAuthnActivityDelegate(delegate)
        delegate.close()
    }
}
