package dev.sk2andy.materialbrowser.browser.formdraft

import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.engine.AndroidBrowserEngineFactory
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowserEngineSessionFactory
import dev.sk2andy.materialbrowser.browser.gecko.GeckoScrollTestActivity
import dev.sk2andy.materialbrowser.browser.systemwebview.SystemWebViewBrowserEngineFactory
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.FormDraftStore
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormDraftEngineInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun geckoRestoresTypedDraftAfterReloadOnlyOnRequest() = verifyRecovery(gecko = true)
    @Test fun systemWebViewRestoresTypedDraftAfterReloadOnlyOnRequest() = verifyRecovery(gecko = false)
    @Test fun geckoPrivateDraftRecoversOnlyInMemory() = verifyRecovery(gecko = true, isPrivate = true)
    @Test fun systemWebViewPrivateDraftRecoversOnlyInMemory() = verifyRecovery(gecko = false, isPrivate = true)

    private fun verifyRecovery(gecko: Boolean, isPrivate: Boolean = false) {
        val title = AtomicReference("")
        val profileId = "form-${UUID.randomUUID()}"
        val store = FormDraftStore(instrumentation.targetContext)
        assertTrue(store.clear())
        val settings = BrowserSessionStore(instrumentation.targetContext)
        val wasEnabled = settings.loadFormDraftSavingEnabled()
        settings.saveFormDraftSavingEnabled(true)
        EdgeToEdgeSiteFixtureServer { HTML }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: AndroidBrowserEngineSessionPort
                lateinit var coordinator: FormDraftCoordinator
                lateinit var view: View
                lateinit var factory: AndroidBrowserEngineFactory
                val url = server.fixtureUrl("/reply?id=1#editor")
                var generation = 0
                scenario.onActivity { activity ->
                    activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                    coordinator = FormDraftCoordinator(activity, initiallyEnabled = true)
                    factory = factory(activity, gecko)
                    session = factory.create(
                        tabId = profileId, profileId = profileId, isPrivate = isPrivate,
                        contentKind = BrowserEngineContentKind.RegularTab,
                        eventSink = BrowserEngineEventSink { event ->
                            event.title?.let(title::set)
                            if (event.type == BrowserEngineEventType.NavigationStarted) {
                                generation++
                                coordinator.detach()
                            }
                            if (event.type == BrowserEngineEventType.NavigationCommitted) {
                                coordinator.bind(
                                    session = session,
                                    profileId = profileId,
                                    url = url,
                                    generation = generation,
                                    tabId = profileId,
                                    isPrivate = isPrivate,
                                ) { true }
                            }
                        },
                    )
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    session.execute(BrowserEngineCommands.load(url))
                }
                try {
                    await("Page loaded") { title.get() == "draft-ready" }
                    awaitTouchReady(scenario, session, view)
                    tap(scenario, view)
                    await("Textarea focused", details = { "Actual title: ${title.get()}" }) {
                        title.get() == "draft-focused"
                    }
                    awaitTextInputReady(scenario)
                    typeText("candy", title)
                    if (!isPrivate) {
                        await("Real typed input saved") {
                            store.load(profileId, url, System.currentTimeMillis())?.fields?.singleOrNull()?.value == "candy"
                        }
                    }
                    scenario.onActivity { session.execute(BrowserEngineCommands.reload()) }
                    await("Page reloaded empty", details = { "Actual title: ${title.get()}" }) {
                        title.get() == "draft-ready"
                    }
                    val token = AtomicReference<Long>()
                    await("Recovery offered") {
                        scenario.onActivity { token.set(coordinator.offer?.token) }
                        token.get() != null
                    }
                    assertEquals("draft-ready", title.get())
                    val restored = AtomicBoolean()
                    val done = CountDownLatch(1)
                    scenario.onActivity {
                        coordinator.restore(token.get()) { restored.set(it); done.countDown() }
                    }
                    assertTrue("Restore response", done.await(10, TimeUnit.SECONDS))
                    assertTrue("Matching empty field restored", restored.get())
                    await("Page received input event", details = { "Actual title: ${title.get()}" }) {
                        title.get() == "draft:candy"
                    }
                    if (isPrivate) {
                        assertNull(store.load(profileId, url, System.currentTimeMillis()))
                        scenario.onActivity { coordinator.deleteTab(profileId) }
                        scenario.onActivity { session.execute(BrowserEngineCommands.reload()) }
                        await("Closed private tab draft discarded") { title.get() == "draft-ready" }
                        instrumentation.waitForIdleSync()
                        scenario.onActivity { assertNull(coordinator.offer) }
                    }
                    settings.saveFormDraftSavingEnabled(false)
                    scenario.onActivity { coordinator.setEnabled(false) {} }
                    await("Disable deletes drafts") { store.load(profileId, url, System.currentTimeMillis()) == null }
                } finally {
                    scenario.onActivity {
                        WindowCompat.getInsetsController(it.window, it.window.decorView)
                            .hide(WindowInsetsCompat.Type.ime())
                        coordinator.close()
                        session.releaseView(view)
                        session.execute(BrowserEngineCommands.close())
                        factory.shutdown()
                    }
                    store.clear()
                    settings.saveFormDraftSavingEnabled(wasEnabled)
                }
            }
        }
    }

    private fun factory(context: android.content.Context, gecko: Boolean): AndroidBrowserEngineFactory =
        if (gecko) GeckoBrowserEngineSessionFactory(context) else SystemWebViewBrowserEngineFactory(context)

    private fun tap(scenario: ActivityScenario<GeckoScrollTestActivity>, view: View) {
        val point = FloatArray(2)
        scenario.onActivity {
            view.requestFocus()
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            point[0] = location[0] + view.width / 2f
            point[1] = location[1] + 100f * view.resources.displayMetrics.density
        }
        assertTrue(UiDevice.getInstance(instrumentation).click(point[0].toInt(), point[1].toInt()))
    }

    private fun awaitTouchReady(
        scenario: ActivityScenario<GeckoScrollTestActivity>,
        session: AndroidBrowserEngineSessionPort,
        view: View,
    ) {
        scenario.onActivity { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        var diagnostic = ""
        await("Rendered editor ready for trusted touch", details = { diagnostic }) {
            var ready = false
            scenario.onActivity { activity ->
                val imeVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime())
                diagnostic = "windowFocus=${activity.hasWindowFocus()}, presented=${session.isContentPresented}, " +
                    "size=${view.width}x${view.height}, ime=$imeVisible"
                ready = activity.hasWindowFocus() && session.isContentPresented &&
                    view.width > 0 && view.height > 0 && imeVisible == false
            }
            ready
        }
        if (view is WebView) {
            val presented = CountDownLatch(1)
            scenario.onActivity {
                view.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        presented.countDown()
                    }
                })
            }
            assertTrue("WebView presented editor before touch", presented.await(10, TimeUnit.SECONDS))
        }
    }

    private fun awaitTextInputReady(scenario: ActivityScenario<GeckoScrollTestActivity>) {
        scenario.onActivity { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.ime())
        }
        var diagnostic = ""
        await("IME accepts trusted text input", details = { diagnostic }) {
            var ready = false
            scenario.onActivity { activity ->
                val imeVisible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime())
                val acceptingText = activity.getSystemService(InputMethodManager::class.java).isAcceptingText
                diagnostic = "ime=$imeVisible, acceptingText=$acceptingText, " +
                    "focusedView=${activity.currentFocus?.javaClass?.simpleName}"
                ready = imeVisible == true && acceptingText
            }
            ready
        }
    }

    private fun typeText(text: String, title: AtomicReference<String>) {
        text.forEachIndexed { index, character ->
            // Wait for Chromium's input/selection round trip before sending the next trusted key.
            instrumentation.waitForIdleSync()
            instrumentation.sendStringSync(character.toString())
            val expectedTitle = "draft:${text.take(index + 1)}"
            await("Typed prefix $expectedTitle", details = { "Actual title: ${title.get()}" }) {
                title.get() == expectedTitle
            }
        }
    }

    private fun await(message: String, details: () -> String = { "" }, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50L)
        assertTrue("$message. ${details()}", condition())
    }

    private companion object {
        val HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>draft-loading</title><style>body{margin:0}textarea{position:absolute;top:0;left:0;width:100%;height:45vh}</style>
            <form id="reply-form"><textarea id="reply" name="reply" aria-label="Reply"></textarea></form>
            <script>reply.onfocus=()=>document.title='draft-focused';
            reply.onclick=event=>{if(event.isTrusted&&document.activeElement===reply)document.title='draft-focused';};
            reply.oninput=()=>document.title='draft:'+reply.value;
            requestAnimationFrame(()=>requestAnimationFrame(()=>document.title='draft-ready'));</script>
        """.trimIndent()
    }
}
