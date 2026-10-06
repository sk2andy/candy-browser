package dev.sk2andy.materialbrowser.browser.gecko

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoBackgroundMemoryFramesInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun untouchedHttpIframeReportsNoData() {
        withLoadedForm(html = PAGE_PREFIX + "<iframe src='/frame'></iframe>", frameHtml = "<!doctype html><p>Frame</p>") { fixture ->
            fixture.scenario.onActivity { fixture.session.setActive(false) }

            assertEquals(false, containsUserInput(fixture.session))
            assertBackgroundResidency(fixture, retained = false)
        }
    }

    @Test
    fun emptyAboutBlankIframeRetainsUnknownDataWhenGeckoInjectsLate() {
        withLoadedForm(html = PAGE_PREFIX + "<iframe src='about:blank'></iframe>") { fixture ->
            fixture.scenario.onActivity { fixture.session.setActive(false) }

            assertEquals(null, containsUserInput(fixture.session))
            assertBackgroundResidency(fixture, retained = true)
        }
    }

    @Test
    fun untouchedSrcdocIframeReportsNoData() {
        withLoadedForm(html = PAGE_PREFIX + "<iframe srcdoc='<p>Frame</p>'></iframe>") { fixture ->
            fixture.scenario.onActivity { fixture.session.setActive(false) }

            assertEquals(false, containsUserInput(fixture.session))
            assertBackgroundResidency(fixture, retained = false)
        }
    }

    @Test
    fun untouchedBlobIframeReportsNoData() {
        withLoadedForm(html = blobFrameHtml("<!doctype html><p>Frame</p>")) { fixture ->
            fixture.scenario.onActivity { fixture.session.setActive(false) }

            assertEquals(false, containsUserInput(fixture.session))
            assertBackgroundResidency(fixture, retained = false)
        }
    }

    @Test
    fun editedSrcdocIframeProtectsParentSession() {
        val frameHtml = FRAME_INPUT_HTML.replace("&", "&amp;").replace("\"", "&quot;")
        withLoadedForm(html = PAGE_PREFIX + "<iframe style='$FRAME_STYLE' srcdoc=\"$frameHtml\"></iframe>") {
            assertEditedFrameProtected(it)
        }
    }

    @Test
    fun editedBlobIframeProtectsParentSession() {
        withLoadedForm(html = blobFrameHtml(FRAME_INPUT_HTML)) {
            assertEditedFrameProtected(it)
        }
    }

    @Test
    fun editedAboutBlankIframeProtectsParentSessionDespiteLateInjection() {
        withLoadedForm(html = """
            <!doctype html><title>loading</title><body style="background:#e7f2fe"><p>Parent</p>
            <iframe id="frame" src="about:blank" style="$FRAME_STYLE"></iframe>
            <script>
                frame.contentDocument.body.innerHTML = `$FRAME_INPUT_HTML`;
                document.title = 'form-ready';
            </script>
        """.trimIndent()) {
            assertEditedFrameProtected(it)
        }
    }

    private fun blobFrameHtml(frameHtml: String): String = """
        <!doctype html><title>loading</title><body style="background:#e7f2fe"><p>Parent</p>
        <iframe id="frame" style="$FRAME_STYLE"></iframe>
        <script>
            frame.onload = () => { document.title = 'form-ready'; };
            frame.src = URL.createObjectURL(new Blob([`$frameHtml`], {type: 'text/html'}));
        </script>
    """.trimIndent()

    private fun assertEditedFrameProtected(fixture: FormFixture) {
        val point = FloatArray(2)
        fixture.scenario.onActivity {
            fixture.geckoView.requestFocus()
            val location = IntArray(2)
            fixture.geckoView.getLocationOnScreen(location)
            point[0] = location[0] + fixture.geckoView.width / 2f
            point[1] = location[1] + fixture.geckoView.height / 4f
        }
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, point[0], point[1], 0)
                .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(50L)
        }
        awaitCondition("Inherited frame input did not receive focus") { fixture.state.get().title == "focused" }
        instrumentation.sendStringSync("candy")
        awaitCondition("Real user input did not reach inherited frame") {
            fixture.state.get().title == "edited:candy"
        }
        fixture.scenario.onActivity { fixture.session.setActive(false) }

        assertEquals(true, containsUserInput(fixture.session))
        assertBackgroundResidency(fixture, retained = true)
    }

    private fun assertBackgroundResidency(fixture: FormFixture, retained: Boolean) {
        val store = BrowserSessionStore(instrumentation.targetContext)
        val originalTabs = store.loadTabs()
        val originalEngine = store.loadAndroidBrowserEngineKind()
        val originalHistory = store.loadHistoryRecordingMode()
        val originalLifetime = store.loadInactiveTabLifetime()
        val originalSettings = store.loadDeveloperSettings()
        var controller: BrowserController? = null
        var backgroundTabId = ""
        try {
            fixture.scenario.onActivity { activity ->
                assertTrue(store.saveTabsImmediately(emptyList(), ""))
                assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
                assertTrue(store.saveHistoryRecordingMode(HistoryRecordingMode.Disabled))
                store.saveInactiveTabLifetime(InactiveTabLifetime.Never)
                store.saveDeveloperSettings(DeveloperSettings())
                val browserController = BrowserController(activity)
                controller = browserController
                backgroundTabId = browserController.selectedTabId
                browserController.installGeckoEngineSessionForTesting(
                    GeckoBrowserEngineSessionAdapter(
                        tabId = backgroundTabId,
                        session = fixture.session,
                        eventSink = BrowserEngineEventSink(browserController::dispatchGeckoEngineEventForTesting),
                    ),
                )
                val tabIndex = browserController.tabs.indexOfFirst { it.id == backgroundTabId }
                browserController.tabs[tabIndex] = browserController.tabs[tabIndex].copy(
                    url = fixture.server.url,
                    title = fixture.state.get().title.orEmpty(),
                )
                browserController.createTab(isIncognito = false)
            }
            // Selection refreshes the page policy asynchronously. Wait for its current
            // handshake before applying the budget, as the production grace normally does.
            if (!retained) {
                val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
                var settledInput = containsUserInput(fixture.session)
                while (settledInput != false && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(POLL_MILLIS)
                    settledInput = containsUserInput(fixture.session)
                }
                assertEquals("Loaded fixture policy did not settle", false, settledInput)
            }
            fixture.scenario.onActivity {
                val browserController = requireNotNull(controller)
                browserController.onAppBackgrounded()
                browserController.trimBackgroundMemoryForTesting()
                assertEquals("Background budget skipped the real Gecko session", 1, pendingMemoryChecks(browserController))
            }
            if (retained) {
                awaitCondition("Protected iframe session check did not complete") {
                    pendingMemoryChecks(requireNotNull(controller)) == 0
                }
            } else {
                awaitCondition("Untouched iframe session was not evicted") {
                    backgroundTabId !in requireNotNull(controller).residentTabIdsForTesting()
                }
            }
            fixture.scenario.onActivity {
                assertEquals(retained, backgroundTabId in requireNotNull(controller).residentTabIdsForTesting())
            }
        } finally {
            fixture.scenario.onActivity {
                controller?.destroy()
                assertTrue(store.saveAndroidBrowserEngineKind(originalEngine))
                assertTrue(store.saveHistoryRecordingMode(originalHistory))
                store.saveInactiveTabLifetime(originalLifetime)
                store.saveDeveloperSettings(originalSettings)
                assertTrue(store.saveTabsImmediately(originalTabs.first, originalTabs.second.orEmpty()))
            }
        }
    }

    private fun pendingMemoryChecks(controller: BrowserController): Int =
        (controller.javaClass.getDeclaredField("pendingMemorySessionChecks").apply { isAccessible = true }
            .get(controller) as Map<*, *>).size

    @Test
    fun untouchedSandboxedHttpIframeReportsNoData() {
        withLoadedForm(html = PAGE_PREFIX + "<iframe sandbox src='/frame'></iframe>", frameHtml = "<!doctype html><p>Frame</p>") { fixture ->
            fixture.scenario.onActivity { fixture.session.setActive(false) }

            assertEquals(false, containsUserInput(fixture.session))
        }
    }

    private fun containsUserInput(session: GeckoBrowserSession): Boolean? {
        val ready = CountDownLatch(1)
        val result = AtomicReference<Boolean?>()
        instrumentation.runOnMainSync {
            session.containsFormData { containsData ->
                result.set(containsData)
                ready.countDown()
            }
        }
        assertTrue("Gecko user-input query did not complete", ready.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        return result.get()
    }

    private fun withLoadedForm(
        html: String = PAGE_PREFIX,
        frameHtml: String? = null,
        test: (FormFixture) -> Unit,
    ) {
        EdgeToEdgeSiteFixtureServer { target ->
            if (target.substringBefore('?') == "/frame") frameHtml else html
        }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var fixture: FormFixture
                scenario.onActivity { activity ->
                    val state = AtomicReference(GeckoBrowserSessionState())
                    val session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "background-memory-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(pageHost = "127.0.0.1"),
                    )
                    session.bindExtensionTab("background-memory-${UUID.randomUUID()}", 1)
                    session.setStateListener { state.set(it) }
                    val view = session.createView(activity)
                    fixture = FormFixture(scenario, server, session, view, view.findGeckoView(), state)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.url))
                }
                try {
                    awaitCondition(
                        "Gecko form document did not load",
                        diagnostic = {
                            "state=${fixture.state.get()}, requests=${server.documentRequestCount.get()}, " +
                                "attached=${fixture.view.isAttachedToWindow}, shown=${fixture.view.isShown}, " +
                                "size=${fixture.view.width}x${fixture.view.height}, " +
                                "presented=${fixture.session.isContentPresented}, " +
                                "nativeOpen=${fixture.geckoView.session?.isOpen}"
                        },
                    ) {
                        fixture.state.get().title == "form-ready" && !fixture.state.get().isLoading &&
                            fixture.view.isAttachedToWindow && fixture.view.isShown &&
                            fixture.view.width > 0 && fixture.view.height > 0 &&
                            fixture.session.isContentPresented
                    }
                    test(fixture)
                } finally {
                    scenario.onActivity {
                        fixture.session.releaseView(fixture.view)
                        fixture.session.close()
                    }
                }
            }
        }
    }

    private fun View.findGeckoView(): GeckoView {
        if (this is GeckoView) return this
        if (this !is ViewGroup) error("GeckoView descendant is missing")
        for (index in 0 until childCount) {
            runCatching { getChildAt(index).findGeckoView() }.getOrNull()?.let { return it }
        }
        error("GeckoView descendant is missing")
    }

    private fun awaitCondition(
        message: String,
        diagnostic: (() -> String)? = null,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        val observed = AtomicBoolean(false)
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { observed.set(condition()) }
            if (observed.get()) return
            SystemClock.sleep(POLL_MILLIS)
        }
        var detail = ""
        instrumentation.runOnMainSync { detail = diagnostic?.invoke().orEmpty() }
        assertTrue("$message; $detail", observed.get())
    }

    private data class FormFixture(
        val scenario: ActivityScenario<GeckoScrollTestActivity>,
        val server: EdgeToEdgeSiteFixtureServer,
        val session: GeckoBrowserSession,
        val view: View,
        val geckoView: GeckoView,
        val state: AtomicReference<GeckoBrowserSessionState>,
    )

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val POLL_MILLIS = 25L
        const val PAGE_PREFIX = "<!doctype html><title>form-ready</title><p>Parent</p>"
        const val FRAME_STYLE = "position:fixed;inset:0;width:100%;height:100%;border:0"
        const val FRAME_INPUT_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <input autocomplete="off" autocapitalize="off" autocorrect="off" spellcheck="false"
                style="position:fixed;inset:0;box-sizing:border-box;width:100%;height:100%;font-size:24px"
                onfocus="parent.document.title=&quot;focused&quot;"
                oninput="parent.document.title=&quot;edited:&quot;+this.value">
        """
    }
}
