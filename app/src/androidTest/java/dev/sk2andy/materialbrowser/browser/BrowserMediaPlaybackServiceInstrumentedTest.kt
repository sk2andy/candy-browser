package dev.sk2andy.materialbrowser.browser

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMediaPlaybackServiceInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rejectedStartStillMeetsForegroundServiceDeadline() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ContextCompat.startForegroundService(
                    activity,
                    Intent(activity, BrowserMediaPlaybackService::class.java)
                        .setAction("invalid-test-action"),
                )
            }
            SystemClock.sleep(FOREGROUND_SERVICE_DEADLINE_MILLIS)
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
        }
    }

    @Test
    fun newerAudioStartSurvivesQueuedStop() {
        val mediaSession = MediaSession(context, "browser-media-service-race")
        try {
            BrowserMediaPlaybackService.start(context, playingAudio("first"), mediaSession.sessionToken)
            BrowserMediaPlaybackService.stop(context)
            BrowserMediaPlaybackService.start(context, playingAudio("newer"), mediaSession.sessionToken)

            awaitServiceRunning()
            SystemClock.sleep(10_500L)
            assertTrue(isServiceRunning())
        } finally {
            context.stopService(Intent(context, BrowserMediaPlaybackService::class.java))
            mediaSession.release()
        }
    }

    private fun playingAudio(title: String) = BrowserMediaState(
        tabId = "audio-tab",
        title = title,
        origin = "media.example",
        kind = BrowserMediaKind.Audio,
        isPlaying = true,
        currentPositionMillis = 1_000L,
        durationMillis = 60_000L,
        playbackRate = 1f,
        sourceUrl = null,
        contentType = "audio/webm",
        posterUrl = null,
    )

    private fun awaitServiceRunning() {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (isServiceRunning()) return
            SystemClock.sleep(50L)
        }
        assertTrue(isServiceRunning())
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean =
        context.getSystemService(ActivityManager::class.java)
            .getRunningServices(Int.MAX_VALUE)
            .any { service ->
                service.service.className == BrowserMediaPlaybackService::class.java.name
            }

    private companion object {
        const val FOREGROUND_SERVICE_DEADLINE_MILLIS = 5_500L
    }
}
