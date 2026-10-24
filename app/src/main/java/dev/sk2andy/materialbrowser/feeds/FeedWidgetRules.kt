package dev.sk2andy.materialbrowser.feeds

internal data class FeedWidgetArticle(
    val id: String,
    val sourceTitle: String,
    val title: String,
)

internal object FeedWidgetRules {
    // Keep the RemoteViews payload bounded when many feeds are subscribed.
    private const val MAX_ARTICLES = 50

    fun articles(state: FeedState): List<FeedWidgetArticle> {
        val sources = state.sources.associateBy { it.id }
        return FeedRules.filter(state, FeedInboxFilter.All)
            .mapNotNull { article ->
                val source = article.sourceIds.firstNotNullOfOrNull(sources::get)
                    ?: return@mapNotNull null
                FeedWidgetArticle(article.id, source.title, article.title)
            }
            .take(MAX_ARTICLES)
    }
}
