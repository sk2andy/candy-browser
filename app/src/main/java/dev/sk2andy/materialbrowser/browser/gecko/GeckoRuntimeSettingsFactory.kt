package dev.sk2andy.materialbrowser.browser.gecko

import androidx.annotation.UiThread
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsRules
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsSettings
import dev.sk2andy.materialbrowser.browser.HttpsOnlyMode
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntimeSettings

internal object GeckoRuntimeSettingsFactory {
    @UiThread
    fun create(
        contentBlocking: ContentBlocking.Settings,
        trustUserCertificates: Boolean = BuildConfig.TRUST_USER_CERTIFICATES,
        dnsOverHttpsSettings: DnsOverHttpsSettings = DnsOverHttpsRules.Default,
        httpsOnlyMode: HttpsOnlyMode = HttpsOnlyMode.Default,
    ): GeckoRuntimeSettings = GeckoRuntimeSettings.Builder()
        .contentBlocking(contentBlocking)
        .loginAutofillEnabled(true)
        .aboutConfigEnabled(true)
        .automaticFontSizeAdjustment(false)
        // Gecko owns its CA store; Android Network Security Config alone cannot opt it in.
        .enterpriseRootsEnabled(trustUserCertificates)
        .build()
        .apply {
            setFingerprintingProtection(true)
            setFingerprintingProtectionPrivateBrowsing(true)
            applyDnsOverHttpsSettings(dnsOverHttpsSettings)
            applyHttpsOnlyMode(httpsOnlyMode)
        }
}

@UiThread
internal fun GeckoRuntimeSettings.applyHttpsOnlyMode(mode: HttpsOnlyMode) {
    setAllowInsecureConnections(
        when (mode) {
            HttpsOnlyMode.Off -> GeckoRuntimeSettings.ALLOW_ALL
            HttpsOnlyMode.PrivateOnly -> GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE
            HttpsOnlyMode.AllTabs -> GeckoRuntimeSettings.HTTPS_ONLY
        },
    )
}

@UiThread
internal fun GeckoRuntimeSettings.applyDnsOverHttpsSettings(settings: DnsOverHttpsSettings) {
    setDohAutoselectEnabled(false)
    val endpoint = DnsOverHttpsRules.endpoint(settings)
    if (endpoint == null) {
        setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_DISABLED)
        setTrustedRecursiveResolverUri("")
    } else {
        setTrustedRecursiveResolverUri(endpoint)
        setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_ONLY)
    }
}
