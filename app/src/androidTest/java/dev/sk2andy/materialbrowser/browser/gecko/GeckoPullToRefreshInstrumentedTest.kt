package dev.sk2andy.materialbrowser.browser.gecko

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.ui.StatusBarStaticOverlayHost
import dev.sk2andy.materialbrowser.ui.BrowserPullToRefreshLayout
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

/** Real Gecko touch/scroll integration with a local GitHub-shaped dialog, not a live-site test. */
@RunWith(AndroidJUnit4::class)
class GeckoPullToRefreshInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun scrolledGitHubLikeDialogKeepsRepeatedPullsWithContent() {
        withFixture("dialog-scrolled", initialScroll = 1200, lockDocumentScroll = true) { fixture ->
            val before = fixture.report()
            assertEquals(0.0, before.getDouble("documentScroll"), 0.5)
            assertEquals(1200.0, before.getDouble("scroll"), 0.5)
            repeat(3) { index ->
                fixture.drag()
                val after = fixture.report()
                fixture.saveEvidence("pull-${index + 1}")
                assertEquals("A scrolled nested container must own the pull", 0, fixture.refreshes.get())
                assertTrue("The touched dialog actually scrolls toward its top", after.getDouble("scroll") < before.getDouble("scroll"))
                assertTrue("Fixture retains room above for each new gesture", after.getDouble("scroll") > 0)
            }
        }
    }

    @Test
    fun freshPullAtDialogTopRefreshesOnce() {
        withFixture("dialog-top") { fixture ->
            fixture.drag()
            fixture.saveEvidence("refresh")
            assertEquals(1, fixture.refreshes.get())
        }
    }

    @Test
    fun rootScrollLockBlocksRefreshEvenAtDialogTop() {
        withFixture("dialog-root-locked", lockDocumentScroll = true) { fixture ->
            assertEquals(0.0, fixture.report().getDouble("scroll"), 0.5)
            assertEquals("hidden", fixture.report().getString("rootOverflow"))
            fixture.drag()
            fixture.saveEvidence("root-locked")
            assertEquals(0, fixture.refreshes.get())
        }
    }

    @Test
    fun reachingDialogTopNeedsAFreshGestureToRefresh() {
        withFixture("dialog-near-top", initialScroll = 120) { fixture ->
            fixture.drag()
            fixture.saveEvidence("reached-top")
            assertEquals(0, fixture.refreshes.get())
            assertEquals(0.0, fixture.report().getDouble("scroll"), 0.5)
            fixture.drag()
            fixture.saveEvidence("fresh-at-top")
            assertEquals(1, fixture.refreshes.get())
        }
    }

    @Test
    fun cancelledPullDoesNotRefreshAndNextPullWorks() {
        withFixture("dialog-cancel") { fixture ->
            fixture.drag(terminalAction = MotionEvent.ACTION_CANCEL)
            assertEquals(0, fixture.refreshes.get())
            fixture.saveEvidence("cancelled")
            fixture.assertIndicatorHidden()
            fixture.drag()
            fixture.saveEvidence("fresh")
            assertEquals(1, fixture.refreshes.get())
        }
    }

    @Test
    fun containedOverscrollAtDialogTopStaysWithPage() {
        withFixture("dialog-contained", overscroll = "contain") { fixture ->
            fixture.drag()
            fixture.saveEvidence("contained")
            assertEquals(0, fixture.refreshes.get())
        }
    }

    @Test
    fun pageOwnedTouchAtTopDoesNotRefresh() {
        withFixture("dialog-page-owned", pageOwnsTouch = true) { fixture ->
            fixture.drag()
            fixture.saveEvidence("page-owned")
            assertEquals(0, fixture.refreshes.get())
            assertTrue(fixture.report().getInt("starts") > 0)
        }
    }

    @Test
    fun pageOwnedFirstMoveAtDocumentTopDoesNotRefresh() {
        withFixture("document-move-owned", pageOwnsMove = true, nested = false) { fixture ->
            fixture.drag()
            fixture.saveEvidence("move-owned")
            assertEquals(0, fixture.refreshes.get())
            assertTrue("The real touchmove listener ran", fixture.report().getInt("moves") > 0)
        }
    }

    @Test
    fun pullWithoutDownPauseStillRefreshes() {
        withFixture("document-fast-pull", nested = false) { fixture ->
            fixture.drag(downPauseMillis = 0)
            fixture.saveEvidence("fast-pull")
            assertEquals(1, fixture.refreshes.get())
        }
    }

    @Test
    fun documentTopStillRefreshes() {
        withFixture("document-top", nested = false) { fixture ->
            fixture.drag()
            fixture.saveEvidence("refresh")
            assertEquals(1, fixture.refreshes.get())
        }
    }

    @Test
    fun shortDocumentStillRefreshes() {
        withFixture("document-short", nested = false, tall = false) { fixture ->
            fixture.drag()
            fixture.saveEvidence("refresh")
            assertEquals(1, fixture.refreshes.get())
        }
    }

    @Test
    fun horizontalGestureDoesNotRefresh() {
        withFixture("dialog-horizontal") { fixture ->
            fixture.drag(horizontal = true)
            fixture.saveEvidence("horizontal")
            assertEquals(0, fixture.refreshes.get())
        }
    }

    private fun withFixture(
        name: String,
        initialScroll: Int = 0,
        overscroll: String = "auto",
        pageOwnsTouch: Boolean = false,
        pageOwnsMove: Boolean = false,
        nested: Boolean = true,
        tall: Boolean = true,
        lockDocumentScroll: Boolean = false,
        block: (Fixture) -> Unit,
    ) {
        val html = fixtureHtml(initialScroll, overscroll, pageOwnsTouch, pageOwnsMove, nested, tall, lockDocumentScroll)
        EdgeToEdgeSiteFixtureServer { html }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                val title = AtomicReference<String?>()
                val refreshes = AtomicInteger()
                val touchDowns = AtomicInteger()
                lateinit var session: GeckoBrowserSession
                lateinit var host: StatusBarStaticOverlayHost
                scenario.onActivity { activity ->
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "pull-refresh-${UUID.randomUUID()}",
                        isPrivate = true,
                        privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(scrollMetricsEnabled = true),
                    )
                    session.bindExtensionTab("pull-refresh-${UUID.randomUUID()}", 1)
                    session.setStateListener { title.set(it.title) }
                    host = StatusBarStaticOverlayHost(activity, pullToRefreshEnabled = true)
                    val engineView = session.createView(activity)
                    engineView.findGeckoView().setOnTouchListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) touchDowns.incrementAndGet()
                        false
                    }
                    host.contentContainer.addView(
                        engineView,
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                    )
                    host.updatePullToRefresh(
                        enabled = true,
                        refreshing = false,
                        indicatorColor = android.graphics.Color.BLUE,
                        indicatorContainerColor = android.graphics.Color.WHITE,
                        indicatorTopInsetPx = 0,
                        // Independently measured document offset: the old admission sees the
                        // document at zero while the touched dialog can still scroll upward.
                        canChildScrollUp = {
                            title.get()?.takeIf { it.startsWith(REPORT_PREFIX) }?.let {
                                JSONObject(it.removePrefix(REPORT_PREFIX)).getDouble("documentScroll") > 0
                            } ?: true
                        },
                        onRefresh = { refreshes.incrementAndGet(); true },
                    )
                    activity.setContentView(host)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/pull-refresh/$name")))
                }
                val fixture = Fixture(name, host, title, refreshes, touchDowns)
                try {
                    awaitCondition("Gecko fixture did not load") {
                        title.get()?.startsWith(REPORT_PREFIX) == true && host.isLaidOut
                    }
                    fixture.saveEvidence("start")
                    block(fixture)
                } finally {
                    instrumentation.runOnMainSync {
                        // This fixture counts refreshes without a navigation completing them.
                        // Stop its spinner before ActivityScenario waits for idle during close.
                        (host.getChildAt(0) as SwipeRefreshLayout).isRefreshing = false
                        session.releaseView(host.contentContainer.getChildAt(0))
                        session.setActive(false)
                        session.close()
                    }
                }
            }
        }
    }

    private inner class Fixture(
        val name: String,
        val host: StatusBarStaticOverlayHost,
        val title: AtomicReference<String?>,
        val refreshes: AtomicInteger,
        val touchDowns: AtomicInteger,
    ) {
        fun report(): JSONObject = JSONObject(requireNotNull(title.get()).removePrefix(REPORT_PREFIX))

        fun assertIndicatorHidden() {
            instrumentation.runOnMainSync {
                val layout = host.getChildAt(0) as BrowserPullToRefreshLayout
                assertTrue("Cancelled pull is not refreshing", !layout.isRefreshing)
                var indicators = 0
                for (index in 0 until layout.childCount) {
                    val child = layout.getChildAt(index)
                    if (child !== layout.contentView) {
                        indicators++
                        assertEquals("Cancelled pull indicator is hidden", View.GONE, child.visibility)
                    }
                }
                assertTrue("The native indicator was inspected", indicators > 0)
            }
        }

        fun drag(
            terminalAction: Int = MotionEvent.ACTION_UP,
            horizontal: Boolean = false,
            downPauseMillis: Long = 100,
        ) {
            val location = IntArray(2)
            instrumentation.runOnMainSync { host.getLocationOnScreen(location) }
            val x = location[0] + host.width * if (horizontal) 0.25f else 0.5f
            val y = location[1] + host.height * 0.45f
            val downTime = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_DOWN, downTime, x, y)
            // Let Gecko's asynchronous hit-test settle before the first slop-crossing MOVE.
            SystemClock.sleep(downPauseMillis)
            repeat(16) { index ->
                val fraction = (index + 1f) / 16
                inject(
                    MotionEvent.ACTION_MOVE,
                    downTime,
                    x + if (horizontal) host.width * 0.4f * fraction else 0f,
                    y + if (horizontal) 0f else host.height * 0.25f * fraction,
                )
                SystemClock.sleep(20)
            }
            inject(
                terminalAction,
                downTime,
                x + if (horizontal) host.width * 0.4f else 0f,
                y + if (horizontal) 0f else host.height * 0.25f,
            )
            SystemClock.sleep(700)
        }

        fun saveEvidence(stage: String) {
            val phase = InstrumentationRegistry.getArguments().getString("evidencePhase", "test")
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "issue-298/$phase").apply { mkdirs() }
            File(directory, "$name-$stage.json").writeText(
                report().put("nativeRefreshCount", refreshes.get())
                    .put("nativeTouchDownCount", touchDowns.get()).put("fixture", name).toString(2),
            )
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(directory, "$name-$stage.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            println("Issue 298 $phase $name $stage: ${report()} nativeRefreshes=${refreshes.get()}")
        }
    }

    private fun inject(action: Int, downTime: Long, x: Float, y: Float) {
        MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).also { event ->
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
    }

    private fun View.findGeckoView(): GeckoView {
        if (this is GeckoView) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                runCatching { getChildAt(index).findGeckoView() }.getOrNull()?.let { return it }
            }
        }
        error("GeckoView descendant is missing")
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var satisfied = false
            instrumentation.runOnMainSync { satisfied = condition() }
            if (satisfied) return
            SystemClock.sleep(50)
        }
        throw AssertionError(message)
    }

    private fun fixtureHtml(
        initialScroll: Int,
        overscroll: String,
        pageOwnsTouch: Boolean,
        pageOwnsMove: Boolean,
        nested: Boolean,
        tall: Boolean,
        lockDocumentScroll: Boolean,
    ): String = """
        <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
          html,body { margin:0; background:#0d1117; color:#f0f6fc; font:16px sans-serif; ${if (nested) "height:100%" else ""}; ${if (lockDocumentScroll) "overflow:hidden" else ""}; }
          #dialog { ${if (nested) "position:fixed;inset:30px 12px;display:flex;flex-direction:column" else ""}; border:1px solid #444;border-radius:12px; }
          header,footer { padding:18px;background:#161b22;flex:none; }
          #scroll { ${if (nested) "overflow-y:auto;flex:1;min-height:0" else ""};overscroll-behavior-y:$overscroll; }
          section { padding:24px;${if (tall) "min-height:4000px" else ""}; }
          p { padding:24px 8px;border-bottom:1px solid #444; }
        </style>
        <div id="dialog"><header>Create new issue: Bug (local fixture)</header>
          <div id="scroll"><section><h2>Description</h2>
            ${(1..if (tall) 32 else 1).joinToString("") { "<p>Issue field $it — nested dialog content</p>" }}
          </section></div><footer>Cancel &nbsp;&nbsp; Create (fixture only)</footer>
        </div>
        <script>
          const scroller = ${if (nested) "document.getElementById('scroll')" else "document.scrollingElement"};
          let starts=0, moves=0;
          ${if (pageOwnsTouch) "scroller.addEventListener('touchstart', e => { starts++; e.preventDefault(); }, {passive:false});" else ""}
          ${if (pageOwnsMove) "scroller.addEventListener('touchmove', e => { moves++; e.preventDefault(); }, {passive:false});" else ""}
          scroller.scrollTop=$initialScroll;
          let previousReport='';
          function report() {
            const snapshot=JSON.stringify({scroll:scroller.scrollTop,documentScroll:window.scrollY,starts,moves,rootOverflow:getComputedStyle(document.documentElement).overflowY});
            if (snapshot !== previousReport) {
              previousReport=snapshot;
              document.title='$REPORT_PREFIX'+snapshot;
            }
            requestAnimationFrame(report);
          }
          requestAnimationFrame(report);
        </script>
    """.trimIndent()

    private companion object {
        const val REPORT_PREFIX = "Candy pull: "
    }
}
