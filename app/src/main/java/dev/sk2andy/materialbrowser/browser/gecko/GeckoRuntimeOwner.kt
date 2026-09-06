package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import androidx.annotation.VisibleForTesting

/** Process-owned browser engine handle without exposing the underlying GeckoRuntime. */
internal interface GeckoRuntimeHandle {
    val extensions: GeckoExtensionRuntime
    val toppings: GeckoToppingHostRuntime

    fun createSession(
        profileId: String,
        isPrivate: Boolean,
        privacyPolicy: GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled,
        privacyEventSink: GeckoPrivacyEventSink = GeckoPrivacyEventSink { },
    ): GeckoBrowserSession

    fun clearAllData(onComplete: (Boolean) -> Unit = {}) {
        onComplete(true)
    }
}

/** Creates GeckoRuntime through an application-context-owned integration edge. */
internal fun interface GeckoRuntimeFactory {
    fun create(): GeckoRuntimeHandle
}

/**
 * Owns the single GeckoRuntime permitted in an Android process.
 *
 * The factory must close over applicationContext. Activity ownership would leak the Activity and
 * conflict with GeckoView's one-active-runtime-per-process contract.
 */
internal object GeckoRuntimeOwner {
    private val lock = Any()

    @Volatile
    private var runtime: GeckoRuntimeHandle? = null

    fun getOrCreate(factory: GeckoRuntimeFactory): GeckoRuntimeHandle =
        runtime ?: synchronized(lock) {
            runtime ?: factory.create().also { created -> runtime = created }
        }

    fun getOrCreate(context: Context): GeckoRuntimeHandle = getOrCreate {
        GeckoViewRuntimeHandle.create(context.applicationContext)
    }

    @VisibleForTesting
    fun resetForTesting() {
        synchronized(lock) {
            runtime = null
        }
    }
}
