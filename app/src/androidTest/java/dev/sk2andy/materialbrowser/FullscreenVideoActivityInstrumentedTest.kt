package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.webkit.WebChromeClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullscreenVideoActivityInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences by lazy {
        instrumentation.targetContext.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun videoOnlyPresentationKeepsFullscreenSourceWebViewAttached() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var sourceView: View
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                controller.createTab("https://example.test/")
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                sourceView = controller.selectedWebViewForTesting()
                assertTrue(sourceView.isAttachedToWindow)
                controller.showFullscreenVideoForTesting(
                    view = View(activity),
                    callback = WebChromeClient.CustomViewCallback {},
                )
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                activity.onPictureInPictureModeChanged(
                    true,
                    Configuration(activity.resources.configuration),
                )
            }
            instrumentation.waitForIdleSync()

            assertTrue(sourceView.isAttachedToWindow)
            scenario.onActivity { activity ->
                activity.onPictureInPictureModeChanged(
                    false,
                    Configuration(activity.resources.configuration),
                )
                activity.browserControllerForTesting().exitFullscreenVideo()
            }
        }
    }

    @Test
    fun webMediaPipKeepsExistingWebViewHost() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var sourceView: View
            lateinit var originalParent: Any
            val detachCount = AtomicInteger()
            val attachmentListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
                    detachCount.incrementAndGet()
                }
            }
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().createTab("https://media.example/")
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                sourceView = controller.selectedWebViewForTesting()
                originalParent = requireNotNull(sourceView.parent)
                sourceView.addOnAttachStateChangeListener(attachmentListener)
                controller.selectedWebViewForTesting().loadDataWithBaseURL(
                    "https://media.example/",
                    PLAYING_VIDEO_HTML,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            awaitCondition {
                var playing = false
                scenario.onActivity { activity ->
                    playing = activity.browserControllerForTesting().webMediaState?.isPlaying == true
                }
                playing
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.browserControllerForTesting().isPictureInPictureEligible)
                assertTrue(activity.isPictureInPictureEligibleForTesting())
            }

            scenario.onActivity { activity ->
                activity.prepareForPictureInPictureTransitionForTesting()
            }
            instrumentation.waitForIdleSync()
            assertSame(originalParent, sourceView.parent)
            assertTrue(sourceView.isAttachedToWindow)
            assertTrue(detachCount.get() == 0)

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().onStop(false)
            }
            instrumentation.waitForIdleSync()
            assertTrue(detachCount.get() == 0)

            scenario.onActivity { activity ->
                activity.onPictureInPictureModeChanged(
                    true,
                    Configuration(activity.resources.configuration),
                )
            }
            instrumentation.waitForIdleSync()

            assertSame(originalParent, sourceView.parent)
            assertTrue(sourceView.isAttachedToWindow)
            assertTrue(detachCount.get() == 0)
            scenario.onActivity { activity ->
                assertTrue(activity.browserControllerForTesting().fullscreenVideoState != null)
                activity.onPictureInPictureModeChanged(
                    false,
                    Configuration(activity.resources.configuration),
                )
                activity.browserControllerForTesting().exitFullscreenVideo()
                sourceView.removeOnAttachStateChangeListener(attachmentListener)
            }
        }
    }

    private fun awaitCondition(
        timeoutMillis: Long = 5_000,
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

    private companion object {
        val PLAYING_VIDEO_HTML =
            """
            <!doctype html>
            <html><body>
              <video style="width:320px;height:180px"></video>
              <script>
                const video = document.querySelector('video');
                Object.defineProperty(video, 'paused', { value: false, configurable: true });
                Object.defineProperty(video, 'ended', { value: false, configurable: true });
                Object.defineProperty(video, 'currentTime', { value: 12, configurable: true });
                Object.defineProperty(video, 'duration', { value: 120, configurable: true });
                Object.defineProperty(video, 'videoWidth', { value: 640, configurable: true });
                Object.defineProperty(video, 'videoHeight', { value: 360, configurable: true });
                Object.defineProperty(window, 'innerWidth', { value: 1080, configurable: true });
                Object.defineProperty(window, 'innerHeight', { value: 1920, configurable: true });
                video.getBoundingClientRect = () => ({
                  left: 0, top: 0, right: 320, bottom: 180, width: 320, height: 180
                });
                setTimeout(() => video.dispatchEvent(new Event('playing')), 250);
              </script>
            </body></html>
            """.trimIndent()
    }
}
