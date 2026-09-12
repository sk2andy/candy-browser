package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import dev.sk2andy.materialbrowser.browser.engine.BrowserWebContentColorScheme
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/** Process-owned browser engine handle without exposing the underlying GeckoRuntime. */
internal interface GeckoRuntimeHandle {
    val extensions: GeckoExtensionRuntime
    val toppings: GeckoToppingHostRuntime

    fun createSession(
        profileId: String,
        isolationEnabled: Boolean = false,
        isPrivate: Boolean,
        privacyPolicy: GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled,
        privacyEventSink: GeckoPrivacyEventSink = GeckoPrivacyEventSink { },
    ): GeckoBrowserSession

    /**
     * Adopts the unopened session returned from WebExtension tabs.create.
     *
     * GeckoView owns opening this session with its generated browsing-context ID after the
     * TabDelegate result resolves. Implementations must install Candy delegates without opening it.
     */
    fun adoptExtensionSession(
        session: GeckoSession,
        profileId: String,
        isolationEnabled: Boolean = false,
        isPrivate: Boolean,
        privacyPolicy: GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled,
        privacyEventSink: GeckoPrivacyEventSink = GeckoPrivacyEventSink { },
    ): GeckoBrowserSession? = null

    fun clearBrowsingData(
        data: GeckoBrowsingData,
        onComplete: (Boolean) -> Unit = {},
    )

    /** Dispatches context-scoped deletion after its sessions close. Gecko exposes no completion. */
    fun requestProfileDataDeletion(profileId: String): Boolean = false

    @UiThread
    fun setBlockThirdPartyCookies(blocked: Boolean)

    @UiThread
    fun setWebRtcProtectionMode(mode: WebRtcProtectionMode, onReady: () -> Unit = {})

    @UiThread
    fun setWebContentFontSizeFactor(factor: Float)

    @UiThread
    fun setWebContentColorScheme(colorScheme: BrowserWebContentColorScheme)

    @UiThread
    fun onConfigurationChanged(configuration: Configuration)

    @UiThread
    fun bindWebAuthnActivityDelegate(delegate: GeckoRuntime.ActivityDelegate)

    @UiThread
    fun unbindWebAuthnActivityDelegate(delegate: GeckoRuntime.ActivityDelegate)

    fun clearAllData(onComplete: (Boolean) -> Unit = {}) {
        clearBrowsingData(GeckoBrowsingData.All, onComplete)
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

    @UiThread
    fun bindWebAuthnActivityDelegate(
        context: Context,
        delegate: GeckoRuntime.ActivityDelegate,
    ) {
        getOrCreate(context).bindWebAuthnActivityDelegate(delegate)
    }

    @UiThread
    fun unbindWebAuthnActivityDelegate(delegate: GeckoRuntime.ActivityDelegate) {
        runtime?.unbindWebAuthnActivityDelegate(delegate)
    }

    @VisibleForTesting
    fun hasRuntimeForTesting(): Boolean = runtime != null

    @VisibleForTesting
    fun resetForTesting() {
        synchronized(lock) {
            runtime = null
        }
    }
}
