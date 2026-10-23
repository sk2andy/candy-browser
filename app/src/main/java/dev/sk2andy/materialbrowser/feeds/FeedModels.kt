package dev.sk2andy.materialbrowser.feeds

import dev.sk2andy.materialbrowser.reader.ReaderDocument

enum class FeedArticleOrigin {
    Feed,
    Website,
}

enum class FeedReadingMode {
    Feed,
    Reader,
    Original,
}

enum class FeedInboxFilter {
    Unread,
    Today,
    Starred,
    All,
    Hidden,
}

enum class FeedRuleAction {
    MarkRead,
    Star,
    Hide,
    Tag,
    Notify,
}

enum class FeedOfflineStatus {
    None,
    Waiting,
    Downloading,
    Ready,
    Failed,
}

data class FeedSource(
    val id: String,
    val url: String,
    val title: String,
    val siteUrl: String = "",
    val folderId: String? = null,
    val readingMode: FeedReadingMode = FeedReadingMode.Reader,
    val lastCheckedMillis: Long = 0,
    val lastSuccessMillis: Long = 0,
    val error: String? = null,
    val notificationsEnabled: Boolean = false,
)

data class FeedFolder(val id: String, val name: String)

data class FeedArticle(
    val id: String,
    val sourceIds: Set<String>,
    val url: String,
    val title: String,
    val origin: FeedArticleOrigin = FeedArticleOrigin.Feed,
    val websiteSnapshotId: String? = null,
    val author: String = "",
    val summary: String = "",
    val content: String = "",
    val publishedAtMillis: Long = 0,
    val receivedAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    val previousContent: String? = null,
    val isRead: Boolean = false,
    val readingProgress: Float = 0f,
    val isStarred: Boolean = false,
    val isHidden: Boolean = false,
    val tags: Set<String> = emptySet(),
    val offlineStatus: FeedOfflineStatus = FeedOfflineStatus.None,
    val offlineDocument: ReaderDocument? = null,
    val offlineDocumentId: String? = null,
    val offlineContentVersion: Int = 0,
    val contentIsSummary: Boolean = content.trim() == summary.trim(),
)

data class FeedSearch(
    val id: String,
    val name: String,
    val query: String = "",
    val sourceId: String? = null,
    val folderId: String? = null,
    val unreadOnly: Boolean = false,
    val tag: String? = null,
)

data class FeedRule(
    val id: String,
    val name: String,
    val query: String,
    val action: FeedRuleAction,
    val sourceId: String? = null,
    val folderId: String? = null,
    val tag: String = "",
    val enabled: Boolean = true,
)

data class FeedSettings(
    val offlineBudgetMb: Int = 250,
    val wifiOnly: Boolean = true,
    val refreshOnOpen: Boolean = true,
    val backgroundRefresh: Boolean = false,
    val digestEnabled: Boolean = false,
    val digestHour: Int = 18,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 8,
)

data class FeedState(
    val sources: List<FeedSource> = emptyList(),
    val folders: List<FeedFolder> = emptyList(),
    val articles: List<FeedArticle> = emptyList(),
    val searches: List<FeedSearch> = emptyList(),
    val rules: List<FeedRule> = emptyList(),
    val settings: FeedSettings = FeedSettings(),
    val queue: List<String> = emptyList(),
    val refreshing: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val deletedWebsiteSnapshotIds: Set<String> = emptySet(),
) {
    val unreadCount: Int
        get() =
            articles.count {
                (it.origin == FeedArticleOrigin.Feed || it.sourceIds.isNotEmpty()) &&
                    !it.isRead &&
                    !it.isHidden
            }
}

data class FeedCandidate(val url: String, val title: String)

data class FeedPreview(
    val url: String,
    val title: String,
    val siteUrl: String,
    val articles: List<FeedArticle>,
)

enum class FeedImportStatus {
    New,
    Existing,
    Invalid,
}

data class FeedImportEntry(
    val title: String,
    val url: String,
    val folder: String?,
    val status: FeedImportStatus,
)

data class FeedImportPreview(val entries: List<FeedImportEntry>)
