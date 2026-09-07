package dev.sk2andy.materialbrowser.browser.actions

enum class LinkLongPressOutcome {
    ShowContext,
    CopyLink,
    OpenInNewTab,
    OpenInPrivateTab,
    Share,
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
            LinkLongPressAction.OpenInNewTab -> LinkLongPressOutcome.OpenInNewTab
            LinkLongPressAction.OpenInPrivateTab -> if (canOpenInPrivate) {
                LinkLongPressOutcome.OpenInPrivateTab
            } else {
                LinkLongPressOutcome.ShowContext
            }
            LinkLongPressAction.Share -> LinkLongPressOutcome.Share
        }
    }
}
