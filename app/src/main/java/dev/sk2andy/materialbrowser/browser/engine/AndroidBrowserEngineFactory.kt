package dev.sk2andy.materialbrowser.browser.engine

import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineCapabilities
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowsingData
import dev.sk2andy.materialbrowser.browser.gecko.GeckoCandyTrailHistoryEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.browser.gecko.GeckoToppingHostState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoToppingInteractionDelegate
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand

/** Process-wide engine adapter. Implementations must initialize only their own runtime. */
internal interface AndroidBrowserEngineFactory {
    val kind: AndroidBrowserEngineKind

    val capabilities: AndroidBrowserEngineCapabilities

    val runtimeVersionName: String?

    fun reconcileToppings(scripts: List<UserScript>)

    fun setToppingHostStateListener(listener: (GeckoToppingHostState) -> Unit)

    fun setToppingInteractionDelegate(delegate: GeckoToppingInteractionDelegate)

    fun invokeToppingMenuCommand(command: UserScriptMenuCommand)

    fun clearToppingValues(scriptId: String)

    fun clearBrowsingData(data: GeckoBrowsingData, onComplete: (Boolean) -> Unit = {})

    fun clearAllData(onComplete: (Boolean) -> Unit = {})

    fun requestProfileDataDeletion(profileId: String): Boolean

    fun setBlockThirdPartyCookies(blocked: Boolean)

    fun setWebRtcProtectionMode(mode: WebRtcProtectionMode)

    fun setWebContentFontSizeFactor(factor: Float)

    fun clearPrivateData() = Unit

    fun shutdown() = Unit

    fun create(
        tabId: String,
        profileId: String,
        isolationEnabled: Boolean = false,
        isPrivate: Boolean,
        privacyPolicy: GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled,
        privacyEventSink: GeckoPrivacyEventSink = GeckoPrivacyEventSink { },
        trailHistoryEventSink: GeckoCandyTrailHistoryEventSink =
            GeckoCandyTrailHistoryEventSink { _, _ -> },
        eventSink: BrowserEngineEventSink,
    ): AndroidBrowserEngineSessionPort
}
