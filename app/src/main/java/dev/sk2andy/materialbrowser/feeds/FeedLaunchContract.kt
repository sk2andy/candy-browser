package dev.sk2andy.materialbrowser.feeds

import android.content.Context
import android.content.Intent
import dev.sk2andy.materialbrowser.MainActivity

object FeedLaunchContract {
    const val ACTION_OPEN = "dev.sk2andy.materialbrowser.action.OPEN_FEEDS"
    const val EXTRA_CATALOG = "feeds_open_catalog"
    const val EXTRA_QUEUE = "feeds_open_queue"
    const val EXTRA_PROFILE = "feeds_profile_id"
    const val EXTRA_ARTICLE = "feeds_article_id"

    fun discoverIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            .putExtra(EXTRA_CATALOG, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
