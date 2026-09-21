package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoMediaRestorationReadbackInstrumentedTest {
    @Test
    fun domAcknowledgementWaitsForItsCompositorReadback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val gate = GeckoMediaRestorationReadback(Handler(Looper.getMainLooper()))
            val results = mutableListOf<Boolean>()
            lateinit var completeCapture: (Boolean) -> Unit
            gate.start(
                isCurrent = { true },
                capture = { completeCapture = it },
                onResult = results::add,
            )
            assertTrue("DOM acknowledgement must not release the cover before readback: $results", results.isEmpty())
            completeCapture(true)
            completeCapture(false)
            assertEquals(listOf(true), results)
        }
    }

    @Test
    fun cancellationAndSupersedingRequestsRejectLateReadbacks() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val gate = GeckoMediaRestorationReadback(Handler(Looper.getMainLooper()))
            val results = mutableListOf<Pair<String, Boolean>>()
            lateinit var firstCapture: (Boolean) -> Unit
            lateinit var secondCapture: (Boolean) -> Unit
            gate.start({ true }, { firstCapture = it }) { results += "first" to it }
            gate.start({ true }, { secondCapture = it }) { results += "second" to it }
            firstCapture(true)
            assertEquals(listOf("first" to false), results)
            gate.cancel()
            secondCapture(true)
            assertEquals(listOf("first" to false, "second" to false), results)
        }
    }

    @Test
    fun replacedPresentationRejectsCompletedReadback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val gate = GeckoMediaRestorationReadback(Handler(Looper.getMainLooper()))
            val results = mutableListOf<Boolean>()
            var current = true
            lateinit var completeCapture: (Boolean) -> Unit
            gate.start({ current }, { completeCapture = it }, results::add)
            current = false
            completeCapture(true)
            assertEquals(listOf(false), results)
        }
    }

    @Test
    fun missingReadbackIsBoundedAndLateCompletionIsIgnored() {
        val completed = CountDownLatch(1)
        val results = mutableListOf<Boolean>()
        lateinit var completeCapture: (Boolean) -> Unit
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            GeckoMediaRestorationReadback(Handler(Looper.getMainLooper())).start(
                isCurrent = { true },
                capture = { completeCapture = it },
                onResult = { results += it; completed.countDown() },
            )
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            completeCapture(true)
            assertEquals(listOf(false), results)
        }
    }
}
