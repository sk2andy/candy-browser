package dev.sk2andy.materialbrowser.feeds

import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderExtractionBlock
import dev.sk2andy.materialbrowser.reader.ReaderExtractionContract
import dev.sk2andy.materialbrowser.reader.ReaderExtractionLink
import dev.sk2andy.materialbrowser.reader.ReaderExtractionPayload
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parses only the fetched, visible HTML. No scripts, remote resources or structured paywall data.
 */
internal object FeedArticleExtraction {
    private const val BLOCK_SELECTOR = "h1, h2, h3, h4, h5, h6, p, blockquote, li"
    private val excludedRole =
        Regex(
            "(?:^|[\\s_-])(advertisement|advertising|cookie|consent|newsletter|related|recommendations|comments|share|social)(?:$|[\\s_-])",
            RegexOption.IGNORE_CASE,
        )
    private val hiddenStyle =
        Regex("(?:display\\s*:\\s*none|visibility\\s*:\\s*hidden)", RegexOption.IGNORE_CASE)

    fun extract(article: FeedArticle, html: String): ReaderDocument? {
        if (html.length > FeedRules.MAX_FEED_BYTES) return null
        val page = Jsoup.parse(html, article.url)
        // Respect the publisher's explicit restricted-article declaration; never reconstruct its
        // body from structured data or treat an exposed teaser as a successful full-text load.
        val restricted =
            Regex("\"isAccessibleForFree\"\\s*:\\s*(?:false|\"false\")", RegexOption.IGNORE_CASE)
        if (
            page.select("script[type=application/ld+json]").any {
                restricted.containsMatchIn(it.data())
            }
        )
            return null
        page
            .select(
                "script, style, noscript, nav, aside, footer, form, button, iframe, template, [hidden], [aria-hidden=true]"
            )
            .remove()
        page
            .getAllElements()
            .filter {
                excludedRole.containsMatchIn("${it.id()} ${it.className()}") ||
                    hiddenStyle.containsMatchIn(it.attr("style"))
            }
            .forEach(Element::remove)
        val roots =
            page
                .select(
                    "[itemprop=articleBody], .entry-content, .post-content, .article-content, .article-body"
                )
                .ifEmpty { page.select("article") }
                .ifEmpty { page.select("main, [role=main]") }
        val candidates =
            roots.ifEmpty {
                // Older publisher layouts often use a plain div around the article paragraphs.
                page.select("p").mapNotNull(Element::parent).distinct()
            }
        val body =
            candidates.maxByOrNull(::readableScore)?.takeIf { readableScore(it) >= 80 }
                ?: return null
        val blocks =
            body
                .select(BLOCK_SELECTOR)
                .filter { element ->
                    // A quote/list item containing paragraphs must not repeat those paragraphs.
                    element.select(BLOCK_SELECTOR).none { it !== element } &&
                        element.select("a").sumOf { it.text().length } * 2 < element.text().length
                }
                .take(ReaderExtractionContract.MAX_BLOCKS)
                .map { element ->
                    val tag = element.normalName()
                    ReaderExtractionBlock(
                        kind =
                            when {
                                tag.startsWith("h") -> "heading"
                                tag == "li" -> "listitem"
                                tag == "blockquote" -> "quote"
                                else -> "paragraph"
                            },
                        text = element.text(),
                        level = tag.removePrefix("h").toIntOrNull() ?: 0,
                        links =
                            element.select("a[href]").take(40).mapNotNull { link ->
                                FeedRules.safeUrl(link.absUrl("href"))?.let {
                                    ReaderExtractionLink(link.text(), it)
                                }
                            },
                    )
                }
        val result =
            ReaderExtractionContract.sanitize(
                ReaderExtractionPayload(
                    title = article.title,
                    sourceUrl = article.url,
                    siteName = page.selectFirst("meta[property=og:site_name]")?.attr("content"),
                    blocks = blocks,
                )
            )
        return (result as? ReaderExtractionResult.Success)?.document?.takeUnless {
            FeedArticleContentRules.isTruncatedPublisherPreview(article, it)
        }
    }

    private fun readableScore(element: Element): Int =
        element.select("p, blockquote, li").sumOf { block ->
            (block.text().length - block.select("a").sumOf { it.text().length } * 2).coerceAtLeast(
                0
            )
        }
}
