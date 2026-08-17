package dev.sk2andy.materialbrowser.browser.userscript

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserScriptInjectionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val webView = AtomicReference<WebView?>()

    @After
    fun tearDown() {
        destroyCurrentWebView()
    }

    private fun destroyCurrentWebView() {
        webView.getAndSet(null)?.let { view ->
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun documentStartRunsBeforeInlinePageJavaScript() {
        assumeEventInjectionSupport()
        val result = loadAndEvaluate(
            baseUrl = "https://example.com/allowed/page",
            html = """
                <html><head><script>
                document.dispatchEvent(new CustomEvent('candy-runtime-test'));
                document.documentElement.dataset.executionOrder += '|page';
                </script></head><body></body></html>
            """.trimIndent(),
            script = script(
                runAt = "document-start",
                body = """
                    document.addEventListener('candy-runtime-test', () => {
                        document.documentElement.dataset.executionOrder = 'userscript';
                    }, { once: true });
                """.trimIndent(),
            ),
            probe = "document.documentElement.dataset.executionOrder",
        )

        assertEquals("\"userscript|page\"", result)
    }

    @Test
    fun documentEndRunsOnceWithCommittedDom() {
        assumeEventInjectionSupport()
        val result = loadAndEvaluate(
            baseUrl = "https://example.com/allowed/page",
            html = "<html><body data-ready='yes'></body></html>",
            script = script(
                runAt = "document-end",
                body = """
                    const runs = Number(document.body.dataset.userscriptEndRuns || 0) + 1;
                    document.body.dataset.userscriptEndRuns = String(runs);
                    document.body.dataset.userscriptEndState =
                        document.readyState + '|' + document.body.dataset.ready;
                """.trimIndent(),
            ),
            probe = """
                document.body.dataset.userscriptEndRuns + '|' +
                    document.body.dataset.userscriptEndState
            """.trimIndent(),
        )

        assertEquals("\"1|interactive|yes\"", result)
    }

    @Test
    fun pathExcludeAndOriginBoundaryPreventExecution() {
        assumeEventInjectionSupport()
        val userScript = script(
            runAt = "document-end",
            body = "document.body.dataset.userscriptRan = 'yes';",
            extraMetadata = "// @exclude https://example.com/allowed/private/*",
        )

        assertEquals(
            "false",
            loadAndEvaluate(
                baseUrl = "https://example.com/allowed/private/page",
                html = "<html><body></body></html>",
                script = userScript,
                probe = "document.body.dataset.userscriptRan === 'yes'",
            ),
        )
        destroyCurrentWebView()
        assertEquals(
            "false",
            loadAndEvaluate(
                baseUrl = "https://notexample.com/allowed/page",
                html = "<html><body></body></html>",
                script = userScript,
                probe = "document.body.dataset.userscriptRan === 'yes'",
            ),
        )
    }

    @Test
    fun matchingIframeNeverRunsUserscript() {
        assumeEventInjectionSupport()
        val result = loadAndEvaluate(
            baseUrl = "https://example.com/allowed/page",
            html = "<html><body><iframe srcdoc='<html><body>frame</body></html>'></iframe></body></html>",
            script = script(
                runAt = "document-end",
                body = "document.body.dataset.userscriptRan = 'yes';",
            ),
            probe = """
                (document.body.dataset.userscriptRan === 'yes') + '|' +
                    (document.querySelector('iframe').contentDocument.body.dataset.userscriptRan === 'yes')
            """.trimIndent(),
        )

        assertEquals("\"true|false\"", result)
    }

    @Test
    fun restrictiveContentSecurityPolicyDoesNotBlockUserscript() {
        assumeEventInjectionSupport()
        val result = loadAndEvaluate(
            baseUrl = "https://example.com/allowed/page",
            html = """
                <html><head>
                <meta http-equiv="Content-Security-Policy" content="script-src 'none'">
                </head><body>
                <script>document.body.dataset.inlineScript = 'ran';</script>
                </body></html>
            """.trimIndent(),
            script = script(
                runAt = "document-end",
                body = "document.body.dataset.cspUserscript = 'ran';",
            ),
            probe = """
                document.body.dataset.cspUserscript + '|' +
                    (document.body.dataset.inlineScript === 'ran')
            """.trimIndent(),
        )

        assertEquals("\"ran|false\"", result)
    }

    @Test
    fun excludedTopLevelOnloadDeclarationCannotEscapeGuard() {
        assumeEventInjectionSupport()
        val result = loadAndEvaluate(
            baseUrl = "https://example.com/allowed/private/page",
            html = "<html><body></body></html>",
            script = script(
                runAt = "document-start",
                extraMetadata = "// @exclude https://example.com/allowed/private/*",
                body = """
                    function onload() {
                        document.body.dataset.escaped = 'yes';
                    }
                """.trimIndent(),
            ),
            probe = "document.body.dataset.escaped === 'yes'",
        )

        assertEquals("false", result)
    }

    @Test
    fun excludedScriptDeclarationsCannotPoisonAnotherScriptWorld() {
        assumeEventInjectionSupport()
        val excluded = script(
            id = "excluded-script",
            runAt = "document-start",
            extraMetadata = "// @exclude https://example.com/allowed/private/*",
            body = "const collision = 'excluded';",
        )
        val allowed = script(
            id = "allowed-script",
            runAt = "document-start",
            body = """
                const collision = 'allowed';
                document.addEventListener('DOMContentLoaded', () => {
                    document.body.dataset.separateWorld = collision;
                }, { once: true });
            """.trimIndent(),
        )
        val result = loadAndEvaluate(
            baseUrl = "https://example.com/allowed/private/page",
            html = "<html><body></body></html>",
            scripts = listOf(excluded, allowed),
            probe = "document.body.dataset.separateWorld",
        )

        assertEquals("\"allowed\"", result)
    }

    private fun loadAndEvaluate(
        baseUrl: String,
        html: String,
        script: UserScript,
        probe: String,
    ): String? = loadAndEvaluate(baseUrl, html, listOf(script), probe)

    private fun loadAndEvaluate(
        baseUrl: String,
        html: String,
        scripts: List<UserScript>,
        probe: String,
    ): String? {
        val loaded = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    val registrations = scripts.map { registeredScript ->
                        val sources = requireNotNull(
                            UserScriptInjection.sources(registeredScript),
                        )
                        val world = WebViewCompat.getExecutionWorld(
                            this,
                            UserScriptInjection.executionWorldName(registeredScript.id),
                        )
                        Triple(registeredScript, sources, world)
                    }
                    registrations.forEach { (registeredScript, sources, world) ->
                        WebViewCompat.addJavaScriptOnEvent(
                            this,
                            sources.guardSource,
                            WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                            UserScriptRules.allowedOriginRules(registeredScript),
                            world,
                        )
                    }
                    registrations.forEach { (registeredScript, sources, world) ->
                        WebViewCompat.addJavaScriptOnEvent(
                            this,
                            sources.userSource,
                            when (registeredScript.runAt) {
                                UserScriptRunAt.DocumentStart ->
                                    WebViewCompat.INJECTION_EVENT_DOCUMENT_START
                                UserScriptRunAt.DocumentEnd ->
                                    WebViewCompat.INJECTION_EVENT_DOCUMENT_END
                            },
                            UserScriptRules.allowedOriginRules(registeredScript),
                            world,
                        )
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            loaded.countDown()
                        }
                    }
                    loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
                },
            )
        }
        assertTrue(loaded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val evaluated = CountDownLatch(1)
        val result = AtomicReference<String?>()
        instrumentation.runOnMainSync {
            requireNotNull(webView.get()).evaluateJavascript(probe) { value ->
                result.set(value)
                evaluated.countDown()
            }
        }
        assertTrue(evaluated.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return result.get()
    }

    private fun script(
        id: String = "runtime-test",
        runAt: String,
        body: String,
        extraMetadata: String = "",
    ): UserScript {
        val source = """
            // ==UserScript==
            // @name Runtime test
            // @match https://*.example.com/allowed/*
            // @run-at $runAt
            $extraMetadata
            // ==/UserScript==
            $body
        """.trimIndent()
        return (UserScriptParser.parse(id, source) as UserScriptParseResult.Accepted)
            .script
    }

    private fun assumeEventInjectionSupport() {
        assumeTrue(
            WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD),
        )
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}
