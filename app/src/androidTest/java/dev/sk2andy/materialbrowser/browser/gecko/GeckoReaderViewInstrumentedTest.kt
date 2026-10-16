package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.reader.ReaderExtractionParser
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class GeckoReaderViewInstrumentedTest {
    @Test
    fun readerBridgeTargetsTopFrameAndBoundsPayloadBeforeNativeMessaging() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val background = context.assets.open("candy_privacy/background.js")
            .bufferedReader()
            .use { reader -> reader.readText() }
        val content = context.assets.open("candy_privacy/content.js")
            .bufferedReader()
            .use { reader -> reader.readText() }

        assertTrue(background.contains("{ frameId: 0 }"))
        assertTrue(background.contains("policy.revision !== message.revision"))
        assertTrue(background.contains("Number.isSafeInteger(message.requestId)"))
        assertTrue(content.contains("self === top && message.type === \"reader-extract\""))
        assertTrue(content.contains("content, 500"))
        assertTrue(content.contains("content, 200"))
        assertTrue(content.contains("textContent, 300"))
        assertTrue(content.contains("slice(0, 2048)"))
    }

    @Test
    fun loadedGeckoArticleCanBeExtractedForReaderView() {
        assertLoadedArticleCanBeExtracted(isPrivate = false)
    }

    @Test
    fun loadedPrivateGeckoArticleCanBeExtractedWithoutChangingTheContract() {
        assertLoadedArticleCanBeExtracted(isPrivate = true)
    }

    @Test
    fun overlappingGeckoReaderRequestsEachCompleteExactlyOnce() {
        withLoadedSession(isPrivate = false, profileSuffix = "overlap") { session, _ ->
            val firstFinished = CountDownLatch(1)
            val secondFinished = CountDownLatch(1)
            var firstCallbackCount = 0
            var secondCallbackCount = 0
            val secondResult = AtomicReference<String?>()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.extractPageForReader {
                    firstCallbackCount += 1
                    firstFinished.countDown()
                }
                session.extractPageForReader { result ->
                    secondCallbackCount += 1
                    secondResult.set(result)
                    secondFinished.countDown()
                }
            }

            assertTrue(
                "First Gecko reader request did not finish",
                firstFinished.await(5, TimeUnit.SECONDS),
            )
            assertTrue(
                "Second Gecko reader request did not finish",
                secondFinished.await(20, TimeUnit.SECONDS),
            )
            assertEquals(1, firstCallbackCount)
            assertEquals(1, secondCallbackCount)
            assertTrue(
                ReaderExtractionParser.parseJson(secondResult.get()) is
                    ReaderExtractionResult.Success,
            )
        }
    }

    @Test
    fun visiblePageTextExtractsRawBoundedJsonWithoutNavigationOrFormContent() {
        withLoadedSession(isPrivate = false, profileSuffix = "page-text") { session, server ->
            val finished = CountDownLatch(1)
            val result = AtomicReference<String?>()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.bindToppingSession("page-text", BrowserEngineContentKind.RegularTab)
                session.extractPageText {
                    result.set(it)
                    finished.countDown()
                }
            }
            assertTrue("Visible page text did not finish", finished.await(20, TimeUnit.SECONDS))
            val payload = JSONObject(requireNotNull(result.get()))
            assertEquals(server.url, payload.getString("sourceUrl"))
            assertEquals("Gecko Reader Story", payload.getString("title"))
            assertTrue(payload.getString("text").contains("Readable article text"))
            assertTrue(!payload.getString("text").contains("Navigation must disappear"))
            assertTrue(!payload.getString("text").contains("Private draft must disappear"))
            assertTrue(!payload.getString("text").contains("Hidden ancestor must disappear"))
            assertTrue(!payload.getString("text").contains("Hidden content must disappear"))
            assertTrue(!payload.getString("text").contains("Editable descendant must disappear"))
            assertTrue(payload.getString("text").length <= 64_000)
        }
    }

    @Test
    fun visiblePageTextRejectsPrivateAndPreviewSessions() {
        for ((isPrivate, kind) in listOf(
            true to BrowserEngineContentKind.RegularTab,
            false to BrowserEngineContentKind.LinkPeek,
            false to BrowserEngineContentKind.ExternalPreview,
        )) {
            withLoadedSession(isPrivate, "page-text-rejected-${kind.name}") { session, _ ->
                val finished = CountDownLatch(1)
                val result = AtomicReference<String?>("not-completed")
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    session.bindToppingSession("page-text-rejected", kind)
                    session.extractPageText {
                        result.set(it)
                        finished.countDown()
                    }
                }
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertNull(result.get())
            }
        }
    }

    private fun assertLoadedArticleCanBeExtracted(isPrivate: Boolean) {
        val extractionFinished = CountDownLatch(1)
        val rawResult = AtomicReference<String?>()
        withLoadedSession(isPrivate = isPrivate, profileSuffix = "extract") { session, server ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.extractPageForReader { result ->
                    rawResult.set(result)
                    extractionFinished.countDown()
                }
            }
            assertTrue(
                "Gecko reader extraction did not finish",
                extractionFinished.await(20, TimeUnit.SECONDS),
            )

            val result = ReaderExtractionParser.parseJson(rawResult.get())
            val payload = JSONObject(requireNotNull(rawResult.get()))

            assertTrue(result is ReaderExtractionResult.Success)
            assertTrue(payload.getBoolean("hasVisibleContent"))
            assertTrue(payload.getString("visibleText").contains("Gecko Reader Story"))
            val document = (result as ReaderExtractionResult.Success).document
            assertEquals("Gecko Reader Story", document.title)
            assertEquals(server.url, document.sourceUrl)
            assertEquals(listOf("https://example.com/more"), document.blocks.last().links.map { it.url })
        }
    }

    private fun withLoadedSession(
        isPrivate: Boolean,
        profileSuffix: String,
        action: (GeckoBrowserSession, ReaderFixtureServer) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = ReaderFixtureServer()
        val pageLoaded = CountDownLatch(1)
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            session = GeckoRuntimeOwner.getOrCreate(context).createSession(
                profileId = "gecko-reader-$profileSuffix${if (isPrivate) "-private" else ""}",
                isPrivate = isPrivate,
                privacyPolicy = GeckoPrivacyPolicy.Disabled,
            )
            view = session.createView(context)
            session.setStateListener { state ->
                if (state.url == server.url && state.lastNavigationSucceeded == true) {
                    pageLoaded.countDown()
                }
            }
            assertTrue(session.loadUrl(server.url))
        }
        try {
            assertTrue("Gecko reader fixture did not load", pageLoaded.await(20, TimeUnit.SECONDS))
            action(session, server)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.releaseView(view)
                session.close()
            }
            server.close()
        }
    }
}

