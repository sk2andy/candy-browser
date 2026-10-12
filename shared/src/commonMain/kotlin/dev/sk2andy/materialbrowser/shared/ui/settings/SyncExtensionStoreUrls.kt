package dev.sk2andy.materialbrowser.shared.ui.settings

object SyncExtensionStoreUrls {
    const val Chrome =
        "https://chromewebstore.google.com/detail/candy-sync/mbmmijoehjmjdehhbikpflhahamohbge"
    const val Firefox = "https://addons.mozilla.org/en-US/firefox/addon/candy-sync/"

    fun isStoreUrl(url: String): Boolean = url == Chrome || url == Firefox
}
