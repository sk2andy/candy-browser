package dev.sk2andy.materialbrowser.feeds

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.reader.ReaderSpeechModelRepository
import dev.sk2andy.materialbrowser.reader.ReaderSpeechStatus
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedMediaPlaybackServiceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = FeedPlaybackManager.get(context)
    private val profiles = BrowserSessionStore(context)
    private val profileId = "feed-audio-${UUID.randomUUID()}"
    private lateinit var savedProfiles: Pair<List<BrowserProfile>, String>
    private lateinit var repository: FeedRepository
    private lateinit var articleIds: List<String>

    @Before
    fun setUp() = runBlocking {
        savedProfiles = profiles.loadProfiles()
        profiles.saveProfiles(listOf(BrowserProfile(profileId, "🍬")), profileId)
        repository = FeedRepository.get(context, profileId)
        val body =
            "A long saved article keeps speaking while Candy is in the background. ".repeat(600)
        repository
            .subscribe(
                FeedParser.parse(
                    """<rss><channel><title>Audio test</title><link>https://example.org</link><item><title>First article</title><link>https://example.org/first</link><description>$body</description></item><item><title>Second article</title><link>https://example.org/second</link><description>$body</description></item></channel></rss>""",
                    "https://example.org/feed",
                )
            )
            .getOrThrow()
        repository.state.value.articles.forEach { article ->
            repository
                .updateOffline(article.id, FeedOfflineStatus.Ready, FeedRules.document(article))
                .getOrThrow()
        }
        val voice = ReaderSpeechModelRepository.get(context)
        await("Voice settings did not load") { voice.state.loaded }
        instrumentation.runOnMainSync { voice.chooseSystemVoice() }
        articleIds = repository.state.value.articles.map { it.id }
        repository.setQueue(articleIds).getOrThrow()
    }

    @After
    fun tearDown() = runBlocking {
        instrumentation.runOnMainSync { manager.invalidate() }
        FeedRepository.deleteProfile(context, profileId).getOrThrow()
        profiles.saveProfiles(savedProfiles.first, savedProfiles.second)
    }

    @Test
    fun speechSurvivesActivityBackgroundAndRealSystemControlsPauseResumeAndAdvance() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrumentation.runOnMainSync { manager.prepare(profileId) }
            await("Queue preview was not prepared") {
                manager.state.value.articleId == articleIds.first()
            }
            instrumentation.runOnMainSync { manager.play(profileId) }
            awaitSpeaking()
            await("Speech made no measurable progress") { manager.state.value.progress > 0f }
            scenario.moveToState(Lifecycle.State.CREATED)
            val before = manager.state.value.progress
            await("Speech stopped when Activity moved to background") {
                manager.state.value.status == ReaderSpeechStatus.Speaking &&
                    manager.state.value.progress > before
            }
            await("Foreground media notification missing") { hasForegroundMediaNotification() }
            assertTrue(isServiceForeground())

            val controller =
                MediaController.Builder(
                        context,
                        SessionToken(
                            context,
                            ComponentName(context, FeedMediaPlaybackService::class.java),
                        ),
                    )
                    .buildAsync()
                    .get(10, TimeUnit.SECONDS)
            try {
                instrumentation.runOnMainSync {
                    assertEquals(C.TIME_UNSET, controller.duration)
                    assertFalse(
                        controller.availableCommands.contains(
                            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
                        )
                    )
                    assertTrue(controller.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT))
                    controller.pause()
                }
                await("System Pause did not pause speech") {
                    manager.state.value.status == ReaderSpeechStatus.Paused
                }
                assertTrue(manager.state.value.isActive)
                instrumentation.runOnMainSync { controller.play() }
                awaitSpeaking()
                instrumentation.runOnMainSync { controller.seekToNext() }
                await("System Next did not advance queue") {
                    manager.state.value.articleId == articleIds[1]
                }
                awaitSpeaking()
                assertFalse(
                    repository.state.value.articles.first { it.id == articleIds.first() }.isRead
                )
                instrumentation.runOnMainSync { manager.invalidate(profileId) }
                await("Invalidation retained media metadata") {
                    manager.state.value.profileId == null && !hasForegroundMediaNotification()
                }
            } finally {
                instrumentation.runOnMainSync { controller.release() }
            }
        }
    }

    @Test
    fun stopImmediatelyFollowedByPlayKeepsNewPlaybackOwner() {
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.runOnMainSync { manager.prepare(profileId) }
            await("Queue preview missing") { manager.state.value.articleId != null }
            instrumentation.runOnMainSync { manager.play(profileId) }
            awaitSpeaking()
            instrumentation.runOnMainSync {
                manager.stop()
                manager.play(profileId)
            }
            awaitSpeaking()
            SystemClock.sleep(250)
            assertTrue(manager.state.value.isActive)
            assertEquals(ReaderSpeechStatus.Speaking, manager.state.value.status)
        }
    }

    private fun awaitSpeaking() {
        await("TTS unavailable or did not speak: ${manager.state.value}", timeoutMillis = 20_000) {
            manager.state.value.status in
                setOf(ReaderSpeechStatus.Speaking, ReaderSpeechStatus.Unavailable)
        }
        assertEquals(
            "Device needs an installed TTS engine and voice",
            ReaderSpeechStatus.Speaking,
            manager.state.value.status,
        )
    }

    private fun await(message: String, timeoutMillis: Long = 10_000, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        assertTrue("$message; state=${manager.state.value}", predicate())
    }

    private fun hasForegroundMediaNotification(): Boolean =
        context.getSystemService(NotificationManager::class.java).activeNotifications.any {
            it.id == FeedMediaPlaybackService.NOTIFICATION_ID &&
                it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
        }

    @Suppress("DEPRECATION")
    private fun isServiceForeground(): Boolean =
        context
            .getSystemService(ActivityManager::class.java)
            .getRunningServices(Int.MAX_VALUE)
            .any {
                it.service.className == FeedMediaPlaybackService::class.java.name && it.foreground
            }
}