private class ReaderFixtureServer : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val worker = thread(name = "gecko-reader-fixture", isDaemon = true) {
        while (!server.isClosed) {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    val body = """
                        <!doctype html>
                        <html>
                          <head>
                            <title>Fallback title</title>
                            <meta property="og:title" content="Gecko Reader Story">
                            <meta property="og:site_name" content="Candy Fixture">
                          </head>
                          <body>
                            <nav>Navigation must disappear</nav>
                            <form><textarea>Private draft must disappear</textarea></form>
                            <div style="opacity:0"><span>Hidden ancestor must disappear</span></div>
                            <div style="content-visibility:hidden"><span>Hidden content must disappear</span></div>
                            <div contenteditable="true"><span contenteditable="false">Editable descendant must disappear</span></div>
                            <article>
                              <h1>Gecko Reader Story</h1>
                              <p>Readable article text from GeckoView with enough detail to pass the bounded Reader Studio extraction contract.</p>
                              <p>Second paragraph links to <a href="https://example.com/more">more context</a> and rejects <a href="javascript:alert(1)">unsafe code</a>.</p>
                            </article>
                          </body>
                        </html>
                    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
                    socket.getOutputStream().apply {
                        write(
                            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n".toByteArray(
                                StandardCharsets.US_ASCII,
                            ),
                        )
                        write(
                            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(
                                StandardCharsets.US_ASCII,
                            ),
                        )
                        write(body)
                        flush()
                    }
                }
            } catch (_: SocketException) {
                if (!server.isClosed) throw AssertionError("Reader fixture server failed")
            }
        }
    }

    val url = "http://127.0.0.1:${server.localPort}/article"

    override fun close() {
        server.close()
        worker.join(2_000)
    }
}
