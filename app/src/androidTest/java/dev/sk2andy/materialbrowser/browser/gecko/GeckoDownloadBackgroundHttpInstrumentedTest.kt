package dev.sk2andy.materialbrowser.browser.gecko

import android.app.ActivityManager
import android.content.ContentUris
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.DownloadsActivity
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest

@RunWith(AndroidJUnit4::class)
class GeckoDownloadBackgroundHttpInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val savedNames = linkedSetOf<String>()

    @After
    fun cleanDownloadedFixture() {
        savedNames.forEach { name ->
            context.contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(name),
            )
        }
    }

    @Test
    fun httpTransferCompletesAfterHomeAndScreenOff() {
        val fileName = "candy-background-http-353-${System.nanoTime()}.bin"
        savedNames += fileName
        val payload = ByteArray(8 * 1024 * 1024) { index -> (index * 31 + index / 251).toByte() }
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val sendBody = CountDownLatch(1)
        val failure = AtomicReference<GeckoDownloadFailure?>()
        val serverFailure = AtomicReference<Throwable?>()
        val received = AtomicReference<Long>()
        val manager = AtomicReference<GeckoDownloadTransferManager>()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val serverThread = Thread {
            try {
                server.accept().use { client ->
                    val input = client.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) Unit
                    val output = client.getOutputStream()
                    output.write(
                        ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/octet-stream\r\n" +
                            "Content-Disposition: attachment; filename=\"$fileName\"\r\n" +
                            "Content-Length: ${payload.size}\r\n" +
                            "Connection: close\r\n\r\n").encodeToByteArray(),
                    )
                    val initialBytes = 64 * 1024
                    output.write(payload, 0, initialBytes)
                    output.flush()
                    check(sendBody.await(90, TimeUnit.SECONDS))
                    output.write(payload, initialBytes, payload.size - initialBytes)
                    output.flush()
                }
            } catch (error: Throwable) {
                serverFailure.set(error)
            }
        }.apply { isDaemon = true; start() }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            device.wakeUp()
            ActivityScenario.launch(DownloadsActivity::class.java).use { scenario ->
                scenario.onActivity {
                    val transferManager = GeckoDownloadTransferManager(context, testExecutor())
                    manager.set(transferManager)
                    transferManager.start(
                        GeckoDownloadTransferRequest(
                            owner = GeckoDownloadOwner("test-profile", isPrivate = false, sessionKey = this),
                            request = WebRequest.Builder("http://127.0.0.1:${server.localPort}/download").build(),
                            fetchFlags = GeckoWebExecutor.FETCH_FLAGS_NONE,
                        ),
                        object : GeckoDownloadTransferListener {
                            override fun onStarted(start: GeckoDownloadTransferStart) { started.countDown() }
                            override fun onComplete(bytesReceived: Long) {
                                received.set(bytesReceived)
                                finished.countDown()
                            }
                            override fun onFailed(reason: GeckoDownloadFailure) {
                                failure.set(reason)
                                finished.countDown()
                            }
                        },
                    )
                }
                try {
                    assertTrue("HTTP transfer did not start", started.await(20, TimeUnit.SECONDS))
                    val deadline = SystemClock.elapsedRealtime() + 10_000L
                    while (!downloadServiceIsForeground() && SystemClock.elapsedRealtime() < deadline) {
                        SystemClock.sleep(50)
                    }
                    assertTrue("download service did not enter foreground", downloadServiceIsForeground())
                    assertTrue("Home navigation failed", device.pressHome())
                    val backgroundDeadline = SystemClock.elapsedRealtime() + 10_000L
                    while (scenario.state.isAtLeast(Lifecycle.State.RESUMED) &&
                        SystemClock.elapsedRealtime() < backgroundDeadline
                    ) SystemClock.sleep(50)
                    assertFalse("activity remained resumed", scenario.state.isAtLeast(Lifecycle.State.RESUMED))
                    device.sleep()
                    assertFalse("display remained on", context.getSystemService(PowerManager::class.java).isInteractive)
                    assertTrue("download service stopped in background", downloadServiceIsForeground())
                    sendBody.countDown()
                    assertTrue("HTTP download did not finish", finished.await(30, TimeUnit.SECONDS))
                    assertNull(failure.get())
                    assertEquals(payload.size.toLong(), received.get())
                    assertArrayEquals(
                        MessageDigest.getInstance("SHA-256").digest(payload),
                        MessageDigest.getInstance("SHA-256").digest(checkNotNull(storedDownload(fileName))),
                    )
                    assertNull(serverFailure.get())
                } finally {
                    device.wakeUp()
                }
            }
        } finally {
            sendBody.countDown()
            manager.get()?.close()
            server.close()
            serverThread.join(5_000)
        }
    }

    @Suppress("DEPRECATION")
    private fun downloadServiceIsForeground(): Boolean = context.getSystemService(ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == GeckoDownloadForegroundService::class.java.name && it.foreground }

    private fun testExecutor() = GeckoWebExecutor(GeckoRuntime.getDefault(context))

    private fun storedDownload(name: String): ByteArray? = context.contentResolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Downloads._ID),
        "${MediaStore.Downloads.DISPLAY_NAME} = ?",
        arrayOf(name),
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) null else context.contentResolver.openInputStream(
            ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0)),
        )?.use { input -> input.readBytes() }
    }
}
