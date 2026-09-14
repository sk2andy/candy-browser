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
    fun initialBodyAndHeaderRemainStableAcrossHeldDocumentLoad() {
        val title = AtomicReference<String?>(null)
        val releaseLoad = CountDownLatch(1)
        EdgeToEdgeSiteFixtureServer { path ->
            when (path) {
                "/initial-load-hold.js" -> {
                    releaseLoad.await(20, TimeUnit.SECONDS)
                    "/* Controlled document load hold. */"
                }
                "/site-matrix/initial-load" -> INITIAL_LOAD_HTML
                else -> INITIAL_BOOTSTRAP_HTML
            }
        }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-initial-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(cssSafeAreaTopInsetPx = NATIVE_TOP_PX),
                    )
                    session.bindExtensionTab("safe-area-initial-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/initial-bootstrap")))
                }
                try {
                    awaitReport(title) { it.getBoolean("bootstrap") && it.getBoolean("loaded") }
                    scenario.onActivity { updateNativeTop(view) }
                    awaitReport(title) { abs(it.getDouble("env") * it.getDouble("density") - NATIVE_TOP_PX) < 0.5 }
                    scenario.onActivity { assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/initial-load"))) }
                    val held = awaitReport(title) {
                        !it.getBoolean("bootstrap") && !it.getBoolean("loaded") && it.getInt("samples") >= 5 &&
                            abs(it.getDouble("headerY") - it.getDouble("env") - 8) < 0.02
                    }
                    assertEquals(NATIVE_TOP_PX.toDouble(), held.getDouble("env") * held.getDouble("density"), 0.5)
                    assertEquals("Initial rendered body already protected", held.getDouble("env"), held.getDouble("firstBody"), 0.02)
                    assertEquals("Header protected while load is pending", held.getDouble("env") + 8, held.getDouble("protectedHeaderY"), 0.5)
                    assertEquals("Body remains stable while load is pending", held.getDouble("minBody"), held.getDouble("maxBody"), 0.02)
                    assertEquals("Header remains stable while load is pending", held.getDouble("minHeaderY"), held.getDouble("maxHeaderY"), 0.5)
                    assertTrue("Header protected within initial-load deadline", held.getDouble("protectedAt") - held.getDouble("firstAt") < 400)
                    releaseLoad.countDown()
                    val loaded = awaitReport(title) { !it.getBoolean("bootstrap") && it.getBoolean("loaded") && it.getInt("afterLoadSamples") >= 5 }
                    assertEquals("Load completion adds no body offset", held.getDouble("firstBody"), loaded.getDouble("body"), 0.02)
                    assertEquals("Load completion adds no header jump", held.getDouble("protectedHeaderY"), loaded.getDouble("headerY"), 0.5)
                    println("Prototype initial-load timing: $loaded")
                } finally {
                    releaseLoad.countDown()
                    scenario.onActivity {
                        session.releaseView(view)
                        session.setActive(false)
                        session.close()
                    }
                }
            }
        }
    }

    @Test
    fun persistentTopSurvivesPassiveNormalInlineResetAndReleasesAuthorValues() {
        val title = AtomicReference<String?>(null)
        val settled = AtomicBoolean(false)
        EdgeToEdgeSiteFixtureServer { path -> if (path == "/late-source.css") LATE_LINK_CSS else HTML }.use { server ->
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
                    println("Prototype CSSOM probes: ${initial.getJSONObject("mediaAttributeProbe")}; ${initial.getJSONObject("mediaListProbe")}")
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
                    assertEquals("static", protected.getString("latentPosition"))
                    assertEquals("none", protected.getString("latentDisplay"))
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
                    val scrolled = awaitReport(title) { it.getDouble("scroll") > 80 && it.getBoolean("resizeSettled") }
                    assertEquals(0, scrolled.getInt("trustedClicks"))
                    assertEquals(1, scrolled.getInt("latentActivations"))
                    assertEquals(3, scrolled.getInt("fixtureResizes"))
                    assertEquals("fixed", scrolled.getString("latentPosition"))
                    assertEquals("block", scrolled.getString("latentDisplay"))
                    assertEquals(scrolled.getDouble("env"), scrolled.getDouble("latent"), 0.02)
                    assertEquals(scrolled.getDouble("env"), scrolled.getDouble("newLatent"), 0.02)
                    assertEquals(scrolled.getDouble("env"), scrolled.getDouble("latentImmediate"), 0.02)
                    assertEquals(scrolled.getDouble("env"), scrolled.getDouble("newImmediate"), 0.02)
                    assertEquals("", scrolled.getString("latentInline"))
                    assertEquals("", scrolled.getString("newLatentInline"))
                    assertEquals(protected.getDouble("sticky"), scrolled.getDouble("sticky"), 0.02)
                    assertEquals(protected.getDouble("fixed"), scrolled.getDouble("fixed"), 0.02)
                    assertEquals(protected.getDouble("above"), scrolled.getDouble("above"), 0.02)
                    assertEquals(protected.getDouble("reset"), scrolled.getDouble("reset"), 0.02)
                    assertEquals(3, scrolled.getInt("topStyleChanges"))
                    assertEquals(scrolled.getDouble("env"), scrolled.getDouble("stickyY"), 0.5)
                    val lateReady = awaitReport(title) { it.getBoolean("lateReady") }
                    assertEquals(0, lateReady.getInt("scrollAfterLateInsert"))
                    scenario.onActivity { session.scrollToVerticalOffset(1_100) }
                    val lateActivated = awaitReport(title) {
                        it.getInt("lateStage") == 1 &&
                            abs(it.getDouble("lateStyle") - it.getDouble("env") - 8) < 0.02 &&
                            abs(it.getDouble("lateLink") - it.getDouble("env") - 20) < 0.02
                    }
                    assertEquals(0, lateActivated.getInt("trustedClicks"))
                    assertEquals(lateActivated.getDouble("env") + 8, lateActivated.getDouble("lateStyle"), 0.02)
                    assertEquals(lateActivated.getDouble("env") + 20, lateActivated.getDouble("lateLink"), 0.02)
                    assertEquals(0, lateActivated.getInt("lateInlineCount"))
                    scenario.onActivity { session.scrollToVerticalOffset(1_700) }
                    val lateUpdated = awaitReport(title) {
                        it.getInt("lateStage") == 2 && it.getInt("lateElapsed") >= 1_000 &&
                            it.getBoolean("lateResizeSettled") &&
                            abs(it.getDouble("lateStyle") - it.getDouble("env") - 24) < 0.02
                    }
                    assertEquals(lateUpdated.getDouble("env") + 20, lateUpdated.getDouble("lateLink"), 0.02)
                    assertEquals(0, lateUpdated.getInt("lateInlineCount"))
                    assertEquals(0, lateUpdated.getInt("trustedClicks"))
                    assertEquals(3, lateUpdated.getInt("lateResizes"))
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
                            abs(it.getDouble("boundary") - 19) < 0.02 && abs(it.getDouble("latent")) < 0.02 &&
                            abs(it.getDouble("newLatent")) < 0.02 && abs(it.getDouble("lateStyle") - 24) < 0.02 &&
                            abs(it.getDouble("lateLink") - 20) < 0.02
                    }
                    assertEquals(3, restored.getInt("topStyleChanges"))
                    assertEquals("important", restored.getString("boundaryPriority"))
                    assertEquals(initial.getDouble("height"), restored.getDouble("height"), 0.5)
                } catch (failure: AssertionError) {
                    scenario.onActivity { GeckoDomDiagnostics.request() }
                    val deadline = SystemClock.elapsedRealtime() + 10_000
                    while (GeckoDomDiagnostics.snapshot.status == "pending" && SystemClock.elapsedRealtime() < deadline) {
                        instrumentation.waitForIdleSync()
                        SystemClock.sleep(50)
                    }
                    val payload = GeckoDomDiagnostics.snapshot.payload
                    val diagnostics = payload?.let { JSONObject(it).optJSONObject("cssSafeAreaDiagnostics") }
                    val detail = "Native CSS probe ${GeckoDomDiagnostics.snapshot.status}: $diagnostics"
                    println(detail)
                    throw AssertionError("${failure.message}\n$detail", failure)
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
        const val LATE_LINK_CSS = "#late-link.late-source {position:fixed;top:20px;display:block;}"
        val INITIAL_BOOTSTRAP_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <div id="probe" style="padding-top:env(safe-area-inset-top)"></div>
            <script>
              setInterval(() => { document.title = '${REPORT_PREFIX}' + JSON.stringify({bootstrap:true,
                env:parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop),
                density:devicePixelRatio,loaded:document.readyState === 'complete'}); }, 50);
            </script>
        """.trimIndent()
        val INITIAL_LOAD_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>html,body {margin:0;} #probe {position:absolute;visibility:hidden;padding-top:env(safe-area-inset-top);}</style>
            <body style="padding-top:4px"><div id="probe"></div>
            <header id="initial-header" style="position:fixed;top:8px;height:24px">Initial header</header><main>Initial body</main>
            <script>
              let samples = 0, afterLoadSamples = 0, firstBody = null, firstHeaderY = null, firstAt = null;
              let protectedHeaderY = null, protectedAt = null;
              let minBody = Infinity, maxBody = -Infinity, minHeaderY = Infinity, maxHeaderY = -Infinity;
              const render = () => {
                const env = parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop);
                const body = parseFloat(getComputedStyle(document.body).paddingTop);
                const headerY = document.getElementById('initial-header').getBoundingClientRect().top;
                const loaded = document.readyState === 'complete';
                if (env > 0) {
                  if (firstBody === null) { firstBody = body; firstHeaderY = headerY; firstAt = performance.now(); }
                  if (protectedHeaderY === null && Math.abs(headerY - env - 8) < .02) {
                    protectedHeaderY = headerY; protectedAt = performance.now();
                  }
                  samples++;
                  if (loaded) afterLoadSamples++;
                  else {
                    minBody = Math.min(minBody,body); maxBody = Math.max(maxBody,body);
                    if (protectedHeaderY !== null) {
                      minHeaderY = Math.min(minHeaderY,headerY); maxHeaderY = Math.max(maxHeaderY,headerY);
                    }
                  }
                  document.title = '${REPORT_PREFIX}' + JSON.stringify({bootstrap:false,env,density:devicePixelRatio,
                    loaded,samples,afterLoadSamples,firstBody,firstHeaderY,firstAt,protectedHeaderY,protectedAt,
                    minBody,maxBody,minHeaderY,maxHeaderY,body,headerY});
                }
                requestAnimationFrame(render);
              };
              requestAnimationFrame(render);
            </script><script async src="/initial-load-hold.js"></script>
        """.trimIndent()
        val HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>
              html,body { margin:0; } #probe { position:absolute; visibility:hidden; padding-top:env(safe-area-inset-top); }
              #fixed { position:fixed; top:8px; left:0; height:20px; } #equal { position:fixed; top:env(safe-area-inset-top); left:80px; }
              #above { position:fixed; top:80px; left:160px; }
              #reset, #boundary { position:fixed; top:8px; left:220px; }
              #latent { position:static; top:auto; display:none; }
              #late-style, #late-link { position:static; top:auto; display:none; }
              #latent.persistent-header { position:fixed!important; top:0px!important; display:block; left:0; height:24px; }
              #new-latent.persistent-header { position:fixed!important; top:0px!important; left:100px; }
              #sticky { position:sticky; top:0px; height:30px; } #tail { height:2400px; }
            </style>
            <body style="padding-top:4px"><div id="probe"></div><div id="fixed">Fixed</div>
            <div id="equal">Equal</div><div id="above">Above inset</div><div id="reset">Passive reset</div>
            <div id="boundary">Inline important boundary</div><div id="latent" class="aok-hidden">Latent header</div>
            <div id="late-style">Late style header</div><div id="late-link">Late link header</div>
            ${"<div></div>".repeat(600)}
            <div id="sticky"><header>Late static header inside sticky wrapper</header></div><div id="tail">Tail</div></body>
            <script>
              const anchors = ['fixed','equal','above','sticky','reset','boundary','latent'].map(id => document.getElementById(id));
              const probeStyleMutation = useMediaList => {
                const style = document.createElement('style'); style.setAttribute('media','not all');
                document.documentElement.appendChild(style);
                style.sheet.insertRule('#fixture-media-probe {position:fixed;top:123px;}',0);
                const sheet = style.sheet, before = sheet.cssRules.length;
                if (useMediaList) sheet.media.mediaText = ''; else style.removeAttribute('media');
                const afterMedia = style.sheet.cssRules.length, sameSheet = style.sheet === sheet;
                document.documentElement.appendChild(style);
                const afterMove = style.sheet.cssRules.length;
                style.remove(); return {before,afterMedia,afterMove,sameSheet};
              };
              const mediaAttributeProbe = probeStyleMutation(false), mediaListProbe = probeStyleMutation(true);
              const previousTop = new Map(anchors.map(element => [element, element.style.top]));
              let topStyleChanges = 0, resetDone = false, resetScheduled = false, trustedClicks = 0;
              let latentActivations = 0, fixtureResizes = 0, resizeSettled = false;
              let latentImmediate = null, newImmediate = null;
              let lateAddedAt = 0, lateStage = 0, lateLinkLoaded = false;
              let lateResizes = 0, lateResizeSettled = false;
              let lateLinkSheet = null;
              let lastScrollAt = Date.now(), scrollAfterLateInsert = 0, lateUpdatePending = false;
              const lateSelector = '#late-style.late-source';
              let lateStyleSheet = null;
              document.addEventListener('click', event => { if (event.isTrusted) trustedClicks++; });
              window.addEventListener('scroll', () => {
                lastScrollAt = Date.now();
                if (lateAddedAt) scrollAfterLateInsert++;
                if (lateAddedAt && lateStage === 0 && scrollY > 900) {
                  document.getElementById('late-style').classList.add('late-source');
                  document.getElementById('late-link').classList.add('late-source');
                  lateStage = 1;
                } else if (lateStage === 1 && scrollY > 1400) {
                  lateUpdatePending = true; lateStage = 2;
                }
                if (scrollY <= 80 || latentActivations) return;
                latentActivations++;
                document.getElementById('latent').classList.remove('aok-hidden');
                document.getElementById('latent').classList.add('persistent-header');
                const added = document.createElement('div');
                added.id = 'new-latent'; added.className = 'persistent-header';
                anchors.push(added); previousTop.set(added, ''); document.body.appendChild(added);
                latentImmediate = parseFloat(getComputedStyle(document.getElementById('latent')).top);
                newImmediate = parseFloat(getComputedStyle(added).top);
                setTimeout(() => {
                  for (let count = 0; count < 3; count++) { window.dispatchEvent(new Event('resize')); fixtureResizes++; }
                  setTimeout(() => { resizeSettled = true; }, 300);
                }, 200);
              });
              new MutationObserver(records => {
                for (const record of records) {
                  if (!previousTop.has(record.target)) continue;
                  const current = record.target.style.top;
                  if (previousTop.get(record.target) !== current) topStyleChanges++;
                  previousTop.set(record.target, current);
                }
              }).observe(document.body, {attributes:true, attributeFilter:['style'], subtree:true});
              const report = () => {
                const number = (id, property) => {
                  const element = document.getElementById(id);
                  return element ? parseFloat(getComputedStyle(element)[property]) : null;
                };
                const env = number('probe','paddingTop');
                if (resizeSettled && !lateAddedAt && Date.now() - lastScrollAt >= 600) {
                  lateStyleSheet = document.createElement('style'); lateStyleSheet.id = 'late-source-sheet';
                  lateStyleSheet.textContent = lateSelector + ' {position:fixed;top:8px;display:block;}';
                  const link = document.createElement('link'); link.rel = 'stylesheet';
                  lateLinkSheet = link;
                  link.onload = () => { lateLinkLoaded = true; };
                  link.href = '/late-source.css';
                  lateAddedAt = Date.now(); document.head.append(lateStyleSheet, link);
                }
                if (lateUpdatePending && Date.now() - lastScrollAt >= 600) {
                  lateUpdatePending = false;
                  lateStyleSheet.textContent = lateSelector + ' {position:fixed;top:24px;display:block;}';
                  lateAddedAt = Date.now();
                }
                const injected = (selector, top) => Array.from(document.styleSheets).some(sheet => {
                  try { return Array.from(sheet.cssRules).some(rule => rule.selectorText === selector &&
                    Math.abs(parseFloat(rule.style?.top) - top) < .02); } catch { return false; }
                });
                const lateStyleReady = injected(lateSelector, env + 8);
                const lateLinkReady = injected('#late-link.late-source', env + 20);
                let lateLinkAccess = 'pending';
                if (lateLinkSheet?.sheet) {
                  try { lateLinkSheet.sheet.cssRules.length; lateLinkAccess = 'readable'; }
                  catch (error) { lateLinkAccess = error.name === 'SecurityError' ? 'security-error' : 'other-error'; }
                }
                const lateReady = lateLinkLoaded && lateAddedAt && Date.now() - lateAddedAt >= 1000 &&
                  Date.now() - lastScrollAt >= 600;
                if (lateStage === 2 && !lateResizes && Math.abs(number('late-style','top') - env - 24) < .02) {
                  for (let count = 0; count < 3; count++) { window.dispatchEvent(new Event('resize')); lateResizes++; }
                  setTimeout(() => { lateResizeSettled = true; }, 300);
                }
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
                  latent:number('latent','top'), latentPosition:getComputedStyle(document.getElementById('latent')).position,
                  latentDisplay:getComputedStyle(document.getElementById('latent')).display, latentInline:document.getElementById('latent').style.top,
                  newLatent:number('new-latent','top'), newLatentInline:document.getElementById('new-latent')?.style.top ?? '',
                  latentActivations, fixtureResizes, resizeSettled, latentImmediate, newImmediate,
                  lateReady, lateStage, lateElapsed:lateAddedAt ? Date.now() - lateAddedAt : 0,
                  lateResizes, lateResizeSettled,
                  lateStyleReady, lateLinkReady, lateLinkLoaded, lateLinkAccess,
                  scrollAfterLateInsert,
                  mediaAttributeProbe, mediaListProbe,
                  lateStyle:number('late-style','top'), lateLink:number('late-link','top'),
                  lateInlineCount:['late-style','late-link'].filter(id => document.getElementById(id).style.top).length,
                  scroll:scrollY, height:innerHeight, loaded:document.readyState === 'complete'});
              };
              setInterval(report,100); report();
            </script>
        """.trimIndent()
    }
}
