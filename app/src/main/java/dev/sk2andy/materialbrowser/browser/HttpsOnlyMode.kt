package dev.sk2andy.materialbrowser.browser

enum class HttpsOnlyMode(val stableId: String) {
    Off("off"),
    PrivateOnly("private_only"),
    AllTabs("all_tabs"),
    ;

    fun protects(isPrivate: Boolean): Boolean = when (this) {
        Off -> false
        PrivateOnly -> isPrivate
        AllTabs -> true
    }

    companion object {
        val Default = AllTabs

        fun fromStableId(value: String?): HttpsOnlyMode =
            entries.firstOrNull { mode -> mode.stableId == value } ?: Default
    }
}
