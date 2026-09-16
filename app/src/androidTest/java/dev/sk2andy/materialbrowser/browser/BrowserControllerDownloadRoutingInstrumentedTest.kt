package dev.sk2andy.materialbrowser.browser

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadManagerApp
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadProtocol
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadCancellation
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExternalDownloadResponse
import dev.sk2andy.materialbrowser.data.BrowserDownloadSettings
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.DownloadManagerMode
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerDownloadRoutingInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var controller: BrowserController? = null
    private var started = 0
    private var discarded = 0
    private val oneDm = ExternalDownloadManagerApp(
        id = "view|idm.internet.download.manager.plus",
        packageName = "idm.internet.download.manager.plus",
        activityName = "idm.internet.download.manager.Downloader",
        label = "1DM+",
        protocol = ExternalDownloadProtocol.View,
        isOneDm = true,
    )

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    @Test
    fun engineResponseOffersOneDmChoiceAndBuiltInKeepsOriginalResponse() {
        activityRule.scenario.onActivity { activity ->
            val browser = BrowserController(activity).also { controller = it }
            browser.onStart()
            browser.externalDownloadDiscoveryForTesting = { listOf(oneDm) }
            browser.updateDownloadSettings(BrowserDownloadSettings(managerMode = DownloadManagerMode.AskEveryTime))

            browser.dispatchSelectedDownloadResponseForTesting(response())

            assertEquals(0, started)
            assertEquals(listOf(oneDm), requireNotNull(browser.pendingDownloadChoice).apps)
            browser.confirmDownloadChoice(null)
            assertEquals(1, started)
            assertEquals(0, discarded)
            assertNull(browser.pendingDownloadChoice)
        }
    }

    @Test
    fun builtInDownloadRequestsNotificationPermissionAtDecisionTime() {
        activityRule.scenario.onActivity { activity ->
            var notificationPermissionRequests = 0
            val browser = BrowserController(
                activity = activity,
                requestDownloadNotificationPermission = { notificationPermissionRequests++ },
            ).also { controller = it }
            browser.onStart()
            browser.updateDownloadSettings(
                BrowserDownloadSettings(managerMode = DownloadManagerMode.BuiltIn),
            )

            browser.dispatchSelectedDownloadResponseForTesting(response())

            assertEquals(1, started)
            assertEquals(1, notificationPermissionRequests)
        }
    }

    @Test
    fun dismissingChoiceReleasesOriginalResponseWithoutDownloading() {
        activityRule.scenario.onActivity { activity ->
            val browser = BrowserController(activity).also { controller = it }
            browser.onStart()
            browser.externalDownloadDiscoveryForTesting = { listOf(oneDm) }
            browser.updateDownloadSettings(BrowserDownloadSettings(managerMode = DownloadManagerMode.AskEveryTime))
            browser.dispatchSelectedDownloadResponseForTesting(response())
            assertNotNull(browser.pendingDownloadChoice)

            browser.dismissDownloadChoice()

            assertEquals(0, started)
            assertEquals(1, discarded)
        }
    }

    @Test
    fun unavailableExternalManagerFallsBackToOriginalResponse() {
        activityRule.scenario.onActivity { activity ->
            val browser = BrowserController(activity).also { controller = it }
            browser.onStart()
            browser.externalDownloadDiscoveryForTesting = { emptyList() }
            browser.updateDownloadSettings(BrowserDownloadSettings(
                managerMode = DownloadManagerMode.External,
                externalManagerId = oneDm.id,
            ))

            browser.dispatchSelectedDownloadResponseForTesting(response())

            assertEquals(1, started)
            assertEquals(0, discarded)
        }
    }

    @Test
    fun selectedOneDmReceivesEngineResponseWithoutStartingCandyDownload() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(
            oneDm.activityName,
            Instrumentation.ActivityResult(Activity.RESULT_OK, null),
            true,
        )
        try {
            activityRule.scenario.onActivity { activity ->
                val browser = BrowserController(activity).also { controller = it }
                browser.onStart()
                browser.externalDownloadDiscoveryForTesting = { listOf(oneDm) }
                browser.updateDownloadSettings(BrowserDownloadSettings(
                    managerMode = DownloadManagerMode.External,
                    externalManagerId = oneDm.id,
                ))

                browser.dispatchSelectedDownloadResponseForTesting(response())

                assertEquals(1, monitor.hits)
                assertEquals(0, started)
                assertEquals(1, discarded)
                assertNull(browser.pendingDownloadChoice)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun blobResponseStaysInsideEngineEvenWhenExternalManagerSelected() {
        activityRule.scenario.onActivity { activity ->
            val browser = BrowserController(activity).also { controller = it }
            browser.onStart()
            browser.externalDownloadDiscoveryForTesting = { listOf(oneDm) }
            browser.updateDownloadSettings(BrowserDownloadSettings(managerMode = DownloadManagerMode.AskEveryTime))

            browser.dispatchSelectedDownloadResponseForTesting(response("blob:https://example.com/generated"))

            assertEquals(1, started)
            assertEquals(0, discarded)
            assertNull(browser.pendingDownloadChoice)
        }
    }

    @Test
    fun closedSourceTabReleasesQueuedResponseInsteadOfStartingStaleTransfer() {
        activityRule.scenario.onActivity { activity ->
            val browser = BrowserController(activity).also { controller = it }
            browser.onStart()
            browser.externalDownloadDiscoveryForTesting = { listOf(oneDm) }
            browser.updateDownloadSettings(BrowserDownloadSettings(managerMode = DownloadManagerMode.AskEveryTime))
            browser.dispatchSelectedDownloadResponseForTesting(response())
            assertNotNull(browser.pendingDownloadChoice)

            browser.closeTab(browser.selectedTabId)
            browser.confirmDownloadChoice(null)

            assertEquals(0, started)
            assertEquals(1, discarded)
        }
    }

    @Test
    fun closedSourceTabRejectsExternalChoiceWithoutLaunchingOneDm() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(
            oneDm.activityName,
            Instrumentation.ActivityResult(Activity.RESULT_OK, null),
            true,
        )
        try {
            activityRule.scenario.onActivity { activity ->
                val browser = BrowserController(activity).also { controller = it }
                browser.onStart()
                browser.externalDownloadDiscoveryForTesting = { listOf(oneDm) }
                browser.updateDownloadSettings(BrowserDownloadSettings(managerMode = DownloadManagerMode.AskEveryTime))
                browser.dispatchSelectedDownloadResponseForTesting(response())
                browser.closeTab(browser.selectedTabId)

                browser.confirmDownloadChoice(oneDm.id)

                assertEquals(0, monitor.hits)
                assertEquals(0, started)
                assertEquals(1, discarded)
                assertNull(browser.pendingDownloadChoice)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun closedContextSourceRejectsBuiltInChoiceWithoutUsingClosedSession() {
        activityRule.scenario.onActivity { activity ->
            val browser = BrowserController(activity).also { controller = it }
            browser.onStart()
            browser.externalDownloadDiscoveryForTesting = { listOf(oneDm) }
            browser.updateDownloadSettings(BrowserDownloadSettings(managerMode = DownloadManagerMode.AskEveryTime))
            browser.contentActions.show(WebContentTarget(linkUrl = "https://example.com/context.pdf"), browser.selectedTabId)
            browser.downloadContextLink()
            val choice = requireNotNull(browser.pendingDownloadChoice)
            assertTrue(requireNotNull(choice.isSourceCurrent).invoke())

            browser.closeTab(browser.selectedTabId)

            assertFalse(requireNotNull(choice.isSourceCurrent).invoke())
            browser.confirmDownloadChoice(null)
            assertNull(browser.contentActions.lastDownload)
            assertNull(browser.pendingDownloadChoice)
        }
    }

    @Test
    fun navigatedContextSourceRejectsDeferredChoice() {
        activityRule.scenario.onActivity { activity ->
            val browser = BrowserController(activity).also { controller = it }
            browser.onStart()
            browser.externalDownloadDiscoveryForTesting = { listOf(oneDm) }
            browser.updateDownloadSettings(BrowserDownloadSettings(managerMode = DownloadManagerMode.AskEveryTime))
            browser.contentActions.show(WebContentTarget(linkUrl = "https://example.com/context.pdf"), browser.selectedTabId)
            browser.downloadContextLink()
            val choice = requireNotNull(browser.pendingDownloadChoice)
            assertTrue(requireNotNull(choice.isSourceCurrent).invoke())

            browser.submitAddress("https://other.example/new-page")

            assertFalse(requireNotNull(choice.isSourceCurrent).invoke())
            browser.confirmDownloadChoice(null)
            assertNull(browser.contentActions.lastDownload)
            assertNull(browser.pendingDownloadChoice)
        }
    }

    @Test
    fun dismissedExternalPreviewReleasesDeferredResponse() {
        activityRule.scenario.onActivity { activity ->
            val browser = BrowserController(activity).also { controller = it }
            browser.onStart()
            browser.externalDownloadDiscoveryForTesting = { listOf(oneDm) }
            browser.updateDownloadSettings(BrowserDownloadSettings(managerMode = DownloadManagerMode.AskEveryTime))
            val sessionId = preparePreview(browser)
            browser.dispatchAuthorizedExternalPreviewDownloadForTesting(response("https://example.com/Candy.apk"))
            assertNotNull(browser.pendingDownloadChoice)

            assertTrue(browser.dismissExternalLinkPreview(sessionId))
            browser.confirmDownloadChoice(null)

            assertEquals(0, started)
            assertEquals(1, discarded)
            assertNull(browser.pendingDownloadChoice)
        }
    }

    @Test
    fun previewNavigationRoundTripRejectsOldDeferredResponse() {
        activityRule.scenario.onActivity { activity ->
            val browser = BrowserController(activity).also { controller = it }
            browser.onStart()
            browser.externalDownloadDiscoveryForTesting = { listOf(oneDm) }
            browser.updateDownloadSettings(BrowserDownloadSettings(managerMode = DownloadManagerMode.AskEveryTime))
            preparePreview(browser)
            browser.dispatchAuthorizedExternalPreviewDownloadForTesting(response("https://example.com/Candy.apk"))
            val choice = requireNotNull(browser.pendingDownloadChoice)
            assertTrue(requireNotNull(choice.isSourceCurrent).invoke())

            browser.dispatchExternalPreviewEventForTesting(navigationStarted("https://other.example/new-page"))
            browser.dispatchExternalPreviewEventForTesting(navigationStarted(PREVIEW_SOURCE_URL))

            assertFalse(requireNotNull(choice.isSourceCurrent).invoke())
            browser.confirmDownloadChoice(null)
            assertEquals(0, started)
            assertEquals(1, discarded)
            assertEquals(PREVIEW_SOURCE_URL, browser.externalLinkPreviewState?.currentUrl)
        }
    }

    private fun preparePreview(browser: BrowserController): Long {
        assertTrue(browser.openExternalLinkPreview(PREVIEW_SOURCE_URL))
        val sessionId = requireNotNull(browser.externalLinkPreviewState).sessionId
        assertTrue(browser.prepareExternalLinkPreview(sessionId))
        return sessionId
    }

    private fun navigationStarted(url: String) = BrowserEngineEvent(
        tabId = "preview",
        type = BrowserEngineEventType.NavigationStarted,
        address = url,
        title = null,
        canGoBack = false,
        canGoForward = false,
        failureDescription = null,
    )

    private fun response(url: String = "https://example.com/report.pdf") = GeckoExternalDownloadResponse(
        metadata = BrowserEngineDownloadResponse(url, "attachment; filename=report.pdf", "application/pdf"),
        startTransfer = {
            started++
            GeckoDownloadCancellation {}
        },
        discard = { discarded++ },
    )

    private companion object {
        const val PREVIEW_SOURCE_URL = "https://example.com/source"
    }
}
