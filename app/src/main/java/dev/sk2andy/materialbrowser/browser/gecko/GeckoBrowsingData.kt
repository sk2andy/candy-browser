package dev.sk2andy.materialbrowser.browser.gecko

internal enum class GeckoBrowsingData {
    AllCaches,
    Cookies,
    All,
}

internal object GeckoBrowsingDataReloadRules {
    fun canReload(
        capturedUrl: String,
        currentUrl: String?,
        capturedNavigationGeneration: Int?,
        currentNavigationGeneration: Int?,
        sameSession: Boolean,
    ): Boolean =
        sameSession &&
            currentUrl == capturedUrl &&
            capturedNavigationGeneration != null &&
            currentNavigationGeneration == capturedNavigationGeneration
}
