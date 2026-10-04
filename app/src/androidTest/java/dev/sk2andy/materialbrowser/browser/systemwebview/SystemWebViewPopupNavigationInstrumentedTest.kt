package dev.sk2andy.materialbrowser.browser.systemwebview

import android.content.Context
import android.content.ContentUris
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.ExternalAppLinkHandling
import dev.sk2andy.materialbrowser.browser.FederatedLoginProvider
import dev.sk2andy.materialbrowser.browser.FederatedLoginPromptChoice
import dev.sk2andy.materialbrowser.browser.attachSelectedWebView
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.GeckoCloseRequestListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoRuntimeOwner
import dev.sk2andy.materialbrowser.browser.selectedWebViewForTesting
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SystemWebViewPopupNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var controller: BrowserController
    private lateinit var host: FrameLayout
    private var blobDownloadIdsBefore: Set<Long>? = null

    @Before
    fun setUp() {
        composeRule.runOnIdle {
            composeRule.activity
                .getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            val store = BrowserSessionStore(composeRule.activity)
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.SystemWebView))
            store.saveExternalAppLinkHandling(ExternalAppLinkHandling.AskEveryTime)
            GeckoRuntimeOwner.resetForTesting()
            controller = BrowserController(composeRule.activity)
            host = FrameLayout(composeRule.activity)
            composeRule.activity.addContentView(
                host,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            controller.onStart()
            controller.onResume()
        }
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            if (::controller.isInitialized) controller.destroy()
            composeRule.activity
                .getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            GeckoRuntimeOwner.resetForTesting()
        }
        blobDownloadIdsBefore?.let { before ->
            jpegDownloads().keys.filterNot(before::contains).forEach { id ->
                composeRule.activity.contentResolver.delete(
                    ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                    null,
                    null,
                )
            }
        }
    }

    @Test
    fun oauthPopupDeliversResultAndClosesBackToOpener() {
        verifyOAuthCompletion(isPrivate = false)
    }

    @Test
    fun privateOAuthPopupDeliversResultWithoutPersistingAuthPages() {
        verifyOAuthCompletion(isPrivate = true)
    }

    @Test
    fun preopenedBlankPopupKeepsNativeOpenerForDelayedNavigation() {
        verifyOAuthCompletion(isPrivate = false, openerPath = "/blank-opener")
    }

    @Test
    fun blobPopupDownloadsThroughControllerAndReturnsToOwningPage() {
        PopupFixtureServer().use { server ->
            val openerUrl = server.url("/blob-opener")
            openPage(openerUrl, "Blob opener")
            val openerId = composeRule.runOnIdle { controller.selectedTabId }
            val before = jpegDownloads().keys.toSet().also { blobDownloadIdsBefore = it }
            tapPage()
            var completedId: Long? = null
            composeRule.waitUntil(15_000) {
                completedId = jpegDownloads().entries.firstOrNull { (id, pending) ->
                    id !in before && !pending
                }?.key
                completedId != null
            }
            val downloadUri = ContentUris.withAppendedId(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                requireNotNull(completedId),
            )
            val bytes = requireNotNull(composeRule.activity.contentResolver.openInputStream(downloadUri))
                .use { it.readBytes() }
            assertArrayEquals(byteArrayOf(-1, -40, -1, -39), bytes)
            composeRule.waitUntil(10_000) {
                composeRule.runOnIdle {
                    controller.selectedTabId == openerId &&
                        controller.tabs.none { it.openerTabId == openerId } &&
                        controller.pendingPopupCountForTesting == 0 &&
                        controller.transientPopupCountForTesting == 0
                }
            }
            composeRule.runOnIdle {
                assertEquals(openerUrl, controller.selectedTab.url)
                assertNull(controller.selectedTab.error)
            }
        }
    }

    @Test
    fun postPopupPreservesSubmittedBodyReferrerAndNativeOpener() {
        verifyPostPopup(grantLoginCompatibility = false)
    }

    @Test
    fun grantedLoginPopupDoesNotReplayPostWhenUserAgentChanges() {
        verifyPostPopup(grantLoginCompatibility = true)
    }

    private fun verifyPostPopup(grantLoginCompatibility: Boolean) {
        PopupFixtureServer().use { server ->
            openPage(server.url("/form-opener"), "POST opener")
            val openerId = composeRule.runOnIdle { controller.selectedTabId }
            if (grantLoginCompatibility) {
                composeRule.runOnIdle {
                    controller.detectFederatedLoginForTesting("https://accounts.google.com/gsi/client")
                }
                composeRule.waitUntil(10_000) {
                    composeRule.runOnIdle { controller.federatedLoginOffer != null }
                }
                composeRule.runOnIdle {
                    controller.respondToFederatedLoginOffer(
                        requireNotNull(controller.federatedLoginOffer).token,
                        FederatedLoginPromptChoice.AllowForTab,
                    )
                }
                awaitPage(server.url("/form-opener"), "POST opener")
            }
            val popupId = openPopup(openerId, server.url("/form-popup"), "POST popup with opener")
            val request = requireNotNull(server.postRequest.get())
            assertEquals("POST", request.method)
            assertEquals("payload=candy-post-fixture", request.body)
            assertEquals(server.url("/form-opener"), request.referrer)
            assertEquals(1, server.postPopupRequests.get())
            composeRule.runOnIdle {
                assertEquals(openerId, controller.selectedTab.openerTabId)
                assertEquals(popupId, controller.selectedTabId)
            }
        }
    }

    @Test
    fun automaticPopupWithoutUserGestureDoesNotCreateCandyTab() {
        PopupFixtureServer().use { server ->
            openPage(server.url("/automatic-opener"), "Automatic popup rejected")
            composeRule.runOnIdle {
                val openerId = controller.selectedTabId
                assertTrue(controller.tabs.none { it.openerTabId == openerId })
                assertEquals(0, controller.pendingPopupCountForTesting)
            }
        }
    }

    @Test
    fun alwaysBlockPopupsRejectsUserOpenedNativeChild() {
        PopupFixtureServer().use { server ->
            val openerUrl = "https://popup.example.com/"
            openPage(server.url("/oauth-opener"), "OAuth opener")
            composeRule.runOnIdle {
                controller.selectedWebViewForTesting().loadDataWithBaseURL(
                    openerUrl,
                    server.blockedOpenerPage(),
                    "text/html",
                    "utf-8",
                    openerUrl,
                )
            }
            awaitPage(openerUrl, "Blocked popup opener")
            val openerId = composeRule.runOnIdle {
                assertTrue(controller.setSelectedAlwaysBlockPopups(true))
                controller.selectedTabId
            }
            tapPage()
            awaitPage(openerUrl, "Blocked popup attempted")
            composeRule.runOnIdle {
                assertEquals(openerId, controller.selectedTabId)
                assertTrue(controller.tabs.none { it.openerTabId == openerId })
                assertNull(controller.blockedPopupOffer)
            }
            assertEquals(0, server.consentRequests.get())
        }
    }

    @Test
    fun manuallyOpenedPageCannotCloseItsCandyTab() {
        PopupFixtureServer().use { server ->
            val url = server.url("/manual-close")
            openPage(url, "Manual close page")
            val tabId = composeRule.runOnIdle { controller.selectedTabId }
            tapPage()
            awaitPage(url, "Manual close ignored")
            composeRule.runOnIdle {
                assertEquals(tabId, controller.selectedTabId)
                assertTrue(controller.tabs.any { it.id == tabId })
                assertNull(controller.selectedTab.openerTabId)
            }
        }
    }

    @Test
    fun recreatedPopupRejectsStaleAndUnexpectedCloseCallbacks() {
        PopupFixtureServer().use { server ->
            openPage(server.url("/oauth-opener"), "OAuth opener")
            val openerId = composeRule.runOnIdle { controller.selectedTabId }
            val consentUrl = server.url("/oauth-consent")
            val popupId = openPopup(openerId, consentUrl, "OAuth consent")
            val oldSession = composeRule.runOnIdle { selectedSession() }
            val oldCloseListener = composeRule.runOnIdle { selectedCloseRequestListener() }
            composeRule.runOnIdle {
                BrowserController::class.java.getDeclaredMethod(
                    "closeBrowserEngineSession",
                    String::class.java,
                ).let { method ->
                    method.isAccessible = true
                    method.invoke(controller, popupId)
                }
                controller.attachSelectedWebView(host)
            }
            awaitPage(consentUrl, "OAuth consent")
            val replacementSession = composeRule.runOnIdle { selectedSession() }
            val replacementCloseListener = composeRule.runOnIdle { selectedCloseRequestListener() }
            assertNotSame(oldSession, replacementSession)
            val callbacksDrained = CountDownLatch(1)
            composeRule.runOnIdle {
                oldCloseListener.onCloseRequest()
                replacementCloseListener.onCloseRequest()
                host.post { callbacksDrained.countDown() }
            }
            assertTrue("Close callbacks did not drain", callbacksDrained.await(10, TimeUnit.SECONDS))
            composeRule.runOnIdle {
                assertEquals(popupId, controller.selectedTabId)
                assertTrue(controller.tabs.any { it.id == popupId })
                assertSame(replacementSession, selectedSession())
                assertNull(controller.selectedTab.error)
            }
        }
    }

    @Test
    fun googleSdkSubresourceOffersScopedLoginCompatibility() {
        PopupFixtureServer().use { server ->
            val url = server.url("/oauth-opener")
            openPage(url, "OAuth opener")
            composeRule.runOnIdle {
                val webView = controller.selectedWebViewForTesting()
                assertNull(
                    webView.webViewClient.shouldInterceptRequest(
                        webView,
                        object : WebResourceRequest {
                            override fun getUrl(): Uri = Uri.parse("https://accounts.google.com/gsi/client")
                            override fun isForMainFrame(): Boolean = false
                            override fun isRedirect(): Boolean = false
                            override fun hasGesture(): Boolean = false
                            override fun getMethod(): String = "GET"
                            override fun getRequestHeaders(): Map<String, String> = emptyMap()
                        },
                    ),
                )
            }
            composeRule.waitUntil(10_000) {
                composeRule.runOnIdle { controller.federatedLoginOffer != null }
            }
            composeRule.runOnIdle {
                val offer = requireNotNull(controller.federatedLoginOffer)
                assertEquals(controller.selectedTabId, offer.tabId)
                assertEquals("127.0.0.1", offer.pageHost)
                assertEquals(FederatedLoginProvider.Google, offer.provider)
                assertFalse(offer.isPrivate)
                assertFalse(offer.showDialog)
            }
        }
    }

    @Test
    fun loginCookieGrantCanBeRevokedWithoutChangingOtherTab() {
        PopupFixtureServer().use { server ->
            val url = server.url("/oauth-opener")
            openPage(url, "OAuth opener")
            lateinit var grantedTabId: String
            lateinit var originalUserAgent: String
            composeRule.runOnIdle {
                grantedTabId = controller.selectedTabId
                originalUserAgent = controller.selectedWebViewForTesting().settings.userAgentString
                assertTrue(originalUserAgent.contains("; wv"))
                assertTrue(originalUserAgent.contains("Version/"))
                assertFalse(
                    CookieManager.getInstance().acceptThirdPartyCookies(controller.selectedWebViewForTesting()),
                )
                controller.detectFederatedLoginForTesting("https://accounts.google.com/gsi/client")
            }
            composeRule.waitUntil(10_000) {
                composeRule.runOnIdle { controller.federatedLoginOffer != null }
            }
            composeRule.runOnIdle {
                controller.respondToFederatedLoginOffer(
                    requireNotNull(controller.federatedLoginOffer).token,
                    FederatedLoginPromptChoice.AllowForTab,
                )
                assertTrue(
                    CookieManager.getInstance().acceptThirdPartyCookies(controller.selectedWebViewForTesting()),
                )
                val userAgent = controller.selectedWebViewForTesting().settings.userAgentString
                assertFalse(userAgent.contains("; wv"))
                assertFalse(userAgent.contains("Version/"))
            }
            openPage(url, "OAuth opener")
            composeRule.runOnIdle {
                assertFalse(
                    CookieManager.getInstance().acceptThirdPartyCookies(controller.selectedWebViewForTesting()),
                )
                assertEquals(originalUserAgent, controller.selectedWebViewForTesting().settings.userAgentString)
                controller.selectTab(grantedTabId)
                controller.attachSelectedWebView(host)
                assertTrue(controller.revokeFederatedLoginCompatibility(grantedTabId))
                assertFalse(
                    CookieManager.getInstance().acceptThirdPartyCookies(controller.selectedWebViewForTesting()),
                )
                assertEquals(originalUserAgent, controller.selectedWebViewForTesting().settings.userAgentString)
            }
        }
    }

    private fun verifyOAuthCompletion(isPrivate: Boolean, openerPath: String = "/oauth-opener") {
        PopupFixtureServer().use { server ->
            val openerUrl = server.url(openerPath)
            openPage(openerUrl, "OAuth opener", isPrivate)
            val openerId = composeRule.runOnIdle { controller.selectedTabId }
            val popupId = openPopup(openerId, server.url("/oauth-consent"), "OAuth consent")
            composeRule.runOnIdle {
                assertEquals(openerId, controller.selectedTab.openerTabId)
                assertEquals(isPrivate, controller.selectedTab.isIncognito)
            }
            tapPage()
            composeRule.waitUntil(15_000) {
                composeRule.runOnIdle {
                    controller.tabs.none { it.id == popupId } &&
                        controller.selectedTabId == openerId &&
                        controller.selectedTab.title == "OAuth signed in"
                }
            }
            composeRule.runOnIdle { controller.attachSelectedWebView(host) }
            awaitPage(openerUrl, "OAuth signed in")
            composeRule.runOnIdle {
                assertNull(controller.selectedTab.error)
                if (isPrivate) {
                    val store = BrowserSessionStore(composeRule.activity)
                    assertTrue(store.flush())
                    assertTrue(store.loadTabs().first.none { it.id == openerId || it.id == popupId })
                    assertTrue(store.loadHistory().none { it.url.startsWith(server.url("/oauth-")) })
                }
            }
        }
    }

    private fun openPage(url: String, title: String, isPrivate: Boolean = false) {
        composeRule.runOnIdle {
            controller.createTab(initialUrl = url, isIncognito = isPrivate)
            controller.attachSelectedWebView(host)
        }
        awaitPage(url, title)
    }

    private fun openPopup(openerId: String, url: String, title: String): String {
        tapPage()
        composeRule.waitUntil(15_000) {
            composeRule.runOnIdle {
                controller.tabs.any { it.openerTabId == openerId && it.url == url }
            }
        }
        val popupId = composeRule.runOnIdle {
            controller.tabs.first { it.openerTabId == openerId && it.url == url }.id.also {
                controller.selectTab(it)
                controller.attachSelectedWebView(host)
            }
        }
        awaitPage(url, title)
        return popupId
    }

    private fun awaitPage(url: String, title: String) {
        try {
            composeRule.waitUntil(15_000) {
                composeRule.runOnIdle {
                    controller.selectedTab.url == url && controller.selectedTab.title == title &&
                        !controller.selectedTab.isLoading
                }
            }
        } catch (error: ComposeTimeoutException) {
            val diagnostics = composeRule.runOnIdle {
                val view = controller.selectedWebViewForTesting()
                "tab=${controller.selectedTab}, webViewUrl=${view.url}, webViewTitle=${view.title}, " +
                    "tabs=${controller.tabs}, pending=${controller.pendingPopupCountForTesting}, " +
                    "transient=${controller.transientPopupCountForTesting}"
            }
            throw AssertionError("Page did not settle: url=$url, title=$title; $diagnostics", error)
        }
    }

    private fun tapPage() {
        composeRule.waitUntil(10_000) {
            composeRule.runOnIdle {
                controller.selectedWebViewForTesting().let { view ->
                    view.isAttachedToWindow && view.isShown && view.width > 0 && view.height > 0
                }
            }
        }
        val location = composeRule.runOnIdle {
            controller.selectedWebViewForTesting().let { view ->
                IntArray(2).also {
                    view.getLocationOnScreen(it)
                    it[0] += view.width / 2
                    it[1] += view.height / 2
                }
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                location[0].toFloat(),
                location[1].toFloat(),
                0,
            ).let { event ->
                instrumentation.sendPointerSync(event)
                event.recycle()
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun selectedSession(): AndroidBrowserEngineSessionPort {
        val field = BrowserController::class.java.getDeclaredField("browserEngineSessions")
        field.isAccessible = true
        val sessions = field.get(controller) as Map<*, *>
        return sessions[controller.selectedTabId] as AndroidBrowserEngineSessionPort
    }

    private fun selectedCloseRequestListener(): GeckoCloseRequestListener {
        val session = selectedSession()
        val field = session.javaClass.getDeclaredField("closeRequestListener")
        field.isAccessible = true
        return field.get(session) as GeckoCloseRequestListener
    }

    private fun jpegDownloads(): Map<Long, Boolean> {
        return composeRule.activity.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.IS_PENDING),
            "${MediaStore.Downloads.MIME_TYPE} = ?",
            arrayOf("image/jpeg"),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val pendingColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.IS_PENDING)
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getLong(idColumn), cursor.getInt(pendingColumn) != 0)
                }
            }
        }.orEmpty()
    }

    private class PopupFixtureServer : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val postRequest = AtomicReference<PostRequest>()
        val consentRequests = AtomicInteger()
        val postPopupRequests = AtomicInteger()
        private val thread = Thread(::serve, "system-webview-popup-fixture").apply {
            isDaemon = true
            start()
        }

        fun url(path: String): String = "http://127.0.0.1:${server.localPort}$path"

        fun blockedOpenerPage(): String = page(
            "Blocked popup opener",
            """
                <button onclick="window.open('${url("/oauth-consent")}','blocked-auth');
                    document.title='Blocked popup attempted'">Open popup</button>
            """.trimIndent(),
        )

        private fun serve() {
            while (!server.isClosed) {
                try {
                    server.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                        val requestLine = reader.readLine().orEmpty().split(' ')
                        val target = requestLine.getOrNull(1).orEmpty().substringBefore('?')
                        if (target == "/oauth-consent") consentRequests.incrementAndGet()
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                            headers[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim()
                        }
                        val body = buildString {
                            repeat(headers["content-length"]?.toIntOrNull() ?: 0) {
                                val char = reader.read()
                                if (char >= 0) append(char.toChar())
                            }
                        }
                        if (target == "/form-popup") {
                            postPopupRequests.incrementAndGet()
                            postRequest.set(PostRequest(requestLine.first(), body, headers["referer"]))
                        }
                        val response = response(target).toByteArray(StandardCharsets.UTF_8)
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n".toByteArray())
                            write("Cache-Control: no-store\r\nContent-Length: ${response.size}\r\n".toByteArray())
                            write("Connection: close\r\n\r\n".toByteArray())
                            write(response)
                            flush()
                        }
                    }
                } catch (error: SocketException) {
                    if (server.isClosed) return
                    throw error
                }
            }
        }

        private fun response(path: String): String = when (path) {
            "/oauth-opener" -> oauthOpener(
                "authPopup=window.open('/oauth-consent','candy-auth');",
            )
            "/blank-opener" -> oauthOpener(
                """
                    authPopup=window.open('','candy-auth');
                    if(authPopup) setTimeout(() => { authPopup.location.href='/oauth-consent'; },250);
                """.trimIndent(),
            )
            "/oauth-consent" -> page(
                "OAuth consent",
                "<button onclick=\"location.href='/oauth-complete'\">Confirm access</button>",
            )
            "/oauth-complete" -> page(
                "OAuth opener missing",
                """
                    <script>
                    if(window.opener) {
                        window.opener.postMessage('candy-auth-complete',location.origin);
                        window.close();
                    }
                    </script>
                """.trimIndent(),
            )
            "/form-opener" -> page(
                "POST opener",
                """
                    <form action='/form-popup' method='post' target='_blank' rel='opener'>
                    <input type='hidden' name='payload' value='candy-post-fixture'>
                    <button type='submit'>Open POST popup</button></form>
                """.trimIndent(),
            )
            "/form-popup" -> page(
                "POST popup missing opener",
                """
                    <script>
                    if(window.opener && window.opener.location.pathname==='/form-opener' &&
                        document.referrer==='${url("/form-opener")}') {
                        document.title='POST popup with opener';
                    } else {
                        document.title='POST popup missing: opener='+Boolean(window.opener)+
                            ', referrer='+document.referrer;
                    }
                    </script>
                """.trimIndent(),
            )
            "/blob-opener" -> page(
                "Blob opener",
                """
                    <button onclick="window.open(window.URL.createObjectURL(new Blob(
                        [new Uint8Array([255,216,255,217])],{type:'image/jpeg'})))">Save JPEG</button>
                """.trimIndent(),
            )
            "/automatic-opener" -> page(
                "Automatic popup waiting",
                """
                    <script>
                    const popup=window.open('/oauth-consent','automatic-auth');
                    document.title=popup ? 'Automatic popup accepted' : 'Automatic popup rejected';
                    </script>
                """.trimIndent(),
            )
            "/manual-close" -> page(
                "Manual close page",
                "<button onclick=\"window.close();document.title='Manual close ignored'\">Close</button>",
            )
            else -> page("Popup fixture resource", "Fixture resource")
        }

        private fun oauthOpener(openAction: String): String = page(
            "OAuth opener",
            """
                <button onclick="$openAction
                    if(!authPopup)document.title='OAuth popup rejected'">Sign in</button>
                <script>
                let authPopup;
                addEventListener('message', event => {
                    if(event.origin===location.origin && event.source===authPopup &&
                        event.data==='candy-auth-complete') document.title='OAuth signed in';
                });
                </script>
            """.trimIndent(),
        )

        private fun page(title: String, body: String): String = """
            <!doctype html><html><head>
            <meta name='viewport' content='width=device-width,initial-scale=1'>
            <title>$title</title><style>html,body{margin:0;height:100%}
            button{position:fixed;inset:0;font-size:32px}</style></head><body>$body</body></html>
        """.trimIndent()

        override fun close() {
            server.close()
            thread.join(2_000L)
        }
    }

    private data class PostRequest(val method: String, val body: String, val referrer: String?)
}
