package dev.sk2andy.materialbrowser.browser.gecko

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.permissions.PermissionPrompt
import dev.sk2andy.materialbrowser.browser.permissions.PermissionPromptChoice
import dev.sk2andy.materialbrowser.browser.permissions.SitePermission
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.PermissionRadarStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoIoPromptsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var testEngineView: View? = null

    @Before
    fun setUp() {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences(PermissionRadarStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        testEngineView = null
    }

    @Test
    fun userActivatedExternalResponseRoutesRealGeckoDownload() {
        FixtureServer().use { server ->
            seedSelectedTab("about:blank")
            launchMainActivity().use { scenario ->
                deleteStoredDownload(DOWNLOAD_FILE_NAME)
                navigateAndAwaitView(scenario, server.url("/download-start"))
                tapAttachedGeckoView()
                assertTrue(
                    awaitValue("download request from Gecko") {
                        true.takeIf { server.downloadRequested.get() }
                    } == true,
                )

                val body = awaitValue("Gecko download body") {
                    storedDownload(DOWNLOAD_FILE_NAME)
                }
                assertNotNull(body)
                assertEquals("candy-gecko-download", requireNotNull(body).decodeToString())
                deleteStoredDownload(DOWNLOAD_FILE_NAME)
            }
        }
    }

    @Test
    fun fileInputUsesCandyChooserAndNavigationOwnsResult() {
        FixtureServer().use { server ->
            seedSelectedTab("about:blank")
            launchMainActivity().use { scenario ->
                navigateAndAwaitView(scenario, server.url("/upload"))
                tapAttachedGeckoView()
                assertTrue(
                    awaitValue("Gecko file chooser launch") {
                        true.takeIf {
                            shellOutput("dumpsys activity activities")
                                .contains("com.google.android.documentsui")
                        }
                    } == true,
                )
                instrumentation.uiAutomation.executeShellCommand("input keyevent 4").close()
                assertTrue(
                    awaitValue("cancelled Gecko file chooser delivery") {
                        scenario.value { controller ->
                            true.takeIf { !controller.hasPendingFileChooserForTesting() }
                        }
                    } == true,
                )
            }
        }
    }

    @Test
    fun cameraAndMicrophoneReachPermissionRadarFromRealGecko() {
        grantRuntimePermission(Manifest.permission.CAMERA)
        grantRuntimePermission(Manifest.permission.RECORD_AUDIO)
        FixtureServer().use { server ->
            seedSelectedTab("about:blank")
            launchMainActivity().use { scenario ->
                navigateAndAwaitView(scenario, server.localhostUrl("/media"))
                tapAttachedGeckoView()

                val promptOrFailure = awaitValue<Any>("Gecko camera/microphone prompt") {
                    scenario.value { controller ->
                        controller.permissionPrompt ?: controller.selectedTab.title
                            .takeIf { title ->
                                title.startsWith("media-") &&
                                    title !in setOf("media-ready", "media-requested")
                            }
                    }
                }
                val prompt = promptOrFailure as? PermissionPrompt
                assertNotNull("Gecko media request failed in page: $promptOrFailure", prompt)
                assertEquals(setOf(SitePermission.Camera, SitePermission.Microphone), prompt?.permissions)
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().respondToPermissionPrompt(
                        requireNotNull(prompt).id,
                        PermissionPromptChoice.Block,
                    )
                }
            }
        }
    }

    @Test
    fun httpBasicAuthAndTargetBlankUseCandyState() {
        FixtureServer().use { server ->
            seedSelectedTab(server.url("/auth"))
            launchMainActivity().use { scenario ->
                val authPrompt = awaitValue("Gecko HTTP auth prompt") {
                    scenario.value(BrowserController::httpAuthPrompt)
                }
                assertTrue(authPrompt?.realm.orEmpty().contains("Candy Gecko"))
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().respondToHttpAuthPrompt(
                        requireNotNull(authPrompt).id,
                        "candy",
                        "secret",
                    )
                }
                assertTrue(
                    awaitValue("authorized Gecko request") {
                        true.takeIf { server.authorized.get() }
                    } == true,
                )

                navigateAndAwaitView(scenario, server.url("/blank"))
                val previousCount = requireNotNull(scenario.value { it.tabs.size })
                tapAttachedGeckoView()
                val childUrl = awaitValue("target=_blank Gecko tab") {
                    scenario.value { controller ->
                        controller.tabs
                            .takeIf { tabs -> tabs.size > previousCount }
                            ?.firstOrNull { tab -> tab.url == server.url("/child") }
                            ?.url
                    }
                }
                assertEquals(server.url("/child"), childUrl)
            }
        }
    }

    private fun navigateAndAwaitView(
        scenario: ActivityScenario<MainActivity>,
        url: String,
    ) {
        scenario.onActivity { activity ->
            assertTrue(activity.browserControllerForTesting().openUrl(url))
        }
        awaitValue("Gecko navigation committed") {
            scenario.value { controller ->
                controller.selectedTab.url.takeIf { it == url && !controller.selectedTab.isLoading }
            }
        }
        awaitValue<View>("visible Gecko page") {
            scenario.value { controller ->
                controller.selectedGeckoViewForTesting()?.also { testEngineView = it }
            }?.takeIf { view ->
                view.isAttachedToWindow && view.isShown && view.width > 0 && view.height > 0
            }
        }
    }

    private fun grantRuntimePermission(permission: String) {
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        }
    }

    private fun launchMainActivity(): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
    )

    private fun seedSelectedTab(url: String) {
        val tab = BrowserTab(
            id = "gecko-io-fixture",
            lastAccessedAt = System.currentTimeMillis(),
            url = url,
        )
        assertTrue(BrowserSessionStore(context).saveTabsImmediately(listOf(tab), tab.id))
    }

    private fun tapAttachedGeckoView() {
        val target = requireNotNull(testEngineView)
        instrumentation.runOnMainSync { target.requestFocus() }
        val location = IntArray(2)
        target.getLocationOnScreen(location)
        val x = location[0] + target.width / 2
        val y = location[1] + target.height / 2
        instrumentation.uiAutomation.executeShellCommand("input tap $x $y").close()
        SystemClock.sleep(250)
    }

    private fun storedDownload(name: String): ByteArray? = context.contentResolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Downloads._ID),
        "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.IS_PENDING} = 0",
        arrayOf(name),
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) null else {
            val uri = android.content.ContentUris.withAppendedId(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                cursor.getLong(0),
            )
            context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
        }
    }

    private fun deleteStoredDownload(name: String) {
        context.contentResolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(name),
        )
    }

    private fun shellOutput(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { reader -> reader.readText() }

    private fun <T> ActivityScenario<MainActivity>.value(
        read: (BrowserController) -> T,
    ): T? {
        var value: T? = null
        onActivity { activity -> value = read(activity.browserControllerForTesting()) }
        return value
    }

    private fun <T> awaitValue(label: String, read: () -> T?): T? {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            read()?.let { return it }
            SystemClock.sleep(25)
        }
        throw AssertionError("Timed out waiting for $label")
    }

    private class FixtureServer : Closeable {
        private val serverSocket = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newCachedThreadPool()
        private val closed = AtomicBoolean()
        val authorized = AtomicBoolean()
        val downloadRequested = AtomicBoolean()
        private val expectedAuth = "Basic " + Base64.getEncoder().encodeToString(
            "candy:secret".toByteArray(StandardCharsets.UTF_8),
        )

        init {
            executor.execute {
                while (!closed.get()) {
                    val socket = runCatching(serverSocket::accept).getOrNull() ?: return@execute
                    executor.execute { runCatching { respond(socket) } }
                }
            }
        }

        fun url(path: String): String = "http://127.0.0.1:${serverSocket.localPort}$path"

        fun localhostUrl(path: String): String = "http://localhost:${serverSocket.localPort}$path"

        private fun respond(socket: Socket) {
            socket.use { connection ->
                val reader = connection.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return
                val headers = ConcurrentHashMap<String, String>()
                while (true) {
                    val line = reader.readLine() ?: return
                    if (line.isEmpty()) break
                    headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                }
                if (path == "/auth" && headers["authorization"] != expectedAuth) {
                    write(
                        connection,
                        "401 Unauthorized",
                        "text/plain",
                        "authentication required",
                        listOf("WWW-Authenticate: Basic realm=\"Candy Gecko\""),
                    )
                    return
                }
                if (path == "/auth") authorized.set(true)
                if (path == "/download") downloadRequested.set(true)
                when (path) {
                    "/download-start" -> write(connection, "200 OK", "text/html", DOWNLOAD_HTML)
                    "/download" -> write(
                        connection,
                        "200 OK",
                        "application/vnd.android.package-archive",
                        "candy-gecko-download",
                        listOf(
                            "Content-Disposition: attachment; filename=\"gecko.apk\"",
                            "X-Content-Type-Options: nosniff",
                        ),
                    )
                    "/upload" -> write(connection, "200 OK", "text/html", UPLOAD_HTML)
                    "/media" -> write(connection, "200 OK", "text/html", MEDIA_HTML)
                    "/blank" -> write(connection, "200 OK", "text/html", BLANK_HTML)
                    else -> write(connection, "200 OK", "text/html", "<title>ready</title>")
                }
            }
        }

        private fun write(
            socket: Socket,
            status: String,
            contentType: String,
            bodyText: String,
            extraHeaders: List<String> = emptyList(),
        ) {
            val body = bodyText.toByteArray(StandardCharsets.UTF_8)
            socket.getOutputStream().buffered().use { output ->
                output.write("HTTP/1.1 $status\r\nContent-Type: $contentType\r\n".toByteArray())
                extraHeaders.forEach { header -> output.write("$header\r\n".toByteArray()) }
                output.write("Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                output.write(body)
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            serverSocket.close()
            executor.shutdownNow()
        }

        private companion object {
            const val DOWNLOAD_HTML = """
                <!doctype html><meta name="viewport" content="width=device-width">
                <a href="/download" aria-label="Candy download" style="position:fixed;inset:0;display:block">download</a>
            """
            const val UPLOAD_HTML = """
                <!doctype html><meta name="viewport" content="width=device-width">
                <input id="candy-file" aria-label="Candy file upload" type="file" multiple style="position:fixed;inset:0;width:100%;height:100%;opacity:.01">
            """
            const val MEDIA_HTML = """
                <!doctype html><meta name="viewport" content="width=device-width"><title>media-ready</title>
                <button aria-label="Candy media request" style="position:fixed;inset:0;width:100%;height:100%" onclick="document.title='media-requested';navigator.mediaDevices.getUserMedia({video:true,audio:true}).then(()=>document.title='media-granted').catch(error=>document.title='media-'+error.name)">media</button>
            """
            const val BLANK_HTML = """
                <!doctype html><meta name="viewport" content="width=device-width">
                <a href="/child" target="_blank" aria-label="Candy blank child" style="position:fixed;inset:0;display:block">child</a>
            """
        }
    }

    private companion object {
        const val DOWNLOAD_FILE_NAME = "gecko.apk"
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.test.GECKO_IO"
    }
}
