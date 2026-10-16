package dev.sk2andy.materialbrowser.constellations

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConstellationPageExtractionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val handler = Handler(Looper.getMainLooper())

    @Test
    fun firstReadableLayoutIsAcceptedAfterAnEmptyInitialExtraction() {
        val attempts = AtomicInteger()
        val completed = CountDownLatch(1)
        var result: String? = null
        instrumentation.runOnMainSync {
            ConstellationPageExtraction.request(
                handler = handler,
                isCurrent = { true },
                extract = { callback -> callback(if (attempts.incrementAndGet() == 1) null else "readable") },
                onComplete = {
                    result = it
                    completed.countDown()
                },
            )
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertEquals("readable", result)
        assertEquals(2, attempts.get())
    }

    @Test
    fun invalidatedOwnerCancelsScheduledRetry() {
        val attempts = AtomicInteger()
        val completed = CountDownLatch(1)
        var current = true
        instrumentation.runOnMainSync {
            ConstellationPageExtraction.request(
                handler = handler,
                isCurrent = { current },
                extract = { callback ->
                    attempts.incrementAndGet()
                    callback(null)
                },
                onComplete = { completed.countDown() },
            )
            current = false
        }
        assertFalse(completed.await(800, TimeUnit.MILLISECONDS))
        assertEquals(1, attempts.get())
    }

    @Test
    fun lateReadableResultIsRejectedAfterOwnerInvalidation() {
        val completed = CountDownLatch(1)
        var current = true
        var resultCallback: ((String?) -> Unit)? = null
        instrumentation.runOnMainSync {
            ConstellationPageExtraction.request(
                handler = handler,
                isCurrent = { current },
                extract = { resultCallback = it },
                onComplete = { completed.countDown() },
            )
            current = false
            requireNotNull(resultCallback).invoke("late readable content")
        }
        assertFalse(completed.await(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun unavailableRendererStopsAfterThreeAttempts() {
        val attempts = AtomicInteger()
        val lastAttempt = CountDownLatch(1)
        val completed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            ConstellationPageExtraction.request(
                handler = handler,
                isCurrent = { true },
                extract = { callback ->
                    if (attempts.incrementAndGet() == 3) lastAttempt.countDown()
                    callback(null)
                },
                onComplete = { completed.countDown() },
            )
        }
        assertTrue(lastAttempt.await(5, TimeUnit.SECONDS))
        assertFalse(completed.await(500, TimeUnit.MILLISECONDS))
        assertEquals(3, attempts.get())
    }
}
