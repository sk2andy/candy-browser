package dev.sk2andy.materialbrowser

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.sk2andy.materialbrowser.browser.integration.ConstellationActivityContract
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ConstellationActivityBackInstrumentedTest {
    @Test
    fun systemBackReturnsFromConstellationsToBrowserActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val profileId = activity.browserControllerForTesting().activeProfileId
                activity.startActivity(ConstellationActivityContract.launchIntent(activity, profileId))
            }
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.constellations_title))), 10_000))
            device.pressBack()
            var resumed: Collection<Activity> = emptyList()
            instrumentation.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            }
            assertTrue("Back should return to the browser activity", resumed.any { it is MainActivity })
        }
    }
}
