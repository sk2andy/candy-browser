package dev.sk2andy.materialbrowser.browser

enum class LinkPeekSwipeAction(val wireValue: String) {
    OpenBackground("open_background"),
    OpenForeground("open_foreground"),
    OpenNewStack("open_new_stack"),
    OpenPrivate("open_private"),
    Copy("copy"),
    Share("share"),
    Favorite("favorite"),
    Snooze("snooze"),
    ReaderLater("reader_later"),
    ;

    companion object {
        fun fromWireValue(value: String?): LinkPeekSwipeAction =
            entries.firstOrNull { action -> action.wireValue == value } ?: OpenBackground
    }
}
