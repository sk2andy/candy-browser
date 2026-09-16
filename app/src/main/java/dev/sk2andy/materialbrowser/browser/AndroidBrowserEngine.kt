package dev.sk2andy.materialbrowser.browser

enum class AndroidBrowserEngineKind(val stableId: String) {
    GeckoView("gecko"),
    SystemWebView("system_webview"),
    ;

    companion object {
        val Default = GeckoView

        fun fromStableId(value: String?): AndroidBrowserEngineKind =
            entries.firstOrNull { it.stableId == value } ?: Default
    }
}

internal data class AndroidBrowserEngineCapabilities(
    val firefoxExtensions: Boolean,
    val toppings: Boolean,
    val nativeAutoplayPolicy: Boolean,
    val insecureHttpPasswordManagerSelection: Boolean,
    val dnsOverHttps: Boolean,
) {
    companion object {
        val GeckoView = AndroidBrowserEngineCapabilities(
            firefoxExtensions = true,
            toppings = true,
            nativeAutoplayPolicy = true,
            insecureHttpPasswordManagerSelection = true,
            dnsOverHttps = true,
        )

        val SystemWebView = AndroidBrowserEngineCapabilities(
            firefoxExtensions = false,
            toppings = true,
            nativeAutoplayPolicy = true,
            insecureHttpPasswordManagerSelection = false,
            dnsOverHttps = false,
        )
    }
}

internal object AndroidBrowserEngineRules {
    fun persistedKind(
        stableId: String?,
        systemWebViewOnly: Boolean,
    ): AndroidBrowserEngineKind = if (systemWebViewOnly) {
        AndroidBrowserEngineKind.SystemWebView
    } else {
        AndroidBrowserEngineKind.fromStableId(stableId)
    }

    fun canSelect(
        kind: AndroidBrowserEngineKind,
        systemWebViewOnly: Boolean,
    ): Boolean = !systemWebViewOnly || kind == AndroidBrowserEngineKind.SystemWebView

    fun capabilities(kind: AndroidBrowserEngineKind): AndroidBrowserEngineCapabilities =
        when (kind) {
            AndroidBrowserEngineKind.GeckoView -> AndroidBrowserEngineCapabilities.GeckoView
            AndroidBrowserEngineKind.SystemWebView ->
                AndroidBrowserEngineCapabilities.SystemWebView
        }
}
