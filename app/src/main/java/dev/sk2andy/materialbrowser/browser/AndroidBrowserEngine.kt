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
) {
    companion object {
        val GeckoView = AndroidBrowserEngineCapabilities(
            firefoxExtensions = true,
            toppings = true,
            nativeAutoplayPolicy = true,
            insecureHttpPasswordManagerSelection = true,
        )

        val SystemWebView = AndroidBrowserEngineCapabilities(
            firefoxExtensions = false,
            toppings = true,
            nativeAutoplayPolicy = true,
            insecureHttpPasswordManagerSelection = false,
        )
    }
}

internal object AndroidBrowserEngineRules {
    fun capabilities(kind: AndroidBrowserEngineKind): AndroidBrowserEngineCapabilities =
        when (kind) {
            AndroidBrowserEngineKind.GeckoView -> AndroidBrowserEngineCapabilities.GeckoView
            AndroidBrowserEngineKind.SystemWebView ->
                AndroidBrowserEngineCapabilities.SystemWebView
        }
}
