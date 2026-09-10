package dev.sk2andy.materialbrowser.browser.actions

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
        target: WebContentTarget,
        canOpenInPrivate: Boolean,
    ): LinkLongPressOutcome {
        if (target.linkUrl == null) return LinkLongPressOutcome.ShowContext
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
