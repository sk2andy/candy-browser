package dev.sk2andy.materialbrowser.browser.actions

enum class LinkLongPressAction(val stableId: String) {
    LinkPeek("link_peek"),
    CopyLink("copy_link"),
    OpenInNewTab("open_in_new_tab"),
    OpenInPrivateTab("open_in_private_tab"),
    Share("share"),
    ;

    companion object {
        fun fromStableId(value: String?): LinkLongPressAction =
            entries.firstOrNull { it.stableId == value } ?: LinkPeek
    }
}
