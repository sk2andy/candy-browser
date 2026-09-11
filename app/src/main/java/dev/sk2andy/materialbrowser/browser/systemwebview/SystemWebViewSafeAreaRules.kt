package dev.sk2andy.materialbrowser.browser.systemwebview

internal object SystemWebViewSafeAreaRules {
    private const val FULL_WEBVIEW_SAFE_AREA_MILESTONE = 144

    fun supportsCssSafeAreaInsets(versionName: String?): Boolean =
        versionName
            ?.substringBefore('.')
            ?.toIntOrNull()
            ?.let { milestone -> milestone >= FULL_WEBVIEW_SAFE_AREA_MILESTONE }
            ?: false
}
