package dev.sk2andy.materialbrowser.browser

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

enum class LinkLongPressOutcome {
    ShowContext,
    CopyLink,
    Share,
    DownloadLink,
    OpenInNewTabInBackground,
    OpenInNewTabInForeground,
    OpenInPrivateTabInBackground,
    OpenInPrivateTabInForeground,
}

object LinkLongPressRules {
    fun outcome(
        action: LinkLongPressAction,
        hasLinkTarget: Boolean,
        canOpenInPrivate: Boolean,
    ): LinkLongPressOutcome {
        if (!hasLinkTarget) return LinkLongPressOutcome.ShowContext
        return when (action) {
            LinkLongPressAction.LinkPeek -> LinkLongPressOutcome.ShowContext
            LinkLongPressAction.CopyLink -> LinkLongPressOutcome.CopyLink
            LinkLongPressAction.Share -> LinkLongPressOutcome.Share
            LinkLongPressAction.DownloadLink -> LinkLongPressOutcome.DownloadLink
            LinkLongPressAction.OpenInNewTabInBackground ->
                LinkLongPressOutcome.OpenInNewTabInBackground
            LinkLongPressAction.OpenInNewTabInForeground ->
                LinkLongPressOutcome.OpenInNewTabInForeground
            LinkLongPressAction.OpenInPrivateTabInBackground -> if (canOpenInPrivate) {
                LinkLongPressOutcome.OpenInPrivateTabInBackground
            } else {
                LinkLongPressOutcome.ShowContext
            }
            LinkLongPressAction.OpenInPrivateTabInForeground -> if (canOpenInPrivate) {
                LinkLongPressOutcome.OpenInPrivateTabInForeground
            } else {
                LinkLongPressOutcome.ShowContext
            }
        }
    }
}
