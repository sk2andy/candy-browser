package dev.sk2andy.materialbrowser.reader.rss

import android.content.Context
import android.content.Intent
import dev.sk2andy.materialbrowser.MainActivity

internal data class RssReaderLaunchRequest(val id: Int, val articleUrl: String? = null)

internal object RssReaderNavigation {
    const val ACTION_OPEN_FEEDS = "dev.sk2andy.materialbrowser.action.OPEN_READER_FEEDS"

    fun feedsIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_FEEDS)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
