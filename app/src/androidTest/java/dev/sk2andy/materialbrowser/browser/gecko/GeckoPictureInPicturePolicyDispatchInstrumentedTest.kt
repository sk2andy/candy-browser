package dev.sk2andy.materialbrowser.browser.gecko

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoPictureInPicturePolicyDispatchInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun policyAcknowledgementDoesNotInvalidateItsRequestedRestoration() {
        verifyRestorationAfterPolicyAcknowledgement(deferRestoration = false)
    }

    @Test
    fun ordinaryPolicyPlaybackPublicationStillAllowsLaterRestoration() {
        verifyRestorationAfterPolicyAcknowledgement(deferRestoration = true)
    }

    @Test
    fun compositorReadbackCompletesAfterRestoration() {
        verifyRestorationAfterPolicyAcknowledgement(deferRestoration = false, measureCompositor = true)
    }

    private fun verifyRestorationAfterPolicyAcknowledgement(
        deferRestoration: Boolean,
        measureCompositor: Boolean = false,
    ) {
        FixtureServer().use { server ->
            lateinit var session: GeckoBrowserSession
            lateinit var view: View
            val loaded = CountDownLatch(1)
            val completed = CountDownLatch(1)
            var restored = false
            var startedAt = 0L
            var acknowledgedAt = 0L
            var requestedAt = 0L
            var completedAt = 0L
            var captureAt = 0L
            var captureSucceeded = false
            var drawCount = 0
            val drawCallback = Runnable { drawCount++ }
            var engineView: GeckoView? = null
            composeRule.runOnUiThread {
                session = GeckoRuntimeOwner.getOrCreate(composeRule.activity).createSession(
                    profileId = "pip-policy-dispatch",
                    isPrivate = false,
                )
                view = session.createView(composeRule.activity)
                composeRule.activity.setContentView(view)
                session.setActive(true)
                session.setStateListener { state ->
                    if (state.title == FIXTURE_TITLE && !state.isLoading) loaded.countDown()
                }
                assertTrue(session.loadUrl(server.url))
            }
            try {
                composeRule.waitUntil(timeoutMillis = 20_000L) { loaded.count == 0L }
                composeRule.runOnUiThread {
                    startedAt = SystemClock.uptimeMillis()
                    session.updatePrivacyPolicy(GeckoPrivacyPolicy.Disabled) {
                        acknowledgedAt = SystemClock.uptimeMillis()
                        fun requestRestoration() {
                            requestedAt = SystemClock.uptimeMillis()
                            session.restorePictureInPicturePresentation { result ->
                                restored = result
                                completedAt = SystemClock.uptimeMillis()
                                if (measureCompositor) {
                                    val geckoView = requireNotNull(findGeckoView(view))
                                    engineView = geckoView
                                    geckoView.session?.compositorController?.addDrawCallback(drawCallback)
                                    geckoView.capturePixels().accept(
                                        { bitmap ->
                                            captureAt = SystemClock.uptimeMillis()
                                            captureSucceeded = bitmap != null
                                            bitmap?.recycle()
                                        },
                                        { error -> Log.e("CandyPipPolicyTest", "Readback failed", error) },
                                    )
                                    view.postDelayed({ completed.countDown() }, 1_000L)
                                } else {
                                    completed.countDown()
                                }
                            }
                        }
                        fun awaitFrames(remaining: Int) {
                            if (remaining == 0) requestRestoration()
                            else view.postOnAnimation { awaitFrames(remaining - 1) }
                        }
                        if (deferRestoration) awaitFrames(4) else requestRestoration()
                    }
                }
                composeRule.waitUntil(timeoutMillis = 5_000L) { completed.count == 0L }
                val timing = "deferred=$deferRestoration, " +
                    "policyAck=${acknowledgedAt - startedAt}ms, " +
                    "restoreDispatch=${requestedAt - startedAt}ms, " +
                    "restoreResult=${completedAt - startedAt}ms, restored=$restored"
                Log.i("CandyPipPolicyTest", timing)
                assertTrue("A later playback notification invalidated the requested restore: $timing", restored)
                if (measureCompositor) {
                    Log.i("CandyPipPolicyTest", "captureAfterRestore=${captureAt - completedAt}ms, " +
                        "captureSucceeded=$captureSucceeded, drawCallbacksAfter1000ms=$drawCount")
                    assertTrue("Compositor readback did not finish", captureSucceeded)
                }
            } finally {
                composeRule.runOnUiThread {
                    engineView?.session?.compositorController?.removeDrawCallback(drawCallback)
                    session.releaseView(view)
                    session.close()
                }
            }
        }
    }

    private fun findGeckoView(view: View): GeckoView? {
        if (view is GeckoView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findGeckoView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/pip-policy"
        private val thread = Thread({
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume request headers.
                        }
                        val body = "<html><head><title>$FIXTURE_TITLE</title></head><body>PiP policy</body></html>"
                            .toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n".toByteArray())
                            write("Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                } catch (error: SocketException) {
                    if (!socket.isClosed) throw error
                }
            }
        }, "pip-policy-dispatch-fixture").apply {
            isDaemon = true
            start()
        }

        override fun close() {
            socket.close()
            thread.join(2_000)
        }
    }

    private companion object {
        const val FIXTURE_TITLE = "Candy PiP policy dispatch"
    }
}
