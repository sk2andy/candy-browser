package dev.sk2andy.materialbrowser

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserWindowStateInstrumentedTest {
    @Test
    fun tabletTabOverviewPreservesLandscapeOrientation() {
        val configuration = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .resources
            .configuration
        assumeTrue(
            configuration.smallestScreenWidthDp >=
                BrowserWindowStateRules.LARGE_SCREEN_MIN_WIDTH_DP,
        )
        assumeTrue(configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setTabOverviewPortraitLocked(true)

                assertEquals(
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    activity.requestedOrientation,
                )
            }
        }
    }
}
