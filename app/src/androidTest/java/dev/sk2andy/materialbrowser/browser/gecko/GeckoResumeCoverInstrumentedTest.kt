package dev.sk2andy.materialbrowser.browser.gecko

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoResult

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoResumeCoverInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun surfaceRecreationKeepsDepartingFrameUntilContentIsPresented() {
        val ready = AtomicBoolean(false)
        val captures = AtomicInteger()
        lateinit var host: FrameLayout
        lateinit var surface: SurfaceView
        lateinit var cover: GeckoResumeCover
        lateinit var image: ImageView
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                host = FrameLayout(activity).apply { setBackgroundColor(Color.MAGENTA) }
                surface = SurfaceView(activity)
                host.addView(surface, matchParentLayoutParams())
                cover = GeckoResumeCover(host, surface) {
                    captures.incrementAndGet()
                    GeckoResult.fromValue(greenFrame(host))
                }.apply {
                    isContentPresented = ready::get
                    onSurfaceCreated = { ready.set(false) }
                    onSurfaceDestroyed = { ready.set(false) }
                }
                image = host.getChildAt(1) as ImageView
                activity.setContentView(host)
            }
            try {
                awaitCondition("Initial surface did not become available") {
                    surface.holder.surface.isValid
                }
                scenario.onActivity {
                    ready.set(true)
                    cover.onContentPresented()
                }
                awaitCondition("Presented content did not allow departure capture") {
                    if (captures.get() == 0) cover.captureBeforeBackground()
                    captures.get() == 1
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { surface.visibility = View.INVISIBLE }
                awaitCondition("Surface loss did not show the captured frame") {
                    !surface.holder.surface.isValid && image.visibility == View.VISIBLE
                }
                scenario.onActivity { cover.onPaintStatusReset() }
                assertGreenCover(scenario)

                scenario.onActivity { surface.visibility = View.VISIBLE }
                awaitCondition("Replacement surface did not become available") {
                    surface.holder.surface.isValid
                }
                val falseReadyProcessed = CountDownLatch(1)
                scenario.onActivity {
                    cover.onContentPresented()
                    host.postOnAnimation { falseReadyProcessed.countDown() }
                }
                assertTrue(
                    "False-ready callback was not processed",
                    falseReadyProcessed.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                )
                assertGreenCover(scenario)

                scenario.onActivity {
                    ready.set(true)
                    cover.onContentPresented()
                }
                awaitCondition("Valid presentation did not remove the cover") {
                    image.visibility == View.GONE && image.drawable == null
                }
            } finally {
                scenario.onActivity { cover.release() }
            }
        }
    }

    @Test
    fun navigationRejectsLateDepartureCapture() {
        assertLateCaptureDoesNotRestoreCover(GeckoResumeCover::onNavigationStarted)
    }

    @Test
    fun releaseRejectsLateDepartureCapture() {
        assertLateCaptureDoesNotRestoreCover(GeckoResumeCover::release)
    }

    @Test
    fun navigationInvalidatesQueuedPresentation() {
        val captures = AtomicInteger()
        lateinit var host: FrameLayout
        lateinit var surface: SurfaceView
        lateinit var cover: GeckoResumeCover
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                host = FrameLayout(activity)
                surface = SurfaceView(activity)
                host.addView(surface, matchParentLayoutParams())
                cover = GeckoResumeCover(host, surface) {
                    captures.incrementAndGet()
                    GeckoResult.fromValue(greenFrame(host))
                }.apply {
                    isContentPresented = { true }
                }
                activity.setContentView(host)
            }
            try {
                awaitCondition("Initial surface did not become available") {
                    surface.holder.surface.isValid
                }
                val queuedPresentationProcessed = CountDownLatch(1)
                scenario.onActivity {
                    cover.onContentPresented()
                    cover.onNavigationStarted()
                    host.postOnAnimation { queuedPresentationProcessed.countDown() }
                }
                assertTrue(
                    "Queued presentation was not processed",
                    queuedPresentationProcessed.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                )
                scenario.onActivity {
                    cover.captureBeforeBackground()
                    assertEquals("Previous document must not become capture-ready again", 0, captures.get())
                }
            } finally {
                scenario.onActivity { cover.release() }
            }
        }
    }

    @Test
    fun viewportResizeRemovesVisibleDepartureFrame() {
        withPendingDepartureCapture { fixture ->
            fixture.scenario.onActivity {
                fixture.result.complete(greenFrame(fixture.host))
            }
            instrumentation.waitForIdleSync()
            fixture.scenario.onActivity { fixture.surface.visibility = View.INVISIBLE }
            awaitCondition("Surface loss did not show the captured frame") {
                fixture.image.visibility == View.VISIBLE
            }

            fixture.scenario.onActivity {
                fixture.host.layoutParams = FrameLayout.LayoutParams(320, 640)
            }
            awaitCondition("Viewport resize did not discard the captured frame") {
                fixture.host.height == 640 && fixture.image.visibility == View.GONE &&
                    fixture.image.drawable == null
            }
        }
    }

    @Test
    fun viewportResizeAwayAndBackRejectsPendingDepartureCapture() {
        withPendingDepartureCapture { fixture ->
            lateinit var bitmap: Bitmap
            fixture.scenario.onActivity {
                bitmap = greenFrame(fixture.host)
                fixture.host.layoutParams = FrameLayout.LayoutParams(320, 640)
            }
            awaitCondition("Viewport did not resize") { fixture.host.height == 640 }
            fixture.scenario.onActivity {
                fixture.host.layoutParams = FrameLayout.LayoutParams(320, 480)
            }
            awaitCondition("Viewport did not return to its captured dimensions") {
                fixture.host.height == 480
            }
            fixture.scenario.onActivity {
                fixture.surface.visibility = View.INVISIBLE
                fixture.result.complete(bitmap)
            }
            awaitCondition("Capture from an earlier viewport was not recycled") { bitmap.isRecycled }
            fixture.scenario.onActivity {
                assertEquals(View.GONE, fixture.image.visibility)
                assertEquals(null, fixture.image.drawable)
            }
        }
    }

    @Test
    fun mismatchingCaptureDimensionsCannotCoverViewport() {
        withPendingDepartureCapture { fixture ->
            val bitmap = Bitmap.createBitmap(320, 479, Bitmap.Config.ARGB_8888)
            fixture.scenario.onActivity {
                fixture.surface.visibility = View.INVISIBLE
                fixture.result.complete(bitmap)
            }
            awaitCondition("Wrong-size capture was not rejected and recycled") { bitmap.isRecycled }
            fixture.scenario.onActivity {
                assertEquals(View.GONE, fixture.image.visibility)
                assertEquals(null, fixture.image.drawable)
            }
        }
    }

    @Test
    fun departureFrameKeepsPixelGeometryDespiteBitmapDensity() {
        withPendingDepartureCapture { fixture ->
            fixture.scenario.onActivity {
                val bitmap = greenFrame(fixture.host).apply {
                    density = 640
                    for (y in 60 until 120) {
                        for (x in 40 until 80) setPixel(x, y, Color.YELLOW)
                    }
                }
                fixture.result.complete(bitmap)
            }
            instrumentation.waitForIdleSync()
            fixture.scenario.onActivity { fixture.surface.visibility = View.INVISIBLE }
            awaitCondition("Surface loss did not show the captured frame") {
                fixture.image.visibility == View.VISIBLE && fixture.image.width == 320 &&
                    fixture.image.height == 480
            }
            fixture.scenario.onActivity {
                val rendered = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888).apply {
                    density = Bitmap.DENSITY_NONE
                }
                try {
                    fixture.image.draw(Canvas(rendered))
                    assertEquals(Color.YELLOW, rendered.getPixel(40, 60))
                    assertEquals(Color.YELLOW, rendered.getPixel(79, 119))
                    assertEquals(PAGE_COLOR, rendered.getPixel(39, 60))
                    assertEquals(PAGE_COLOR, rendered.getPixel(80, 119))
                    assertEquals(PAGE_COLOR, rendered.getPixel(40, 59))
                    assertEquals(PAGE_COLOR, rendered.getPixel(79, 120))
                } finally {
                    rendered.recycle()
                }
            }
        }
    }

    private fun withPendingDepartureCapture(test: (CoverFixture) -> Unit) {
        val captures = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var fixture: CoverFixture
            scenario.onActivity { activity ->
                val host = FrameLayout(activity)
                val surface = SurfaceView(activity)
                val result = GeckoResult<Bitmap>()
                host.addView(surface, matchParentLayoutParams())
                val cover = GeckoResumeCover(host, surface) {
                    captures.incrementAndGet()
                    result
                }.apply { isContentPresented = { true } }
                fixture = CoverFixture(
                    scenario,
                    host,
                    surface,
                    cover,
                    host.getChildAt(1) as ImageView,
                    result,
                )
                activity.setContentView(host, FrameLayout.LayoutParams(320, 480))
            }
            try {
                awaitCondition("Initial surface did not become available") {
                    fixture.host.width == 320 && fixture.host.height == 480 &&
                        fixture.surface.holder.surface.isValid
                }
                scenario.onActivity { fixture.cover.onContentPresented() }
                awaitCondition("Presented content did not allow departure capture") {
                    if (captures.get() == 0) fixture.cover.captureBeforeBackground()
                    captures.get() == 1
                }
                test(fixture)
            } finally {
                scenario.onActivity { fixture.cover.release() }
            }
        }
    }

    private data class CoverFixture(
        val scenario: ActivityScenario<MainActivity>,
        val host: FrameLayout,
        val surface: SurfaceView,
        val cover: GeckoResumeCover,
        val image: ImageView,
        val result: GeckoResult<Bitmap>,
    )

    private fun assertLateCaptureDoesNotRestoreCover(clearCover: (GeckoResumeCover) -> Unit) {
        lateinit var result: GeckoResult<Bitmap>
        val captures = AtomicInteger()
        val ready = AtomicBoolean(false)
        lateinit var surface: SurfaceView
        lateinit var cover: GeckoResumeCover
        lateinit var image: ImageView
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                result = GeckoResult()
                val host = FrameLayout(activity)
                surface = SurfaceView(activity)
                host.addView(surface, matchParentLayoutParams())
                cover = GeckoResumeCover(host, surface) {
                    captures.incrementAndGet()
                    result
                }.apply {
                    isContentPresented = ready::get
                    onSurfaceCreated = { ready.set(false) }
                    onSurfaceDestroyed = { ready.set(false) }
                }
                image = host.getChildAt(1) as ImageView
                activity.setContentView(host)
            }
            try {
                awaitCondition("Initial surface did not become available") {
                    surface.holder.surface.isValid
                }
                scenario.onActivity {
                    ready.set(true)
                    cover.onContentPresented()
                }
                awaitCondition("Presented content did not allow departure capture") {
                    if (captures.get() == 0) cover.captureBeforeBackground()
                    captures.get() == 1
                }
                scenario.onActivity { surface.visibility = View.INVISIBLE }
                awaitCondition("Surface did not become unavailable") {
                    !surface.holder.surface.isValid
                }
                lateinit var bitmap: Bitmap
                scenario.onActivity {
                    bitmap = greenFrame(surface)
                    clearCover(cover)
                    result.complete(bitmap)
                }
                awaitCondition("Late capture was not rejected and recycled") { bitmap.isRecycled }
                scenario.onActivity {
                    assertEquals(View.GONE, image.visibility)
                    assertEquals(null, image.drawable)
                }
            } finally {
                scenario.onActivity { cover.release() }
            }
        }
    }

    private fun assertGreenCover(scenario: ActivityScenario<MainActivity>) {
        lateinit var window: Window
        scenario.onActivity { activity ->
            window = activity.window
            val host = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
            val image = host.getChildAt(1) as ImageView
            assertEquals(View.VISIBLE, image.visibility)
        }
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var observedColor = Color.TRANSPARENT
        while (SystemClock.elapsedRealtime() < deadline) {
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot(window))
            try {
                observedColor = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            } finally {
                bitmap.recycle()
            }
            if (observedColor == PAGE_COLOR) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertEquals("Captured green pixels did not cover the lost surface", PAGE_COLOR, observedColor)
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        val observed = AtomicBoolean(false)
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { observed.set(condition()) }
            if (observed.get()) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue(message, observed.get())
    }

    private fun greenFrame(view: View): Bitmap = Bitmap.createBitmap(
        view.width,
        view.height,
        Bitmap.Config.ARGB_8888,
    ).apply {
        eraseColor(PAGE_COLOR)
    }

    private fun matchParentLayoutParams(): FrameLayout.LayoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )

    private companion object {
        const val PAGE_COLOR = 0xFF00B450.toInt()
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
