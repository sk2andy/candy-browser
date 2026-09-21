package dev.sk2andy.materialbrowser

import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class MainActivityPictureInPictureControllerInstrumentedTest {
    @Test
    fun returnCoverPrecedesExpandedLayoutWaitWithoutBlockingCompletion() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val events = mutableListOf<String>()
                var videoOnly = true
                var coverVisible = false
                val controller = MainActivityPictureInPictureController(
                    activity = activity,
                    browserController = activity.browserControllerForTesting(),
                    isVideoOnlyPresentation = { videoOnly },
                    setVideoOnlyPresentation = { videoOnly = it },
                    setReturnRestorationPending = {
                        coverVisible = it
                        events += "cover:$it"
                    },
                    applyBrowserSystemUi = { events += "system-ui" },
                )
                try {
                    val delayedLayout = Configuration(activity.resources.configuration).apply {
                        screenWidthDp += 1_000
                        screenHeightDp += 1_000
                    }
                    controller.onModeChanged(false, delayedLayout)

                    assertEquals(listOf("cover:true", "system-ui"), events)
                    assertTrue(coverVisible)
                    assertTrue(videoOnly)

                    // Publishing the cover must not mark restoration as already started.
                    // Once decor size is ready, the normal completion path must still run.
                    val expandedLayout = Configuration(activity.resources.configuration).apply {
                        screenWidthDp = 1
                        screenHeightDp = 1
                    }
                    controller.onModeChanged(false, expandedLayout)
                    assertFalse(videoOnly)
                    assertFalse(coverVisible)
                } finally {
                    controller.onDestroy()
                }
            }
        }
    }
}
