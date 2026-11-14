package dev.sk2andy.materialbrowser.reader

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderExtractionScriptInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scripts: List<String> by lazy {
        val geckoSource = instrumentation.targetContext.assets
            .open("candy_privacy/content.js").bufferedReader().use { it.readText() }
        val geckoFunction = geckoSource.substringAfter("function extractCandyReaderPayload() {")
            .substringBefore("\nbrowser.runtime.onMessage.addListener")
        listOf(
            ReaderExtractionScript.javascript,
            "(() => { function extractCandyReaderPayload() {" + geckoFunction +
                "return JSON.stringify(extractCandyReaderPayload()); })()",
        )
    }

    @Test
    fun nestedBlocksKeepSurroundingTextAndLinksOnceInReadingOrder() {
        for (script in scripts) {
            val blocks = extract(
                """
                    <article><h2>Heading</h2><p>First line<br>Second line</p>
                    <blockquote>Before <a href="/lead">quote</a><p>Nested paragraph</p>After quote</blockquote>
                    <ul><li>Before item<p>Nested <a href="/nested">item</a></p>After item</li></ul>
                    </article>
                """.trimIndent(),
                script,
            ).getJSONArray("blocks")
            assertEquals(
                listOf(
                    "Heading", "First line Second line", "Before quote", "Nested paragraph",
                    "After quote", "Before item", "Nested item", "After item",
                ),
                (0 until blocks.length()).map { blocks.getJSONObject(it).getString("text") },
            )
            assertEquals("heading", blocks.getJSONObject(0).getString("kind"))
            assertEquals("quote", blocks.getJSONObject(2).getString("kind"))
            assertEquals("listitem", blocks.getJSONObject(5).getString("kind"))
            assertEquals(
                "https://reader.test/lead",
                blocks.getJSONObject(2).getJSONArray("links").getJSONObject(0).getString("url"),
            )
            assertEquals(
                "https://reader.test/nested",
                blocks.getJSONObject(6).getJSONArray("links").getJSONObject(0).getString("url"),
            )
            assertEquals(0, blocks.getJSONObject(5).getJSONArray("links").length())
        }
    }

    @Test
    fun originalStylesExcludeHiddenBlocksInlineTextAndLinks() {
        for (script in scripts) {
            val blocks = extract(
                """
                    <style>.responsive {display:none}</style><article>
                    <p>Visible <span class="responsive">HIDDEN_INLINE</span>article <a href="/visible">link</a>
                    <a hidden href="/hidden">HIDDEN_LINK</a></p>
                    <div hidden><p>HIDDEN_PARENT</p></div>
                    <p class="responsive">HIDDEN_STYLESHEET</p>
                    <p style="display:none">HIDDEN_DISPLAY</p>
                    <div style="opacity:0"><p>HIDDEN_TRANSPARENT_PARENT</p></div>
                    <p style="visibility:hidden">HIDDEN_VISIBILITY</p>
                    <div style="content-visibility:hidden"><p>HIDDEN_CONTENT</p></div>
                    <p aria-hidden="true">HIDDEN_ACCESSIBILITY</p></article>
                """.trimIndent(),
                script,
            ).getJSONArray("blocks")
            assertEquals(1, blocks.length())
            assertEquals("Visible article link", blocks.getJSONObject(0).getString("text"))
            val links = blocks.getJSONObject(0).getJSONArray("links")
            assertEquals(1, links.length())
            assertEquals("https://reader.test/visible", links.getJSONObject(0).getString("url"))
        }
    }

    @Test
    fun hiddenAncestorOutsideSelectedArticleExcludesItsText() {
        for (script in scripts) {
            val payload = extract(
                "<main style=\"display:none\"><article><p>Hidden article</p></article></main>",
                script,
            )
            assertEquals(0, payload.getJSONArray("blocks").length())
        }
    }

    @Test
    fun extractionKeepsExistingBlockAndCharacterBounds() {
        for (script in scripts) {
            val manyBlocks = extract("<article>" + "<p>Paragraph</p>".repeat(605) + "</article>", script)
                .getJSONArray("blocks")
            assertEquals(600, manyBlocks.length())
            val longBlocks = extract(
                "<article>" + ("<p>" + "x".repeat(12_100) + "</p>").repeat(45) + "</article>",
                script,
            ).getJSONArray("blocks")
            val lengths = (0 until longBlocks.length()).map {
                longBlocks.getJSONObject(it).getString("text").length
            }
            assertTrue(lengths.all { it <= 12_000 })
            assertEquals(500_000, lengths.sum())
        }
    }

    private fun extract(html: String, script: String): JSONObject {
        val loaded = CountDownLatch(1)
        val result = AtomicReference<String>()
        val evaluated = CountDownLatch(1)
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL("https://reader.test/", html, "text/html", "utf-8", null)
            }
        }
        try {
            assertTrue("Reader fixture loaded", loaded.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(script) { value ->
                    result.set(value)
                    evaluated.countDown()
                }
            }
            assertTrue("Reader extraction completed", evaluated.await(5, TimeUnit.SECONDS))
            return JSONObject(JSONArray("[" + result.get() + "]").getString(0))
        } finally {
            instrumentation.runOnMainSync { webView.destroy() }
        }
    }
}
