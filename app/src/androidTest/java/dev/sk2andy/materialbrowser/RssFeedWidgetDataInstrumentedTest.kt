package dev.sk2andy.materialbrowser

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.ContextWrapper
import android.widget.ListView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.browser.ProfileProtectionSession
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleRules
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.feeds.FeedArticle
import dev.sk2andy.materialbrowser.feeds.FeedPreview
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedRules
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssFeedWidgetDataInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val profiles = BrowserSessionStore(context)
    private val firstId = "widget-first-${UUID.randomUUID()}"
    private val secondId = "widget-second-${UUID.randomUUID()}"
    private lateinit var savedProfiles: Pair<List<BrowserProfile>, String>

    @Before
    fun setUp() {
        savedProfiles = profiles.loadProfiles()
        profiles.saveProfiles(listOf(BrowserProfile(firstId, "🍬"), BrowserProfile(secondId, "🍭")), firstId)
    }

    @After
    fun tearDown() = runBlocking {
        FeedRepository.deleteProfile(context, firstId).getOrThrow()
        FeedRepository.deleteProfile(context, secondId).getOrThrow()
        ProfileProtectionSession.forget(firstId)
        profiles.saveProfiles(savedProfiles.first, savedProfiles.second)
    }

    @Test
    fun newSubscriptionAppearsFromProfileRepositoryAndDisappearsWhenHidden() = runBlocking {
        val repository = FeedRepository.get(context, firstId)
        repository.subscribe(preview("Current profile article")).getOrThrow()
        FeedRepository.get(context, secondId).subscribe(preview("Other profile article")).getOrThrow()

        assertWidget(count = 1, title = "Current profile article")

        repository.setHidden(repository.state.value.articles.single().id, true).getOrThrow()
        assertWidget(count = 0)

        profiles.saveProfiles(profiles.loadProfiles().first, secondId)
        assertWidget(count = 1, title = "Other profile article")
    }

    @Test
    fun protectedProfilesNeverPublishLauncherTitlesEvenWhileUnlocked() = runBlocking {
        FeedRepository.get(context, firstId).subscribe(preview("Protected article")).getOrThrow()
        profiles.saveProfiles(
            listOf(BrowserProfile(firstId, "🍬", protection = ProfileProtection(ProfileLockTrigger.AppClosed))),
            firstId,
        )
        ProfileProtectionSession.unlock(firstId)

        assertWidget(count = 0)
    }

    @Test
    fun privateSubscriptionsCannotEnterTheWidget() = runBlocking {
        val privateRepository = FeedRepository.get(context, firstId, isPrivate = true)
        assertTrue(privateRepository.subscribe(preview("Private article")).isFailure)

        assertWidget(count = 0)
    }

    @Test
    fun guestLockHidesOwnerFeedAndCleanupRestoresIt() = runBlocking {
        FeedRepository.get(context, firstId).subscribe(preview("Owner article")).getOrThrow()
        val sandbox = File(context.cacheDir, "rss-widget-guest-${UUID.randomUUID()}").apply { mkdirs() }
        val guestContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = sandbox
        }
        try {
            assertWidget(count = 1, title = "Owner article", widgetContext = guestContext)
            val marker = File(sandbox, "${GuestBubbleRules.SESSION_DIRECTORY_NAME}/session")
            marker.parentFile?.mkdirs()
            // Corrupt or interrupted markers must also fail closed.
            marker.writeText("interrupted guest session")
            assertWidget(count = 0, widgetContext = guestContext)

            assertTrue(marker.delete())
            assertWidget(count = 1, title = "Owner article", widgetContext = guestContext)
        } finally {
            sandbox.deleteRecursively()
        }
    }

    private suspend fun assertWidget(count: Int, title: String? = null, widgetContext: Context = context) {
        val views = RssFeedWidgetProvider.loadViews(widgetContext, widgetId = 502)
        instrumentation.runOnMainSync {
            val host = AppWidgetHostView(context)
            host.addView(views.apply(context, host))
            val articles = host.findViewById<ListView>(R.id.rss_widget_articles)
            assertEquals(count, articles.count)
            if (title != null) {
                val row = articles.adapter.getView(0, null, articles)
                assertEquals(title, row.findViewById<TextView>(R.id.rss_widget_article).text.toString())
            }
        }
    }

    private fun preview(title: String) = FeedPreview(
        url = "https://example.com/feed.xml",
        title = "Example feed",
        siteUrl = "https://example.com",
        articles = listOf(FeedArticle(
            id = FeedRules.identity("https://example.com/article"),
            sourceIds = emptySet(),
            url = "https://example.com/article",
            title = title,
            publishedAtMillis = 1L,
        )),
    )
}
