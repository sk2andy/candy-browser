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
import org.junit.Assert.assertNull
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
    fun naviUsesGenericViewProtocolWithoutSessionData() {
        val intent = manager.createIntent(
            request,
            naviApp(),
            BrowserDownloadSettings(shareSessionDataWithOneDm = true),
            allowSessionData = true,
        )

        assertEquals("https://example.com/private.pdf", intent.dataString)
        assertEquals("application/pdf", intent.type)
        assertEquals(NAVI_PACKAGE, intent.component?.packageName)
        assertEquals(NAVI_ACTIVITY, intent.component?.className)
        assertFalse(intent.hasExtra("extra_cookies"))
        assertFalse(intent.hasExtra("extra_useragent"))
        assertFalse(intent.hasExtra("extra_referer"))
    }

    @Test
    fun gopeedUsesExplicitViewProtocolWithoutSessionData() {
        for (allowSessionData in listOf(true, false)) {
            val intent = manager.createIntent(
                request,
                gopeedApp(),
                BrowserDownloadSettings(shareSessionDataWithOneDm = true),
                allowSessionData = allowSessionData,
            )

            assertEquals("android.intent.action.VIEW", intent.action)
            assertEquals("https://example.com/private.pdf", intent.dataString)
            assertEquals("application/pdf", intent.type)
            assertEquals(GOPEED_PACKAGE, intent.component?.packageName)
            assertEquals(GOPEED_ACTIVITY, intent.component?.className)
            assertNull(intent.extras)
        }
    }

    @Test
    fun aria2AppUsesExplicitViewProtocolWithoutSessionData() {
        for (allowSessionData in listOf(true, false)) {
            val intent = manager.createIntent(
                request,
                aria2App(),
                BrowserDownloadSettings(shareSessionDataWithOneDm = true),
                allowSessionData = allowSessionData,
            )

            assertEquals("android.intent.action.VIEW", intent.action)
            assertEquals("https://example.com/private.pdf", intent.dataString)
            assertEquals("application/pdf", intent.type)
            assertEquals(ARIA2_APP_PACKAGE, intent.component?.packageName)
            assertEquals(ARIA2_APP_ACTIVITY, intent.component?.className)
            assertNull(intent.extras)
        }
    }

    @Test
    fun installedAria2AppIsDiscoveredWithoutMimeFilterMatching() {
        assumeTrue("Requires real Aria2App installation", isPackageInstalled(ARIA2_APP_PACKAGE))

        for (downloadRequest in listOf(null, request, request.copy(mimeType = "application/zip"))) {
            val aria2App = manager.discover(downloadRequest)
                .singleOrNull { it.packageName == ARIA2_APP_PACKAGE }

            assertNotNull(aria2App)
            assertFalse(requireNotNull(aria2App).isOneDm)
            assertEquals("view|$ARIA2_APP_PACKAGE", aria2App.id)
            assertEquals(ARIA2_APP_ACTIVITY, aria2App.activityName)
            assertTrue(aria2App.label.isNotBlank())
            assertNotNull(
                manager.createIntent(
                    downloadRequest ?: request,
                    aria2App,
                    BrowserDownloadSettings(),
                    allowSessionData = false,
                ).resolveActivity(context.packageManager),
            )
        }
    }

    @Test
    fun installedGopeedIsDiscoveredWithoutMimeFilterMatching() {
        assumeTrue("Requires real Gopeed installation", isPackageInstalled(GOPEED_PACKAGE))

        for (downloadRequest in listOf(null, request, request.copy(mimeType = "application/zip"))) {
            val gopeed = manager.discover(downloadRequest).singleOrNull { it.packageName == GOPEED_PACKAGE }

            assertNotNull(gopeed)
            assertFalse(requireNotNull(gopeed).isOneDm)
            assertEquals("view|$GOPEED_PACKAGE", gopeed.id)
            assertEquals(GOPEED_ACTIVITY, gopeed.activityName)
            assertTrue(gopeed.label.isNotBlank())
            assertNotNull(
                manager.createIntent(
                    downloadRequest ?: request,
                    gopeed,
                    BrowserDownloadSettings(),
                    allowSessionData = false,
                ).resolveActivity(context.packageManager),
            )
        }
    }

    @Test
    fun installedNaviIsDiscoveredAndResolvable() {
        assumeTrue("Requires real Download Navi installation", isPackageInstalled(NAVI_PACKAGE))

        val navi = manager.discover(request).firstOrNull { it.packageName == NAVI_PACKAGE }

        assertNotNull(navi)
        assertFalse(requireNotNull(navi).isOneDm)
        assertEquals(NAVI_ACTIVITY, navi.activityName)
        assertNotNull(
            manager.createIntent(
                request,
                navi,
                BrowserDownloadSettings(shareSessionDataWithOneDm = true),
                allowSessionData = true,
            ).resolveActivity(context.packageManager),
        )
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

    private fun naviApp() = ExternalDownloadManagerApp(
        id = "view|$NAVI_PACKAGE",
        packageName = NAVI_PACKAGE,
        activityName = NAVI_ACTIVITY,
        label = "Download Navi",
        protocol = ExternalDownloadProtocol.View,
        isOneDm = false,
    )

    private fun gopeedApp() = ExternalDownloadManagerApp(
        id = "view|$GOPEED_PACKAGE",
        packageName = GOPEED_PACKAGE,
        activityName = GOPEED_ACTIVITY,
        label = "Gopeed",
        protocol = ExternalDownloadProtocol.View,
        isOneDm = false,
    )

    private fun aria2App() = ExternalDownloadManagerApp(
        id = "view|$ARIA2_APP_PACKAGE",
        packageName = ARIA2_APP_PACKAGE,
        activityName = ARIA2_APP_ACTIVITY,
        label = "Aria2App",
        protocol = ExternalDownloadProtocol.View,
        isOneDm = false,
    )

    private fun isPackageInstalled(packageName: String): Boolean {
        require(packageName.matches(Regex("[A-Za-z0-9._]+")))
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("pm path $packageName"),
        ).bufferedReader().use { it.readText() }
        return output.lineSequence().any { it.startsWith("package:") }
    }

    private companion object {
        const val GOPEED_PACKAGE = "com.gopeed.gopeed"
        const val GOPEED_ACTIVITY = "com.gopeed.gopeed.MainActivity"
        const val ARIA2_APP_PACKAGE = "com.gianlu.aria2app"
        const val ARIA2_APP_ACTIVITY = "com.gianlu.aria2app.LoadingActivity"
        const val NAVI_PACKAGE = "com.tachibana.downloader"
        const val NAVI_ACTIVITY = "com.tachibana.downloader.ui.adddownload.AddDownloadActivity"
    }
}
