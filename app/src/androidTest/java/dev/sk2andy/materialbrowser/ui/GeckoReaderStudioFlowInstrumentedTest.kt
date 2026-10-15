package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.test.espresso.Espresso
import android.content.pm.ActivityInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasClickAction
import org.junit.Assert.assertFalse
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
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("wm user-rotation lock 0").close()
        composeRule.activityRule.scenario.close()
        clearPreferences()
        ReaderLibraryStore(context).clear()
    }

    @Test
    fun geckoReaderMenuActionIsEnabledAndOpensExtractedArticle() {
        ReaderStudioFixtureServer().use { server ->
            composeRule.waitForIdle()
            val closeInput = context.getString(R.string.cd_close_address_input)
            if (composeRule.onAllNodesWithContentDescription(closeInput).fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithContentDescription(closeInput).performClick()
            }
            composeRule.activityRule.scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.usesGeckoEngine)
                controller.submitAddress(server.url)
            }
            awaitPageLoaded(server.url)

            composeRule.onNodeWithContentDescription(
                context.getString(R.string.cd_more_options),
            ).performClick()
            composeRule.waitUntil(TIMEOUT_MILLIS) {
                composeRule.onNodeWithText(context.getString(R.string.reader_open_action)).isDisplayed()
            }
            composeRule.onNodeWithText(context.getString(R.string.reader_open_action))
                .assertIsDisplayed()
                .assertIsEnabled()
                .performClick()

            composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithTag(ReaderStudioTestTags.Article)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertIsDisplayed()
            val viewport = composeRule.onNodeWithTag(ReaderStudioTestTags.Article).fetchSemanticsNode().boundsInRoot
            assertTrue("Reader viewport must start at the window top", viewport.top == 0f)
            composeRule.onNode(
                hasText("127.0.0.1") and hasAnyAncestor(hasTestTag(ReaderStudioTestTags.Screen)),
            ).assertDoesNotExist()
            var statusInset = 0
            composeRule.activityRule.scenario.onActivity { activity ->
                statusInset = ViewCompat.getRootWindowInsets(activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.statusBars()).top
            }
            assertTrue(composeRule.onNodeWithTag(ReaderStudioTestTags.ArticleTitle).fetchSemanticsNode().boundsInRoot.top >= statusInset)
            capture("paper-default")
            openSettings()
            composeRule.onNodeWithText(context.getString(R.string.reader_theme_paper)).assertIsSelected()
            composeRule.onNodeWithText(context.getString(R.string.reader_theme_light)).performScrollTo().performClick()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Continue).performScrollTo().performClick()
            capture("light")
            composeRule.onNodeWithTag(ReaderStudioTestTags.Article).performTouchInput { swipeUp() }
            assertTrue(composeRule.onNodeWithTag(ReaderStudioTestTags.Article).fetchSemanticsNode().boundsInRoot.top == 0f)
            val scrim = composeRule.onNodeWithTag(ReaderStudioTestTags.StatusBarScrim).fetchSemanticsNode().boundsInRoot
            assertTrue(scrim.top == 0f && scrim.height > 0f)
            capture("status-scroll-light")
            composeRule.onNodeWithTag(ReaderStudioTestTags.Url).performTouchInput { click() }
            composeRule.activityRule.scenario.onActivity { activity ->
                assertFalse(ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
            }
            repeat(4) {
                composeRule.onNodeWithTag(ReaderStudioTestTags.Toggle).performClick()
                composeRule.onNodeWithTag(ReaderStudioTestTags.Url).assertDoesNotExist()
                composeRule.onNodeWithTag(ReaderStudioTestTags.Toggle).performClick()
                composeRule.onNodeWithTag(ReaderStudioTestTags.Url).assertIsDisplayed()
            }
            openSettings()
            composeRule.onNodeWithText(context.getString(R.string.reader_theme_night)).performScrollTo().performClick()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Continue).performScrollTo().performClick()
            composeRule.activityRule.scenario.onActivity { activity ->
                val systemBars = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                assertFalse(systemBars.isAppearanceLightStatusBars)
                assertFalse(systemBars.isAppearanceLightNavigationBars)
            }
            capture("night")
            composeRule.onNodeWithTag(ReaderStudioTestTags.Article).performTouchInput { swipeUp() }
            capture("status-scroll-night")
            openSettings()
            composeRule.onNodeWithText(context.getString(R.string.reader_font_serif)).performScrollTo().performClick()
            composeRule.onNodeWithText(context.getString(R.string.reader_theme_paper)).performScrollTo().performClick()
            capture("sheet")
            Espresso.pressBack()
            composeRule.waitUntil(TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithTag(ReaderStudioTestTags.Sheet).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag(ReaderStudioTestTags.Sheet).assertDoesNotExist()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertIsDisplayed()
            capture("paper")
            openSettings()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Save).performScrollTo().performClick()
            composeRule.waitUntil(TIMEOUT_MILLIS) { ReaderLibraryStore(context).load(false).snapshots.any { it.document.sourceUrl == server.url } }
            composeRule.onNodeWithTag(ReaderStudioTestTags.Library).performScrollTo().performClick()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertDoesNotExist()
            capture("library")
            openSettings()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Hide).performScrollTo().performClick()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Pill).assertDoesNotExist()
            Espresso.pressBack()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Pill).assertIsDisplayed()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertDoesNotExist()
            capture("library-restored")
            composeRule.onAllNodes(
                hasText("Gecko Reader Story") and hasClickAction() and
                    hasAnyAncestor(hasTestTag(ReaderStudioTestTags.Screen)),
            ).onFirst().performClick()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertIsDisplayed()
            openSettings()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Hide).performScrollTo().performClick()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Pill).assertDoesNotExist()
            capture("hidden")
            composeRule.onNodeWithTag(ReaderStudioTestTags.Article).performTouchInput { click(); swipeUp() }
            composeRule.onNodeWithTag(ReaderStudioTestTags.Pill).assertDoesNotExist()
            Espresso.pressBack()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Pill).assertIsDisplayed()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Url).assertDoesNotExist()
            capture("compact")
            rotate(landscape = true)
            composeRule.onNodeWithTag(ReaderStudioTestTags.Url).assertDoesNotExist()
            openSettings()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Typeface).assertIsDisplayed()
            capture("landscape-sheet")
            Espresso.pressBack()
            composeRule.waitUntil(TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithTag(ReaderStudioTestTags.Sheet).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag(ReaderStudioTestTags.Sheet).assertDoesNotExist()
            capture("landscape")
            rotate(landscape = false)
            composeRule.onNodeWithTag(ReaderStudioTestTags.Url).assertDoesNotExist()
            openSettings()
            composeRule.onNodeWithText(context.getString(R.string.reader_original)).performScrollTo().performClick()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Screen).assertDoesNotExist()
            composeRule.activityRule.scenario.onActivity { activity ->
                assertTrue(activity.browserControllerForTesting().selectedTab.url == server.url)
            }
            composeRule.onNodeWithContentDescription(context.getString(R.string.cd_more_options)).performClick()
            composeRule.waitUntil(TIMEOUT_MILLIS) {
                composeRule.onNodeWithText(context.getString(R.string.reader_open_action)).isDisplayed()
            }
            composeRule.onNodeWithText(context.getString(R.string.reader_open_action))
                .assertIsDisplayed()
                .performClick()
            composeRule.waitUntil(TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithTag(ReaderStudioTestTags.Article).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("↗ Weiter zum nächsten Artikel").performScrollTo()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Article).performTouchInput { swipeUp() }
            capture("link")
            composeRule.onNodeWithText("↗ Weiter zum nächsten Artikel").performClick()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Screen).assertDoesNotExist()
            awaitPageLoaded(server.url.replace("/article", "/next"))
            capture("navigation")
        }
    }

    private fun openSettings() {
        composeRule.onNodeWithTag(ReaderStudioTestTags.Settings).performClick()
        var lastBounds: androidx.compose.ui.geometry.Rect? = null
        var stableSince = SystemClock.elapsedRealtime()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            val bounds = composeRule.onAllNodesWithTag(ReaderStudioTestTags.Sheet)
                .fetchSemanticsNodes().singleOrNull()?.boundsInRoot
            if (bounds == null || bounds != lastBounds) {
                lastBounds = bounds
                stableSince = SystemClock.elapsedRealtime()
                false
            } else {
                SystemClock.elapsedRealtime() - stableSince >= 350
            }
        }
    }

    private fun rotate(landscape: Boolean) {
        composeRule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "wm user-rotation lock ${if (landscape) 1 else 0}",
        ).close()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            val bounds = composeRule.onNodeWithTag(ReaderStudioTestTags.Screen).fetchSemanticsNode().boundsInRoot
            (bounds.width > bounds.height) == landscape
        }
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        android.os.SystemClock.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val phase = InstrumentationRegistry.getArguments().getString("evidence_phase", "after")
        val directory = java.io.File(context.getExternalFilesDir(null), "issue301").apply { mkdirs() }
        java.io.File(directory, "$phase-$name.png").outputStream().use {
            check(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
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
                              <h1>Die Kunst, langsamer zu lesen</h1>
                              ${(1..16).joinToString("\n") { "<p>In einer Welt, die ständig nach mehr Geschwindigkeit verlangt, wirkt langsames Lesen fast schon revolutionär. Dabei ist es vor allem eines: eine bewusste Entscheidung für mehr Tiefe, mehr Verständnis und mehr Freude an guten Gedanken.</p>" }}
                              <p><a href="/next">Weiter zum nächsten Artikel</a></p>
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
