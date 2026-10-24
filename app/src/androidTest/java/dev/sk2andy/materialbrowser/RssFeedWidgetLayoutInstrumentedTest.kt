package dev.sk2andy.materialbrowser

import android.appwidget.AppWidgetHostView
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.feeds.FeedArticle
import dev.sk2andy.materialbrowser.feeds.FeedSource
import dev.sk2andy.materialbrowser.feeds.FeedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class RssFeedWidgetLayoutInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun resizingShowsMoreArticlesWithoutStretchingTheExistingRows() {
        instrumentation.runOnMainSync {
            val host = createWidget(feed())
            layout(host, heightDp = 240)
            val articles = host.findViewById<ListView>(R.id.rss_widget_articles)
            val compactCount = fullyVisibleRows(articles)
            val compactRowHeight = articles.getChildAt(0).height
            assertTrue(compactCount > 0)

            layout(host, heightDp = 480)

            assertTrue(fullyVisibleRows(articles) > compactCount)
            assertEquals(compactRowHeight, articles.getChildAt(0).height)
            assertEquals(12, articles.count)

            layout(host, heightDp = 240)

            assertEquals(compactCount, fullyVisibleRows(articles))
        }
    }

    @Test
    fun articleListScrollsPastTheOriginalThreeEntries() {
        instrumentation.runOnMainSync {
            val host = createWidget(feed())
            layout(host, heightDp = 240)
            val articles = host.findViewById<ListView>(R.id.rss_widget_articles)
            assertEquals(12, articles.count)
            assertEquals("Article 12", articleTitle(articles.getChildAt(0)))
            assertTrue(articles.canScrollVertically(1))

            repeat(12) { articles.scrollListBy(articles.height) }

            assertTrue(articles.firstVisiblePosition > 2)
            assertEquals(11, articles.lastVisiblePosition)
            assertEquals("Article 1", articleTitle(articles.getChildAt(articles.childCount - 1)))
            assertFalse(articles.canScrollVertically(1))
            assertTrue(articles.canScrollVertically(-1))
            assertNotNull(articles.onItemClickListener)
        }
    }

    @Test
    fun recentLimitIncludesNewestArticlesAcrossAllFeedsAndMatchesTheBadge() {
        instrumentation.runOnMainSync {
            val olderFeed = feed(1..40, sourceId = "older-feed", sourceTitle = "Older source")
            val newerFeed = feed(41..80, sourceId = "newer-feed", sourceTitle = "Newer source")
            val host = createWidget(FeedState(
                sources = olderFeed.sources + newerFeed.sources,
                articles = olderFeed.articles + newerFeed.articles,
            ))
            layout(host, heightDp = 240)
            val articles = host.findViewById<ListView>(R.id.rss_widget_articles)

            assertEquals(50, articles.count)
            assertEquals(
                context.getString(R.string.rss_widget_count, 50),
                host.findViewById<TextView>(R.id.rss_widget_count).text.toString(),
            )
            assertEquals("Article 80", articleTitle(articles.getChildAt(0)))
            assertEquals(
                "Newer source",
                articles.getChildAt(0).findViewById<TextView>(R.id.rss_widget_feed).text.toString(),
            )

            repeat(50) { articles.scrollListBy(articles.height) }

            assertEquals(49, articles.lastVisiblePosition)
            val lastRow = articles.getChildAt(articles.childCount - 1)
            assertEquals("Article 31", articleTitle(lastRow))
            assertEquals(
                "Older source",
                lastRow.findViewById<TextView>(R.id.rss_widget_feed).text.toString(),
            )
        }
    }

    @Test
    fun emptyFeedShowsTheEmptyStateAndReapplyRestoresTheList() {
        instrumentation.runOnMainSync {
            val host = createWidget(FeedState())
            layout(host, heightDp = 240)
            val articles = host.findViewById<ListView>(R.id.rss_widget_articles)
            val empty = host.findViewById<TextView>(R.id.rss_widget_empty)
            assertEquals(View.VISIBLE, empty.visibility)
            assertEquals(View.GONE, articles.visibility)
            assertEquals(0, articles.count)

            RssFeedWidgetProvider.createViews(context, feed(), widgetId = 501)
                .reapply(context, host.getChildAt(0))
            layout(host, heightDp = 240)

            assertEquals(View.GONE, empty.visibility)
            assertEquals(View.VISIBLE, articles.visibility)
            assertEquals(12, articles.count)
            assertTrue(host.findViewById<View>(R.id.rss_widget_all_feeds).hasOnClickListeners())

            RssFeedWidgetProvider.createViews(context, FeedState(), widgetId = 501)
                .reapply(context, host.getChildAt(0))
            layout(host, heightDp = 240)

            assertEquals(View.VISIBLE, empty.visibility)
            assertEquals(View.GONE, articles.visibility)
            assertEquals(0, articles.count)
        }
    }

    private fun createWidget(state: FeedState): AppWidgetHostView =
        AppWidgetHostView(context).apply {
            addView(RssFeedWidgetProvider.createViews(context, state, widgetId = 501).apply(context, this))
        }

    private fun layout(host: AppWidgetHostView, heightDp: Int) {
        val density = context.resources.displayMetrics.density
        val width = (320 * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        host.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, width, height)
    }

    private fun fullyVisibleRows(articles: ListView): Int =
        (0 until articles.childCount).count { index ->
            val row = articles.getChildAt(index)
            row.top >= articles.paddingTop && row.bottom <= articles.height - articles.paddingBottom
        }

    private fun articleTitle(row: View): String =
        row.findViewById<TextView>(R.id.rss_widget_article).text.toString()

    private fun feed(
        articleNumbers: IntRange = 1..12,
        sourceId: String = "widget-feed",
        sourceTitle: String = "Example feed",
    ): FeedState = FeedState(
        sources = listOf(FeedSource(sourceId, "https://example.com/feed.xml", sourceTitle)),
        articles = articleNumbers.map { index ->
            FeedArticle(
                id = "article-$index",
                sourceIds = setOf(sourceId),
                title = "Article $index",
                url = "https://example.com/articles/$index",
                publishedAtMillis = index.toLong(),
            )
        },
    )
}
