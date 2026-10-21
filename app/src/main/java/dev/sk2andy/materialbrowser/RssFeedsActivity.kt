package dev.sk2andy.materialbrowser

import android.content.Intent
import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleGate
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.RssFeedStore
import dev.sk2andy.materialbrowser.reader.rss.RssFeed
import dev.sk2andy.materialbrowser.reader.rss.RssFeedNetwork
import dev.sk2andy.materialbrowser.reader.rss.RssFeedNotifier
import dev.sk2andy.materialbrowser.reader.rss.RssRefreshScheduler
import dev.sk2andy.materialbrowser.ui.RssFeedsScreen
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import dev.sk2andy.materialbrowser.ui.theme.setCandyContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class RssFeedsActivity : ComponentActivity() {
    private val store by lazy { RssFeedStore(this) }
    private var feeds by mutableStateOf<List<RssFeed>>(emptyList())
    private var pageUrl by mutableStateOf("")
    private var candidates by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var isWorking by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")
    private var statusMessage by mutableStateOf("")
    private var pendingNotificationFeedId: String? = null
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val feedId = pendingNotificationFeedId
        pendingNotificationFeedId = null
        if (granted && feedId != null) {
            persistNotifications(feedId, enabled = true)
        } else if (!granted) {
            statusMessage = getString(R.string.rss_notifications_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (GuestBubbleGate.redirectIfLocked(this) || AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        pageUrl = intent.getStringExtra(EXTRA_PAGE_URL).orEmpty()
        if (intent.getBooleanExtra(EXTRA_PRIVATE, false)) {
            finish()
            return
        }
        feeds = store.feeds()
        RssRefreshScheduler.schedule(this)
        RssRefreshScheduler.refreshNow(this)
        val appearance = dev.sk2andy.materialbrowser.data.BrowserSessionStore(this)
            .loadAppearanceSettings()
        setCandyContent(animationsEnabled = appearance.animationsEnabled) {
            CandyTheme(settings = appearance) {
                RssFeedsScreen(
                    pageUrl = pageUrl,
                    candidates = candidates,
                    feeds = feeds,
                    isWorking = isWorking,
                    errorMessage = errorMessage,
                    statusMessage = statusMessage,
                    onBack = ::finish,
                    onDiscover = ::discover,
                    onSubscribe = ::subscribe,
                    onRemove = ::removeFeed,
                    onNotificationsChanged = ::setNotifications,
                    onOpenArticle = ::openArticle,
                )
            }
        }
        if (pageUrl.isNotBlank()) discover()
    }

    private fun discover() {
        if (isWorking || pageUrl.isBlank()) return
        isWorking = true
        errorMessage = ""
        lifecycleScope.launch {
            candidates = withContext(Dispatchers.IO) { RssFeedNetwork.discover(pageUrl) }
            isWorking = false
            if (candidates.isEmpty()) errorMessage = getString(R.string.rss_no_feed_found)
        }
    }

    private fun subscribe(feedUrl: String) {
        if (isWorking) return
        isWorking = true
        errorMessage = ""
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.IO) { RssFeedNetwork.fetch(feedUrl) }
            if (parsed == null) {
                isWorking = false
                errorMessage = getString(R.string.rss_load_failed)
                return@launch
            }
            val safeFeedUrl = dev.sk2andy.materialbrowser.reader.rss.RssFeedRules
                .safeHttpUrl(feedUrl) ?: run {
                isWorking = false
                errorMessage = getString(R.string.rss_load_failed)
                return@launch
            }
            val feed = RssFeed(
                id = UUID.randomUUID().toString(),
                title = parsed.title,
                feedUrl = safeFeedUrl,
                siteUrl = pageUrl,
                articles = parsed.articles,
                lastUpdatedAt = System.currentTimeMillis(),
            )
            val added = withContext(Dispatchers.IO) { store.add(feed) }
            isWorking = false
            if (added) {
                statusMessage = getString(R.string.rss_subscribed, feed.title)
                feeds = store.feeds()
                candidates = candidates.filterNot { it.first == feedUrl }
                RssRefreshScheduler.schedule(this@RssFeedsActivity)
                RssFeedWidgetProvider.updateAll(this@RssFeedsActivity)
            } else {
                statusMessage = getString(R.string.rss_already_subscribed)
            }
        }
    }

    private fun removeFeed(feedId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            store.remove(feedId)
            withContext(Dispatchers.Main) {
                feeds = store.feeds()
                RssRefreshScheduler.schedule(this@RssFeedsActivity)
                RssFeedWidgetProvider.updateAll(this@RssFeedsActivity)
            }
        }
    }

    private fun setNotifications(feedId: String, enabled: Boolean) {
        if (
            enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotificationFeedId = feedId
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        persistNotifications(feedId, enabled)
    }

    private fun persistNotifications(feedId: String, enabled: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            store.setNotifications(feedId, enabled)
            withContext(Dispatchers.Main) {
                feeds = store.feeds()
                if (!enabled) RssFeedNotifier(this@RssFeedsActivity).cancelFeed(feedId)
            }
        }
    }

    private fun openArticle(url: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(RssFeedNotifier.ACTION_OPEN_ARTICLE)
                .setData(Uri.parse(url))
                .putExtra(RssFeedNotifier.EXTRA_ARTICLE_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    companion object {
        const val EXTRA_PAGE_URL = "rss_page_url"
        const val EXTRA_PRIVATE = "rss_private_source"

        fun intent(context: android.content.Context, pageUrl: String, isPrivate: Boolean) =
            Intent(context, RssFeedsActivity::class.java)
                .putExtra(EXTRA_PAGE_URL, pageUrl)
                .putExtra(EXTRA_PRIVATE, isPrivate)
    }
}
