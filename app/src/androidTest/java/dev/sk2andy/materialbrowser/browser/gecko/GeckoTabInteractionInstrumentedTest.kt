package dev.sk2andy.materialbrowser.browser.gecko

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowserMemorySettings
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.data.GeckoSessionStateStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoTabInteractionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = BrowserSessionStore(context)
    private val preferences = context.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
        )
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        store.saveStartupAnimationEnabled(false)
        store.saveOpenHomeOnStartupEnabled(false)
        store.saveSearchSuggestionProvider(SearchSuggestionProvider.None)
        store.saveRecallEnabled(false)
        // Exercise resident session interaction independently of the eviction deadlines.
        store.saveDeveloperSettings(
            DeveloperSettings(
                browserMemorySettings = BrowserMemorySettings(
                    foregroundTabIdleTimeoutMinutes = 60,
                    backgroundWarmTabCount = 1,
                ),
            ),
        )
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun regularGeckoTabsRemainInteractiveAcrossRepeatedSwitchesAndResume() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        verifyInteraction(AndroidBrowserEngineKind.GeckoView, isPrivate = false)
    }

    @Test
    fun privateGeckoTabsRemainInteractiveWithoutPersistingPages() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        verifyInteraction(AndroidBrowserEngineKind.GeckoView, isPrivate = true)
    }

    @Test
    fun systemWebViewTabsPreserveInteractionAndPageState() {
        verifyInteraction(AndroidBrowserEngineKind.SystemWebView, isPrivate = false)
    }

    @Test
    fun temporarilyPausedProtectionDoesNotReloadResidentTabOnSelection() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
        store.saveDeveloperSettings(DeveloperSettings())
        val requests = AtomicInteger()
        EdgeToEdgeSiteFixtureServer { path ->
            if (path == "/a" || path == "/b") {
                requests.incrementAndGet()
                fixtureHtml(path.removePrefix("/"))
            } else "<html></html>"
        }.use { server ->
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                lateinit var firstId: String
                lateinit var secondId: String
                var firstSession: GeckoSession? = null
                var secondSession: GeckoSession? = null
                fun awaitLoaded(expectedRequests: Int) {
                    val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
                    while (SystemClock.elapsedRealtime() < deadline) {
                        var loaded = false
                        scenario.onActivity { activity ->
                            loaded = !activity.browserControllerForTesting().selectedTab.isLoading
                        }
                        if (loaded && requests.get() >= expectedRequests) return
                        SystemClock.sleep(50L)
                    }
                    throw AssertionError("Document did not finish loading: requests=${requests.get()}")
                }
                try {
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        assertEquals(10, controller.residentTabLimit)
                        assertEquals(3, controller.developerSettings.browserMemorySettings.foregroundTabIdleTimeoutMinutes)
                        assertEquals(0, controller.developerSettings.browserMemorySettings.backgroundWarmTabCount)
                        firstId = controller.selectedTabId
                        controller.submitAddress(server.fixtureUrl("/a"))
                    }
                    awaitTitle(scenario, "a:0")
                    awaitLoaded(1)
                    scenario.onActivity { activity ->
                        assertTrue(activity.browserControllerForTesting().pauseSiteProtection(firstId, persistently = false))
                    }
                    // Changing site protection deliberately reloads once; activation must not.
                    awaitLoaded(2)
                    awaitTitle(scenario, "a:0")
                    firstSession = nativeSession(scenario)
                    tapPage(scenario)
                    awaitTitle(scenario, "a:1")
                    scenario.onActivity { activity ->
                        secondId = activity.browserControllerForTesting().createTab(initialUrl = server.fixtureUrl("/b"), isIncognito = false)
                    }
                    awaitLoaded(3)
                    awaitTitle(scenario, "b:0")
                    secondSession = nativeSession(scenario)
                    repeat(3) { cycle ->
                        scenario.onActivity { activity ->
                            val controller = activity.browserControllerForTesting()
                            assertEquals(2, controller.activeTabs.size)
                            assertTrue(controller.isTabSessionResident(firstId))
                            assertTrue(controller.isTabSessionResident(secondId))
                            controller.selectTab(firstId)
                        }
                        assertSame(firstSession, nativeSession(scenario))
                        awaitTitle(scenario, "a:${cycle + 1}")
                        tapPage(scenario)
                        awaitTitle(scenario, "a:${cycle + 2}")
                        scenario.onActivity { it.browserControllerForTesting().selectTab(secondId) }
                        assertSame(secondSession, nativeSession(scenario))
                        awaitTitle(scenario, "b:$cycle")
                        tapPage(scenario)
                        awaitTitle(scenario, "b:${cycle + 1}")
                        assertEquals("Reactivating resident cookie exception must not reload", 3, requests.get())
                    }
                } finally {
                    val result = JSONObject().put("requests", requests.get())
                        .put("firstSession", System.identityHashCode(firstSession))
                        .put("secondSession", System.identityHashCode(secondSession))
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        result.put("residentIds", JSONArray(controller.activeTabs.filter { controller.isTabSessionResident(it.id) }.map { it.id }))
                            .put("titles", JSONArray(controller.activeTabs.map { it.title }))
                            .put("tabCount", controller.activeTabs.size)
                    }
                    File(context.getExternalFilesDir(null), "paused-cookie-resident-tab.txt").writeText(result.toString(2))
                }
            }
        }
    }

    @Test
    fun rapidForegroundGeckoSwitchesPreserveScrollAndSafeAreaLayout() {
        verifyRapidLayoutSwitches(forceSafeArea = false)
    }

    @Test
    fun rapidForegroundForcedSafeAreaSwitchesPreserveScrollAndLayout() {
        verifyRapidLayoutSwitches(forceSafeArea = true)
    }

    @Test
    fun foregroundSwitchesRetainCandySafeAreaStyleNodes() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
        val requests = AtomicInteger()
        val evidence = JSONArray()
        EdgeToEdgeSiteFixtureServer { path ->
            if (path == "/a" || path == "/b") {
                requests.incrementAndGet()
                layoutFixtureHtml(path.removePrefix("/"), stickyHeader = true)
            } else "<html></html>"
        }.use { server ->
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                try {
                    lateinit var firstId: String
                    lateinit var secondId: String
                    scenario.onActivity { activity ->
                        firstId = activity.browserControllerForTesting().createTab(
                            initialUrl = server.fixtureUrl("/a"), isIncognito = false,
                        )
                    }
                    val first = awaitLayoutReport(scenario, "a") {
                        it.getInt("ownedStyleCount") > 0 && it.getInt("protectedMarkerCount") > 0
                    }
                    evidence.put(first)
                    val firstSettled = collectLayoutFrames(scenario, "a", evidence, first.getInt("sequence")).last()
                    val firstSession = nativeSession(scenario)
                    scenario.onActivity { activity ->
                        secondId = activity.browserControllerForTesting().createTab(
                            initialUrl = server.fixtureUrl("/b"), isIncognito = false,
                        )
                    }
                    val second = awaitLayoutReport(scenario, "b") {
                        it.getInt("ownedStyleCount") > 0 && it.getInt("protectedMarkerCount") > 0
                    }
                    evidence.put(second)
                    val secondSettled = collectLayoutFrames(scenario, "b", evidence, second.getInt("sequence")).last()
                    val secondSession = nativeSession(scenario)
                    val initialStyleIds = mapOf(
                        firstId to firstSettled.getString("ownedStyleIds"),
                        secondId to secondSettled.getString("ownedStyleIds"),
                    )
                    val failures = mutableListOf<String>()
                    repeat(4) { cycle ->
                        for ((tabId, page, session) in listOf(
                            Triple(firstId, "a", firstSession), Triple(secondId, "b", secondSession),
                        )) {
                            var previousSequence = 0
                            scenario.onActivity { activity ->
                                val controller = activity.browserControllerForTesting()
                                previousSequence = JSONObject(controller.tabs.first { it.id == tabId }.title
                                    .removePrefix("layout:")).getInt("sequence")
                                controller.selectTab(tabId)
                            }
                            assertSame("CSS fixture replaced its Gecko session", session, nativeSession(scenario))
                            repeat(4) { frame ->
                                val report = awaitLayoutReport(scenario, page) {
                                    it.getBoolean("styleBaselineCaptured") && it.getInt("sequence") > previousSequence
                                }
                                previousSequence = report.getInt("sequence")
                                evidence.put(report)
                                if (report.getInt("removedStyleCount") != 0 || !report.getBoolean("initialStylesRetained") ||
                                    report.getString("initialStyleIds") != initialStyleIds.getValue(tabId) ||
                                    report.getString("ownedStyleIds") != initialStyleIds.getValue(tabId)
                                ) failures += "cycle=$cycle page=$page frame=$frame removed=${report.getInt("removedStyleCount")} " +
                                    "initial=${report.getString("initialStyleIds")} current=${report.getString("ownedStyleIds")}"
                            }
                        }
                    }
                    assertEquals("CSS policy refresh reloaded either fixture", 2, requests.get())
                    assertTrue("Tab switches rebuilt owned CSS: $failures", failures.isEmpty())
                } finally {
                    File(context.getExternalFilesDir(null), "rapid-tab-owned-style-identity.json")
                        .writeText(JSONObject().put("requests", requests.get()).put("reports", evidence).toString(2))
                }
            }
        }
    }

    private fun verifyRapidLayoutSwitches(forceSafeArea: Boolean) {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
        val requests = AtomicInteger()
        val evidence = JSONArray()
        EdgeToEdgeSiteFixtureServer { path ->
            if (path == "/a" || path == "/b") {
                requests.incrementAndGet()
                layoutFixtureHtml(path.removePrefix("/"))
            } else "<html></html>"
        }.use { server ->
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                try {
                    lateinit var firstId: String
                    lateinit var secondId: String
                    scenario.onActivity { activity ->
                        firstId = activity.browserControllerForTesting().createTab(
                            initialUrl = server.fixtureUrl("/a"), isIncognito = false,
                        )
                    }
                    awaitLayoutReport(scenario, "a")
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        if (forceSafeArea) assertTrue(controller.setForceSafeArea(firstId, true))
                        assertTrue(controller.scrollSelectedBrowserEngineToVerticalOffset(900))
                    }
                    awaitLayoutReport(scenario, "a") { it.getDouble("scroll") > 30 }
                    val firstSession = nativeSession(scenario)
                    val firstBaseline = collectLayoutFrames(scenario, "a", evidence).last()
                    assertTrue("Fixture did not exercise a top safe area", firstBaseline.getDouble("env") > 0 || firstBaseline.getInt("nativeTopMargin") > 0)
                    scenario.onActivity { activity ->
                        secondId = activity.browserControllerForTesting().createTab(
                            initialUrl = server.fixtureUrl("/b"), isIncognito = false,
                        )
                    }
                    awaitLayoutReport(scenario, "b")
                    scenario.onActivity { activity ->
                        assertTrue(activity.browserControllerForTesting().scrollSelectedBrowserEngineToVerticalOffset(1500))
                    }
                    awaitLayoutReport(scenario, "b") { it.getDouble("scroll") > 30 }
                    val secondSession = nativeSession(scenario)
                    val secondBaseline = collectLayoutFrames(scenario, "b", evidence).last()
                    val deviations = mutableListOf<String>()
                    repeat(6) { cycle ->
                        for ((tabId, page, baseline) in listOf(
                            Triple(firstId, "a", firstBaseline), Triple(secondId, "b", secondBaseline),
                        )) {
                            var previousSequence = 0
                            scenario.onActivity { activity ->
                                val controller = activity.browserControllerForTesting()
                                previousSequence = JSONObject(controller.tabs.first { it.id == tabId }.title
                                    .removePrefix("layout:")).getInt("sequence")
                                controller.selectTab(tabId)
                            }
                            assertSame(if (page == "a") firstSession else secondSession, nativeSession(scenario))
                            val frames = collectLayoutFrames(scenario, page, evidence, previousSequence)
                            for ((frame, report) in frames.withIndex()) {
                                assertEquals("Switch reloaded document $page", baseline.getString("document"), report.getString("document"))
                                for (key in listOf("scroll", "env", "bodyPadding", "fixedTop", "fixedY", "stickyY", "markerDocumentY", "height", "viewportHeight", "viewportTop", "nativeTopMargin", "nativeBottomMargin", "nativeWidth", "nativeHeight")) {
                                    if (abs(baseline.getDouble(key) - report.getDouble(key)) > 1.0) {
                                        deviations += "cycle=$cycle page=$page frame=$frame $key:${baseline.getDouble(key)}→${report.getDouble(key)}"
                                    }
                                }
                            }
                        }
                    }
                    assertEquals("Foreground switching reloaded a document", 2, requests.get())
                    assertTrue("Foreground layout changed: ${deviations.take(20)}", deviations.isEmpty())
                } finally {
                    File(context.getExternalFilesDir(null), "rapid-tab-layout-forced-$forceSafeArea.json")
                        .writeText(JSONObject().put("forceSafeArea", forceSafeArea).put("requests", requests.get())
                            .put("frames", evidence).toString(2))
                }
            }
        }
    }

    private fun awaitLayoutReport(
        scenario: ActivityScenario<MainActivity>,
        page: String,
        condition: (JSONObject) -> Boolean = { true },
    ): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var title = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { title = it.browserControllerForTesting().selectedTab.title }
            val report = runCatching { JSONObject(title.removePrefix("layout:")) }.getOrNull()
            if (report != null && report.optString("page") == page &&
                report.optString("visibility") == "visible" && report.optBoolean("loaded") && condition(report)
            ) return report
            SystemClock.sleep(16L)
        }
        throw AssertionError("Missing layout report for $page: $title")
    }

    private fun collectLayoutFrames(
        scenario: ActivityScenario<MainActivity>,
        page: String,
        evidence: JSONArray,
        previousSequence: Int = -1,
    ): List<JSONObject> = buildList {
        var sequence = previousSequence
        repeat(16) {
            val report = awaitLayoutReport(scenario, page) { it.getInt("sequence") > sequence }
            sequence = report.getInt("sequence")
            scenario.onActivity { activity ->
                val view = requireNotNull(activity.browserControllerForTesting().selectedBrowserEngineViewForTesting())
                val margins = (view as ViewGroup).getChildAt(0).layoutParams as ViewGroup.MarginLayoutParams
                report.put("nativeTopMargin", margins.topMargin)
                report.put("nativeBottomMargin", margins.bottomMargin)
                report.put("nativeWidth", view.width).put("nativeHeight", view.height)
                report.put("capturedAt", SystemClock.elapsedRealtime())
            }
            evidence.put(report)
            add(report)
            SystemClock.sleep(16L)
        }
    }

    private fun layoutFixtureHtml(page: String, stickyHeader: Boolean = false): String = """
        <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
        <style>
            body{margin:0;padding-top:0;background:#e7f2fe;color:#172b48;font:24px sans-serif;min-height:5000px}
            #fixed{position:${if (stickyHeader) "sticky" else "fixed"};top:0;left:0;width:100%;height:60px;background:#b60086;z-index:2}
            #sticky{position:sticky;top:0;height:40px;background:#007f99}
            #marker{margin-top:600px;height:40px;background:#00a84f}
            #probe{position:fixed;left:0;top:0;visibility:hidden;padding-top:env(safe-area-inset-top)}
        </style>
        <header id="fixed">Fixed $page</header><div id="sticky">Sticky $page</div>
        <main style="padding-top:0"><div id="marker">Document marker</div></main><div id="probe"></div>
        <script>
            const documentId = crypto.randomUUID();
            let sequence = 0;
            let nextStyleId = 0;
            const styleIds = new WeakMap();
            const styleId = node => {
                if (!styleIds.has(node)) styleIds.set(node, ++nextStyleId);
                return styleIds.get(node);
            };
            // Addon-created CSSOM rules are unreadable from the page in Gecko. This
            // fixture authors its only STYLE in HEAD; prototype layers are anonymous
            // direct children of HTML, unlike the separately marked inset bridge.
            const ownedStyles = () => Array.from(document.documentElement.children).filter(node =>
                node.tagName === 'STYLE' && !node.id && !node.hasAttribute('data-candy-browser-owned'));
            const protectedMarkers = () => Array.from(document.querySelectorAll('body,header,main,#sticky'))
                .flatMap(node => node.getAttributeNames().filter(name => name.startsWith('data-candy-safe-area-')));
            let initialStyles = null;
            let removedStyleCount = 0;
            new MutationObserver(records => {
                if (initialStyles === null) return;
                for (const record of records) {
                    for (const node of record.removedNodes) {
                        if (node.nodeType === 1 && node.tagName === 'STYLE') removedStyleCount++;
                    }
                }
            }).observe(document.documentElement, {childList:true, subtree:true});
            document.addEventListener('visibilitychange', () => {
                if (document.visibilityState === 'hidden' && initialStyles === null) {
                    initialStyles = ownedStyles();
                    initialStyles.forEach(styleId);
                }
            });
            const number = (id, property) => parseFloat(getComputedStyle(document.getElementById(id))[property]) || 0;
            const report = () => {
                const currentStyles = ownedStyles();
                document.title = 'layout:' + JSON.stringify({
                    page:'$page', document:documentId, sequence:++sequence,
                    visibility:document.visibilityState, loaded:document.readyState === 'complete',
                    scroll:scrollY, env:number('probe','paddingTop'), bodyPadding:parseFloat(getComputedStyle(document.body).paddingTop),
                    fixedTop:number('fixed','top'), fixedY:document.getElementById('fixed').getBoundingClientRect().top,
                    stickyY:document.getElementById('sticky').getBoundingClientRect().top,
                    markerDocumentY:document.getElementById('marker').getBoundingClientRect().top + scrollY,
                    height:innerHeight, viewportHeight:visualViewport.height, viewportTop:visualViewport.offsetTop,
                    ownedStyleCount:currentStyles.length, ownedStyleIds:currentStyles.map(styleId).join(','),
                    protectedMarkerCount:protectedMarkers().length,
                    styleBaselineCaptured:initialStyles !== null,
                    initialStyleIds:(initialStyles || []).map(styleId).join(','), removedStyleCount,
                    initialStylesRetained:initialStyles !== null && initialStyles.length > 0 &&
                        initialStyles.every(node => node.isConnected && currentStyles.includes(node))
                });
            };
            document.addEventListener('visibilitychange', report);
            addEventListener('resize', report); addEventListener('scroll', report);
            const frame = () => { report(); requestAnimationFrame(frame); };
            requestAnimationFrame(frame);
        </script>
    """.trimIndent()

    private fun verifyInteraction(engine: AndroidBrowserEngineKind, isPrivate: Boolean) {
        store.saveAndroidBrowserEngineKind(engine)
        val requests = AtomicInteger()
        EdgeToEdgeSiteFixtureServer { path ->
            if (path == "/a" || path == "/b") {
                requests.incrementAndGet()
                fixtureHtml(path.removePrefix("/"))
            } else {
                "<html></html>"
            }
        }.use { server ->
            lateinit var firstId: String
            lateinit var secondId: String
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                try {
                    scenario.onActivity { activity ->
                        assertEquals(engine, activity.browserControllerForTesting().browserEngineKind)
                        firstId = activity.browserControllerForTesting().createTab(
                            initialUrl = server.fixtureUrl("/a"),
                            isIncognito = isPrivate,
                        )
                    }
                    awaitTitle(scenario, "a:0")
                    val firstSession = nativeSession(scenario)
                    if (engine == AndroidBrowserEngineKind.GeckoView) {
                        assertTrue("First Gecko renderer must own a session", firstSession != null)
                    }
                    tapPage(scenario)
                    awaitTitle(scenario, "a:1")
                    scenario.onActivity { activity ->
                        secondId = activity.browserControllerForTesting().createTab(
                            initialUrl = server.fixtureUrl("/b"),
                            isIncognito = isPrivate,
                        )
                    }
                    awaitTitle(scenario, "b:0")
                    val secondSession = nativeSession(scenario)
                    if (engine == AndroidBrowserEngineKind.GeckoView) {
                        awaitTitle(scenario, "a:1", firstId, visible = false)
                    }
                    if (engine == AndroidBrowserEngineKind.GeckoView) {
                        assertTrue("Second Gecko renderer must own a session", secondSession != null)
                    }
                    var firstCount = 1
                    var secondCount = 0
                    repeat(SWITCH_CYCLES) { cycle ->
                        tapPage(scenario)
                        awaitTitle(scenario, "b:${++secondCount}")
                        if (cycle == 0) SystemClock.sleep(TAB_BACKGROUND_MILLIS)
                        scenario.onActivity { it.browserControllerForTesting().selectTab(firstId) }
                        awaitTitle(scenario, "a:$firstCount")
                        assertSame(firstSession, nativeSession(scenario))
                        if (engine == AndroidBrowserEngineKind.GeckoView) {
                            awaitTitle(scenario, "b:$secondCount", secondId, visible = false)
                        }
                        tapPage(scenario)
                        awaitTitle(scenario, "a:${++firstCount}")
                        if (cycle == 1) {
                            val previousView = awaitView(scenario)
                            scenario.moveToState(Lifecycle.State.CREATED)
                            if (engine == AndroidBrowserEngineKind.GeckoView) {
                                scenario.onActivity { activity ->
                                    assertNull(activity.browserControllerForTesting().selectedBrowserEngineViewForTesting())
                                    assertNull(previousView.parent)
                                    assertNull(nativeView(previousView)?.session)
                                }
                            }
                            SystemClock.sleep(BACKGROUND_MILLIS)
                            scenario.moveToState(Lifecycle.State.RESUMED)
                            if (engine == AndroidBrowserEngineKind.GeckoView) {
                                assertNotSame(previousView, awaitView(scenario))
                                assertSame(firstSession, nativeSession(scenario))
                            }
                            tapPage(scenario)
                            awaitTitle(scenario, "a:${++firstCount}")
                        }
                        if (cycle < SWITCH_CYCLES - 1) {
                            scenario.onActivity { it.browserControllerForTesting().selectTab(secondId) }
                            awaitTitle(scenario, "b:$secondCount")
                            assertSame(secondSession, nativeSession(scenario))
                            if (engine == AndroidBrowserEngineKind.GeckoView) {
                                awaitTitle(scenario, "a:$firstCount", firstId, visible = false)
                            }
                        }
                    }
                    assertEquals("Tab switches/resume must not reload either document", 2, requests.get())
                    if (isPrivate) assertPrivateDataAbsent(firstId, secondId)
                } finally {
                    saveEvidence(engine, isPrivate)
                }
            }
            if (isPrivate) assertPrivateDataAbsent(firstId, secondId)
        }
    }

    private fun assertPrivateDataAbsent(firstId: String, secondId: String) {
        val storedTabs = preferences.getString(BrowserSessionStore.KEY_TABS, "").orEmpty()
        assertFalse(storedTabs.contains("127.0.0.1"))
        assertFalse(storedTabs.contains(firstId))
        assertFalse(storedTabs.contains(secondId))
        assertEquals(null, GeckoSessionStateStore(context).load(firstId))
        assertEquals(null, GeckoSessionStateStore(context).load(secondId))
    }

    private fun saveEvidence(engine: AndroidBrowserEngineKind, isPrivate: Boolean) {
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(context.getExternalFilesDir(null), "issue-257-${engine.stableId}-$isPrivate.png")
                .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            image.recycle()
        }
    }

    private fun tapPage(scenario: ActivityScenario<MainActivity>) {
        val view = awaitView(scenario)
        var x = 0f
        var y = 0f
        scenario.onActivity {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            x = location[0] + view.width * 0.5f
            y = location[1] + view.height * 0.35f
        }
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).let { event ->
                try {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                } finally {
                    event.recycle()
                }
            }
        }
    }

    private fun awaitView(scenario: ActivityScenario<MainActivity>): View {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            var view: View? = null
            scenario.onActivity { activity ->
                view = activity.browserControllerForTesting().selectedBrowserEngineViewForTesting()
                    ?.takeIf { it.isAttachedToWindow && it.isShown && it.width > 0 && it.height > 0 }
            }
            if (view != null) return requireNotNull(view)
            SystemClock.sleep(50L)
        }
        throw AssertionError("Selected renderer did not attach")
    }

    private fun nativeSession(scenario: ActivityScenario<MainActivity>): GeckoSession? {
        val view = awaitView(scenario)
        var session: GeckoSession? = null
        scenario.onActivity { session = nativeView(view)?.session }
        return session
    }

    private fun nativeView(view: View): GeckoView? {
        if (view is GeckoView) return view
        if (view is ViewGroup) {
            repeat(view.childCount) { index ->
                nativeView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun awaitTitle(
        scenario: ActivityScenario<MainActivity>,
        expected: String,
        tabId: String? = null,
        visible: Boolean = true,
    ) {
        val expectedTitle = "$expected:${if (visible) "visible" else "hidden"}"
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var title = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                title = if (tabId == null) controller.selectedTab.title else {
                    controller.tabs.first { it.id == tabId }.title
                }
            }
            if (title == expectedTitle) {
                instrumentation.waitForIdleSync()
                if (visible) awaitPagePixels(scenario, expected.substringAfter(':').toInt())
                return
            }
            SystemClock.sleep(50L)
        }
        assertEquals("Page did not preserve counter/visibility or accept input", expectedTitle, title)
    }

    private fun awaitPagePixels(scenario: ActivityScenario<MainActivity>, count: Int) {
        val view = awaitView(scenario)
        val expectedColor = if (count % 2 == 0) Color.rgb(0, 127, 153) else Color.rgb(182, 0, 134)
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var observedColor = Color.TRANSPARENT
        while (SystemClock.elapsedRealtime() < deadline) {
            val location = IntArray(2)
            scenario.onActivity { view.getLocationOnScreen(location) }
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val pixels = screenshot.copy(Bitmap.Config.ARGB_8888, false)
            screenshot.recycle()
            try {
                observedColor = pixels.getPixel(
                    location[0] + view.width / 2,
                    location[1] + view.height * 7 / 10,
                )
                if (observedColor == expectedColor) return
            } finally {
                pixels.recycle()
            }
            SystemClock.sleep(100L)
        }
        assertEquals("Live counter changed but rendered page remained stale", expectedColor, observedColor)
    }

    private fun fixtureHtml(page: String): String = """
        <!doctype html><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>body{margin:0;background:#007f99;color:white;font:24px sans-serif}
        button{position:fixed;left:0;top:20vh;width:100vw;height:30vh;font-size:32px}</style>
        <h1>Tab $page</h1><p>Live page state, no reload</p><button>Tap counter: 0</button>
        <script>
        let count = 0;
        const report = () => { document.title = '$page:' + count + ':' + document.visibilityState; };
        document.addEventListener('visibilitychange', report);
        report();
        document.querySelector('button').onclick = () => {
            count++;
            document.body.style.background = count % 2 ? '#b60086' : '#007f99';
            document.querySelector('button').textContent = 'Tap counter: ' + count;
            report();
        };
        </script>
    """.trimIndent()

    private companion object {
        const val SWITCH_CYCLES = 4
        const val TIMEOUT_MILLIS = 20_000L
        const val TAB_BACKGROUND_MILLIS = 12_000L
        const val BACKGROUND_MILLIS = 31_000L
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.TEST_GECKO_TAB_INTERACTION"
    }
}
