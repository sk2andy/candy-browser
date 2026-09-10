package dev.sk2andy.materialbrowser.browser.actions

enum class LinkLongPressAction(val stableId: String) {
    LinkPeek("link_peek"),
    CopyLink("copy_link"),
    Share("share"),
    DownloadLink("download_link"),
    OpenInNewTabInBackground("open_in_new_tab"),
    OpenInNewTabInForeground("open_in_new_tab_foreground"),
    OpenInPrivateTabInBackground("open_in_private_tab_background"),
    OpenInPrivateTabInForeground("open_in_private_tab"),
    ;

    companion object {
        fun fromStableId(value: String?): LinkLongPressAction =
            entries.firstOrNull { it.stableId == value } ?: LinkPeek
    }
}
