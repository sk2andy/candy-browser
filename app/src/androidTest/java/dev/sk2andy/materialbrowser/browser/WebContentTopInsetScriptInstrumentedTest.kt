package dev.sk2andy.materialbrowser.browser

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.gecko.GeckoScrollTestActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebContentTopInsetScriptInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val webView = AtomicReference<WebView>()
    private var visibleWebViewScenario: ActivityScenario<GeckoScrollTestActivity>? = null

    @After
    fun tearDown() {
        webView.getAndSet(null)?.let { view ->
            instrumentation.runOnMainSync {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
        }
        visibleWebViewScenario?.close()
        visibleWebViewScenario = null
    }

    @Test
    fun reinjectionKeepsBoundedNativeFallbackAfterPersistentLayoutFailure() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = 100,
                requiredFailureCount = 2,
            ),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(
            view,
            """
                const blocker = document.createElement('style');
                blocker.textContent =
                  'html:root::before { height: 0 !important; min-height: 0 !important; }';
                document.head.appendChild(blocker);
            """.trimIndent(),
        )
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)
        evaluate(
            view,
            """
                globalThis.__candyReconcileContentTopInset();
                globalThis.__candyReconcileContentTopInset();
            """.trimIndent(),
        )

        assertTrue(
            "Persistent layout failure did not request native top protection",
            fallbackReceived.await(STALE_TIMER_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
        evaluate(view, WebContentTopInsetScript.installScript)

        assertEquals(
            "true",
            evaluate(
                view,
                "Boolean(document.querySelector(" +
                    "'style[data-candy-browser-owned=\"true\"]'))",
            ),
        )
    }

    @Test
    fun bridgeFailureSettingsBoundNativeFallbackConfirmation() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = 100,
                requiredFailureCount = 2,
            ),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(
            view,
            """
                const blocker = document.createElement('style');
                blocker.textContent =
                  'html:root::before { height: 0 !important; min-height: 0 !important; }';
                document.head.appendChild(blocker);
            """.trimIndent(),
        )
        evaluate(view, "globalThis.__candyReconfigureContentTopInset();")

        assertTrue(
            "Confirmed bridge failure did not request native top protection",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun semanticStickyHeaderRequestsNativeTopArea() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(
                fallbackReceived = fallbackReceived,
                nativeTopHeaderEnabled = true,
            ),
            html = """
                <html><head>
                <meta name="theme-color" content="#123456">
                <style>
                  html, body { margin: 0; }
                  header { position: sticky; top: 0; width: 100%; height: 56px; background: #123456; }
                  main { height: 200vh; }
                </style></head><body><header>Menu</header><main>Content</main></body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)

        assertTrue(
            "Semantic sticky header did not request native top protection",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
        assertEquals(
            "true",
            evaluate(
                view,
                "document.documentElement.getAttribute(" +
                    "'data-candy-browser-native-top-header') === 'true'",
            ),
        )
    }

    @Test
    fun fixedHeaderRequiresNewMeaningfulScrollBeforeNativeTopArea() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = 100,
                nativeTopHeaderEnabled = true,
            ),
            html = """
                <html><head>
                <meta name="theme-color" content="#234567">
                <style>
                  html, body { margin: 0; }
                  header { position: fixed; top: 0; width: 100%; height: 56px; background: #234567; }
                  main { height: 300vh; }
                </style></head><body><header>Menu</header><main>Content</main></body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)

        assertFalse(
            "Initial fixed declaration disabled edge-to-edge before persistence was proven",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
        evaluate(view, "scrollTo(0, 160)")
        assertTrue(
            "Fixed header that remained pinned after meaningful scroll was not promoted",
            fallbackReceived.await(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
    }

    @Test
    fun continuedScrollDiscoversOldControlAtTrailingInsetRow() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(fallbackReceived = fallbackReceived),
            html = """
                <html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                  html, body { margin: 0; }
                  main { height: 500vh; }
                  body:has(.added-trigger) #late-control {
                    position: fixed;
                    top: calc(var(--candy-browser-content-top-inset) - 2px);
                    right: 16px; width: 12px; height: 2px;
                    padding: 0; border: 0; z-index: 10;
                  }
                </style></head><body><main></main>
                <button id="late-control" aria-label="Late control"></button>
                </body></html>
            """.trimIndent(),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        SystemClock.sleep(SHADOW_HOOK_STABILIZATION_MILLIS)
        evaluate(
            view,
            """
                const originalHitTest = document.elementsFromPoint.bind(document);
                globalThis.__lateControlHitQueries = 0;
                globalThis.__lateControlHits = 0;
                globalThis.__lateControlHitPoints = [];
                globalThis.__lateControlFrameCount = 0;
                const countFrame = () => {
                  __lateControlFrameCount++;
                  requestAnimationFrame(countFrame);
                };
                requestAnimationFrame(countFrame);
                document.elementsFromPoint = (x, y) => {
                  // Controlled scheduling pressure, not a native-performance benchmark.
                  const finish = performance.now() + 4;
                  while (performance.now() < finish) {}
                  const hits = originalHitTest(x, y);
                  __lateControlHitQueries++;
                  __lateControlHitPoints.push([x, y]);
                  if (hits.some((element) => element.id === 'late-control')) __lateControlHits++;
                  return hits;
                };
                const trigger = document.createElement('span');
                trigger.className = 'added-trigger';
                document.querySelector('main').appendChild(trigger);
            """.trimIndent(),
        )
        var protectedDuringScroll = false
        repeat(12) { index ->
            evaluate(view, "scrollTo(0, ${if (index % 2 == 0) 100 else 200});")
            SystemClock.sleep(550)
            protectedDuringScroll = protectedDuringScroll || evaluate(
                view,
                """
                    (() => {
                      const control = document.querySelector('#late-control');
                      const inset = Number.parseFloat(document.documentElement.style.getPropertyValue(
                        '--candy-browser-content-top-inset'
                      ));
                      return control.getAttribute('data-candy-browser-top-inset-offset') === 'true' &&
                        control.getBoundingClientRect().top >= inset + 8 - 0.5;
                    })();
                """.trimIndent(),
            ) == "true"
        }
        val observation = evaluate(
            view,
            """
                (() => {
                  const control = document.querySelector('#late-control');
                  const rect = control.getBoundingClientRect();
                  const style = getComputedStyle(control);
                  return JSON.stringify({
                    inset: document.documentElement.style.getPropertyValue('--candy-browser-content-top-inset'),
                    scrollY, width: innerWidth, position: style.position, translate: style.translate,
                    bounds: [rect.left, rect.top, rect.width, rect.height],
                    owned: control.getAttribute('data-candy-browser-top-inset-offset'),
                    flow: control.getAttribute('data-candy-browser-top-inset-flow-target'),
                    queries: __lateControlHitQueries, hits: __lateControlHits,
                    frames: __lateControlFrameCount, visibility: document.visibilityState,
                    lastPoints: __lateControlHitPoints.slice(-20),
                    deepestRow: Math.max(...__lateControlHitPoints.map((point) => point[1]))
                  });
                })();
            """.trimIndent(),
        )
        assertTrue(
            "An old control at a late raster coordinate remained unprotected during continued scrolling: $observation",
            protectedDuringScroll,
        )
        val density = evaluate(view, "devicePixelRatio").toDouble()
        assertTrue(
            elementTop(view, "#late-control") >=
                TOP_INSET_PX / density + COMPACT_CONTROL_PADDING_CSS_PIXELS - CSS_PIXEL_TOLERANCE,
        )
        assertEquals(1L, fallbackReceived.count)
    }

    @Test
    fun repeatedReconcileReusesStableCandidatesAndKeepsHeaderSafe() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = 100,
            ),
            html = """
                <html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                  html, body { margin: 0; }
                  #header { position: fixed; inset: 0 0 auto 0; height: 64px; background: white; }
                  #feed { margin-top: 2000px; }
                  .card { height: 80px; }
                  body:has(.added-trigger) #late-control {
                    position: fixed; top: 8px; left: 50%; width: 12px; height: 12px;
                    padding: 0; z-index: 10;
                  }
                </style></head><body>
                <header id="header"><button>Menu</button></header><main id="feed"></main>
                <button id="late-control" aria-label="Late control"></button>
                <script>
                  for (let index = 0; index < 400; index++) {
                    const card = document.createElement('article');
                    card.className = 'card';
                    card.textContent = 'Feed card ' + index;
                    document.querySelector('#feed').appendChild(card);
                  }
                </script></body></html>
            """.trimIndent(),
        )
        evaluate(
            view,
            """
                globalThis.__safeAreaHitQueries = 0;
                globalThis.__safeAreaTaskHitQueries = 0;
                globalThis.__safeAreaMaxTaskHitQueries = 0;
                const originalElementsFromPoint = document.elementsFromPoint.bind(document);
                document.elementsFromPoint = (x, y) => {
                  globalThis.__safeAreaHitQueries++;
                  if (__safeAreaTaskHitQueries === 0) {
                    queueMicrotask(() => { __safeAreaTaskHitQueries = 0; });
                  }
                  __safeAreaTaskHitQueries++;
                  __safeAreaMaxTaskHitQueries = Math.max(__safeAreaMaxTaskHitQueries, __safeAreaTaskHitQueries);
                  return originalElementsFromPoint(x, y);
                };
            """.trimIndent(),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        // Let all existing stabilization callbacks finish before measuring steady-state work.
        SystemClock.sleep(SHADOW_HOOK_STABILIZATION_MILLIS)

        val repeatedQueries = evaluate(
            view,
            """
                globalThis.__safeAreaHitQueries = 0;
                for (let index = 0; index < 10; index++) {
                  globalThis.__candyReconcileContentTopInset();
                }
                globalThis.__safeAreaHitQueries;
            """.trimIndent(),
        ).toInt()
        assertTrue(
            "Stable candidates triggered broad discovery on repeated reconcile: " +
                "queries=$repeatedQueries",
            repeatedQueries <= MAX_STABLE_RECONCILE_HIT_QUERIES,
        )
        val density = evaluate(view, "devicePixelRatio").toDouble()
        assertTrue(elementTop(view, "#header") >= TOP_INSET_PX / density - CSS_PIXEL_TOLERANCE)

        evaluate(
            view,
            """
                globalThis.__safeAreaHitQueries = 0;
                globalThis.__safeAreaMaxTaskHitQueries = 0;
                const addedCards = document.createDocumentFragment();
                for (let index = 0; index < 100; index++) {
                  const card = document.createElement('article');
                  card.className = index === 99 ? 'card added-trigger' : 'card';
                  card.textContent = 'Added feed card ' + index;
                  addedCards.appendChild(card);
                }
                document.querySelector('#feed').appendChild(addedCards);
            """.trimIndent(),
        )
        // An offscreen leaf can change an old element via :has(); it is not a safe scan-skip proof.
        awaitScriptCondition(
            view,
            "document.querySelector('#late-control').getAttribute('data-candy-browser-top-inset-offset') === 'true'",
        )
        val appendTaskQueries = evaluate(view, "globalThis.__safeAreaMaxTaskHitQueries").toInt()
        assertTrue(
            "Feed mutation accumulated an unbounded point-query task: " +
                "queries=$appendTaskQueries",
            appendTaskQueries <= MAX_FEED_APPEND_TASK_HIT_QUERIES,
        )
        assertTrue(elementTop(view, "#header") >= TOP_INSET_PX / density - CSS_PIXEL_TOLERANCE)
        assertTrue(
            elementTop(view, "#late-control") >=
                TOP_INSET_PX / density + COMPACT_CONTROL_PADDING_CSS_PIXELS - CSS_PIXEL_TOLERANCE,
        )
        assertFalse(
            "Stable fixed header unexpectedly requested native fallback",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun customElementReactionCannotLeaveReparentedNodeInReadCache() {
        val view = loadPage(
            bridge = TopInsetBridge(CountDownLatch(1)),
            html = """
                <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>html,body{margin:0}main{height:500vh}</style></head><body><main>
                <div id="old-parent"><candy-read-probe id="probe">Probe</candy-read-probe></div>
                <div id="new-parent"></div></main></body></html>
            """.trimIndent(),
        )
        evaluate(
            view,
            """
                customElements.define('candy-read-probe', class extends HTMLElement {
                  static get observedAttributes() { return ['data-test-reparent']; }
                  attributeChangedCallback() {
                    const reads = globalThis.__testInsetReads;
                    globalThis.__callbackParent = reads.parentElementOrShadowHost(this).id;
                    document.querySelector('#new-parent').appendChild(this);
                    globalThis.__callbackReturned = true;
                  }
                });
            """.trimIndent(),
        )
        val testScript = WebContentTopInsetScript.installScript.substringBeforeLast("})();") +
            """
                globalThis.__testInsetReads = {
                  withLayoutReadCache, parentElementOrShadowHost, setOwnedAttribute,
                };
                })();
            """.trimIndent()
        evaluate(view, testScript)
        evaluate(
            view,
            """
                globalThis.__readChecks = {};
                __testInsetReads.withLayoutReadCache(() => {
                  const element = document.querySelector('#probe');
                  __readChecks.oldParent = __testInsetReads.parentElementOrShadowHost(element).id;
                  __testInsetReads.setOwnedAttribute(element, 'data-test-reparent', 'true');
                  __readChecks.synchronousCallback = globalThis.__callbackReturned === true;
                  __readChecks.freshParent = __testInsetReads.parentElementOrShadowHost(element).id;
                });
            """.trimIndent(),
        )
        assertEquals("\"old-parent\"", evaluate(view, "__readChecks.oldParent"))
        assertEquals("\"old-parent\"", evaluate(view, "__callbackParent"))
        assertEquals("true", evaluate(view, "__readChecks.synchronousCallback"))
        assertEquals("\"new-parent\"", evaluate(view, "__readChecks.freshParent"))
    }

    @Test
    fun ancestorFeedInsertionRepairsInFrameWithoutMutationCallbackGeometry() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(fallbackReceived, performanceDiagnosticsEnabled = true),
            html = """
                <html><head><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
                <style>html,body{margin:0}#header{position:fixed;top:0;left:0;width:100%;height:64px;
                transform:translateZ(0);background:white}main{height:500vh}
                body:has(.trigger) #header{top:8px}</style></head>
                <body><header id="header"><button>Menu</button></header><main>Feed</main></body></html>
            """.trimIndent(),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        SystemClock.sleep(SHADOW_HOOK_STABILIZATION_MILLIS)
        awaitScriptCondition(view, "document.querySelector('#header').hasAttribute('data-candy-browser-top-inset-offset')")
        evaluate(
            view,
            """
                globalThis.__ownedGeometry = { mutationActive: false, readsInMutation: 0, knownRefreshes: 0, frames: 0 };
                const header = document.querySelector('#header');
                const originalRect = header.getBoundingClientRect.bind(header);
                header.getBoundingClientRect = (...options) => {
                  if (__ownedGeometry.mutationActive) __ownedGeometry.readsInMutation++;
                  return originalRect(...options);
                };
                const originalMark = performance.mark.bind(performance);
                performance.mark = (name, ...options) => {
                  if (name === 'Candy.SafeArea.Mutations.start') __ownedGeometry.mutationActive = true;
                  if (name === 'Candy.SafeArea.KnownOffsets.start') __ownedGeometry.knownRefreshes++;
                  if (name === 'Candy.SafeArea.OwnedMutationFrame.start') __ownedGeometry.frames++;
                  return originalMark(name, ...options);
                };
                const originalMeasure = performance.measure.bind(performance);
                performance.measure = (name, ...options) => {
                  try { return originalMeasure(name, ...options); }
                  finally {
                    if (name === 'Candy.SafeArea.Mutations') __ownedGeometry.mutationActive = false;
                  }
                };
                const trigger = document.createElement('div');
                trigger.className = 'trigger';
                document.body.append(trigger);
            """.trimIndent(),
        )
        awaitScriptCondition(view, "__ownedGeometry.knownRefreshes > 0")
        assertEquals(
            "Ancestor feed insertion must not force geometry inside MutationObserver",
            0,
            evaluate(view, "__ownedGeometry.readsInMutation").toInt(),
        )
        assertTrue("Owned repair must run in actual animation frame", evaluate(view, "__ownedGeometry.frames").toInt() > 0)
        val expectedTop = TOP_INSET_PX / evaluate(view, "devicePixelRatio").toDouble()
        assertEquals(
            expectedTop,
            evaluate(view, "document.querySelector('#header').getBoundingClientRect().top").toDouble(),
            CSS_PIXEL_TOLERANCE,
        )
        assertEquals("\"8px\"", evaluate(view, "getComputedStyle(document.querySelector('#header')).top"))
        assertFalse(fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS))
    }

    @Test
    fun authorAnimationFrameMutationKeepsOwnedHeaderOutsideStatusBarAtMicrotaskCheckpoint() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(fallbackReceived),
            html = """
                <html><head><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
                <style>html,body{margin:0}#header{position:fixed;top:0;left:0;width:100%;height:64px;
                transform:translateZ(0);background:white}main{height:500vh}</style></head>
                <body><header id="header"><button>Menu</button></header><main>Feed</main></body></html>
            """.trimIndent(),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        awaitScriptCondition(view, "document.querySelector('#header').hasAttribute('data-candy-browser-top-inset-offset')")
        evaluate(
            view,
            """
                globalThis.__authorFrameTop = null;
                requestAnimationFrame(() => {
                  const header = document.querySelector('#header');
                  header.style.top = '-20px';
                  queueMicrotask(() => { globalThis.__authorFrameTop = header.getBoundingClientRect().top; });
                });
            """.trimIndent(),
        )
        awaitScriptCondition(view, "__authorFrameTop !== null")
        val expectedTop = TOP_INSET_PX / evaluate(view, "devicePixelRatio").toDouble()
        assertTrue(
            "Author rAF mutation must stay safe at the microtask checkpoint before paint",
            evaluate(view, "__authorFrameTop").toDouble() >= expectedTop - CSS_PIXEL_TOLERANCE,
        )
        assertFalse(fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS))
    }

    @Test
    fun unrelatedFeedMutationsDeferOwnedGeometryReadsButKeepGlobalCssRepair() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            html = """
                <html><head><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
                <style>html,body{margin:0}#header{position:fixed;top:0;left:0;width:100%;height:64px;
                transform:translateZ(0);background:white}main{height:500vh}
                body:has(.leaf.changed) #header{top:8px}</style></head>
                <body><header id="header"><button>Menu</button></header>
                <main><div id="feed"><span id="leaf" class="leaf">Feed</span></div></main></body></html>
            """.trimIndent(),
            bridge = TopInsetBridge(
                fallbackReceived = fallbackReceived,
                performanceDiagnosticsEnabled = true,
            ),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        SystemClock.sleep(SHADOW_HOOK_STABILIZATION_MILLIS)
        awaitScriptCondition(view, "document.querySelector('#header').hasAttribute('data-candy-browser-top-inset-offset')")
        evaluate(
            view,
            """
                globalThis.__feedGeometry = { knownOffsetActive: false, reads: 0, mutations: 0, completed: false };
                const header = document.querySelector('#header');
                const originalRect = header.getBoundingClientRect.bind(header);
                header.getBoundingClientRect = (...options) => {
                  if (__feedGeometry.knownOffsetActive) __feedGeometry.reads++;
                  return originalRect(...options);
                };
                const originalMark = performance.mark.bind(performance);
                performance.mark = (name, ...options) => {
                  if (name === 'Candy.SafeArea.KnownOffsets.start') __feedGeometry.knownOffsetActive = true;
                  if (name === 'Candy.SafeArea.Mutations.start') __feedGeometry.mutations++;
                  return originalMark(name, ...options);
                };
                const originalMeasure = performance.measure.bind(performance);
                performance.measure = (name, ...options) => {
                  try { return originalMeasure(name, ...options); }
                  finally {
                    if (name === 'Candy.SafeArea.KnownOffsets') __feedGeometry.knownOffsetActive = false;
                  }
                };
                (async () => {
                  const leaf = document.querySelector('#leaf');
                  for (let index = 0; index < 10; index++) {
                    leaf.className = index === 9 ? 'leaf changed' : 'leaf update-' + index;
                    await new Promise((resolve) => setTimeout(resolve, 25));
                  }
                  __feedGeometry.readsBeforeQuiet = __feedGeometry.reads;
                  __feedGeometry.completed = true;
                })();
            """.trimIndent(),
        )
        awaitScriptCondition(view, "__feedGeometry.completed")
        assertTrue("Actual feed batches must reach Candy", evaluate(view, "__feedGeometry.mutations").toInt() > 0)
        assertEquals(
            "Unrelated feed batches must not force owned-header geometry",
            0,
            evaluate(view, "__feedGeometry.readsBeforeQuiet").toInt(),
        )
        val expectedTop = TOP_INSET_PX / evaluate(view, "devicePixelRatio").toDouble()
        awaitScriptCondition(view, "Math.abs(document.querySelector('#header').getBoundingClientRect().top - $expectedTop) < 0.5")
        assertEquals(
            "Global :has() author top must remain intact",
            "\"8px\"",
            evaluate(view, "getComputedStyle(document.querySelector('#header')).top"),
        )
        assertFalse(fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS))
    }

    @Test
    fun stickyAuthorMutationClearAndReapplyWritesReachQuietObserverState() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadViewportStickyPage(
            TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = 100,
                performanceDiagnosticsEnabled = true,
            ),
        )
        evaluate(
            view,
            """
                globalThis.__stickyDiagnostics = { reconciles: 0, mutations: 0, styleWrites: 0 };
                const originalMark = performance.mark.bind(performance);
                performance.mark = (name, ...options) => {
                  if (name === 'Candy.SafeArea.Reconcile.start') __stickyDiagnostics.reconciles++;
                  if (name === 'Candy.SafeArea.Mutations.start') __stickyDiagnostics.mutations++;
                  return originalMark(name, ...options);
                };
                const writes = new MutationObserver((records) => {
                  for (const record of records) {
                    if (record.attributeName === 'style' && __stickyHeaders.includes(record.target)) {
                      __stickyDiagnostics.styleWrites++;
                    }
                  }
                });
                const options = { attributes: true, attributeFilter: ['style'], subtree: true };
                writes.observe(document.documentElement, options);
                writes.observe(document.querySelector('#shadow-host').shadowRoot, options);
                globalThis.__stickyWriteObserver = writes;
            """.trimIndent(),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(view, "scrollTo(0, 400);")
        SystemClock.sleep(SHADOW_HOOK_STABILIZATION_MILLIS)
        awaitScriptCondition(
            view,
            "__stickyHeaders.every((element) => " +
                "__candyBrowserContentTopInset.ownedCssStickyElements.has(element))",
        )
        evaluate(
            view,
            """
                __stickyDiagnostics.reconciles = 0;
                __stickyDiagnostics.mutations = 0;
                __stickyDiagnostics.styleWrites = 0;
                __stickyHeaders[0].style.top = '4px';
                __stickyHeaders[1].classList.add('author-change');
            """.trimIndent(),
        )
        awaitScriptCondition(
            view,
            "__stickyHeaders[0].style.getPropertyValue('--candy-browser-owned-sticky-original-top') === '4px' && " +
                "__stickyHeaders[1].style.getPropertyValue('--candy-browser-owned-sticky-original-top') === '6px' && " +
                "__stickyHeaders.every((element) => __candyBrowserContentTopInset.ownedCssStickyElements.has(element))",
        )
        SystemClock.sleep(SCROLL_SETTLE_MILLIS)
        val settledReconciles = evaluate(view, "__stickyDiagnostics.reconciles").toInt()
        val settledMutations = evaluate(view, "__stickyDiagnostics.mutations").toInt()
        val settledWrites = evaluate(view, "__stickyDiagnostics.styleWrites").toInt()
        assertTrue("Actual author mutation must run Candy recovery", settledReconciles > 0)
        assertTrue("Actual MutationObserver batches must reach Candy", settledMutations > 0)
        assertTrue("Candy clear/reapply must produce actual observed style writes", settledWrites > 2)

        // Longer than the configured quiet period: self-generated batches must remain idle.
        SystemClock.sleep(SCROLL_SETTLE_MILLIS)
        assertEquals(settledReconciles, evaluate(view, "__stickyDiagnostics.reconciles").toInt())
        assertEquals(settledMutations, evaluate(view, "__stickyDiagnostics.mutations").toInt())
        assertEquals(settledWrites, evaluate(view, "__stickyDiagnostics.styleWrites").toInt())
        val expectedTop = TOP_INSET_PX / evaluate(view, "devicePixelRatio").toDouble()
        for (index in 0..1) {
            val top = evaluate(view, "__stickyHeaders[$index].getBoundingClientRect().top").toDouble()
            assertEquals(expectedTop, top, CSS_PIXEL_TOLERANCE)
        }
        assertFalse(fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS))
    }

    @Test
    fun stableDocumentAndShadowViewportStickyFollowRootCssInsetWithoutAnotherLayoutRead() {
        val view = loadViewportStickyPage(TopInsetBridge(CountDownLatch(1)))
        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(view, "scrollTo(0, 400);")
        SystemClock.sleep(SHADOW_HOOK_STABILIZATION_MILLIS)
        awaitScriptCondition(
            view,
            "__stickyHeaders.every((element) => " +
                "__candyBrowserContentTopInset.ownedCssStickyElements.has(element))",
        )
        val geometry = JSONObject(
            evaluate(
                view,
                """
                    (() => {
                      let headerRectReads = 0;
                      let headerStyleReads = 0;
                      const nativeRects = __stickyHeaders.map((element) =>
                        element.getBoundingClientRect.bind(element));
                      __stickyHeaders.forEach((element, index) => {
                        element.getBoundingClientRect = () => {
                          headerRectReads++;
                          return nativeRects[index]();
                        };
                      });
                      const nativeStyle = globalThis.getComputedStyle.bind(globalThis);
                      globalThis.getComputedStyle = (element, ...options) => {
                        if (__stickyHeaders.includes(element)) headerStyleReads++;
                        return nativeStyle(element, ...options);
                      };
                      const property = '--candy-browser-content-top-inset';
                      const beforeInset = Number.parseFloat(document.documentElement.style.getPropertyValue(property));
                      const before = nativeRects.map((rect) => rect().top);
                      const afterInset = beforeInset + 24;
                      // Same browser task: no timer, MutationObserver delivery or scroll refresh can help.
                      document.documentElement.style.setProperty(property, afterInset + 'px', 'important');
                      const after = nativeRects.map((rect) => rect().top);
                      return { beforeInset, afterInset, before, after, headerRectReads, headerStyleReads };
                    })();
                """.trimIndent(),
            ),
        )
        for (index in 0..1) {
            assertEquals(geometry.getDouble("beforeInset"), geometry.getJSONArray("before").getDouble(index), CSS_PIXEL_TOLERANCE)
            assertEquals(geometry.getDouble("afterInset"), geometry.getJSONArray("after").getDouble(index), CSS_PIXEL_TOLERANCE)
        }
        assertEquals(0, geometry.getInt("headerRectReads"))
        assertEquals(0, geometry.getInt("headerStyleReads"))
    }

    @Test
    fun fixedAndStickyHeadersKeepTheirSafeTopAcrossScrollAndVisibilityChanges() {
        val view = loadPage(
            bridge = TopInsetBridge(
                fallbackReceived = CountDownLatch(1),
                layoutQuietPeriodMillis = 100,
                requiredFailureCount = 2,
            ),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 300vh; }
                  #fixed, #sticky {
                    box-sizing: border-box;
                    left: 0;
                    top: 0;
                    width: 100%;
                    height: 64px;
                    background: white;
                  }
                  #fixed { position: fixed; }
                  #sticky { position: sticky; margin-top: 96px; }
                </style></head><body>
                <header id="fixed"><button>Vimeo</button></header>
                <header id="sticky"><button>Sticky</button></header>
                <main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)
        val density = evaluate(view, "devicePixelRatio").toDouble()
        val expectedTop = TOP_INSET_PX / density
        val fixedTopBefore = elementTop(view, "#fixed")

        evaluate(view, "scrollTo(0, 400)")
        SystemClock.sleep(SCROLL_REGRESSION_WINDOW_MILLIS)
        val fixedTopAfterScroll = elementTop(view, "#fixed")
        val stickyTopAfterScroll = elementTop(view, "#sticky")

        evaluate(view, "document.querySelector('#fixed').style.display = 'none'")
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)
        evaluate(view, "document.querySelector('#fixed').style.display = ''")
        SystemClock.sleep(SCROLL_REGRESSION_WINDOW_MILLIS)
        val fixedTopAfterVisibilityChange = elementTop(view, "#fixed")

        assertTrue(fixedTopBefore >= expectedTop - CSS_PIXEL_TOLERANCE)
        assertEquals(fixedTopBefore, fixedTopAfterScroll, CSS_PIXEL_TOLERANCE)
        assertEquals(fixedTopBefore, fixedTopAfterVisibilityChange, CSS_PIXEL_TOLERANCE)
        assertTrue(stickyTopAfterScroll >= expectedTop - CSS_PIXEL_TOLERANCE)
    }

    @Test
    fun visibleHeaderReturningToFlowDropsItsOwnedTranslation() {
        val view = loadPage(
            bridge = TopInsetBridge(CountDownLatch(1)),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 200vh; }
                  #header { position: fixed; inset: 0 0 auto 0; height: 64px; background: white; }
                </style></head><body>
                <header id="header"><button>Menu</button></header>
                <main>Content</main>
                </body></html>
            """.trimIndent(),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        assertEquals(
            "true",
            evaluate(
                view,
                "document.querySelector('#header').getAttribute(" +
                    "'data-candy-browser-top-inset-offset')",
            ).removeSurrounding("\""),
        )

        evaluate(
            view,
            "document.querySelector('#header').style.position='relative'",
        )
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)

        val density = evaluate(view, "devicePixelRatio").toDouble()
        assertEquals(TOP_INSET_PX / density, elementTop(view, "#header"), CSS_PIXEL_TOLERANCE)
        assertEquals(
            "null",
            evaluate(
                view,
                "document.querySelector('#header').getAttribute(" +
                    "'data-candy-browser-top-inset-offset')",
            ),
        )
        assertEquals(
            "\"\"",
            evaluate(
                view,
                "document.querySelector('#header').style.getPropertyValue(" +
                    "'--candy-browser-owned-top-inset-offset')",
            ),
        )
    }

    @Test
    fun laterDomMutationResumesSuspendedLayoutRecovery() {
        val view = loadPage(
            TopInsetBridge(
                fallbackReceived = CountDownLatch(1),
                layoutQuietPeriodMillis = 100,
                requiredFailureCount = 2,
            ),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(
            view,
            """
                const blocker = document.createElement('style');
                blocker.id = 'blocker';
                blocker.textContent =
                  'html:root::before { height: 0 !important; min-height: 0 !important; }';
                document.head.appendChild(blocker);
                globalThis.__candyReconcileContentTopInset();
                globalThis.__candyReconcileContentTopInset();
            """.trimIndent(),
        )
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)

        evaluate(
            view,
            "document.querySelector('#blocker').remove()",
        )
        SystemClock.sleep(MUTATION_SETTLE_MILLIS * 2)

        val density = evaluate(view, "devicePixelRatio").toDouble()
        assertEquals(
            TOP_INSET_PX / density,
            evaluate(view, "parseFloat(getComputedStyle(document.documentElement,'::before').height)")
                .toDouble(),
            CSS_PIXEL_TOLERANCE,
        )
    }

    @Test
    fun viewportCoverAttributeCannotDisableCandyTopProtection() {
        val view = loadPage(
            bridge = TopInsetBridge(CountDownLatch(1), viewportCoverAllowed = true),
            html = """
                <html><head>
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <style>
                    html, body { margin: 0; }
                    #header { position: fixed; inset: 0 0 auto 0; height: 64px; }
                  </style>
                </head><body><header id="header">Reddit</header><main>Content</main></body></html>
            """.trimIndent(),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        assertEquals(
            "true",
            evaluate(view, "Boolean(document.querySelector('style[data-candy-browser-owned]'))"),
        )

        evaluate(
            view,
            "document.querySelector('meta[name=viewport]').content += ',viewport-fit=cover'",
        )
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)

        assertEquals(
            "true",
            evaluate(view, "Boolean(document.querySelector('style[data-candy-browser-owned]'))"),
        )
        val density = evaluate(view, "devicePixelRatio").toDouble()
        assertTrue(
            elementTop(view, "#header") >= TOP_INSET_PX / density - CSS_PIXEL_TOLERANCE,
        )
    }

    @Test
    fun compactFixedMenuTouchTargetClearsStatusBar() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = 100,
                requiredFailureCount = 2,
            ),
            html = """
                <html><head></head><body>
                <button id="menu" style="position:fixed;top:0;left:0;width:48px;height:48px">
                  Menu
                </button>
                <main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)

        val devicePixelRatio = evaluate(view, "devicePixelRatio").toDouble()
        val menuTop = evaluate(
            view,
            "document.querySelector('#menu').getBoundingClientRect().top",
        ).toDouble()
        val minimumMenuTop = TOP_INSET_PX / devicePixelRatio + COMPACT_CONTROL_PADDING_CSS_PIXELS
        assertTrue(
            "Compact menu touch target remained at the status-bar edge: " +
                "top=$menuTop minimum=$minimumMenuTop",
            menuTop >= minimumMenuTop - CSS_PIXEL_TOLERANCE,
        )
        assertFalse(
            "Compact menu padding triggered native fallback",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun viewportWideIdentityTransformedHeaderClearsInsetBeforeDenseDiscoveryCompletes() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(fallbackReceived = fallbackReceived),
            html = """
                <html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
                <style>
                  html, body { margin: 0; min-height: 250vh; }
                  #header {
                    position: fixed; inset: 0 0 auto 0; width: 100%; height: 64px;
                    background: white; transform: translateZ(0);
                  }
                </style></head><body>
                <header id="header"><button>Menu</button></header><main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)

        val density = evaluate(view, "devicePixelRatio").toDouble()
        assertEquals(
            "Identity-transformed header waited for asynchronous dense discovery",
            TOP_INSET_PX / density,
            elementTop(view, "#header"),
            CSS_PIXEL_TOLERANCE,
        )
        assertEquals(
            "true",
            evaluate(view, "getComputedStyle(document.querySelector('#header')).transform !== 'none'"),
        )
        assertEquals(1L, fallbackReceived.count)
    }

    @Test
    fun compactMenuOverNonInteractiveHeaderStaysEdgeToEdgeAfterScroll() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = 100,
                requiredFailureCount = 2,
            ),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 300vh; }
                  #drawer-shell, #drawer-backdrop {
                    position: fixed;
                    inset: 0;
                    z-index: 9998;
                  }
                  #drawer-shell { background: transparent; }
                  #drawer-backdrop { background: rgba(0, 0, 0, 0.6); z-index: 9997; }
                  #navd {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 60px;
                    height: 64px;
                  }
                  #header {
                    position: fixed;
                    top: 40px;
                    left: 0;
                    width: 100%;
                    height: 80px;
                    z-index: 1;
                    background: white;
                  }
                  #sign-in { position: absolute; top: 8px; right: 8px; }
                </style></head><body>
                <div id="drawer-backdrop"></div>
                <div id="drawer-shell">
                  <div role="button" style="height:120px">Hidden drawer action</div>
                </div>
                <div id="navd"><div><div id="menu" role="button">Menu</div></div></div>
                <header id="header"><button id="sign-in">Sign in</button></header>
                <main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(view, "scrollTo(0, 400); scrollTo(0, 0);")
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)
        evaluate(view, "globalThis.__candyReconcileContentTopInset();")

        val devicePixelRatio = evaluate(view, "devicePixelRatio").toDouble()
        val menuTop = evaluate(
            view,
            "document.querySelector('#menu').getBoundingClientRect().top",
        ).toDouble()
        assertEquals(
            "true",
            evaluate(
                view,
                "document.querySelector('#navd')" +
                    ".getAttribute('data-candy-browser-top-inset-offset')",
            ).removeSurrounding("\""),
        )
        val minimumMenuTop = TOP_INSET_PX / devicePixelRatio + COMPACT_CONTROL_PADDING_CSS_PIXELS
        assertTrue(
            "Compact menu remained in the status bar beside a separate header: " +
                "top=$menuTop minimum=$minimumMenuTop",
            menuTop >= minimumMenuTop - CSS_PIXEL_TOLERANCE,
        )
        assertFalse(
            "A separate header caused the edge-to-edge document to request native fallback",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun searchHeaderActivatedByInputClearsStatusBarBeforeLayoutQuietPeriod() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = SEARCH_LAYOUT_QUIET_PERIOD_MILLIS,
                requiredFailureCount = 3,
            ),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 200vh; }
                  #query { margin-top: 120px; }
                  #drawer-shell, #drawer-backdrop {
                    position: fixed;
                    inset: 0;
                    z-index: 9998;
                  }
                  #drawer-shell { background: transparent; }
                  #drawer-backdrop { background: rgba(0, 0, 0, 0.6); z-index: 9997; }
                  #search-header {
                    position: fixed;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 64px;
                    background: white;
                    z-index: 10000;
                  }
                </style></head><body>
                <input id="query" aria-label="Search">
                <div id="drawer-backdrop"></div>
                <div id="drawer-shell">
                  <div role="button" style="height:120px">Hidden drawer action</div>
                </div>
                <form id="search-header" hidden>
                  <button type="button">Add</button>
                  <input value="Vimeo sample video">
                  <button type="button">Close</button>
                </form>
                <main>Suggestions</main>
                <script>
                  document.querySelector('#query').addEventListener('input', () => {
                    document.querySelector('#search-header').hidden = false;
                  });
                </script>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)
        val devicePixelRatio = evaluate(view, "devicePixelRatio").toDouble()
        val expectedTop = TOP_INSET_PX / devicePixelRatio

        evaluate(
            view,
            "document.querySelector('#query').dispatchEvent(" +
                "new InputEvent('input', { bubbles: true, inputType: 'insertText', data: 'v' }));",
        )
        val deadline = SystemClock.elapsedRealtime() + IMMEDIATE_LAYOUT_TIMEOUT_MILLIS
        var headerTop = Double.NEGATIVE_INFINITY
        while (SystemClock.elapsedRealtime() < deadline) {
            headerTop = evaluate(
                view,
                "document.querySelector('#search-header').getBoundingClientRect().top",
            ).toDouble()
            if (headerTop >= expectedTop - CSS_PIXEL_TOLERANCE) break
            SystemClock.sleep(FRAME_SETTLE_MILLIS)
        }

        assertTrue(
            "Input-activated search header remained in the status bar before layout quiet: " +
                "top=$headerTop expected=$expectedTop",
            headerTop >= expectedTop - CSS_PIXEL_TOLERANCE,
        )
        assertFalse(
            "Immediate search-header reconciliation requested native fallback",
            fallbackReceived.await(IMMEDIATE_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun viewportWideInteractiveHeaderKeepsExactStatusInset() {
        val view = loadPage(
            bridge = TopInsetBridge(CountDownLatch(1)),
            html = """
                <html><head></head><body>
                <header id="header" style="position:fixed;top:0;left:0;width:100%;height:48px">
                  <button>Menu</button>
                </header>
                <main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)

        assertElementUsesExactStatusInset(view, "#header")
    }

    @Test
    fun compactDecorativeElementKeepsExactStatusInset() {
        val view = loadPage(
            bridge = TopInsetBridge(CountDownLatch(1)),
            html = """
                <html><head></head><body>
                <div id="decoration" style="position:fixed;top:0;left:0;width:24px;height:24px">
                  Candy
                </div>
                <main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)

        assertElementUsesExactStatusInset(view, "#decoration")
    }

    @Test
    fun nestedAbsoluteSearchUsesOuterHeaderAndSurvivesParentVisibility() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(fallbackReceived),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 200vh; }
                  #header { position: absolute; inset: 0 0 auto 0; height: 64px; }
                  #search { position: absolute; inset: 12px 12px auto 72px; height: 40px; }
                </style></head><body>
                <header id="header"><input id="search"></header><main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)
        assertEquals(
            "true",
            evaluate(
                view,
                "document.querySelector('#header').getAttribute(" +
                    "'data-candy-browser-top-inset-offset')",
            ).removeSurrounding("\""),
        )
        assertEquals(
            "null",
            evaluate(
                view,
                "document.querySelector('#search').getAttribute(" +
                    "'data-candy-browser-top-inset-offset')",
            ),
        )

        evaluate(view, "document.querySelector('#header').hidden = true")
        repeat(4) { evaluate(view, "globalThis.__candyReconcileContentTopInset()") }
        evaluate(view, "document.querySelector('#header').hidden = false")
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)

        val density = evaluate(view, "devicePixelRatio").toDouble()
        assertTrue(elementTop(view, "#header") >= TOP_INSET_PX / density - CSS_PIXEL_TOLERANCE)
        assertFalse(
            "Hidden absolute search child accumulated offsets and forced native fallback",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun preFocusedSearchReceivesOneTopInset() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(fallbackReceived),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 200vh; }
                  #header { position: fixed; inset: 0 0 auto 0; height: 64px; }
                </style></head><body>
                <header id="header"><input id="search"></header><main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, "document.querySelector('#search').focus({ preventScroll: true })")
        evaluate(view, WebContentTopInsetScript.installScript)

        assertElementUsesExactStatusInset(view, "#header")
        assertTrue(
            evaluate(view, "document.querySelector('#header').style.translate")
                .contains("--candy-browser-owned-top-inset-offset"),
        )
        assertFalse(
            "Pre-focused search accumulated top offsets",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun openShadowRootFixedHeaderReceivesTopProtection() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(fallbackReceived),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 200vh; }
                  shreddit-app { display: block; width: 100%; height: 64px; }
                </style></head>
                <body><shreddit-app id="app"></shreddit-app><main>Content</main>
                <script>
                  const root = document.querySelector('#app').attachShadow({ mode: 'open' });
                </script></body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(
            view,
            "document.querySelector('#app').shadowRoot.innerHTML = " +
                "'<header id=\"shadow-header\" " +
                "style=\"position:fixed;inset:0 0 auto 0;height:64px;background:white\">" +
                "<button>Reddit</button></header>'",
        )
        val density = evaluate(view, "devicePixelRatio").toDouble()
        val headerTop = awaitElementTop(
            view,
            "document.querySelector('#app').shadowRoot" +
                ".querySelector('#shadow-header').getBoundingClientRect().top",
            TOP_INSET_PX / density - CSS_PIXEL_TOLERANCE,
        )
        val diagnostics = evaluate(
            view,
            "JSON.stringify({" +
                "hits:document.elementsFromPoint(10,10).map(element=>element.tagName)," +
                "shadowHits:typeof document.querySelector('#app').shadowRoot.elementsFromPoint," +
                "owned:document.querySelector('#app').shadowRoot.querySelector('#shadow-header')" +
                ".getAttribute('data-candy-browser-top-inset-offset')," +
                "style:Boolean(document.querySelector('style[data-candy-browser-owned]'))})",
        )
        assertTrue(
            "Open ShadowRoot header remained under status bar: " +
                "top=$headerTop expected=${TOP_INSET_PX / density} diagnostics=$diagnostics",
            headerTop >= TOP_INSET_PX / density - CSS_PIXEL_TOLERANCE,
        )
        assertFalse(
            "Late ShadowRoot header required native fallback",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun shadowRootAttachedAfterInstallTriggersTopProtection() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(fallbackReceived, layoutQuietPeriodMillis = 100),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 200vh; }
                  #app { display: block; width: 100%; height: 64px; margin-top: 200vh; }
                </style></head><body><div id="app"></div><main>Content</main></body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(
            view,
            "globalThis.__siteAttachShadow = Element.prototype.attachShadow; " +
                "Element.prototype.attachShadow = function(options) { " +
                "return Reflect.apply(globalThis.__siteAttachShadow, this, [options]); }",
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(
            view,
            "document.querySelector('#app').attachShadow({ mode: 'open' })",
        )
        SystemClock.sleep(SHADOW_HOOK_STABILIZATION_MILLIS)
        evaluate(
            view,
            "document.querySelector('#app').shadowRoot.innerHTML = " +
                "'<header id=\"shadow-header\" " +
                "style=\"position:fixed;inset:0 0 auto 0;height:64px;background:white\">" +
                "Reddit</header>'",
        )

        val density = evaluate(view, "devicePixelRatio").toDouble()
        val requiredTop = TOP_INSET_PX / density
        val headerTop = awaitElementTop(
            view,
            "document.querySelector('#app').shadowRoot" +
                ".querySelector('#shadow-header').getBoundingClientRect().top",
            requiredTop - CSS_PIXEL_TOLERANCE,
        )

        assertTrue(
            "Dynamically attached ShadowRoot header remained under status bar: " +
                "top=$headerTop expected=$requiredTop",
            headerTop >= requiredTop - CSS_PIXEL_TOLERANCE,
        )
        assertFalse(
            "Dynamically attached ShadowRoot required native fallback",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun nestedScrollContainerProtectsHeaderWhenItBecomesSticky() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(fallbackReceived),
            html = """
                <html><head><style>
                  html, body { margin: 0; }
                  #scroller { height: 400px; overflow-y: auto; }
                  #lead { height: 240px; }
                  #header { position: sticky; top: 0; height: 64px; background: white; }
                  #content { height: 2000px; }
                </style></head><body>
                <section id="scroller">
                  <div id="lead"></div><header id="header">Vimeo</header><div id="content"></div>
                </section>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(view, "document.querySelector('#scroller').scrollTop = 400")
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)

        val density = evaluate(view, "devicePixelRatio").toDouble()
        val requiredTop = TOP_INSET_PX / density
        assertEquals(
            "Nested scrollport already below status bar received a duplicate inset",
            requiredTop,
            elementTop(view, "#header"),
            CSS_PIXEL_TOLERANCE,
        )
        assertFalse(
            "Nested scroll sticky header required native fallback",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun tapTapStickyHeaderUsesSafeTopBeforeFirstScrollFrame() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(fallbackReceived),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 250vh; }
                  #header {
                    position: sticky;
                    top: 0;
                    height: 56px;
                    background: white;
                    transition: top 200ms linear;
                  }
                  #content { height: 2000px; }
                </style></head><body>
                <header id="header">TapTap</header><div id="content"></div>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)
        SystemClock.sleep(250L)
        val density = evaluate(view, "devicePixelRatio").toDouble()
        val requiredTop = TOP_INSET_PX / density
        assertTrue(elementTop(view, "#header") >= requiredTop - CSS_PIXEL_TOLERANCE)

        val immediateTop = evaluate(
            view,
            "(() => { scrollTo(0, 480); " +
                "return document.querySelector('#header').getBoundingClientRect().top; })()",
        ).toDouble()

        assertTrue(
            "TapTap-style sticky header entered status bar before deferred recovery: " +
                "top=$immediateTop expected=$requiredTop",
            immediateTop >= requiredTop - CSS_PIXEL_TOLERANCE,
        )
        assertEquals(
            "true",
            evaluate(
                view,
                "document.querySelector('#header').getAttribute(" +
                    "'data-candy-browser-top-inset-sticky')",
            ).removeSurrounding("\""),
        )
        assertFalse(
            "TapTap-style sticky header required native fallback",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun stickyHeaderThatRejectsLocalOffsetFallsBackAfterScrollSettles() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = 100,
                requiredFailureCount = 2,
            ),
            html = """
                <html><head><style>
                  html, body { margin: 0; }
                  #scroller { position: fixed; inset: 0; overflow-y: auto; }
                  #lead { height: 240px; }
                  #header {
                    position: sticky;
                    top: -1px;
                    height: 64px;
                    background: white;
                    transition: translate 1s linear;
                  }
                  #content { height: 2000px; }
                </style></head><body>
                <section id="scroller">
                  <div id="lead"></div><header id="header">Vimeo</header><div id="content"></div>
                </section>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(
            view,
            "document.querySelector('#scroller').scrollTop = 400",
        )

        assertTrue(
            "Unstable sticky header did not request native top fallback",
            fallbackReceived.await(STALE_TIMER_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    private fun assertElementUsesExactStatusInset(
        view: WebView,
        selector: String,
    ) {
        val devicePixelRatio = evaluate(view, "devicePixelRatio").toDouble()
        val elementTop = evaluate(
            view,
            "document.querySelector('$selector').getBoundingClientRect().top",
        ).toDouble()
        val expectedTop = TOP_INSET_PX / devicePixelRatio
        assertTrue(
            "$selector unexpectedly received compact-control padding: " +
                "top=$elementTop expected=$expectedTop",
            kotlin.math.abs(elementTop - expectedTop) <= CSS_PIXEL_TOLERANCE,
        )
    }

    private fun elementTop(
        view: WebView,
        selector: String,
    ): Double = evaluate(
        view,
        "document.querySelector('$selector').getBoundingClientRect().top",
    ).toDouble()

    private fun awaitElementTop(
        view: WebView,
        script: String,
        minimumTop: Double,
    ): Double {
        val deadline = SystemClock.uptimeMillis() + PAGE_TIMEOUT_SECONDS * 1_000
        var top = Double.NEGATIVE_INFINITY
        while (SystemClock.uptimeMillis() < deadline) {
            top = evaluate(view, script).toDouble()
            if (top >= minimumTop) return top
            SystemClock.sleep(50)
        }
        return top
    }

    private fun loadPage(
        bridge: TopInsetBridge,
        html: String = "<html><head></head><body><main>Content</main></body></html>",
    ): WebView {
        val pageLoaded = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        val scenario = ActivityScenario.launch(GeckoScrollTestActivity::class.java)
        visibleWebViewScenario = scenario
        scenario.onActivity { activity ->
            val view = WebView(activity)
            createdView.set(view)
            webView.set(view)
            activity.setContentView(
                view,
                ViewGroup.LayoutParams(VIEWPORT_WIDTH_PX, VIEWPORT_HEIGHT_PX),
            )
            view.apply {
                settings.javaScriptEnabled = true
                addJavascriptInterface(bridge, WebContentTopInsetScript.bridgeName)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        pageLoaded.countDown()
                    }
                }
                measure(
                    View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH_PX, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(
                        VIEWPORT_HEIGHT_PX,
                        View.MeasureSpec.EXACTLY,
                    ),
                )
                layout(0, 0, VIEWPORT_WIDTH_PX, VIEWPORT_HEIGHT_PX)
                loadDataWithBaseURL(
                    "https://safe-area.test/",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        }
        assertTrue(pageLoaded.await(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        return createdView.get().also { view ->
            instrumentation.runOnMainSync {
                assertTrue(view.isAttachedToWindow)
                assertTrue(view.isShown)
                assertEquals(VIEWPORT_WIDTH_PX, view.width)
                assertEquals(VIEWPORT_HEIGHT_PX, view.height)
            }
        }
    }

    private fun loadViewportStickyPage(bridge: TopInsetBridge): WebView = loadPage(
        bridge = bridge,
        html = """
            <html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              html, body { margin: 0; }
              #columns { display: flex; }
              #document-column, #shadow-host { display: block; width: 50%; }
              #document-sticky { position: sticky; top: 0; height: 64px; background: white; }
              #document-feed { height: 4000px; }
            </style></head><body>
            <main id="columns">
              <section id="document-column"><header id="document-sticky">Document</header><div id="document-feed"></div></section>
              <section id="shadow-host"></section>
            </main>
            <script>
              const shadow = document.querySelector('#shadow-host').attachShadow({ mode: 'open' });
              shadow.innerHTML = '<style>#shadow-sticky{position:sticky;top:0;height:64px;background:white}' +
                '#shadow-sticky.author-change{top:6px}#shadow-feed{height:4000px}</style>' +
                '<header id="shadow-sticky">Shadow</header><div id="shadow-feed"></div>';
              globalThis.__stickyHeaders = [document.querySelector('#document-sticky'), shadow.querySelector('#shadow-sticky')];
            </script></body></html>
        """.trimIndent(),
    )

    private fun awaitScriptCondition(view: WebView, script: String) {
        val deadline = SystemClock.uptimeMillis() + PAGE_TIMEOUT_SECONDS * 1_000
        var satisfied = false
        while (!satisfied && SystemClock.uptimeMillis() < deadline) {
            satisfied = evaluate(view, script) == "true"
            if (!satisfied) SystemClock.sleep(50)
        }
        assertTrue("Browser condition did not settle: $script", satisfied)
    }

    private fun evaluate(view: WebView, script: String): String {
        val evaluated = CountDownLatch(1)
        val result = AtomicReference<String>()
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { value ->
                result.set(value)
                evaluated.countDown()
            }
        }
        assertTrue(evaluated.await(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private class TopInsetBridge(
        private val fallbackReceived: CountDownLatch,
        private val layoutQuietPeriodMillis: Int = 400,
        private val requiredFailureCount: Int = 3,
        private val viewportCoverAllowed: Boolean = false,
        private val performanceDiagnosticsEnabled: Boolean = false,
        private val nativeTopHeaderEnabled: Boolean = false,
    ) {
        @JavascriptInterface
        fun topInsetPx(): Int = TOP_INSET_PX

        @JavascriptInterface
        fun viewportCoverAllowed(): Boolean = viewportCoverAllowed

        @JavascriptInterface
        fun performanceDiagnosticsEnabled(): Boolean = performanceDiagnosticsEnabled

        @JavascriptInterface
        fun navigationGeneration(): Int = NAVIGATION_GENERATION

        @JavascriptInterface
        fun policyRevision(): Long = POLICY_REVISION

        @JavascriptInterface
        fun safeAreaLayoutQuietPeriodMillis(): Int = layoutQuietPeriodMillis

        @JavascriptInterface
        fun safeAreaRequiredFailureCount(): Int = requiredFailureCount

        @JavascriptInterface
        fun nativeTopHeaderEnabled(): Boolean = nativeTopHeaderEnabled

        @JavascriptInterface
        fun fallbackToNative(
            generation: Int,
            revision: Long,
            themeColor: String?,
            isTopHeader: Boolean,
        ) {
            if (generation == NAVIGATION_GENERATION && revision == POLICY_REVISION) {
                fallbackReceived.countDown()
            }
        }
    }

    private companion object {
        const val TOP_INSET_PX = 96
        const val NAVIGATION_GENERATION = 7
        const val POLICY_REVISION = 11L
        const val MUTATION_SETTLE_MILLIS = 100L
        const val SCROLL_SETTLE_MILLIS = 550L
        const val COMPACT_CONTROL_PADDING_CSS_PIXELS = 8.0
        const val CSS_PIXEL_TOLERANCE = 0.5
        const val NO_FALLBACK_WINDOW_MILLIS = 350L
        const val SEARCH_LAYOUT_QUIET_PERIOD_MILLIS = 800
        const val IMMEDIATE_LAYOUT_TIMEOUT_MILLIS = 250L
        const val IMMEDIATE_FALLBACK_WINDOW_MILLIS = 100L
        const val FRAME_SETTLE_MILLIS = 16L
        const val VIEWPORT_WIDTH_PX = 1_080
        const val VIEWPORT_HEIGHT_PX = 1_920
        const val MAX_STABLE_RECONCILE_HIT_QUERIES = 200
        const val MAX_FEED_APPEND_TASK_HIT_QUERIES = 64
        const val STALE_TIMER_WINDOW_MILLIS = 600L
        const val SCROLL_REGRESSION_WINDOW_MILLIS = 600L
        const val SHADOW_HOOK_STABILIZATION_MILLIS = 5_200L
        const val PAGE_TIMEOUT_SECONDS = 5L
    }
}
