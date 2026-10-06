package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoUserInputRequestInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun currentBooleanResultCompletesOnlyOnce() {
        instrumentation.runOnMainSync {
            val fixture = RequestFixture()
            fixture.start()

            fixture.request.accept(fixture.reply().put("hasUserInput", false))
            fixture.request.accept(fixture.reply().put("hasUserInput", true))

            assertEquals(listOf(false), fixture.results)
        }
    }

    @Test
    fun malformedOrUnknownResultNeverConfirmsCleanDocument() {
        instrumentation.runOnMainSync {
            listOf(JSONObject.NULL, "false", 0).forEach { value ->
                val fixture = RequestFixture()
                fixture.start()

                fixture.request.accept(fixture.reply().put("hasUserInput", value))

                assertEquals(listOf<Boolean?>(null), fixture.results)
            }
        }
    }

    @Test
    fun staleRequestIdentityCannotCompleteCurrentQuery() {
        instrumentation.runOnMainSync {
            val fixture = RequestFixture()
            fixture.start()
            val replacements = mapOf(
                "token" to "other-token",
                "nonce" to "other-nonce",
                "requestId" to 99L,
                "revision" to 99L,
                "navigationGeneration" to 99,
            )
            replacements.forEach { (key, value) ->
                fixture.request.accept(fixture.reply().put(key, value).put("hasUserInput", false))
            }

            assertTrue(fixture.results.isEmpty())
            fixture.request.accept(fixture.reply().put("hasUserInput", true))
            assertEquals(listOf(true), fixture.results)
        }
    }

    @Test
    fun cancelledAndSupersededQueriesRejectLateResults() {
        instrumentation.runOnMainSync {
            val fixture = RequestFixture()
            fixture.start()
            val oldReply = fixture.reply().put("hasUserInput", false)
            fixture.start()

            assertEquals(listOf<Boolean?>(null), fixture.results)
            fixture.request.accept(oldReply)
            assertEquals(listOf<Boolean?>(null), fixture.results)
            fixture.request.cancel()
            fixture.request.accept(fixture.reply().put("hasUserInput", false))
            assertEquals(listOf<Boolean?>(null, null), fixture.results)
        }
    }

    @Test
    fun failedPostReturnsUnknown() {
        instrumentation.runOnMainSync {
            val results = mutableListOf<Boolean?>()
            GeckoUserInputRequest(Handler(Looper.getMainLooper())).start(
                token = "binding-token",
                revision = 2L,
                navigationGeneration = 3,
                post = { throw IllegalStateException("Port disconnected") },
                onResult = results::add,
            )

            assertEquals(listOf<Boolean?>(null), results)
        }
    }

    @Test
    fun missingResponseTimesOutToUnknown() {
        val completed = CountDownLatch(1)
        val results = mutableListOf<Boolean?>()
        instrumentation.runOnMainSync {
            GeckoUserInputRequest(Handler(Looper.getMainLooper())).start(
                token = "binding-token",
                revision = 2L,
                navigationGeneration = 3,
                post = {},
                onResult = {
                    results += it
                    completed.countDown()
                },
            )
        }

        assertTrue(completed.await(7, TimeUnit.SECONDS))
        assertEquals(listOf<Boolean?>(null), results)
    }

    private class RequestFixture {
        val request = GeckoUserInputRequest(Handler(Looper.getMainLooper()))
        val results = mutableListOf<Boolean?>()
        private lateinit var message: JSONObject

        fun start() = request.start(
            token = "binding-token",
            revision = 2L,
            navigationGeneration = 3,
            post = { message = it },
            onResult = results::add,
        )

        fun reply(): JSONObject = JSONObject(message.toString()).put("type", "user-input-result")
    }
}
