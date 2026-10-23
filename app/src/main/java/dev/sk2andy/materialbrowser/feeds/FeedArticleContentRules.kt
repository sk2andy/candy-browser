package dev.sk2andy.materialbrowser.feeds

import dev.sk2andy.materialbrowser.reader.ReaderDocument

/**
 * A description copied into content by the feed parser is still a preview, regardless of length.
 */
internal object FeedArticleContentRules {
    fun hasFeedBody(article: FeedArticle): Boolean =
        article.content.isNotBlank() && !article.contentIsSummary

    fun isLegacyPreview(article: FeedArticle, document: ReaderDocument): Boolean =
        article.offlineContentVersion == 0 &&
            article.origin == FeedArticleOrigin.Feed &&
            article.summary.isNotBlank() &&
            !hasFeedBody(article) &&
            document.blocks.size == 1 &&
            document.blocks.single().text.trim() ==
                article.content.ifBlank { article.summary }.trim()

    private val teaserEnding =
        Regex(
            "(?:…|\\.{3}|\\[mehr\\]|(?:read more|continue reading|weiterlesen|mehr lesen)\\s*[.→»›]*)\\s*$",
            RegexOption.IGNORE_CASE,
        )

    fun isTruncatedPublisherPreview(article: FeedArticle, document: ReaderDocument): Boolean {
        if (!article.contentIsSummary || !teaserEnding.containsMatchIn(article.summary.trim()))
            return false
        val summary = teaserEnding.replace(article.summary.trim(), "").trim()
        val body =
            document.blocks.filterNot { it.text == article.title }.joinToString(" ") { it.text }
        return teaserEnding.replace(body.trim(), "").replace(Regex("\\s+"), " ").trim() ==
            summary.replace(Regex("\\s+"), " ")
    }
}
