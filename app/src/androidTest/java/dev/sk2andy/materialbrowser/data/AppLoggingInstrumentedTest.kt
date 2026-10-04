package dev.sk2andy.materialbrowser.data

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLoggingInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun exportsSynchronousCrashMetadataWithoutMessagesUrlsOrFilenames() {
        val uri = createExportUri()
        try {
            AppLogging.setPrivateBrowsingActive(false)
            assertTrue(AppLogging.setEnabled(true))
            assertTrue(AppLogging.clear())
            val cause = IllegalArgumentException("https://private.example/path?token=secret")
            cause.stackTrace = emptyArray()
            val error = IllegalStateException("password=private-input", cause)
            error.stackTrace = arrayOf(
                StackTraceElement(
                    "dev.sk2andy.materialbrowser.browser.BrowserController",
                    "start",
                    "/private/user-profile.kt",
                    42,
                ),
            )

            AppLogging.record(AppLogEvent.UncaughtException, error, sync = true)

            val exported = exportText(uri)
            assertTrue(exported.startsWith("Test diagnostics\n\nCandy app logs\n"))
            assertTrue(exported.contains(" UncaughtException\n"))
            assertTrue(exported.contains("  exception java.lang.IllegalStateException\n"))
            assertTrue(exported.contains("  exception java.lang.IllegalArgumentException\n"))
            assertTrue(
                exported.contains(
                    "    at dev.sk2andy.materialbrowser.browser.BrowserController.start:42\n",
                ),
            )
            assertFalse(exported.contains("private"))
            assertFalse(exported.contains("password"))
            assertFalse(exported.contains("secret"))
            assertFalse(exported.contains("https://"))
        } finally {
            AppLogging.setPrivateBrowsingActive(false)
            AppLogging.setEnabled(false)
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun exportsAddressBarParkingReasonsAndManualRestoration() {
        val uri = createExportUri()
        try {
            AppLogging.setPrivateBrowsingActive(false)
            assertTrue(AppLogging.setEnabled(true))
            assertTrue(AppLogging.clear())
            AppLogging.record(AppLogEvent.AddressBarAutoParkedForVisibleControl)
            AppLogging.record(AppLogEvent.AddressBarAutoParkedForFocusedInput)
            AppLogging.record(AppLogEvent.AddressBarManuallyUnparked)

            val exported = exportText(uri)
            assertTrue(exported.contains(" AddressBarAutoParkedForVisibleControl\n"))
            assertTrue(exported.contains(" AddressBarAutoParkedForFocusedInput\n"))
            assertTrue(exported.contains(" AddressBarManuallyUnparked\n"))
            assertFalse(exported.contains("https://"))
        } finally {
            AppLogging.setPrivateBrowsingActive(false)
            AppLogging.setEnabled(false)
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun privateBrowsingDropsEventsAndClearRemovesSavedAndQueuedRecords() {
        val uri = createExportUri()
        try {
            AppLogging.setPrivateBrowsingActive(false)
            assertTrue(AppLogging.setEnabled(true))
            assertTrue(AppLogging.clear())
            AppLogging.record(AppLogEvent.BrowserStarted, sync = true)
            AppLogging.setPrivateBrowsingActive(true)

            AppLogging.record(AppLogEvent.UncaughtException, IllegalStateException(), sync = true)
            AppLogging.record(AppLogEvent.GeckoRendererCrashed)
            AppLogging.record(AppLogEvent.AddressBarAutoParkedForVisibleControl, sync = true)
            AppLogging.record(AppLogEvent.AddressBarAutoParkedForFocusedInput, sync = true)
            AppLogging.record(AppLogEvent.AddressBarManuallyUnparked, sync = true)

            val privateExport = exportText(uri)
            assertTrue(privateExport.contains(" BrowserStarted\n"))
            assertFalse(privateExport.contains("UncaughtException"))
            assertFalse(privateExport.contains("GeckoRendererCrashed"))
            assertFalse(privateExport.contains("AddressBar"))
            AppLogging.setPrivateBrowsingActive(false)
            repeat(128) { AppLogging.record(AppLogEvent.ExtensionInstallFailed) }

            assertTrue(AppLogging.clear())
            AppLogging.record(AppLogEvent.SystemRendererGone, sync = true)

            val clearedExport = exportText(uri)
            assertTrue(clearedExport.contains(" SystemRendererGone\n"))
            assertFalse(clearedExport.contains("BrowserStarted"))
            assertFalse(clearedExport.contains("ExtensionInstallFailed"))
            assertFalse(clearedExport.contains("GeckoRendererCrashed"))

            assertTrue(AppLogging.setEnabled(false))
            AppLogging.record(AppLogEvent.BrowserStarted, sync = true)

            assertEquals("Test diagnostics\n\nCandy app logs\n", exportText(uri))
            val logDirectory = File(context.noBackupFilesDir, "app_logs")
            assertEquals(
                setOf("native-capture.txt"),
                logDirectory.listFiles().orEmpty().map { it.name }.toSet(),
            )
        } finally {
            AppLogging.setPrivateBrowsingActive(false)
            AppLogging.setEnabled(false)
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun downloadDiagnosticsRejectPrivateSessionsAndPrivateOwners() {
        val uri = createExportUri()
        val owner = Any()
        try {
            AppLogging.setPrivateBrowsingActive(false)
            assertTrue(AppLogging.setEnabled(true))
            assertTrue(AppLogging.clear())
            AppLogging.setPrivateBrowsingActive(true, owner)
            AppLogging.recordDiagnostic(AppLogEvent.SystemDownloadNetworkFailed, isPrivate = false)
            assertEquals("Test diagnostics\n\nCandy app logs\n", exportText(uri))

            AppLogging.setPrivateBrowsingActive(false, owner)
            AppLogging.recordDiagnostic(AppLogEvent.SystemDownloadStorageFailed, isPrivate = true)
            assertEquals("Test diagnostics\n\nCandy app logs\n", exportText(uri))

            val error = IllegalStateException("https://private.example Cookie=secret /private/file")
            error.stackTrace = arrayOf(StackTraceElement("Code", "enqueue", "/private/file", 42))
            AppLogging.recordDiagnostic(
                AppLogEvent.SystemDownloadEnqueueFailed,
                isPrivate = false,
                error = error,
            )
            val exported = exportText(uri)
            assertTrue(exported.contains(" SystemDownloadEnqueueFailed\n"))
            assertTrue(exported.contains("  exception java.lang.IllegalStateException\n"))
            assertTrue(exported.contains("    at Code.enqueue:42\n"))
            assertFalse(exported.contains("SystemDownloadNetworkFailed"))
            assertFalse(exported.contains("SystemDownloadStorageFailed"))
            assertFalse(exported.contains("private"))
            assertFalse(exported.contains("Cookie"))
            assertFalse(exported.contains("secret"))
        } finally {
            AppLogging.setPrivateBrowsingActive(false, owner)
            AppLogging.setPrivateBrowsingActive(false)
            AppLogging.setEnabled(false)
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun loggingRemainsPausedUntilAllPrivateOwnersReleaseIt() {
        val uri = createExportUri()
        val ownerA = Any()
        val ownerB = Any()
        try {
            AppLogging.setPrivateBrowsingActive(false)
            assertTrue(AppLogging.setEnabled(true))
            assertTrue(AppLogging.clear())
            AppLogging.setPrivateBrowsingActive(true, ownerA)
            AppLogging.setPrivateBrowsingActive(true, ownerB)
            AppLogging.record(AppLogEvent.GeckoRendererCrashed, sync = true)

            AppLogging.setPrivateBrowsingActive(false, ownerA)
            AppLogging.record(AppLogEvent.GeckoRendererKilled, sync = true)

            assertEquals("Test diagnostics\n\nCandy app logs\n", exportText(uri))

            AppLogging.setPrivateBrowsingActive(false, ownerB)
            AppLogging.record(AppLogEvent.BrowserStarted, sync = true)

            val exported = exportText(uri)
            assertTrue(exported.contains(" BrowserStarted\n"))
            assertFalse(exported.contains("GeckoRendererCrashed"))
            assertFalse(exported.contains("GeckoRendererKilled"))
        } finally {
            AppLogging.setPrivateBrowsingActive(false, ownerA)
            AppLogging.setPrivateBrowsingActive(false, ownerB)
            AppLogging.setPrivateBrowsingActive(false)
            AppLogging.setEnabled(false)
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun createExportUri(): Uri = requireNotNull(
        context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "candy-app-logs-${UUID.randomUUID()}.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            },
        ),
    )

    private fun exportText(uri: Uri): String {
        assertTrue(AppLogging.export(context, uri, "Test diagnostics"))
        return requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }
}
