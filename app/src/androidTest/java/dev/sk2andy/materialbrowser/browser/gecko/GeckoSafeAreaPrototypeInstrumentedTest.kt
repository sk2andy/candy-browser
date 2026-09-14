package dev.sk2andy.materialbrowser.browser.gecko

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.data.GeckoSafeAreaSettings
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real CSSOM ownership and sticky scrolling; not a site-coverage or performance claim. */
@RunWith(AndroidJUnit4::class)
class GeckoSafeAreaPrototypeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun persistentTopSurvivesPassiveNormalInlineResetAndReleasesAuthorValues() {
        val title = AtomicReference<String?>(null)
        val settled = AtomicBoolean(false)
        EdgeToEdgeSiteFixtureServer { HTML }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                val policy = GeckoPrivacyPolicy.Disabled.copy(cssSafeAreaTopInsetPx = NATIVE_TOP_PX)
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-prototype-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = policy.copy(geckoSafeAreaSettings = GeckoSafeAreaSettings(enabled = false)),
                    )
                    session.bindExtensionTab("safe-area-prototype-${UUID.randomUUID()}", 1)
                    session.setStateListener { state ->
                        title.set(state.title)
                        settled.set(!state.isLoading)
                    }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/safe-area-prototype")))
                }
                try {
                    awaitReport(title) { settled.get() && it.getBoolean("loaded") && it.getDouble("height") > 0 }
                    scenario.onActivity { updateNativeTop(view) }
                    val initial = awaitReport(title) { abs(it.getDouble("env") * it.getDouble("density") - NATIVE_TOP_PX) < 0.5 }
                    fun updatePolicy(next: GeckoPrivacyPolicy) {
                        val ready = CountDownLatch(1)
                        scenario.onActivity { session.updatePrivacyPolicy(next, onReady = ready::countDown) }
                        assertTrue("Prototype policy acknowledgement", ready.await(30, TimeUnit.SECONDS))
                    }
                    updatePolicy(policy)
                    val protected = awaitReport(title) {
                        abs(it.getDouble("fixed") - it.getDouble("env") - 8) < 0.02 &&
                            abs(it.getDouble("sticky") - it.getDouble("env")) < 0.02 &&
                            abs(it.getDouble("equal") - 2 * it.getDouble("env")) < 0.02 &&
                            abs(it.getDouble("above") - it.getDouble("env") - 80) < 0.02 &&
                            abs(it.getDouble("reset") - it.getDouble("env") - 8) < 0.02 &&
                            abs(it.getDouble("boundary") - it.getDouble("env") - 8) < 0.02 &&
                            !it.getBoolean("resetDone")
                    }
                    assertEquals(NATIVE_TOP_PX.toDouble(), protected.getDouble("env") * protected.getDouble("density"), 0.5)
                    assertEquals(protected.getDouble("env"), protected.getDouble("body"), 0.02)
                    assertEquals(0, protected.getInt("topStyleChanges"))
                    assertEquals(0, protected.getInt("inlineTopCount"))
                    assertEquals("4px", protected.getString("bodyInline"))
                    val passive = awaitReport(title) { it.getBoolean("resetDone") && it.getInt("topStyleChanges") == 3 }
                    assertEquals(0, passive.getInt("trustedClicks"))
                    assertEquals("0px", passive.getString("resetInline"))
                    assertEquals("", passive.getString("resetPriority"))
                    assertEquals("0px", passive.getString("stickyInline"))
                    assertEquals("0px", passive.getString("bodyInline"))
                    assertEquals(protected.getDouble("reset"), passive.getDouble("reset"), 0.02)
                    assertEquals(protected.getDouble("sticky"), passive.getDouble("sticky"), 0.02)
                    assertEquals(protected.getDouble("body"), passive.getDouble("body"), 0.02)
                    assertEquals(19.0, passive.getDouble("boundary"), 0.02)
                    assertEquals("important", passive.getString("boundaryPriority"))
                    scenario.onActivity { session.scrollToVerticalOffset(600) }
                    val scrolled = awaitReport(title) { it.getDouble("scroll") > 80 }
                    assertEquals(protected.getDouble("sticky"), scrolled.getDouble("sticky"), 0.02)
                    assertEquals(protected.getDouble("fixed"), scrolled.getDouble("fixed"), 0.02)
                    assertEquals(protected.getDouble("above"), scrolled.getDouble("above"), 0.02)
                    assertEquals(protected.getDouble("reset"), scrolled.getDouble("reset"), 0.02)
                    assertEquals(3, scrolled.getInt("topStyleChanges"))
                    assertEquals(scrolled.getDouble("env"), scrolled.getDouble("stickyY"), 0.5)
                    scenario.onActivity { activity ->
                        listOf(view, (view as ViewGroup).getChildAt(0)).forEach { surface ->
                            val margins = surface.layoutParams as ViewGroup.MarginLayoutParams
                            assertEquals(0, margins.topMargin)
                            assertEquals(0, margins.bottomMargin)
                            val location = IntArray(2)
                            surface.getLocationInWindow(location)
                            assertEquals(0, location[1])
                            assertEquals(activity.window.decorView.height, surface.height)
                        }
                    }
                    updatePolicy(policy.copy(geckoSafeAreaSettings = GeckoSafeAreaSettings(enabled = false)))
                    val restored = awaitReport(title) {
                        abs(it.getDouble("body")) < 0.02 && abs(it.getDouble("fixed") - 8) < 0.02 &&
                            abs(it.getDouble("sticky")) < 0.02 && abs(it.getDouble("equal") - it.getDouble("env")) < 0.02 &&
                            abs(it.getDouble("above") - 80) < 0.02 && abs(it.getDouble("reset")) < 0.02 &&
                            abs(it.getDouble("boundary") - 19) < 0.02
                    }
                    assertEquals(3, restored.getInt("topStyleChanges"))
                    assertEquals("important", restored.getString("boundaryPriority"))
                    assertEquals(initial.getDouble("height"), restored.getDouble("height"), 0.5)
                } finally {
                    scenario.onActivity {
                        session.releaseView(view)
                        session.setActive(false)
                        session.close()
                    }
                }
            }
        }
    }

    private fun updateNativeTop(view: View) {
        (view as GeckoViewInsetHost).updateInsets(
            GeckoViewInsetRules.resolve(
                safeArea = GeckoViewInsets(left = 0, top = NATIVE_TOP_PX, right = 0, bottom = 0),
                forceNativeSafeArea = false,
                forceNativeTopSafeArea = false,
                isFullscreenContent = false,
                isInsideSafeDrawingHost = false,
            ),
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, NATIVE_TOP_PX, 0, 0))
                .build(),
        )
    }

    private fun awaitReport(title: AtomicReference<String?>, predicate: (JSONObject) -> Boolean): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            val value = title.get().orEmpty()
            val report = if (value.startsWith(REPORT_PREFIX) && value.length < 2_048) {
                runCatching { JSONObject(value.removePrefix(REPORT_PREFIX)) }.getOrNull()
            } else null
            if (report != null && predicate(report)) return report
            SystemClock.sleep(50)
        }
        throw AssertionError("Prototype report did not settle: ${title.get()}")
    }

    private companion object {
        const val NATIVE_TOP_PX = 137
        const val REPORT_PREFIX = "Candy prototype: "
        val HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>
              html,body { margin:0; } #probe { position:absolute; visibility:hidden; padding-top:env(safe-area-inset-top); }
              #fixed { position:fixed; top:8px; left:0; height:20px; } #equal { position:fixed; top:env(safe-area-inset-top); left:80px; }
              #above { position:fixed; top:80px; left:160px; }
              #reset, #boundary { position:fixed; top:8px; left:220px; }
              #sticky { position:sticky; top:0px; height:30px; } #tail { height:2400px; }
            </style>
            <body style="padding-top:4px"><div id="probe"></div><div id="fixed">Fixed</div>
            <div id="equal">Equal</div><div id="above">Above inset</div><div id="reset">Passive reset</div>
            <div id="boundary">Inline important boundary</div>${"<div></div>".repeat(600)}
            <div id="sticky"><header>Late static header inside sticky wrapper</header></div><div id="tail">Tail</div></body>
            <script>
              const anchors = ['fixed','equal','above','sticky','reset','boundary'].map(id => document.getElementById(id));
              const previousTop = new Map(anchors.map(element => [element, element.style.top]));
              let topStyleChanges = 0, resetDone = false, resetScheduled = false, trustedClicks = 0;
              document.addEventListener('click', event => { if (event.isTrusted) trustedClicks++; });
              new MutationObserver(records => {
                for (const record of records) {
                  if (!previousTop.has(record.target)) continue;
                  const current = record.target.style.top;
                  if (previousTop.get(record.target) !== current) topStyleChanges++;
                  previousTop.set(record.target, current);
                }
              }).observe(document.body, {attributes:true, attributeFilter:['style'], subtree:true});
              const report = () => {
                const number = (id, property) => parseFloat(getComputedStyle(document.getElementById(id))[property]);
                const env = number('probe','paddingTop');
                if (!resetScheduled && env > 0 && Math.abs(number('reset','top') - env - 8) < .02 &&
                    Math.abs(number('sticky','top') - env) < .02 && Math.abs(number('above','top') - env - 80) < .02) {
                  resetScheduled = true;
                  setTimeout(() => {
                    document.getElementById('reset').style.setProperty('top','0px');
                    document.getElementById('sticky').style.setProperty('top','0px');
                    document.body.style.setProperty('padding-top','0px');
                    document.getElementById('boundary').style.setProperty('top','19px','important');
                    resetDone = true;
                  }, 1500);
                }
                document.title = '${REPORT_PREFIX}' + JSON.stringify({env:number('probe','paddingTop'), density:devicePixelRatio,
                  body:parseFloat(getComputedStyle(document.body).paddingTop), fixed:number('fixed','top'),
                  equal:number('equal','top'), above:number('above','top'), sticky:number('sticky','top'), stickyY:document.getElementById('sticky').getBoundingClientRect().top,
                  reset:number('reset','top'), boundary:number('boundary','top'), resetDone, trustedClicks, topStyleChanges,
                  inlineTopCount:anchors.filter(element => element.style.top).length, bodyInline:document.body.style.paddingTop,
                  resetInline:document.getElementById('reset').style.top, resetPriority:document.getElementById('reset').style.getPropertyPriority('top'),
                  stickyInline:document.getElementById('sticky').style.top, boundaryPriority:document.getElementById('boundary').style.getPropertyPriority('top'),
                  scroll:scrollY, height:innerHeight, loaded:document.readyState === 'complete'});
              };
              setInterval(report,100); report();
            </script>
        """.trimIndent()
    }
}
