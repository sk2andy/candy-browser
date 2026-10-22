package dev.sk2andy.materialbrowser.reader.rss

import dev.sk2andy.materialbrowser.reader.ReaderStudioSessionRules

internal enum class RssReaderLoadAction { Wait, Cancel, Extract, Unsupported, Failed }

internal object RssReaderLoadRules {
    fun action(
        requestedTabId: String,
        selectedTabId: String,
        url: String,
        isLoading: Boolean,
        isPrivate: Boolean,
        hasError: Boolean,
    ): RssReaderLoadAction = when {
        requestedTabId != selectedTabId || isPrivate -> RssReaderLoadAction.Cancel
        hasError -> RssReaderLoadAction.Failed
        isLoading -> RssReaderLoadAction.Wait
        !ReaderStudioSessionRules.isSupportedSource(url) -> RssReaderLoadAction.Unsupported
        else -> RssReaderLoadAction.Extract
    }
}
