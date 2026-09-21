package dev.sk2andy.materialbrowser.browser.engine

import android.content.Context
import android.content.res.Configuration
import android.view.View
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineCapabilities
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsSettings
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreparedSession
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowsingData
import dev.sk2andy.materialbrowser.browser.gecko.GeckoCandyTrailHistoryEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionActionKey
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionChromeHost
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionSessionIdentity
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

    fun setExtensionChromeHost(host: GeckoExtensionChromeHost?) = Unit

    fun clickExtensionAction(key: GeckoExtensionActionKey): Boolean = false

    fun dismissExtensionPopup() = Unit

    fun notifySelectedExtensionTabChanged() = Unit

    fun extensionSessionIdentity(tabId: String): GeckoExtensionSessionIdentity? = null

    fun prepareSession(tabId: String, session: BrowserEnginePreparedSession): Boolean = false

    fun createExtensionPopupView(
        context: Context,
        session: BrowserEnginePreparedSession,
    ): View? = null

    fun releaseExtensionPopupView(view: View) = Unit

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

    fun setDnsOverHttpsSettings(settings: DnsOverHttpsSettings) = Unit

    fun setWebContentFontSizeFactor(factor: Float)

    fun setWebContentColorScheme(colorScheme: BrowserWebContentColorScheme) = Unit

    fun onConfigurationChanged(configuration: Configuration) = Unit

    fun setForceDarkWebsites(enabled: Boolean) = Unit

    fun clearPrivateData() = Unit

    fun shutdown() = Unit

    fun create(
        tabId: String,
        profileId: String,
        isolationEnabled: Boolean = false,
        isPrivate: Boolean,
        contentKind: BrowserEngineContentKind,
        privacyPolicy: GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled,
        privacyEventSink: GeckoPrivacyEventSink = GeckoPrivacyEventSink { },
        trailHistoryEventSink: GeckoCandyTrailHistoryEventSink =
            GeckoCandyTrailHistoryEventSink { _, _ -> },
        eventSink: BrowserEngineEventSink,
    ): AndroidBrowserEngineSessionPort
}

internal enum class BrowserEngineContentKind {
    RegularTab,
    LinkPeek,
    ExternalPreview,
}

internal enum class BrowserWebContentColorScheme {
    System,
    Light,
    Dark,
}
