package dev.sk2andy.materialbrowser.browser.gecko

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.permissions.PermissionPrompt
import dev.sk2andy.materialbrowser.browser.permissions.PermissionPromptChoice
import dev.sk2andy.materialbrowser.browser.permissions.PermissionOrigin
import dev.sk2andy.materialbrowser.browser.permissions.SitePermission
import dev.sk2andy.materialbrowser.browser.permissions.SitePermissionDecision
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
import kotlin.math.abs
import org.json.JSONObject
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
    fun cancellingFilePickerWithFocusedEditorRestoresLivePageWithoutResumeCover() {
        FixtureServer().use { server ->
            seedSelectedTab("about:blank")
            launchMainActivity().use { scenario ->
                navigateAndAwaitView(scenario, server.url("/focused-upload"))
                val initialReport = awaitFocusedUploadReport(scenario) { true }
                val initialEngineHeight = scenario.value { geckoEngineView().height }

                tapFocusedUploadControl(initialReport, "editor")
                awaitImeVisibility(scenario, visible = true)
                val focusedReport = awaitFocusedUploadReport(scenario) { report ->
                    report.getBoolean("editorFocused") &&
                        report.getDouble("height") < initialReport.getDouble("height")
                }
                tapFocusedUploadControl(focusedReport, "upload")
                awaitValue("DocumentsUI as resumed activity") {
                    true.takeIf {
                        shellOutput("dumpsys activity activities").lineSequence().any { line ->
                            (line.contains("topResumedActivity") || line.contains("mResumedActivity")) &&
                                line.contains(".documentsui")
                        }
                    }
                }
                instrumentation.uiAutomation.executeShellCommand("input keyevent 4").close()

                awaitValue("cancelled focused Gecko file chooser delivery") {
                    scenario.value { controller ->
                        true.takeIf { !controller.hasPendingFileChooserForTesting() }
                    }
                }
                awaitImeVisibility(scenario, visible = false)
                awaitValue("restored native Gecko viewport") {
                    scenario.value {
                        true.takeIf { geckoEngineView().height == initialEngineHeight }
                    }
                }
                val restoredReport = awaitFocusedUploadReport(scenario) { report ->
                    report.getInt("pickerOpens") == 1 &&
                        report.getInt("heartbeat") > focusedReport.getInt("heartbeat") &&
                        abs(report.getDouble("height") - initialReport.getDouble("height")) < 1 &&
                        abs(report.getDouble("width") - initialReport.getDouble("width")) < 1 &&
                        abs(report.getDouble("scale") - initialReport.getDouble("scale")) < 0.01 &&
                        abs(report.getJSONObject("editor").getDouble("height") -
                            initialReport.getJSONObject("editor").getDouble("height")) < 1
                }
                awaitValue("Gecko resume cover removed after picker cancellation") {
                    scenario.value {
                        val engine = geckoEngineView()
                        val cover = (0 until engine.childCount)
                            .map(engine::getChildAt)
                            .filterIsInstance<ImageView>()
                            .single()
                        true.takeIf { cover.visibility == View.GONE }
                    }
                }
                tapFocusedUploadControl(restoredReport, "probe")
                awaitFocusedUploadReport(scenario) { report ->
                    report.getInt("clicks") == 1 &&
                        report.getInt("heartbeat") > restoredReport.getInt("heartbeat")
                }
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
    fun websiteNotificationRequestsPermissionAndAppearsInAndroid() {
        grantRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
        FixtureServer().use { server ->
            seedSelectedTab("about:blank")
            launchMainActivity().use { scenario ->
                navigateAndAwaitView(scenario, server.localhostUrl("/notification"))
                tapAttachedGeckoView()

                val prompt = awaitValue("Gecko notification permission prompt") {
                    scenario.value(BrowserController::permissionPrompt)
                }
                assertEquals(setOf(SitePermission.Notifications), prompt?.permissions)
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().respondToPermissionPrompt(
                        requireNotNull(prompt).id,
                        PermissionPromptChoice.AllowAlways,
                    )
                }

                assertEquals(
                    "notification-granted",
                    awaitValue("notification permission result") {
                        scenario.value { controller ->
                            controller.selectedTab.title.takeIf { it == "notification-granted" }
                        }
                    },
                )
                val manager = context.getSystemService(NotificationManager::class.java)
                val posted = awaitValue("Android website notification") {
                    manager.activeNotifications.firstOrNull { status ->
                        status.notification.extras.getString(Notification.EXTRA_TITLE) ==
                            "Candy test notification"
                    }
                }
                assertNotNull(posted)
                requireNotNull(posted).notification.contentIntent.send()
                assertEquals(
                    "notification-clicked",
                    awaitValue("website notification click") {
                        scenario.value { controller ->
                            controller.selectedTab.title.takeIf { it == "notification-clicked" }
                        }
                    },
                )
                manager.cancel(posted.tag, posted.id)
            }
        }
    }

    @Test
    fun notificationRadarRevocationPersistsAcrossActivityRestart() {
        grantRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
        FixtureServer().use { server ->
            val pageUrl = server.localhostUrl("/notification")
            val stateUrl = server.localhostUrl("/notification-state")
            val origin = requireNotNull(PermissionOrigin.normalize(pageUrl))
            seedSelectedTab("about:blank")
            launchMainActivity().use { scenario ->
                navigateAndAwaitView(scenario, pageUrl)
                tapAttachedGeckoView()
                val promptOrGrant = awaitValue<Any>("Gecko notification grant") {
                    scenario.value { controller ->
                        controller.permissionPrompt ?: true.takeIf {
                            controller.selectedTab.title == "notification-granted"
                        }
                    }
                }
                if (promptOrGrant is PermissionPrompt) {
                    scenario.onActivity { activity ->
                        activity.browserControllerForTesting().respondToPermissionPrompt(
                            promptOrGrant.id,
                            PermissionPromptChoice.AllowAlways,
                        )
                    }
                }
                awaitValue("notification grant") {
                    scenario.value { it.selectedTab.title.takeIf { title ->
                        title == "notification-granted"
                    } }
                }
                scenario.onActivity { activity ->
                    assertTrue(activity.browserControllerForTesting().setSitePermissionDecision(
                        "gecko-io-fixture", origin, SitePermission.Notifications,
                        SitePermissionDecision.Block,
                    ))
                }
                awaitValue("Gecko reload after notification block") {
                    scenario.value { controller ->
                        controller.selectedTab.title.takeIf { title ->
                            title == "notification-ready" && !controller.selectedTab.isLoading
                        }
                    }
                }
                navigateAndAwaitView(scenario, stateUrl)
                assertEquals("notification-state-denied", awaitValue("revoked Gecko grant") {
                    scenario.value { it.selectedTab.title.takeIf { title ->
                        title == "notification-state-denied"
                    } }
                })
            }
            launchMainActivity().use { scenario ->
                navigateAndAwaitView(scenario, stateUrl)
                assertEquals("notification-state-denied", awaitValue("persisted Gecko block") {
                    scenario.value { it.selectedTab.title.takeIf { title ->
                        title == "notification-state-denied"
                    } }
                })
                scenario.onActivity { activity ->
                    assertTrue(activity.browserControllerForTesting().setSitePermissionDecision(
                        "gecko-io-fixture", origin, SitePermission.Notifications,
                        SitePermissionDecision.Ask,
                    ))
                }
                navigateAndAwaitView(scenario, pageUrl)
                navigateAndAwaitView(scenario, stateUrl)
                assertEquals("notification-state-default", awaitValue("reset Gecko permission") {
                    scenario.value { it.selectedTab.title.takeIf { title ->
                        title == "notification-state-default"
                    } }
                })
            }
        }
    }

    @Test
    fun privatePageNotificationRequestIsDeniedWithoutPrompt() {
        FixtureServer().use { server ->
            seedSelectedTab("about:blank")
            launchMainActivity().use { scenario ->
                scenario.onActivity { activity ->
                    assertTrue(activity.browserControllerForTesting().openLinkInPrivate(
                        server.localhostUrl("/blank"),
                    ))
                }
                navigateAndAwaitView(scenario, server.localhostUrl("/notification"))
                tapAttachedGeckoView()
                assertEquals("notification-denied", awaitValue("private notification denial") {
                    scenario.value { it.selectedTab.title.takeIf { title ->
                        title == "notification-denied"
                    } }
                })
                assertEquals(null, scenario.value(BrowserController::permissionPrompt))
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

    private fun geckoEngineView(): ViewGroup =
        (requireNotNull(testEngineView) as ViewGroup).getChildAt(0) as ViewGroup

    private fun tapFocusedUploadControl(report: JSONObject, control: String) {
        val bounds = report.getJSONObject(control)
        val point = IntArray(2)
        instrumentation.runOnMainSync {
            val engine = geckoEngineView()
            engine.getLocationOnScreen(point)
            val density = report.getDouble("density")
            point[0] += ((bounds.getDouble("left") + bounds.getDouble("width") / 2) * density).toInt()
            point[1] += ((bounds.getDouble("top") + bounds.getDouble("height") / 2) * density).toInt()
        }
        instrumentation.uiAutomation.executeShellCommand("input tap ${point[0]} ${point[1]}").close()
    }

    private fun awaitImeVisibility(scenario: ActivityScenario<MainActivity>, visible: Boolean) {
        awaitValue("keyboard visible=$visible") {
            var matches = false
            scenario.onActivity { activity ->
                matches = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == visible
            }
            true.takeIf { matches }
        }
    }

    private fun awaitFocusedUploadReport(
        scenario: ActivityScenario<MainActivity>,
        matches: (JSONObject) -> Boolean,
    ): JSONObject = requireNotNull(awaitValue("focused upload page report") {
        scenario.value { controller ->
            controller.selectedTab.title.takeIf { it.startsWith(FOCUSED_UPLOAD_REPORT_PREFIX) }
                ?.removePrefix(FOCUSED_UPLOAD_REPORT_PREFIX)
                ?.let(::JSONObject)
                ?.takeIf(matches)
        }
    })

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
                    "/focused-upload" -> write(connection, "200 OK", "text/html", FOCUSED_UPLOAD_HTML)
                    "/media" -> write(connection, "200 OK", "text/html", MEDIA_HTML)
                    "/notification" -> write(connection, "200 OK", "text/html", NOTIFICATION_HTML)
                    "/notification-state" -> write(
                        connection, "200 OK", "text/html", NOTIFICATION_STATE_HTML,
                    )
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
            const val FOCUSED_UPLOAD_HTML = """
                <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>
                  html,body{margin:0;height:100%;overflow:hidden}
                  #editor{position:fixed;left:15%;top:20%;width:70%;height:30%;box-sizing:border-box}
                  button{position:fixed;left:25%;width:50%;height:40px}
                  #upload{top:5%}#probe{bottom:15%}
                </style>
                <textarea id="editor" aria-label="Candy upload editor"></textarea>
                <input id="file" type="file" hidden>
                <button id="upload">Choose file</button><button id="probe">Check live page</button>
                <script>
                  const editor=document.querySelector('#editor');
                  const upload=document.querySelector('#upload');
                  const probe=document.querySelector('#probe');
                  let heartbeat=0,pickerOpens=0,clicks=0;
                  upload.addEventListener('pointerdown',event=>event.preventDefault());
                  upload.addEventListener('click',()=>{
                    pickerOpens++;document.querySelector('#file').click();sample();
                  });
                  probe.addEventListener('click',()=>{clicks++;sample();});
                  function bounds(element){
                    const rect=element.getBoundingClientRect();
                    return {left:rect.left,top:rect.top,width:rect.width,height:rect.height};
                  }
                  function sample(){
                    document.title='Candy focused upload: '+JSON.stringify({
                      heartbeat:++heartbeat,pickerOpens,clicks,editorFocused:document.activeElement===editor,
                      height:visualViewport.height,width:visualViewport.width,scale:visualViewport.scale,
                      density:devicePixelRatio,editor:bounds(editor),upload:bounds(upload),probe:bounds(probe)
                    });
                  }
                  setInterval(sample,100);sample();
                </script>
            """
            const val MEDIA_HTML = """
                <!doctype html><meta name="viewport" content="width=device-width"><title>media-ready</title>
                <button aria-label="Candy media request" style="position:fixed;inset:0;width:100%;height:100%" onclick="document.title='media-requested';navigator.mediaDevices.getUserMedia({video:true,audio:true}).then(()=>document.title='media-granted').catch(error=>document.title='media-'+error.name)">media</button>
            """
            const val NOTIFICATION_HTML = """
                <!doctype html><meta name="viewport" content="width=device-width"><title>notification-ready</title>
                <button style="position:fixed;inset:0;width:100%;height:100%" onclick="document.title='notification-requested';Notification.requestPermission().then(result=>{document.title='notification-'+result;if(result==='granted'){const notice=new Notification('Candy test notification',{body:'Gecko notification'});notice.onclick=()=>document.title='notification-clicked';}})">notify</button>
            """
            const val NOTIFICATION_STATE_HTML = """
                <!doctype html><script>document.title='notification-state-'+Notification.permission</script>
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
        const val FOCUSED_UPLOAD_REPORT_PREFIX = "Candy focused upload: "
    }
}
