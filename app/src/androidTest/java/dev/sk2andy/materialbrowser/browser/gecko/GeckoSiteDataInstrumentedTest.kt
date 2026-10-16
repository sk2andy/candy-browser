package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoSiteDataInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun deletionPreservesOtherSiteIsolatedProfileAndPrivateContexts() {
        FixtureServer().use { fixture ->
            val profile = "ä-site-data-${UUID.randomUUID()}"
            val sessions = listOf(
                createSession(profile),
                createSession(profile),
                createSession("site-data-other-${UUID.randomUUID()}"),
                createSession(profile, isPrivate = true),
                createSession("site-data-private-${UUID.randomUUID()}", isPrivate = true),
            )
            val sites = listOf(fixture.siteA, fixture.siteB, fixture.siteA, fixture.siteA, fixture.siteA)
            try {
                sessions.zip(sites).forEach { (session, site) ->
                    assertStored(load(session, "$site/seed"), true)
                }
                clear(sessions[0], fixture.siteA)
                sessions.zip(sites).forEachIndexed { index, (session, site) ->
                    assertStored(load(session, "$site/inspect"), index != 0)
                }

                // The same domain and profile ID in private mode is a separate deletion target.
                clear(sessions[3], fixture.siteA)
                sessions.zip(sites).forEachIndexed { index, (session, site) ->
                    assertStored(load(session, "$site/inspect"), index != 0 && index != 3)
                }
            } finally {
                sessions.forEach(::close)
            }
        }
    }

    @Test
    fun sharedDefaultStorageClearsOnlyItsSiteAndRejectsInvalidTargets() {
        FixtureServer().use { fixture ->
            val first = createSession("site-shared-${UUID.randomUUID()}", isolated = false)
            val second = createSession("site-shared-${UUID.randomUUID()}", isolated = false)
            val isolated = createSession("site-isolated-${UUID.randomUUID()}")
            val sessions = listOf(first, second, isolated)
            try {
                sessions.forEach { assertStored(load(it, "${fixture.siteA}/seed"), true) }
                instrumentation.runOnMainSync {
                    first.session.clearSiteData("about:blank") { assertFalse(it) }
                    first.session.clearSiteData("${fixture.siteB}/inspect") { assertFalse(it) }
                }
                sessions.forEach { assertStored(load(it, "${fixture.siteA}/inspect"), true) }
                clear(first, fixture.siteA)
                sessions.forEachIndexed { index, session ->
                    assertStored(load(session, "${fixture.siteA}/inspect"), index == 2)
                }
            } finally {
                sessions.forEach(::close)
            }
        }
    }

    private data class Session(val session: GeckoBrowserSession, val view: View)

    private fun createSession(profileId: String, isPrivate: Boolean = false, isolated: Boolean = true): Session {
        lateinit var result: Session
        instrumentation.runOnMainSync {
            val session = GeckoRuntimeOwner.getOrCreate(context).createSession(
                profileId = profileId,
                isolationEnabled = isolated,
                isPrivate = isPrivate,
                privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(pageHost = "127.0.0.1"),
            )
            assertTrue(session.supportsSiteDataDeletion)
            result = Session(session, session.createView(context))
        }
        return result
    }

    private fun close(value: Session) {
        instrumentation.runOnMainSync {
            value.session.releaseView(value.view)
            value.session.close()
            assertFalse(value.session.supportsSiteDataDeletion)
            value.session.clearSiteData("http://127.0.0.1/") { assertFalse(it) }
        }
    }

    private fun load(value: Session, url: String): JSONObject {
        val id = UUID.randomUUID().toString()
        val prefix = "fixture-$id|"
        val latest = AtomicReference<GeckoBrowserSessionState>()
        val loaded = CountDownLatch(1)
        instrumentation.runOnMainSync {
            value.session.setStateListener { state ->
                latest.set(state)
                if (state.title?.startsWith(prefix) == true) loaded.countDown()
            }
            assertTrue(value.session.loadUrl("$url?id=$id"))
        }
        assertTrue("Fixture did not load: ${latest.get()}", loaded.await(30, TimeUnit.SECONDS))
        val state = JSONObject(latest.get().title!!.removePrefix(prefix))
        assertFalse(state.toString(), state.has("error"))
        return state
    }

    private fun clear(value: Session, site: String) {
        // Match the exact currently loaded URL, as the controller's captured target does.
        val current = AtomicReference<GeckoBrowserSessionState>()
        val completed = CountDownLatch(1)
        val result = AtomicReference<Boolean>()
        instrumentation.runOnMainSync {
            value.session.setStateListener { current.set(it) }
            assertTrue(current.get().url!!.startsWith(site))
            value.session.clearSiteData(current.get().url!!) {
                result.set(it)
                completed.countDown()
            }
        }
        assertTrue("Site deletion did not complete", completed.await(35, TimeUnit.SECONDS))
        assertEquals("Site deletion must succeed", true, result.get())
    }

    private fun assertStored(state: JSONObject, expected: Boolean) {
        listOf("cookie", "httpOnly", "local", "session", "indexed").forEach { key ->
            assertEquals("$key: $state", expected, state.getBoolean(key))
        }
    }

    private class FixtureServer : AutoCloseable {
        private val server = ServerSocket(0, 16, InetAddress.getByName("0.0.0.0"))
        val siteA = "http://127.0.0.1:${server.localPort}"
        val siteB = "http://127.0.0.2:${server.localPort}"
        private val thread = Thread({
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val reader = socket.getInputStream().bufferedReader()
                        val request = reader.readLine().orEmpty()
                        if (request.isBlank()) return@use
                        val headers = buildList {
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                add(line)
                            }
                        }
                        val seed = request.contains("/seed?")
                        val httpOnly = headers.any {
                            it.startsWith("Cookie:", ignoreCase = true) && "server_session=value" in it
                        }
                        val body = page(seed, httpOnly).toByteArray(Charsets.UTF_8)
                        val cookie = if (seed) "Set-Cookie: server_session=value; HttpOnly; Path=/\r\n" else ""
                        socket.getOutputStream().apply {
                            write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                                "Cache-Control: no-store\r\n$cookie" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                            write(body)
                            flush()
                        }
                    }
                } catch (_: IOException) {
                    // Gecko may cancel speculative or replaced requests before reading the body.
                }
            }
        }, "gecko-site-data-fixture").apply { isDaemon = true; start() }

        override fun close() {
            server.close()
            thread.join(2_000)
        }

        private fun page(seed: Boolean, httpOnly: Boolean): String = """
            <!doctype html><meta charset="utf-8"><title>Loading fixture</title><script>
            (async () => {
              const prefix = 'fixture-' + new URL(location.href).searchParams.get('id') + '|';
              try {
                if ($seed) {
                  document.cookie = 'fixture=value; path=/';
                  localStorage.setItem('fixture', 'value');
                  sessionStorage.setItem('fixture', 'value');
                }
                const db = await new Promise((resolve, reject) => {
                  const request = indexedDB.open('candy-site-data', 1);
                  request.onupgradeneeded = () => request.result.createObjectStore('values');
                  request.onsuccess = () => resolve(request.result);
                  request.onerror = () => reject(request.error);
                });
                if ($seed) await new Promise((resolve, reject) => {
                  const tx = db.transaction('values', 'readwrite');
                  tx.objectStore('values').put('value', 'fixture');
                  tx.oncomplete = resolve; tx.onerror = () => reject(tx.error);
                });
                const indexed = await new Promise((resolve, reject) => {
                  const request = db.transaction('values').objectStore('values').get('fixture');
                  request.onsuccess = () => resolve(request.result === 'value');
                  request.onerror = () => reject(request.error);
                });
                db.close();
                document.title = prefix + JSON.stringify({
                  cookie: document.cookie.includes('fixture=value'),
                  httpOnly: $seed || $httpOnly,
                  local: localStorage.getItem('fixture') === 'value',
                  session: sessionStorage.getItem('fixture') === 'value', indexed,
                });
              } catch (error) { document.title = prefix + JSON.stringify({error: String(error)}); }
            })();
            </script>
        """.trimIndent()
    }
}
