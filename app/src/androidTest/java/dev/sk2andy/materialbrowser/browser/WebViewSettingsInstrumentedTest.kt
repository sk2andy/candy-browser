package dev.sk2andy.materialbrowser.browser

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewSettingsInstrumentedTest {
    @Test
    fun enablesNativePinchZoomWithoutLegacyButtons() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            WebView(instrumentation.targetContext).apply {
                settings.enablePinchZoom()

                assertTrue(settings.supportZoom())
                assertTrue(settings.builtInZoomControls)
                assertFalse(settings.displayZoomControls)

                destroy()
            }
        }
    }

    @Test
    fun togglesMediaGestureRequirementForForegroundAndBackgroundTabs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            WebView(instrumentation.targetContext).apply {
                settings.requireMediaPlaybackGesture()
                assertTrue(settings.mediaPlaybackRequiresUserGesture)

                settings.allowContinuousMediaPlayback()
                assertFalse(settings.mediaPlaybackRequiresUserGesture)

                settings.requireMediaPlaybackGesture()
                assertTrue(settings.mediaPlaybackRequiresUserGesture)

                destroy()
            }
        }
    }
}
