package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoExtensionRuntimeInstrumentedTest {
    @Test
    fun mozillaSignedXpiUsesUserInstallPipelineAndAppearsInInventory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val completed = CountDownLatch(1)
        val result = AtomicReference<GeckoExtensionMutationResult>()
        val wasAlreadyInstalled = AtomicBoolean()
        lateinit var runtime: GeckoViewRuntimeHandle
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context) as GeckoViewRuntimeHandle
            val repository = GeckoExtensionRepository(runtime.extensions) {
                GeckoExtensionPermissionDecision(
                    grantPermissions = true,
                    allowInPrivateBrowsing = false,
                )
            }
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                val installed = runtime.extensions.listInstalled()
                val existing = installed.firstOrNull { extension ->
                    !extension.isBuiltIn &&
                        extension.name.orEmpty().contains("uBlock", ignoreCase = true)
                }
                wasAlreadyInstalled.set(existing != null)
                result.set(
                    if (existing != null) {
                        GeckoExtensionMutationResult.Applied(
                            extensionId = existing.id,
                            snapshot = GeckoExtensionSnapshot(installed),
                        )
                    } else {
                        repository.installSignedXpi(
                            rawUri = SIGNED_EXTENSION_URI,
                            context = GeckoExtensionManagementContext(
                                profileId = "firefox-extension-install",
                                isPrivate = false,
                            ),
                        )
                    },
                )
                completed.countDown()
            }
        }

        assertTrue("Mozilla-signed XPI install did not finish", completed.await(45, TimeUnit.SECONDS))
        val applied = result.get() as GeckoExtensionMutationResult.Applied
        val extension = checkNotNull(applied.snapshot.extension(applied.extensionId))
        assertFalse(extension.isBuiltIn)
        assertTrue(extension.enabled)
        assertFalse(extension.allowedInPrivateBrowsing)
        assertTrue(extension.name.orEmpty().contains("uBlock", ignoreCase = true))
        Log.i(TEST_TAG, "Mozilla-signed extension already installed=${wasAlreadyInstalled.get()}")
    }

    @Test
    fun installedBuiltInFixtureIsListedAndRunsAcrossRealisticNavigation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        val installed = CountDownLatch(1)
        lateinit var runtime: GeckoViewRuntimeHandle
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context) as GeckoViewRuntimeHandle
            runtime.ensureBuiltInExtensionFixture()
                .withHandler(Handler(Looper.getMainLooper()))
                .accept(
                    { extension ->
                        if (extension?.id == FIXTURE_ID) installed.countDown()
                    },
                    { error -> throw AssertionError("Firefox extension fixture failed", error) },
                )
        }
        assertTrue("Firefox extension fixture was not installed", installed.await(20, TimeUnit.SECONDS))

        val listed = CountDownLatch(1)
        val registered = AtomicReference<GeckoExtension>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                registered.set(
                    runtime.extensions.listInstalled().single { extension ->
                        extension.id == FIXTURE_ID
                    },
                )
                listed.countDown()
            }
        }
        assertTrue("Firefox extension fixture was not listed", listed.await(20, TimeUnit.SECONDS))
        assertTrue(registered.get().enabled)
        assertTrue(registered.get().isBuiltIn)

        lateinit var regularSession: GeckoBrowserSession
        lateinit var regularView: View
        val googleMarked = CountDownLatch(1)
        val redditMarked = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            regularSession = runtime.createSession("firefox-extension-regular", isPrivate = false)
            regularView = regularSession.createView(context)
            regularSession.setStateListener { state ->
                when (state.title) {
                    "$MARKER_PREFIX$GOOGLE_HOST" -> googleMarked.countDown()
                    "$MARKER_PREFIX$REDDIT_HOST" -> redditMarked.countDown()
                }
            }
            assertTrue(regularSession.loadUrl(server.url(GOOGLE_HOST)))
        }

        try {
            assertTrue("Fixture did not run on Google-like page", googleMarked.await(20, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue(regularSession.loadUrl(server.url(REDDIT_HOST)))
            }
            assertTrue("Fixture did not run after Reddit-like navigation", redditMarked.await(20, TimeUnit.SECONDS))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                regularSession.releaseView(regularView)
                regularSession.close()
            }
            server.close()
        }
    }

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread({ serve() }, "gecko-extension-fixture").apply {
            isDaemon = true
            start()
        }

        fun url(host: String): String = "http://$host:${socket.localPort}/feed"

        private fun serve() {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume request headers.
                        }
                        val body = "<html><head><title>$ORIGINAL_TITLE</title></head><body>feed</body></html>"
                            .toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                            write("Content-Length: ${body.size}\r\n".toByteArray())
                            write("Connection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                } catch (error: SocketException) {
                    if (socket.isClosed) return
                }
            }
        }

        override fun close() {
            socket.close()
            thread.join(2_000)
        }
    }

    private companion object {
        const val FIXTURE_ID = "candy-firefox-fixture@sk2andy.dev"
        const val SIGNED_EXTENSION_URI =
            "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
        const val GOOGLE_HOST = "google.candy.localhost"
        const val REDDIT_HOST = "reddit.candy.localhost"
        const val MARKER_PREFIX = "Candy Firefox Extension: "
        const val ORIGINAL_TITLE = "Original"
        const val TEST_TAG = "CandyExtensionE2E"
    }
}
