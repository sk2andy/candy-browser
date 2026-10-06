package dev.sk2andy.materialbrowser.browser.gecko

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoBackgroundMemoryInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun inputFreePageReportsNoNativeOrTrackedFormData() {
        withLoadedForm(html = "<!doctype html><title>form-ready</title><p>Control</p>") { fixture ->
            assertEquals(false, containsUserInput(fixture.session))
            assertEquals(false, containsNativeFormData(fixture.geckoView))
            assertEquals(false, containsFormData(fixture.session))
        }
    }

    @Test
    fun untouchedDefaultSelectedOptionReportsNativeDataWithoutTrustedInput() {
        withLoadedForm(html = """
            <!doctype html><title>form-ready</title>
            <select id="choice"><option value="first">First</option><option value="second" selected>Second</option></select>
        """.trimIndent()) { fixture ->
            assertEquals(false, containsUserInput(fixture.session))
            assertEquals(true, containsNativeFormData(fixture.geckoView))
            assertEquals(true, containsFormData(fixture.session))
        }
    }

    @Test
    fun scriptAssignedTextReportsNativeDataWithoutTrustedInput() {
        withLoadedForm(html = """
            <!doctype html><title>loading</title><input id="editor" name="draft">
            <script>
                document.getElementById('editor').value = 'page-owned';
                document.title = 'form-ready';
            </script>
        """.trimIndent()) { fixture ->
            assertEquals(false, containsUserInput(fixture.session))
            assertEquals(true, containsNativeFormData(fixture.geckoView))
            assertEquals(true, containsFormData(fixture.session))
        }
    }

    private fun containsNativeFormData(view: GeckoView): Boolean? {
        val ready = CountDownLatch(1)
        val result = AtomicReference<Boolean?>()
        instrumentation.runOnMainSync {
            requireNotNull(view.session).containsFormData().accept(
                { value ->
                    result.set(value)
                    ready.countDown()
                },
                { ready.countDown() },
            )
        }
        assertTrue("Native Gecko form query did not complete", ready.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        return result.get()
    }

    private fun containsUserInput(session: GeckoBrowserSession): Boolean? {
        val ready = CountDownLatch(1)
        val result = AtomicReference<Boolean?>()
        instrumentation.runOnMainSync {
            session.containsUserInput { value ->
                result.set(value)
                ready.countDown()
            }
        }
        assertTrue("Trusted-input probe did not complete", ready.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        return result.get()
    }

    @Test
    fun unchangedFormReportsNoDataAfterSessionBecomesInactive() {
        withLoadedForm { fixture ->
            fixture.scenario.onActivity { fixture.session.setActive(false) }

            assertEquals(false, containsUserInput(fixture.session))
            assertEquals(false, containsFormData(fixture.session))
        }
    }

    @Test
    fun editedFormSurvivesInactiveUiMemoryTrimWithoutReload() {
        withLoadedForm { fixture ->
            tapInput(fixture)
            awaitCondition(
                "Input did not receive focus",
                diagnostic = { inputDiagnostic(fixture) },
            ) { fixture.state.get().title == "focused" }
            instrumentation.sendStringSync("candy")
            awaitCondition("Real user input did not reach Gecko") {
                fixture.state.get().title == "edited:candy"
            }
            fixture.scenario.onActivity { fixture.session.setActive(false) }
            assertEquals(true, containsUserInput(fixture.session))
            assertEquals(true, containsFormData(fixture.session))

            fixture.scenario.onActivity {
                val nativeSession = requireNotNull(fixture.geckoView.session)
                fixture.session.trimUiMemory()

                assertSame(nativeSession, fixture.geckoView.session)
                assertTrue("UI memory trim closed the page session", nativeSession.isOpen)
            }
            assertEquals(true, containsUserInput(fixture.session))
            assertEquals(true, containsFormData(fixture.session))
            fixture.scenario.onActivity {
                assertEquals("UI memory trim replaced the edited document", "edited:candy", fixture.state.get().title)
                assertEquals(1, fixture.server.documentRequestCount.get())
                fixture.session.setActive(true)
            }
            instrumentation.waitForIdleSync()
            tapInput(fixture)
            awaitCondition("Reactivated Gecko view did not regain input focus") {
                fixture.geckoView.hasFocus()
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MOVE_END)
            awaitCondition("Reactivated input caret did not move after the existing draft") {
                fixture.state.get().title == "caret:5:5"
            }
            instrumentation.sendStringSync("browser")
            awaitCondition(
                "UI memory trim lost the edited document",
                diagnostic = {
                    "title=${fixture.state.get().title}, requests=${fixture.server.documentRequestCount.get()}, " +
                        "focused=${fixture.geckoView.hasFocus()}"
                },
            ) {
                fixture.state.get().title == "edited:candybrowser"
            }

            assertEquals("UI memory trim reloaded the document", 1, fixture.server.documentRequestCount.get())
        }
    }

    @Test
    fun closedSessionReportsUnknownFormState() {
        withLoadedForm { fixture ->
            fixture.scenario.onActivity { fixture.session.close() }

            assertEquals(null, containsUserInput(fixture.session))
            assertEquals(null, containsFormData(fixture.session))
        }
    }

    @Test
    fun editedAutocompleteOffFormRemainsProtectedAfterBlur() {
        withLoadedForm(html = BLUR_HTML) { fixture ->
            enterDraft(fixture)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
            awaitCondition("Edited input did not lose focus") {
                fixture.state.get().title == "blurred:candy"
            }
            fixture.scenario.onActivity { fixture.session.setActive(false) }

            assertEquals(true, containsUserInput(fixture.session))
            assertEquals(true, containsFormData(fixture.session))
        }
    }

    @Test
    fun editedAutocompleteOffTextareaProtectsInactiveSession() {
        withLoadedForm(html = TEXTAREA_HTML) { fixture ->
            enterDraft(fixture)
            fixture.scenario.onActivity { fixture.session.setActive(false) }

            assertEquals(true, containsUserInput(fixture.session))
            assertEquals(true, containsFormData(fixture.session))
        }
    }

    @Test
    fun editedContenteditableProtectsInactiveSession() {
        withLoadedForm(html = CONTENTEDITABLE_HTML) { fixture ->
            enterDraft(fixture)
            fixture.scenario.onActivity { fixture.session.setActive(false) }

            assertEquals(true, containsUserInput(fixture.session))
            assertEquals(true, containsFormData(fixture.session))
        }
    }

    @Test
    fun editedHttpIframeProtectsParentSession() {
        withLoadedForm(html = IFRAME_HTML, frameHtml = FRAME_HTML) { fixture ->
            enterDraft(fixture)
            fixture.scenario.onActivity { fixture.session.setActive(false) }

            assertEquals(true, containsUserInput(fixture.session))
            assertEquals(true, containsFormData(fixture.session))
        }
    }

    private fun enterDraft(fixture: FormFixture) {
        tapInput(fixture)
        awaitCondition(
            "Input did not receive focus",
            diagnostic = { inputDiagnostic(fixture) },
        ) { fixture.state.get().title == "focused" }
        instrumentation.sendStringSync("candy")
        awaitCondition("Real user input did not reach Gecko") {
            fixture.state.get().title == "edited:candy"
        }
    }

    private fun containsFormData(session: GeckoBrowserSession): Boolean? {
        val ready = CountDownLatch(1)
        val result = AtomicReference<Boolean?>()
        instrumentation.runOnMainSync {
            session.containsFormData { containsData ->
                result.set(containsData)
                ready.countDown()
            }
        }
        assertTrue("Gecko form query did not complete", ready.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        return result.get()
    }

    private fun inputDiagnostic(fixture: FormFixture): String =
        "title=${fixture.state.get().title}, focused=${fixture.geckoView.hasFocus()}, " +
            "size=${fixture.geckoView.width}x${fixture.geckoView.height}"

    private fun tapInput(fixture: FormFixture) {
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
            val event = MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                point[0],
                point[1],
                0,
            ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(50L)
        }
    }

    private fun withLoadedForm(
        html: String = HTML,
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
        const val HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>form-ready</title>
            <label for="editor" style="position:fixed;top:16px;left:16px;z-index:1;pointer-events:none">Draft</label>
            <form autocomplete="off">
                <input id="editor" name="draft" type="text" autocomplete="off"
                    autocapitalize="off" autocorrect="off" spellcheck="false"
                    style="position:fixed;inset:0;box-sizing:border-box;width:100%;height:100%;font-size:24px"
                    onfocus="document.title='focused'"
                    oninput="document.title='edited:'+this.value"
                    onkeyup="if(event.key==='End')document.title='caret:'+this.selectionStart+':'+this.selectionEnd">
            </form>
        """
        const val BLUR_HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>form-ready</title>
            <label for="editor" style="position:fixed;top:16px;left:16px;z-index:1;pointer-events:none">Draft</label>
            <form autocomplete="off">
                <input id="editor" name="draft" type="text" autocomplete="off"
                    autocapitalize="off" autocorrect="off" spellcheck="false"
                    style="position:fixed;inset:0;box-sizing:border-box;width:100%;height:100%;font-size:24px"
                    onfocus="document.title='focused'"
                    oninput="document.title='edited:'+this.value"
                    onblur="document.title='blurred:'+this.value">
            </form>
            <button type="button" style="position:fixed;top:16px;right:16px;z-index:2">Next</button>
        """
        const val TEXTAREA_HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>form-ready</title>
            <label for="editor" style="position:fixed;top:16px;left:16px;z-index:1;pointer-events:none">Draft</label>
            <form autocomplete="off">
                <textarea id="editor" name="draft" autocomplete="off" autocapitalize="off"
                    autocorrect="off" spellcheck="false"
                    style="position:fixed;inset:0;box-sizing:border-box;width:100%;height:100%;font-size:24px"
                    onfocus="document.title='focused'"
                    oninput="document.title='edited:'+this.value"></textarea>
            </form>
        """
        const val CONTENTEDITABLE_HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>form-ready</title>
            <span style="position:fixed;top:16px;left:16px;z-index:1;pointer-events:none">Draft</span>
            <div id="editor" contenteditable="true" role="textbox" autocapitalize="off"
                autocorrect="off" spellcheck="false"
                style="position:fixed;inset:0;box-sizing:border-box;width:100%;height:100%;font-size:24px"
                onfocus="document.title='focused'"
                oninput="document.title='edited:'+this.textContent"></div>
        """
        const val IFRAME_HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>form-ready</title>
            <span style="position:fixed;top:16px;left:16px;z-index:1;pointer-events:none">Framed draft</span>
            <iframe src="/frame" title="Framed editor"
                style="position:fixed;inset:0;border:0;width:100%;height:100%"></iframe>
        """
        const val FRAME_HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <form autocomplete="off">
                <input id="editor" name="draft" type="text" autocomplete="off"
                    autocapitalize="off" autocorrect="off" spellcheck="false"
                    style="position:fixed;inset:0;box-sizing:border-box;width:100%;height:100%;font-size:24px"
                    onfocus="parent.document.title='focused'"
                    oninput="parent.document.title='edited:'+this.value">
            </form>
        """
    }
}
