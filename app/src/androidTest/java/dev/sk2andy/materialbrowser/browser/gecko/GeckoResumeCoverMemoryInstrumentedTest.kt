package dev.sk2andy.materialbrowser.browser.gecko

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoResult

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoResumeCoverMemoryInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun trimMemoryRemovesDisplayedDepartureFrame() {
        withPendingDepartureCapture { fixture ->
            showCapturedFrame(fixture)

            fixture.scenario.onActivity {
                fixture.cover.trimMemory()

                assertEquals(View.GONE, fixture.image.visibility)
                assertEquals(null, fixture.image.drawable)
            }
        }
    }

    @Test
    fun trimMemoryRejectsLateDepartureCapture() {
        withPendingDepartureCapture { fixture ->
            lateinit var bitmap: Bitmap
            fixture.scenario.onActivity {
                bitmap = frame(fixture.host, Color.GREEN)
                fixture.surface.visibility = View.INVISIBLE
            }
            awaitCondition("Surface did not become unavailable") {
                !fixture.surface.holder.surface.isValid
            }
            fixture.scenario.onActivity {
                fixture.cover.trimMemory()
                fixture.captures.single().complete(bitmap)
            }

            awaitCondition("Late capture was not rejected and recycled") { bitmap.isRecycled }
            fixture.scenario.onActivity {
                assertEquals(View.GONE, fixture.image.visibility)
                assertEquals(null, fixture.image.drawable)
            }
        }
    }

    @Test
    fun trimMemoryAllowsFreshCaptureAfterContentReturns() {
        withPendingDepartureCapture { fixture ->
            showCapturedFrame(fixture)
            fixture.scenario.onActivity {
                fixture.cover.trimMemory()
                fixture.surface.visibility = View.VISIBLE
            }
            awaitCondition("Replacement surface did not become available") {
                fixture.surface.holder.surface.isValid
            }
            fixture.scenario.onActivity { fixture.cover.onContentPresented() }
            awaitCondition("New content did not allow another departure capture") {
                if (fixture.captures.size == 1) fixture.cover.captureBeforeBackground()
                fixture.captures.size == 2
            }
            lateinit var bitmap: Bitmap
            fixture.scenario.onActivity {
                bitmap = frame(fixture.host, Color.BLUE)
                fixture.captures.last().complete(bitmap)
            }
            instrumentation.waitForIdleSync()
            fixture.scenario.onActivity { fixture.surface.visibility = View.INVISIBLE }

            awaitCondition("Fresh departure frame did not become visible") {
                fixture.image.visibility == View.VISIBLE
            }
            fixture.scenario.onActivity {
                assertSame(bitmap, (fixture.image.drawable as BitmapDrawable).bitmap)
                assertEquals(Color.BLUE, bitmap.getPixel(0, 0))
            }
        }
    }

    private fun showCapturedFrame(fixture: CoverFixture) {
        fixture.scenario.onActivity {
            fixture.captures.single().complete(frame(fixture.host, Color.GREEN))
        }
        instrumentation.waitForIdleSync()
        fixture.scenario.onActivity { fixture.surface.visibility = View.INVISIBLE }
        awaitCondition("Surface loss did not show the captured frame") {
            !fixture.surface.holder.surface.isValid && fixture.image.visibility == View.VISIBLE
        }
    }

    private fun withPendingDepartureCapture(test: (CoverFixture) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var fixture: CoverFixture
            scenario.onActivity { activity ->
                val host = FrameLayout(activity)
                val surface = SurfaceView(activity)
                val captures = mutableListOf<GeckoResult<Bitmap>>()
                host.addView(
                    surface,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                val cover = GeckoResumeCover(host, surface) {
                    GeckoResult<Bitmap>().also(captures::add)
                }.apply { isContentPresented = { true } }
                fixture = CoverFixture(
                    scenario,
                    host,
                    surface,
                    cover,
                    host.getChildAt(1) as ImageView,
                    captures,
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
                    if (fixture.captures.isEmpty()) fixture.cover.captureBeforeBackground()
                    fixture.captures.size == 1
                }
                test(fixture)
            } finally {
                scenario.onActivity { fixture.cover.release() }
            }
        }
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

    private fun frame(view: View, color: Int): Bitmap = Bitmap.createBitmap(
        view.width,
        view.height,
        Bitmap.Config.ARGB_8888,
    ).apply { eraseColor(color) }

    private data class CoverFixture(
        val scenario: ActivityScenario<MainActivity>,
        val host: FrameLayout,
        val surface: SurfaceView,
        val cover: GeckoResumeCover,
        val image: ImageView,
        val captures: MutableList<GeckoResult<Bitmap>>,
    )

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
