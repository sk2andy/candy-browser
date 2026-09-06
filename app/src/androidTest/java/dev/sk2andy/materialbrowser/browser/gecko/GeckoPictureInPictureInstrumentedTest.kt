package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.FullscreenVideoSource
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoPictureInPictureInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun selectedRegularFullscreenGeckoVideoSurvivesPictureInPictureLifecycle() {
        lateinit var stableGeckoHost: View
        lateinit var initialTextureEngineView: View
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.usesGeckoEngine)
                assertTrue(controller.openUrl("https://media.example/"))
            }
            awaitCondition {
                var attached = false
                scenario.onActivity { activity ->
                    attached = activity.browserControllerForTesting()
                        .selectedGeckoViewForTesting()
                        ?.isAttachedToWindow == true
                }
                attached
            }
            scenario.onActivity { activity ->
                stableGeckoHost = requireNotNull(
                    activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                )
                initialTextureEngineView = requireNotNull(
                    (stableGeckoHost as ViewGroup).singleChild(),
                )
                assertNotNull(
                    initialTextureEngineView.findDescendant(TextureView::class.java),
                )
                activity.browserControllerForTesting().reportSelectedGeckoMediaStateForTesting(
                    eligibleMediaState(),
                )
            }
            awaitCondition {
                var eligible = false
                scenario.onActivity { activity ->
                    eligible = activity.isPictureInPictureEligibleForTesting()
                }
                eligible
            }
            scenario.onActivity { activity ->
                activity.prepareForPictureInPictureTransitionForTesting()
                val state = activity.browserControllerForTesting().fullscreenVideoState
                assertNotNull(state)
                assertEquals(FullscreenVideoSource.GeckoView, state?.source)
                val surfaceEngineView = requireNotNull(
                    (stableGeckoHost as ViewGroup).singleChild(),
                )
                assertSame(
                    stableGeckoHost,
                    activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                )
                assertNotSame(initialTextureEngineView, surfaceEngineView)
                assertNotNull(surfaceEngineView.findDescendant(SurfaceView::class.java))
                activity.browserControllerForTesting().onStop(
                    isInPictureInPictureMode = false,
                )
            }
            awaitCondition {
                var surfaceReady = false
                scenario.onActivity { activity ->
                    val host = activity.browserControllerForTesting()
                        .selectedGeckoViewForTesting() as? ViewGroup
                    val surface = host?.singleChild()?.findDescendant(SurfaceView::class.java)
                    surfaceReady = surface?.let { view ->
                        view.isAttachedToWindow && view.width > 0 && view.height > 0
                    } == true
                }
                surfaceReady
            }
            // A loaded emulator can deliver the platform mode callback after the former 2 s guard.
            SystemClock.sleep(2_500L)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertNotNull(activity.browserControllerForTesting().fullscreenVideoState)
                activity.onPictureInPictureModeChanged(
                    true,
                    Configuration(activity.resources.configuration),
                )
                assertNotNull(activity.browserControllerForTesting().fullscreenVideoState)
                activity.onPictureInPictureModeChanged(
                    false,
                    Configuration(activity.resources.configuration),
                )
                val restoredEngineView = requireNotNull(
                    (stableGeckoHost as ViewGroup).singleChild(),
                )
                assertSame(
                    stableGeckoHost,
                    activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                )
                assertNotSame(initialTextureEngineView, restoredEngineView)
                assertNotNull(restoredEngineView.findDescendant(TextureView::class.java))
                activity.browserControllerForTesting().exitFullscreenVideo()
            }
        }
    }

    @Test
    fun privateGeckoVideoCannotEnterPictureInPicture() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.setBlankTabIncognito(true))
                controller.reportSelectedGeckoMediaStateForTesting(eligibleMediaState())
                assertFalse(controller.isPictureInPictureEligible)
                assertFalse(activity.isPictureInPictureEligibleForTesting())
            }
        }
    }

    private fun awaitCondition(
        timeoutMillis: Long = 30_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Condition not met within ${timeoutMillis}ms", condition())
    }

    private fun eligibleMediaState() = GeckoMediaSessionState(
        isActive = true,
        isPlaying = true,
        isFullscreen = true,
        currentPositionMillis = 1_000,
        durationMillis = 60_000,
        videoWidth = 1_280,
        videoHeight = 720,
        videoTrackCount = 1,
    )

    private fun ViewGroup.singleChild(): View? =
        takeIf { childCount == 1 }?.getChildAt(0)

    private fun <T : View> View.findDescendant(type: Class<T>): T? {
        if (type.isInstance(this)) return type.cast(this)
        if (this !is ViewGroup) return null
        repeat(childCount) { index ->
            getChildAt(index).findDescendant(type)?.let { descendant -> return descendant }
        }
        return null
    }
}
