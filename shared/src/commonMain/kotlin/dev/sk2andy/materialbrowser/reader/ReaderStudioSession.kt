package dev.sk2andy.materialbrowser.reader

data class ReaderStudioSession(
    val tabId: String,
    val sourceUrl: String,
    val isPrivate: Boolean,
    val requestId: Int,
)

object ReaderStudioSessionRules {
    fun isSupportedSource(sourceUrl: String): Boolean =
        ReaderExtractionContract.safeHttpUrl(sourceUrl) != null

    fun shouldClose(session: ReaderStudioSession?, selectedTabId: String): Boolean =
        session != null && session.tabId != selectedTabId

    fun acceptsResult(session: ReaderStudioSession?, requestId: Int): Boolean =
        session?.requestId == requestId
}
