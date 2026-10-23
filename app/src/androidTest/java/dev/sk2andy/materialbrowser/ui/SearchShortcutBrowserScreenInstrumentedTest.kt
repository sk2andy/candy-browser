package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.SearchShortcut
import dev.sk2andy.materialbrowser.browser.SearxngSettings
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchShortcutBrowserScreenInstrumentedTest {
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
    fun typedShortcutBecomesChipAndSubmitsQueryToItsWebsite() {
        lateinit var browser: BrowserController
        composeRule.runOnIdle {
            clearSession()
            browser = BrowserController(composeRule.activity)
            controller = browser
            browser.updateSearchShortcuts(listOf(SearchShortcut("gh", "https://github.com/search?q=%s")))
            browser.updateSearchSuggestionProvider(SearchSuggestionProvider.None)
            browser.onStart()
            browser.onResume()
        }
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(controller = browser, openAddressEditorOnLaunch = true)
            }
        }
        composeRule.onNodeWithTag(AddressBarTestTags.Editor).performTextInput("!gh ")
        composeRule.onNodeWithTag(AddressSearchShortcutTestTags.Chip).assertIsDisplayed()
        composeRule.onNodeWithTag(AddressBarTestTags.Editor).performImeAction()
        composeRule.runOnIdle { assertEquals(BLANK_URL, browser.selectedTab.url) }

        composeRule.onNodeWithTag(AddressBarTestTags.Editor).performTextInput("compose android")
        composeRule.onNodeWithTag(AddressSearchShortcutTestTags.Chip).assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val evidence = File(instrumentation.targetContext.getExternalFilesDir(null), "search-shortcut-chip.png")
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            evidence.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        composeRule.onNodeWithTag(AddressBarTestTags.Editor).performImeAction()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browser.selectedTab.url == "https://github.com/search?q=compose%20android"
        }
        composeRule.runOnIdle {
            assertEquals("https://github.com/search?q=compose%20android", browser.selectedTab.url)
        }
    }

    @Test
    fun shortcutAutocompleteRequestsQueryAndFilledSuggestionKeepsWebsite() {
        SearchSuggestionFixtureServer().use { server ->
            lateinit var browser: BrowserController
            composeRule.runOnIdle {
                clearSession()
                browser = BrowserController(composeRule.activity)
                controller = browser
                browser.updateSearchShortcuts(
                    listOf(SearchShortcut("gh", "${server.baseUrl}/search?q=%s")),
                )
                browser.updateSearxngSettings(
                    SearxngSettings(
                        instanceUrl = server.baseUrl,
                        suggestionFallback = SearchSuggestionProvider.None,
                    ),
                )
                browser.updateSearchSuggestionProvider(SearchSuggestionProvider.SearXNG)
                browser.onStart()
                browser.onResume()
            }
            composeRule.setContent {
                MaterialBrowserTheme {
                    BrowserScreen(controller = browser, openAddressEditorOnLaunch = true)
                }
            }

            composeRule.onNodeWithTag(AddressBarTestTags.Editor).performTextInput("!gh comp")
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithTag(
                    AddressSuggestionTestTags.fillSearch("compose android"),
                ).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(AddressSearchShortcutTestTags.Chip).assertIsDisplayed()
            composeRule.onNodeWithTag(AddressSuggestionTestTags.searchRow("compose android"))
                .assertIsDisplayed()
            val suggestionRequests = server.requests.filter { it.startsWith("/autocompleter?") }
            assertTrue(suggestionRequests.contains("/autocompleter?q=comp"))
            assertFalse(
                suggestionRequests.any { request ->
                    Uri.parse(request).getQueryParameter("q")?.contains("!gh") == true
                },
            )

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val evidence = File(
                instrumentation.targetContext.getExternalFilesDir(null),
                "search-shortcut-autocomplete.png",
            )
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                evidence.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }

            composeRule.onNodeWithTag(AddressSuggestionTestTags.fillSearch("compose android"))
                .performClick()
            composeRule.onNodeWithTag(AddressSearchShortcutTestTags.Chip).assertIsDisplayed()
            composeRule.onNodeWithTag(AddressBarTestTags.Editor).performImeAction()
            val expectedUrl = "${server.baseUrl}/search?q=compose%20android"
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                browser.selectedTab.url == expectedUrl &&
                    server.requests.contains("/search?q=compose%20android")
            }
            composeRule.runOnIdle { assertEquals(expectedUrl, browser.selectedTab.url) }
        }
    }

    private fun clearSession() {
        composeRule.activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private class SearchSuggestionFixtureServer : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val baseUrl = "http://127.0.0.1:${server.localPort}"
        val requests = CopyOnWriteArrayList<String>()
        private val thread = Thread(::serve, "shortcut-autocomplete-fixture").apply {
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
                        val target = reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Drain headers before returning the local fixture response.
                        }
                        requests += target
                        val isSuggestion = target.startsWith("/autocompleter?")
                        val contentType = if (isSuggestion) "application/json" else "text/html"
                        val body = if (isSuggestion) {
                            "[\"compose android\"]"
                        } else {
                            "<!doctype html><title>Shortcut results</title><main>compose android</main>"
                        }
                        val response = buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: $contentType; charset=utf-8\r\n")
                            append("Content-Length: ${body.toByteArray().size}\r\n")
                            append("Connection: close\r\n\r\n")
                            append(body)
                        }
                        connection.getOutputStream().buffered().use { output ->
                            output.write(response.toByteArray())
                        }
                    }
                }
            }
        }

        override fun close() {
            server.close()
            thread.join(1_000L)
        }
    }
}
