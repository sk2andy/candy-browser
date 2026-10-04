package dev.sk2andy.materialbrowser.browser.systemwebview.commands

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class WebViewSiteDataInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun deletingSitePreservesOtherSiteProfileAndPrivateStorage() {
        composeRule.runOnIdle {
            assertTrue("Device must support site deletion", WebViewSiteData.isSupported(true))
            assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        }
        FixtureServer().use { fixture ->
            val profiles = listOf("target", "other", "private").map { "candy_site_test_${it}_${UUID.randomUUID()}" }
            val views = mutableListOf<WebView>()
            composeRule.runOnIdle {
                val host = LinearLayout(composeRule.activity).apply { orientation = LinearLayout.VERTICAL }
                composeRule.activity.addContentView(
                    host,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                listOf(profiles[0], profiles[0], profiles[1], profiles[2]).forEach { profile ->
                    val view = createView(profile)
                    views += view
                    host.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                }
            }
            try {
                val sites = listOf(fixture.siteA, fixture.siteB, fixture.siteA, fixture.siteA)
                val scopes = listOf("target-profile-site-A", "target-profile-site-B", "other-profile-site-A", "private-storage-site-A")
                views.zip(sites).forEach { (view, site) ->
                    load(view, "$site/seed")
                    assertStored(state(view), true)
                    composeRule.runOnIdle {
                        WebViewCompat.getProfile(view).cookieManager
                            .setCookie(site, "server_session=value; HttpOnly; Path=/")
                    }
                    assertHttpOnlyCookie(view, site, true)
                }
                val done = CountDownLatch(1)
                val succeeded = AtomicReference<Boolean>()
                composeRule.runOnIdle {
                    WebViewSiteData.clear(views[0], fixture.siteA, true) {
                        succeeded.set(it)
                        done.countDown()
                    }
                }
                assertTrue("Native deletion completes", done.await(15, TimeUnit.SECONDS))
                assertEquals(true, succeeded.get())
                views.zip(sites).forEachIndexed { index, (view, site) ->
                    load(view, "$site/inspect?scope=${scopes[index]}")
                    assertStored(state(view), index != 0)
                    assertHttpOnlyCookie(view, site, index != 0)
                }
                captureEvidence("native-sites-after-delete.png")

                // Deletion in the private storage context must leave regular storage untouched.
                val privateDone = CountDownLatch(1)
                composeRule.runOnIdle {
                    WebViewSiteData.clear(views[3], fixture.siteA, true) {
                        assertTrue(it)
                        privateDone.countDown()
                    }
                }
                assertTrue(privateDone.await(15, TimeUnit.SECONDS))
                load(views[3], "${fixture.siteA}/inspect")
                assertStored(state(views[3]), false)
                assertHttpOnlyCookie(views[3], fixture.siteA, false)
                load(views[2], "${fixture.siteA}/inspect")
                assertStored(state(views[2]), true)
                assertHttpOnlyCookie(views[2], fixture.siteA, true)
                load(views[1], "${fixture.siteB}/inspect")
                assertStored(state(views[1]), true)
                assertHttpOnlyCookie(views[1], fixture.siteB, true)
            } finally {
                composeRule.runOnIdle {
                    views.forEach { view ->
                        (view.parent as? ViewGroup)?.removeView(view)
                        view.destroy()
                    }
                    profiles.forEach { ProfileStore.getInstance().deleteProfile(it) }
                }
            }
        }
    }

    @Test
    fun invalidTargetDoesNotDeleteAnyCookies() {
        val profile = "candy_site_invalid_${UUID.randomUUID()}"
        val view = AtomicReference<WebView>()
        composeRule.runOnIdle {
            assertTrue(WebViewSiteData.isSupported(true))
            view.set(createView(profile))
            val cookies = WebViewCompat.getProfile(view.get()).cookieManager
            cookies.setCookie("https://a.example", "fixture=retained")
            WebViewSiteData.clear(view.get(), "about:blank", true) { assertFalse(it) }
            assertTrue(cookies.getCookie("https://a.example").contains("fixture=retained"))
            view.get().destroy()
            ProfileStore.getInstance().deleteProfile(profile)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createView(profile: String): WebView = WebView(composeRule.activity).also {
        WebViewCompat.setProfile(it, profile)
        it.settings.javaScriptEnabled = true
        it.settings.domStorageEnabled = true
    }

    private fun load(view: WebView, url: String) {
        javascript(view, "window.fixtureState = null")
        composeRule.runOnIdle { view.loadUrl(url) }
        composeRule.waitUntil(15_000) {
            javascript(view, "window.fixtureState || null") != "null"
        }
    }

    private fun state(view: WebView): JSONObject =
        JSONObject(JSONTokener(javascript(view, "window.fixtureState")).nextValue() as String)

    private fun assertStored(state: JSONObject, expected: Boolean) {
        assertEquals(state.toString(), expected, state.getString("cookie").contains("fixture=value"))
        assertEquals(state.toString(), expected, state.optString("local") == "value")
        assertEquals(state.toString(), expected, state.optString("indexed") == "value")
    }

    private fun assertHttpOnlyCookie(view: WebView, site: String, expected: Boolean) {
        composeRule.runOnIdle {
            assertEquals(
                expected,
                WebViewCompat.getProfile(view).cookieManager.getCookie(site).orEmpty()
                    .contains("server_session=value"),
            )
        }
    }

    private fun captureEvidence(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "issue250").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun javascript(view: WebView, script: String): String {
        val done = CountDownLatch(1)
        val value = AtomicReference<String>()
        composeRule.runOnIdle { view.evaluateJavascript(script) { value.set(it); done.countDown() } }
        check(done.await(5, TimeUnit.SECONDS))
        return value.get()
    }

    private class FixtureServer : Closeable {
        private val socket = ServerSocket(0, 16, InetAddress.getByName("0.0.0.0"))
        val siteA = "http://127.0.0.1:${socket.localPort}"
        val siteB = "http://127.0.0.2:${socket.localPort}"
        private val worker = Thread {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                client.use {
                    val reader = it.getInputStream().bufferedReader()
                    val request = reader.readLine().orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    val seed = request.contains("/seed")
                    val body = """
                        <!doctype html><meta name="viewport" content="width=device-width"><title>Site data fixture</title>
                        <style>body{font:14px sans-serif}h1{font-size:16px}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style>
                        <body><h1 id="scope">Site data fixture</h1><pre id="result">Loading</pre><script>
                        (async () => {
                          if ($seed) {
                            document.cookie = 'fixture=value; Path=/';
                            localStorage.setItem('fixture', 'value');
                            await new Promise((resolve, reject) => {
                              const request = indexedDB.open('candy-fixture', 1);
                              request.onupgradeneeded = () => request.result.createObjectStore('values');
                              request.onerror = () => reject(request.error);
                              request.onsuccess = () => {
                                const db = request.result;
                                const tx = db.transaction('values', 'readwrite');
                                tx.objectStore('values').put('value', 'fixture');
                                tx.oncomplete = () => { db.close(); resolve(); };
                                tx.onerror = () => reject(tx.error);
                              };
                            });
                          }
                          let indexed = '';
                          if ((await indexedDB.databases()).some(db => db.name === 'candy-fixture')) {
                            indexed = await new Promise((resolve, reject) => {
                              const request = indexedDB.open('candy-fixture', 1);
                              request.onerror = () => reject(request.error);
                              request.onsuccess = () => {
                                const db = request.result;
                                const get = db.transaction('values').objectStore('values').get('fixture');
                                get.onsuccess = () => { db.close(); resolve(get.result || ''); };
                                get.onerror = () => reject(get.error);
                              };
                            });
                          }
                          window.fixtureState = JSON.stringify({cookie: document.cookie, local: localStorage.getItem('fixture') || '', indexed});
                          document.getElementById('scope').textContent = new URL(location.href).searchParams.get('scope') || 'Site data fixture';
                          document.getElementById('result').textContent = location.origin + '\n' + window.fixtureState;
                        })();
                        </script>
                    """.trimIndent().toByteArray()
                    it.getOutputStream().write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nCache-Control: no-store\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(),
                    )
                    it.getOutputStream().write(body)
                }
            }
        }.apply { isDaemon = true; start() }

        override fun close() {
            socket.close()
            worker.join(2_000)
        }
    }
}
