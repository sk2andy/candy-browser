package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.HomePageSettings
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePageBrowserScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            clearSession()
        }
    }

    @Test
    fun newTabAndHomeButtonsLoadIndependentLocalPagesWithoutEditor() {
        HomePageFixtureServer().use { server ->
            lateinit var browser: BrowserController
            composeRule.runOnIdle {
                clearSession()
                browser = BrowserController(composeRule.activity).also { controller = it }
                browser.updateSearchSuggestionProvider(SearchSuggestionProvider.None)
                browser.updateAddressBarActionLayout(
                    AddressBarActionLayout(
                        beforeAddress = listOf(AddressBarAction.Home),
                        afterAddress = listOf(AddressBarAction.NewTab),
                    ),
                )
                browser.onStart()
                browser.onResume()
            }
            composeRule.setContent {
                MaterialBrowserTheme {
                    BrowserScreen(controller = browser, openAddressEditorOnLaunch = false)
                }
            }
            composeRule.onNodeWithTag(AddressBarActionTestTags.action(AddressBarAction.Home))
                .assertIsDisplayed().performClick()
            composeRule.runOnIdle { assertEquals(BLANK_URL, browser.selectedTab.url) }
            composeRule.onNodeWithTag(AddressBarTestTags.Editor).assertDoesNotExist()
            screenshot("issue350-internal-default.png")

            composeRule.runOnIdle {
                browser.updateHomePageSettings(
                    HomePageSettings("${server.baseUrl}/home", "${server.baseUrl}/new"),
                )
            }
            composeRule.onNodeWithTag(AddressBarActionTestTags.action(AddressBarAction.NewTab))
                .assertIsDisplayed().performClick()
            awaitPage(browser, server, "/new", "Candy custom new tab")
            composeRule.onNodeWithTag(AddressBarTestTags.Editor).assertDoesNotExist()
            screenshot("issue350-custom-new-tab.png")
            val customTabId = browser.selectedTabId

            composeRule.onNodeWithTag(AddressBarActionTestTags.action(AddressBarAction.Home))
                .assertIsDisplayed().performClick()
            awaitPage(browser, server, "/home", "Candy custom home")
            composeRule.runOnIdle { assertEquals(customTabId, browser.selectedTabId) }
            composeRule.onNodeWithTag(AddressBarTestTags.Editor).assertDoesNotExist()
            screenshot("issue350-custom-home.png")

            composeRule.onNodeWithTag(AddressBarActionTestTags.action(AddressBarAction.NewTab)).performClick()
            awaitPage(browser, server, "/new", "Candy custom new tab")
            composeRule.runOnIdle {
                assertTrue(customTabId != browser.selectedTabId)
                browser.createUserTab(isIncognito = true)
                assertTrue(browser.selectedTab.isIncognito)
                assertEquals(BLANK_URL, browser.selectedTab.url)
            }
            composeRule.waitUntil(timeoutMillis = 15_000L) {
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                if (bitmap == null) false else {
                    val color = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                    bitmap.recycle()
                    Color.red(color) < 128 && Color.green(color) < 128 && Color.blue(color) < 128
                }
            }
            screenshot("issue350-private-internal.png")
        }
    }

    private fun awaitPage(browser: BrowserController, server: HomePageFixtureServer, path: String, title: String) {
        try {
            composeRule.waitUntil(timeoutMillis = 60_000L) {
                browser.selectedTab.url == "${server.baseUrl}$path" &&
                    browser.selectedTab.title == title &&
                    !browser.selectedTab.isLoading &&
                    server.requests.contains(path)
            }
        } catch (error: Throwable) {
            screenshot("issue350-timeout.png")
            throw AssertionError("Expected $path; actual=${browser.selectedTab}; requests=${server.requests}", error)
        }
        composeRule.waitForIdle()
        val expectedColor = Color.parseColor(if (path == "/home") "#d7edff" else "#e6f3d5")
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if (bitmap == null) false else {
                val painted = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == expectedColor
                bitmap.recycle()
                painted
            }
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val file = File(instrumentation.targetContext.getExternalFilesDir(null), name)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun clearSession() {
        composeRule.activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private class HomePageFixtureServer : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val baseUrl = "http://127.0.0.1:${server.localPort}"
        val requests = CopyOnWriteArrayList<String>()
        private val thread = Thread(::serve, "home-page-fixture").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return
                socket.use { connection ->
                    runCatching {
                        connection.soTimeout = 2_000
                        val reader = connection.getInputStream().bufferedReader()
                        val path = reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume request headers before replying.
                        }
                        requests += path
                        val title = if (path == "/home") "Candy custom home" else "Candy custom new tab"
                        val color = if (path == "/home") "#d7edff" else "#e6f3d5"
                        val body = """
                            <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                            <title>$title</title></head><body style="background:$color;color:#102030;font-family:sans-serif;padding:32px">
                            <h1>$title</h1><p>Independent destination: $path</p><p>Local test fixture. No Internet required.</p>
                            </body></html>
                        """.trimIndent().toByteArray(Charsets.UTF_8)
                        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                        connection.getOutputStream().buffered().use { output ->
                            output.write(header.toByteArray(Charsets.UTF_8))
                            output.write(body)
                        }
                    }
                }
            }
        }

        override fun close() {
            server.close()
            thread.join(2_500L)
        }
    }
}
