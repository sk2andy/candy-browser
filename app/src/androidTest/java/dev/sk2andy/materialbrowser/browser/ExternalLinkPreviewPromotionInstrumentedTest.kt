package dev.sk2andy.materialbrowser.browser

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ExternalLinkPreviewPromotionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val preferences = context.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Before
    fun setUp() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        BrowserSessionStore(context).apply {
            saveStartupAnimationEnabled(false)
            saveOpenHomeOnStartupEnabled(false)
            saveSearchSuggestionProvider(SearchSuggestionProvider.None)
            assertTrue(saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
        }
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun promotionRetainsLiveDocumentFormScrollAndBackHistoryWithoutReloading() {
        val requests = AtomicInteger()
        val reports = AtomicReference<JSONObject?>(null)
        EdgeToEdgeSiteFixtureServer { requestTarget ->
            val request = Uri.parse("http://127.0.0.1$requestTarget")
            when (request.path) {
                WARM_PATH, PAGE_PATH -> {
                    requests.incrementAndGet()
                    fixtureHtml(request.path == WARM_PATH)
                }
                REPORT_PATH -> {
                    request.getQueryParameter("data")?.let { reports.set(JSONObject(it)) }
                    ""
                }
                else -> ""
            }
        }.use { server ->
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                var sessionId = 0L
                var originalTabCount = 0
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    originalTabCount = controller.tabs.size
                    assertTrue(controller.openExternalLinkPreview(server.fixtureUrl(WARM_PATH)))
                    sessionId = requireNotNull(controller.externalLinkPreviewState).sessionId
                }
                awaitReport(reports) { it.getString("page") == "warm" && it.getInt("ticks") >= 20 }
                tapPageControl(scenario, isPreview = true)
                awaitReport(reports) { it.getString("page") == "main" && it.getInt("ticks") >= 5 }
                awaitCondition("Preview did not retain its previous document") {
                    var ready = false
                    scenario.onActivity { activity ->
                        val state = activity.browserControllerForTesting().externalLinkPreviewState
                        ready = state?.currentUrl == server.fixtureUrl(PAGE_PATH) && state.canGoBack && !state.isLoading
                    }
                    ready
                }
                tapPageControl(scenario, isPreview = true)
                val before = awaitReport(reports) { it.getInt("clicks") == 1 && it.getInt("scrollY") > 500 }
                assertEquals("draft-1", before.getString("input"))
                assertEquals("changed-1", before.getString("marker"))

                lateinit var previewView: View
                lateinit var previewSession: GeckoSession
                val requestsBeforePromotion = requests.get()
                var result: ExternalLinkPreviewCommitResult? = null
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    previewView = requireNotNull(controller.externalLinkPreviewEngineViewForTesting())
                    previewSession = requireNotNull(nativeView(previewView)?.session)
                    assertEquals(originalTabCount, controller.tabs.size)
                    controller.commitExternalLinkPreview(sessionId) { result = it }
                }
                awaitCondition("Preview did not become a regular tab") { result != null }
                assertTrue(result is ExternalLinkPreviewCommitResult.Opened)
                val promoted = awaitReport(reports) {
                    it.getString("page") == "main" && it.getInt("ticks") >= before.getInt("ticks") + 3
                }

                assertEquals("Promotion must keep JavaScript state", 1, promoted.getInt("clicks"))
                assertEquals("Promotion must keep form values", "draft-1", promoted.getString("input"))
                assertEquals("Promotion must keep DOM mutations", "changed-1", promoted.getString("marker"))
                assertTrue(
                    "Promotion must keep scroll position: before=$before, promoted=$promoted",
                    abs(before.getInt("scrollY") - promoted.getInt("scrollY")) <= 5,
                )
                assertEquals("Promotion must not request the document again", requestsBeforePromotion, requests.get())
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertNull(controller.externalLinkPreviewState)
                    assertEquals(originalTabCount + 1, controller.tabs.size)
                    assertEquals((result as ExternalLinkPreviewCommitResult.Opened).tabId, controller.selectedTabId)
                    assertEquals(server.fixtureUrl(PAGE_PATH), controller.selectedTab.url)
                    assertFalse(controller.selectedTab.isIncognito)
                    assertTrue(controller.selectedTab.canGoBack)
                    val trailUrls = controller.candyTrail(controller.selectedTabId).nodes.map { it.url }
                    assertTrue("Promotion must start its Candy Trail at the current page", trailUrls.contains(server.fixtureUrl(PAGE_PATH)))
                    val selectedView = requireNotNull(controller.selectedBrowserEngineViewForTesting())
                    assertSame(previewView, selectedView)
                    assertSame(previewSession, nativeView(selectedView)?.session)
                }

                tapPageControl(scenario, isPreview = false)
                awaitReport(reports) { it.getInt("clicks") == 2 && it.getString("input") == "draft-2" }
                assertEquals("Promoted page must remain interactive without reloading", requestsBeforePromotion, requests.get())
                scenario.onActivity { it.browserControllerForTesting().goBack() }
                awaitReport(reports) { it.getString("page") == "warm" }
                awaitCondition("Promoted session did not deliver back-navigation state to its tab") {
                    var returned = false
                    scenario.onActivity { activity ->
                        val tab = activity.browserControllerForTesting().selectedTab
                        returned = tab.url == server.fixtureUrl(WARM_PATH) && tab.canGoForward
                    }
                    returned
                }
            }
        }
    }

    @Test
    fun promotionBeforePreviewPreparationLoadsOnlyOnceAndRejectsRepeatedCommit() {
        val requests = AtomicInteger()
        val reports = AtomicReference<JSONObject?>(null)
        EdgeToEdgeSiteFixtureServer { requestTarget ->
            val request = Uri.parse("http://127.0.0.1$requestTarget")
            when (request.path) {
                PAGE_PATH -> {
                    requests.incrementAndGet()
                    fixtureHtml(isWarm = false)
                }
                REPORT_PATH -> {
                    request.getQueryParameter("data")?.let { reports.set(JSONObject(it)) }
                    ""
                }
                else -> ""
            }
        }.use { server ->
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    val tabCount = controller.tabs.size
                    assertTrue(controller.openExternalLinkPreview(server.fixtureUrl(PAGE_PATH)))
                    val sessionId = requireNotNull(controller.externalLinkPreviewState).sessionId
                    assertNull(controller.externalLinkPreviewEngineViewForTesting())
                    controller.commitExternalLinkPreview(sessionId) { result ->
                        assertTrue(result is ExternalLinkPreviewCommitResult.Opened)
                    }
                    controller.commitExternalLinkPreview(sessionId) { result ->
                        assertEquals(ExternalLinkPreviewCommitResult.MissingPreview, result)
                    }
                    assertEquals(tabCount + 1, controller.tabs.size)
                }
                awaitReport(reports) { it.getString("page") == "main" && it.getInt("ticks") >= 5 }
                tapPageControl(scenario, isPreview = false)
                awaitReport(reports) { it.getInt("clicks") == 1 }
                assertEquals(1, requests.get())
            }
        }
    }

    @Test
    fun closedPreviewStartsAUsableTabWithAReplacementSession() {
        val reports = AtomicReference<JSONObject?>(null)
        EdgeToEdgeSiteFixtureServer { requestTarget ->
            val request = Uri.parse("http://127.0.0.1$requestTarget")
            if (request.path == REPORT_PATH) {
                request.getQueryParameter("data")?.let { reports.set(JSONObject(it)) }
                ""
            } else {
                fixtureHtml(isWarm = false)
            }
        }.use { server ->
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                lateinit var closedSession: GeckoSession
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertTrue(controller.openExternalLinkPreview(server.fixtureUrl(PAGE_PATH)))
                    val sessionId = requireNotNull(controller.externalLinkPreviewState).sessionId
                    assertTrue(controller.prepareExternalLinkPreview(sessionId))
                    closedSession = requireNotNull(
                        nativeView(requireNotNull(controller.externalLinkPreviewEngineViewForTesting()))?.session,
                    )
                    controller.dispatchExternalPreviewEventForTesting(
                        BrowserEngineEvent(
                            tabId = "preview",
                            type = BrowserEngineEventType.Closed,
                            address = server.fixtureUrl(PAGE_PATH),
                            title = null,
                            canGoBack = false,
                            canGoForward = false,
                            failureDescription = null,
                        ),
                    )
                    controller.commitExternalLinkPreview(sessionId) { result ->
                        assertTrue(result is ExternalLinkPreviewCommitResult.Opened)
                    }
                }
                awaitReport(reports) { it.getString("page") == "main" && it.getInt("ticks") >= 5 }
                tapPageControl(scenario, isPreview = false)
                awaitReport(reports) { it.getInt("clicks") == 1 }
                scenario.onActivity { activity ->
                    val selectedView = requireNotNull(
                        activity.browserControllerForTesting().selectedBrowserEngineViewForTesting(),
                    )
                    assertNotSame(closedSession, nativeView(selectedView)?.session)
                }
            }
        }
    }

    private fun tapPageControl(scenario: ActivityScenario<MainActivity>, isPreview: Boolean) {
        var renderer: GeckoView? = null
        awaitCondition("Fixture renderer did not attach") {
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val host = if (isPreview) controller.externalLinkPreviewEngineViewForTesting()
                else controller.selectedBrowserEngineViewForTesting()
                renderer = host?.let(::nativeView)?.takeIf { it.isAttachedToWindow && it.isShown && it.height > 0 }
            }
            renderer != null
        }
        var x = 0
        var y = 0
        scenario.onActivity {
            val view = requireNotNull(renderer)
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            x = location[0] + view.width / 2
            y = location[1] + view.height * 35 / 100
        }
        assertTrue(device.click(x, y))
    }

    private fun nativeView(view: View): GeckoView? = when (view) {
        is GeckoView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { nativeView(view.getChildAt(it)) }
        else -> null
    }

    private fun awaitReport(reports: AtomicReference<JSONObject?>, ready: (JSONObject) -> Boolean): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            reports.get()?.takeIf(ready)?.let { return it }
            SystemClock.sleep(100L)
        }
        error("Fixture did not reach its expected state; last report: ${reports.get()}")
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(100L)
        }
        assertTrue(message, condition())
    }

    private fun fixtureHtml(isWarm: Boolean): String = """
        <!doctype html><html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
          html, body { margin:0; min-height:6000px; }
          button, a { position:fixed; left:0; top:20vh; width:100vw; height:30vh;
            display:flex; align-items:center; justify-content:center; background:#eee;
            border:0; color:black; font:24px sans-serif; }
        </style></head><body>
        ${if (isWarm) "<a href=\"$PAGE_PATH\">Open interactive page</a>" else "<button id=\"change\">Change document</button>"}
        <input id="draft" value="original"><p id="marker">original</p>
        <script>
          let clicks = 0;
          let ticks = 0;
          const draft = document.getElementById('draft');
          const marker = document.getElementById('marker');
          document.getElementById('change')?.addEventListener('click', () => {
            clicks++;
            draft.value = 'draft-' + clicks;
            marker.textContent = 'changed-' + clicks;
            window.scrollTo(0, 900);
          });
          setInterval(() => {
            ticks++;
            const report = { page:'${if (isWarm) "warm" else "main"}', ticks, clicks,
              input:draft.value, marker:marker.textContent, scrollY:Math.round(window.scrollY) };
            fetch('$REPORT_PATH?data=' + encodeURIComponent(JSON.stringify(report)), {cache:'no-store'});
          }, 100);
        </script></body></html>
    """.trimIndent()

    private companion object {
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.test.EXTERNAL_PREVIEW_PROMOTION"
        const val WARM_PATH = "/preview-promotion/warm"
        const val PAGE_PATH = "/preview-promotion/main"
        const val REPORT_PATH = "/preview-promotion/report"
        const val TIMEOUT_MILLIS = 20_000L
    }
}
