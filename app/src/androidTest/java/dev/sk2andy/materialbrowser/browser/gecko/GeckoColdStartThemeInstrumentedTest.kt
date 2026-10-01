package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.io.FileInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoColdStartThemeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun coldSystemDarkReachesFirstInlineWebsiteScriptWithoutALaterThemeCorrection() {
        // Run this class alone in a fresh instrumentation process with system night already set.
        assertFalse("Cold-start check requires a fresh process", GeckoRuntimeOwner.hasRuntimeForTesting())
        assertEquals("yes", shell("cmd uimode night").substringAfter(':').trim())
        assertEquals(
            Configuration.UI_MODE_NIGHT_YES,
            context.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
        )
        val preferences = context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val originalPreferences = preferences.all
        try {
            assertTrue(
                preferences.edit().clear().putString(
                    BrowserSessionStore.KEY_ANDROID_BROWSER_ENGINE,
                    AndroidBrowserEngineKind.GeckoView.stableId,
                ).commit(),
            )
            BrowserSessionStore(context).saveExternalLinkPreviewEnabled(false)
            FirstInlineThemeServer().use { server ->
                val intent = Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse(server.url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                    val title = AtomicReference<String>()
                    val loaded = AtomicReference(false)
                    awaitCondition("Initial document did not complete: ${server.loadReport.get()}") {
                        scenario.onActivity { activity ->
                            val controller = activity.browserControllerForTesting()
                            val tab = controller.selectedTabForTesting()
                            title.set(tab.title)
                            loaded.set(tab.url == server.url && !tab.isLoading)
                        }
                        loaded.get() && server.loadReport.get() != null
                    }
                    // Native media-query notifications can arrive after the load callback. The
                    // title never changes, so this observation cannot hide an initially light page.
                    SystemClock.sleep(750L)
                    val report = requireNotNull(server.loadReport.get())
                    Log.i("CandyGeckoColdTheme", "first=$report changes=${server.themeChanges.get()} title=${title.get()}")
                    assertEquals("theme-first-dark", title.get())
                    assertEquals("dark", report.initial)
                    assertEquals("dark", report.current)
                    assertEquals(0, report.changes)
                    assertEquals(0, server.themeChanges.get())
                    assertEquals("First navigation must not rely on a reload", 1, server.pageRequests.get())
                    scenario.onActivity { activity ->
                        assertEquals(
                            BrowserAppearanceMode.System,
                            activity.browserControllerForTesting().appearanceSettings.appearanceMode,
                        )
                        assertEquals(
                            Configuration.UI_MODE_NIGHT_YES,
                            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
                        )
                    }
                }
            }
        } finally {
            preferences.edit().clear().apply {
                originalPreferences.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }.commit()
        }
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50L)
        }
        assertTrue(message, condition())
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { output ->
            FileInputStream(output.fileDescriptor).bufferedReader().use { it.readText() }
        }

    private data class ThemeReport(val initial: String?, val current: String?, val changes: Int?)

    private class FirstInlineThemeServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val pageRequests = AtomicInteger()
        val themeChanges = AtomicInteger()
        val loadReport = AtomicReference<ThemeReport>()
        val url = "http://127.0.0.1:${socket.localPort}/"
        private val thread = Thread({ serve() }, "gecko-first-inline-theme-fixture").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        val path = reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume request headers.
                        }
                        val request = Uri.parse(path)
                        val isPage = path == "/"
                        if (isPage) pageRequests.incrementAndGet()
                        if (request.path == "/probe") {
                            val changes = request.getQueryParameter("changes")?.toIntOrNull()
                            if (changes != null) themeChanges.updateAndGet { maxOf(it, changes) }
                            if (request.getQueryParameter("phase") == "load") {
                                loadReport.set(
                                    ThemeReport(
                                        initial = request.getQueryParameter("initial"),
                                        current = request.getQueryParameter("current"),
                                        changes = changes,
                                    ),
                                )
                            }
                        }
                        val body = if (isPage) pageBody().toByteArray() else ByteArray(0)
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                            write("Cache-Control: no-store\r\n".toByteArray())
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

        private fun pageBody(): String = """
            <!doctype html><html><head><script>
            const scheme = matchMedia('(prefers-color-scheme: dark)');
            const initial = scheme.matches ? 'dark' : 'light';
            document.title = 'theme-first-' + initial;
            let changes = 0;
            const report = phase => {
                const query = new URLSearchParams({
                    phase, initial, current: scheme.matches ? 'dark' : 'light', changes: String(changes)
                });
                fetch('/probe?' + query, {cache: 'no-store'});
            };
            scheme.addEventListener('change', () => { changes++; report('change'); });
            window.addEventListener('load', () => report('load'));
            </script><style>
            :root { color-scheme: light dark; }
            body { background: white; color: black; }
            @media (prefers-color-scheme: dark) { body { background: black; color: white; } }
            </style></head><body>first inline theme probe</body></html>
        """.trimIndent()

        override fun close() {
            socket.close()
            thread.join(1_000L)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
    }
}
