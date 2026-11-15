package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.sync.MozillaSyncRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

@RunWith(AndroidJUnit4::class)
class GeckoMozillaAccountWebChannelInstrumentedTest {
    @Test
    fun mozillaAuthorizationPageSendsWebChannelStatus() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveMozillaAuth") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = MozillaSyncRepository.getInstance(context)
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val statusReceived = CountDownLatch(1)
        var statusResponse: String? = null
        val pageState = AtomicReference<GeckoBrowserSessionState?>()
        val nativeConnection = AtomicReference("none")
        val ready = CountDownLatch(1)
        var installed = false
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        var binding: AutoCloseable? = null
        withContext(Dispatchers.Main) {
            assertTrue(repository.configureLocalProfile("fxa-webchannel-smoke", "Candy WebChannel smoke test"))
            repository.start()
            val url = requireNotNull(repository.beginAuthentication())
            session = GeckoRuntimeOwner.getOrCreate(context).createSession(
                "fxa-webchannel-smoke",
                isPrivate = false,
                privacyPolicy = GeckoPrivacyPolicy.Disabled,
            )
            scenario.onActivity { activity ->
                view = session.createView(activity)
                activity.setContentView(view)
            }
            session.setActive(true)
            session.setStateListener(pageState::set)
            binding = session.bindMozillaAccountWebChannel(
                handler = { message ->
                    val response = repository.handleWebChannelMessage(message)
                    if (JSONObject(message).optJSONObject("message")?.optString("command") == "fxaccounts:fxa_status") {
                        statusResponse = response
                        statusReceived.countDown()
                    }
                    response
                },
                onReady = { success ->
                    installed = success
                    ready.countDown()
                    if (success) {
                        val native = nativeSession(session)
                        val channel = requireNotNull(binding)
                        val extension = channel.javaClass.getDeclaredField("extension")
                            .apply { isAccessible = true }.get(channel) as WebExtension
                        val delegate = requireNotNull(native.webExtensionController.getMessageDelegate(extension, NATIVE_APP))
                        native.webExtensionController.setMessageDelegate(
                            extension,
                            object : WebExtension.MessageDelegate {
                                override fun onConnect(port: WebExtension.Port) {
                                    nativeConnection.set(
                                        "origin=${safeLocation(port.sender.url)}, " +
                                            "environment=${port.sender.environmentType}, top=${port.sender.isTopLevel}, " +
                                            "sameSession=${port.sender.session === native}, name=${port.name}",
                                    )
                                    delegate.onConnect(port)
                                }
                            },
                            NATIVE_APP,
                        )
                        session.loadUrl(url)
                    }
                },
            )
        }
        try {
            assertTrue("WebChannel installation completed", ready.await(15, TimeUnit.SECONDS))
            assertTrue("WebChannel installed", installed)
            val received = statusReceived.await(30, TimeUnit.SECONDS)
            val state = pageState.get()
            assertTrue(
                "Mozilla authorization page reached the native WebChannel; " +
                    "page=${safeLocation(state?.url)}, title=${state?.title?.take(100)}, " +
                    "loading=${state?.isLoading}, success=${state?.lastNavigationSucceeded}, " +
                    "failure=${state?.failureKind}, http=${state?.httpStatusCode}; native=${nativeConnection.get()}",
                received,
            )
            assertNotNull("Native account status response produced", statusResponse)
        } finally {
            withContext(Dispatchers.Main) {
                binding?.close()
                session.releaseView(view)
                session.close()
                repository.logout()
            }
            scenario.close()
        }
    }

    @Test
    fun builtInChannelBindsOnlyExplicitSessionAndDetachesOnClose() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ready = CountDownLatch(1)
        var installed = false
        lateinit var authSession: GeckoBrowserSession
        lateinit var otherSession: GeckoBrowserSession
        lateinit var privateSession: GeckoBrowserSession
        var binding: AutoCloseable? = null
        instrumentation.runOnMainSync {
            val runtime = GeckoRuntimeOwner.getOrCreate(context)
            authSession = runtime.createSession("fxa-channel-auth", isPrivate = false)
            otherSession = runtime.createSession("fxa-channel-other", isPrivate = false)
            privateSession = runtime.createSession("fxa-channel-private", isPrivate = true)
            binding = authSession.bindMozillaAccountWebChannel({ null }) {
                installed = it
                ready.countDown()
            }
        }
        try {
            assertTrue("WebChannel installation completed", ready.await(15, TimeUnit.SECONDS))
            assertTrue("Built-in WebChannel installed", installed)
            instrumentation.runOnMainSync {
                val channel = requireNotNull(binding)
                val extension = channel.javaClass.getDeclaredField("extension")
                    .apply { isAccessible = true }.get(channel) as WebExtension
                assertNotNull(nativeSession(authSession).webExtensionController.getMessageDelegate(extension, NATIVE_APP))
                assertNull(nativeSession(otherSession).webExtensionController.getMessageDelegate(extension, NATIVE_APP))
                assertNull(privateSession.bindMozillaAccountWebChannel({ null }) { error("Private binding must be rejected") })
                channel.close()
                assertNull(nativeSession(authSession).webExtensionController.getMessageDelegate(extension, NATIVE_APP))
            }
        } finally {
            instrumentation.runOnMainSync {
                binding?.close()
                authSession.close()
                otherSession.close()
                privateSession.close()
            }
        }
    }

    private fun nativeSession(session: GeckoBrowserSession): GeckoSession =
        session.javaClass.getDeclaredField("session")
            .apply { isAccessible = true }.get(session) as GeckoSession

    private fun safeLocation(url: String?): String = url?.let {
        val uri = Uri.parse(it)
        "${uri.scheme}://${uri.host}${uri.path}"
    } ?: "none"

    private companion object {
        const val NATIVE_APP = "mozacWebchannel"
    }
}
