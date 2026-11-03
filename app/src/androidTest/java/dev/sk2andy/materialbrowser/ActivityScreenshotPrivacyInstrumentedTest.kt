package dev.sk2andy.materialbrowser

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 32)
class ActivityScreenshotPrivacyInstrumentedTest {
    @Test
    fun protectedRecentsPreserveExistingSecureFlags() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val secure = WindowManager.LayoutParams.FLAG_SECURE
                activity.window.clearFlags(secure)
                activity.setCandyRecentsScreenshotEnabled(false)
                activity.setCandyRecentsScreenshotEnabled(false)
                assertEquals(secure, activity.window.attributes.flags and secure)
                activity.setCandyRecentsScreenshotEnabled(true)
                assertEquals(0, activity.window.attributes.flags and secure)

                activity.window.addFlags(secure)
                activity.setCandyRecentsScreenshotEnabled(false)
                activity.setCandyRecentsScreenshotEnabled(true)
                assertEquals(secure, activity.window.attributes.flags and secure)
                activity.window.clearFlags(secure)
            }
        }
    }
}
