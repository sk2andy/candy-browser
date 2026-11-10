package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoWebAuthnAvailabilityInstrumentedTest {
    @Test
    fun securePagesAndFramesDoNotAdvertiseWebAuthn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody(PAGE)) }
            server.start()
            val url = server.url("/").toString().replace("127.0.0.1", "localhost")
            for (isPrivate in listOf(false, true)) {
                val reached = CountDownLatch(1)
                val title = AtomicReference<String>()
                lateinit var session: GeckoBrowserSession
                lateinit var view: android.view.View
                instrumentation.runOnMainSync {
                    val runtime = GeckoRuntimeOwner.getOrCreate(context)
                    session = runtime.createSession(
                        profileId = "webauthn-availability",
                        isPrivate = isPrivate,
                        privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(pageHost = "localhost"),
                    )
                    view = session.createView(context)
                    session.setStateListener { state ->
                        title.set(state.title)
                        if (state.title?.startsWith("webauthn:") == true) reached.countDown()
                    }
                    session.setActive(true)
                    assertTrue(session.loadUrl(url))
                }
                try {
                    assertTrue("Page did not report feature detection: ${title.get()}", reached.await(20, TimeUnit.SECONDS))
                    assertEquals("webauthn:true:undefined:object:undefined", title.get())
                } finally {
                    instrumentation.runOnMainSync {
                        session.releaseView(view)
                        session.setActive(false)
                        session.close()
                    }
                }
            }
        }
    }

    private companion object {
        const val PAGE = """
            <!doctype html><script>
            const authAtStart = typeof PublicKeyCredential;
            onmessage = e => document.title = 'webauthn:' + isSecureContext + ':' +
                authAtStart + ':' + typeof navigator.credentials + ':' + e.data;
            </script><iframe srcdoc="<script>parent.postMessage(typeof PublicKeyCredential, '*')</script>"></iframe>
        """
    }
}
