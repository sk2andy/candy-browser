package dev.sk2andy.materialbrowser.browser

import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMediaPlaybackServiceInstrumentedTest {
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

    private companion object {
        const val FOREGROUND_SERVICE_DEADLINE_MILLIS = 5_500L
    }
}
