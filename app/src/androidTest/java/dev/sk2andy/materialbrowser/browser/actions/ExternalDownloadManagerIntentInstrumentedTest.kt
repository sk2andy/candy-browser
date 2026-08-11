package dev.sk2andy.materialbrowser.browser.actions

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequest
import dev.sk2andy.materialbrowser.data.BrowserDownloadSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalDownloadManagerIntentInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val manager = ExternalDownloadManager(context)
    private val request = BrowserDownloadRequest(
        url = "https://example.com/private.pdf",
        fileName = "private.pdf",
        mimeType = "application/pdf",
        userAgent = "Candy/1",
        cookies = "session=secret",
        referrer = "https://example.com/account",
    )

    @Test
    fun oneDmReceivesSessionDataOnlyWhenExplicitlyAllowed() {
        val app = oneDmApp()
        val optedIn = BrowserDownloadSettings(shareSessionDataWithOneDm = true)

        val shared = manager.createIntent(request, app, optedIn, allowSessionData = true)
        assertEquals("session=secret", shared.getStringExtra("extra_cookies"))
        assertEquals("Candy/1", shared.getStringExtra("extra_useragent"))
        assertEquals("https://example.com/account", shared.getStringExtra("extra_referer"))

        val privateIntent = manager.createIntent(request, app, optedIn, allowSessionData = false)
        assertFalse(privateIntent.hasExtra("extra_cookies"))
        assertFalse(privateIntent.hasExtra("extra_useragent"))
        assertFalse(privateIntent.hasExtra("extra_referer"))
    }

    @Test
    fun genericManagerNeverReceivesSessionData() {
        val app = ExternalDownloadManagerApp(
            id = "view|com.example.downloader|com.example.downloader.MainActivity",
            packageName = "com.example.downloader",
            activityName = "com.example.downloader.MainActivity",
            label = "Downloader",
            protocol = ExternalDownloadProtocol.View,
            isOneDm = false,
        )

        val intent = manager.createIntent(
            request,
            app,
            BrowserDownloadSettings(shareSessionDataWithOneDm = true),
            allowSessionData = true,
        )

        assertEquals("https://example.com/private.pdf", intent.dataString)
        assertEquals("application/pdf", intent.type)
        assertFalse(intent.hasExtra("extra_cookies"))
        assertFalse(intent.hasExtra("extra_useragent"))
        assertFalse(intent.hasExtra("extra_referer"))
    }

    @Test
    fun installedSupportedManagersAreDiscoveredAndResolvable() {
        assumeTrue(
            "Requires real 1DM and ADM installations",
            isPackageInstalled("idm.internet.download.manager") && isPackageInstalled("com.dv.adm"),
        )

        val discovered = manager.discover(request)
        val oneDm = discovered.firstOrNull {
            it.packageName == "idm.internet.download.manager"
        }
        val adm = discovered.firstOrNull { it.packageName == "com.dv.adm" }

        assertNotNull(oneDm)
        assertNotNull(adm)
        assertTrue(requireNotNull(oneDm).isOneDm)
        assertFalse(requireNotNull(adm).isOneDm)
        assertNotNull(
            manager.createIntent(
                request,
                oneDm,
                BrowserDownloadSettings(shareSessionDataWithOneDm = true),
                allowSessionData = true,
            ).resolveActivity(context.packageManager),
        )
        assertNotNull(
            manager.createIntent(
                request,
                adm,
                BrowserDownloadSettings(shareSessionDataWithOneDm = true),
                allowSessionData = true,
            ).resolveActivity(context.packageManager),
        )
    }

    private fun oneDmApp() = ExternalDownloadManagerApp(
        id = "view|idm.internet.download.manager|idm.internet.download.manager.Downloader",
        packageName = "idm.internet.download.manager",
        activityName = "idm.internet.download.manager.Downloader",
        label = "1DM",
        protocol = ExternalDownloadProtocol.View,
        isOneDm = true,
    )

    private fun isPackageInstalled(packageName: String): Boolean {
        require(packageName.matches(Regex("[A-Za-z0-9._]+")))
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("pm path $packageName"),
        ).bufferedReader().use { it.readText() }
        return output.lineSequence().any { it.startsWith("package:") }
    }
}
