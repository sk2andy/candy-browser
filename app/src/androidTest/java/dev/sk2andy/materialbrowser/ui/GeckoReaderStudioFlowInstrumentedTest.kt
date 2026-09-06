package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.reader.ReaderLibraryStore
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoReaderStudioFlowInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    init {
        clearPreferences()
        ReaderLibraryStore(context).clear()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        composeRule.activityRule.scenario.close()
        clearPreferences()
        ReaderLibraryStore(context).clear()
    }

    @Test
    fun geckoReaderMenuActionIsEnabledAndOpensExtractedArticle() {
        ReaderStudioFixtureServer().use { server ->
            composeRule.onNodeWithContentDescription(
                context.getString(R.string.cd_close_address_input),
            ).performClick()
            composeRule.activityRule.scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.usesGeckoEngine)
                controller.submitAddress(server.url)
            }
            awaitPageLoaded(server.url)

            composeRule.onNodeWithContentDescription(
                context.getString(R.string.cd_more_options),
            ).performClick()
            composeRule.onNodeWithText(context.getString(R.string.reader_open_action))
                .assertIsDisplayed()
                .assertIsEnabled()
                .performClick()

            composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithTag(ReaderStudioTestTags.Article)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.onNodeWithText("Gecko Reader Story").assertIsDisplayed()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertIsDisplayed()
        }
    }

    private fun clearPreferences() {
        listOf(
            BrowserSessionStore.PREFERENCES_NAME,
            GestureOnboardingStore.PREFERENCES_NAME,
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun awaitPageLoaded(expectedUrl: String) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            var loaded = false
            composeRule.activityRule.scenario.onActivity { activity ->
                val tab = activity.browserControllerForTesting().selectedTab
                loaded = tab.url == expectedUrl && !tab.isLoading
            }
            if (loaded) {
                composeRule.waitForIdle()
                return
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("Gecko Reader Studio fixture did not load")
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
    }
}

private class ReaderStudioFixtureServer : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val worker = thread(name = "gecko-reader-studio-fixture", isDaemon = true) {
        while (!server.isClosed) {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    val body = """
                        <!doctype html>
                        <html>
                          <head><meta property="og:title" content="Gecko Reader Story"></head>
                          <body>
                            <article>
                              <h1>Gecko Reader Story</h1>
                              <p>Reader Studio now receives enough meaningful article text from the selected GeckoView session to render this document.</p>
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
                if (!server.isClosed) throw AssertionError("Reader Studio fixture server failed")
            }
        }
    }

    val url = "http://127.0.0.1:${server.localPort}/article"

    override fun close() {
        server.close()
        worker.join(2_000)
    }
}
