package dev.sk2andy.materialbrowser.browser.userscript

import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.data.UserScriptValueStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserScriptRuntimeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scriptId = "gm-runtime-test"
    private val webView = AtomicReference<WebView?>()
    private lateinit var valueStore: UserScriptValueStore
    private lateinit var runtime: UserScriptRuntime

    @Before
    fun setUp() {
        assumeTrue(
            WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD),
        )
        valueStore = UserScriptValueStore(instrumentation.targetContext)
        valueStore.clear(scriptId)
        runtime = UserScriptRuntime(valueStore)
    }

    @After
    fun tearDown() {
        webView.getAndSet(null)?.let { view ->
            instrumentation.runOnMainSync {
                runtime.remove(view)
                view.destroy()
            }
        }
        if (::valueStore.isInitialized) valueStore.clear(scriptId)
    }

    @Test
    fun grantedApisPersistValuesWithoutExposingBridgeToPageWorld() {
        valueStore.set(scriptId, "previous", "\"saved\"")
        val script = script(
            grants = """
                // @grant GM.addStyle
                // @grant GM.getValue
                // @grant GM.setValue
                // @grant GM.listValues
            """.trimIndent(),
            body = """
                GM.addStyle('body { --candy-userscript-test: ready; }');
                GM.setValue('theme', 'dark')
                    .then(() => Promise.all([
                        GM.getValue('previous', 'missing'),
                        GM.listValues(),
                    ]))
                    .then(([previous, keys]) => {
                        document.body.dataset.gmResult =
                            previous + '|' + keys.sort().join(',');
                    });
            """.trimIndent(),
        )

        load(script, isPrivate = false)

        assertTrue(waitForEvaluation("document.body.dataset.gmResult !== undefined"))
        assertEquals(
            "\"saved|previous,theme|undefined\"",
            evaluate(
                """
                    document.body.dataset.gmResult + '|' +
                        typeof globalThis.${UserScriptBridgeContract.BRIDGE_NAME}
                """.trimIndent(),
            ),
        )
        assertTrue(waitForStoredValue("theme", "\"dark\""))
    }

    @Test
    fun privateWebViewGetsNeitherScriptNorBridge() {
        load(
            script = script(
                grants = "// @grant GM_setValue",
                body = "document.body.dataset.userscriptRan = 'yes';",
            ),
            isPrivate = true,
        )

        assertEquals(
            "\"false|undefined\"",
            evaluate(
                """
                    (document.body.dataset.userscriptRan === 'yes') + '|' +
                        typeof globalThis.${UserScriptBridgeContract.BRIDGE_NAME}
                """.trimIndent(),
            ),
        )
        assertTrue(valueStore.snapshot(scriptId).isEmpty())
    }

    @Test
    fun legacyMutationsStaySynchronouslyVisibleWhilePersistenceIsQueued() {
        load(
            script = script(
                grants = """
                    // @grant GM_getValue
                    // @grant GM_setValue
                    // @grant GM_deleteValue
                    // @grant GM_listValues
                """.trimIndent(),
                body = """
                    GM_setValue('a', 1);
                    GM_setValue('b', 2);
                    GM_deleteValue('a');
                    document.body.dataset.legacyResult =
                        GM_getValue('b', 0) + '|' + GM_listValues().join(',');
                """.trimIndent(),
            ),
            isPrivate = false,
        )

        assertEquals("\"2|b\"", evaluate("document.body.dataset.legacyResult"))
        assertTrue(waitForStoredSnapshot(mapOf("b" to "2")))
    }

    @Test
    fun menuCallbackAndOpenTabStayBoundToTheInstalledDocument() {
        val commands = AtomicReference<List<UserScriptMenuCommand>>(emptyList())
        val openTab = AtomicReference<UserScriptOpenTabRequest?>()
        runtime = UserScriptRuntime(
            valueStore = valueStore,
            onMenuCommandsChanged = { _, current -> commands.set(current) },
            onOpenTab = openTab::set,
        )
        load(
            script = script(
                grants = """
                    // @grant GM_registerMenuCommand
                    // @grant GM_unregisterMenuCommand
                    // @grant GM_openInTab
                """.trimIndent(),
                body = """
                    GM_registerMenuCommand('Run helper', () => {
                        document.body.dataset.menuInvoked = 'yes';
                    });
                    GM_openInTab('/next', { active: true });
                """.trimIndent(),
            ),
            isPrivate = false,
        )

        assertTrue(
            "commands=${commands.get()} openTab=${openTab.get()}",
            waitUntil { commands.get().size == 1 && openTab.get() != null },
        )
        assertEquals("Run helper", commands.get().single().caption)
        assertEquals("https://example.com/next", openTab.get()?.url)
        assertEquals(true, openTab.get()?.active)
        instrumentation.runOnMainSync { runtime.invokeMenuCommand(commands.get().single()) }
        assertTrue(waitForEvaluation("document.body.dataset.menuInvoked === 'yes'"))
    }

    private fun load(script: UserScript, isPrivate: Boolean) {
        val loaded = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    runtime.install(
                        tabId = "runtime-test-tab",
                        webView = this,
                        scripts = listOf(script),
                        isPrivate = isPrivate,
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            loaded.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://example.com/allowed/page",
                        "<html><body></body></html>",
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(loaded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    private fun evaluate(source: String): String? {
        val evaluated = CountDownLatch(1)
        val result = AtomicReference<String?>()
        instrumentation.runOnMainSync {
            requireNotNull(webView.get()).evaluateJavascript(source) { value ->
                result.set(value)
                evaluated.countDown()
            }
        }
        assertTrue(evaluated.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return result.get()
    }

    private fun waitForStoredValue(key: String, value: String): Boolean {
        val deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (SystemClock.uptimeMillis() < deadline) {
            if (valueStore.snapshot(scriptId)[key] == value) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun waitForStoredSnapshot(expected: Map<String, String>): Boolean {
        val deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (SystemClock.uptimeMillis() < deadline) {
            if (valueStore.snapshot(scriptId) == expected) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun waitForEvaluation(source: String): Boolean {
        val deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(source) == "true") return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun waitUntil(predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun script(grants: String, body: String): UserScript {
        val source = """
            // ==UserScript==
            // @name GM runtime test
            // @match https://example.com/allowed/*
            // @run-at document-end
            $grants
            // ==/UserScript==
            $body
        """.trimIndent()
        return (UserScriptParser.parse(scriptId, source) as UserScriptParseResult.Accepted).script
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val POLL_INTERVAL_MILLIS = 25L
    }
}
