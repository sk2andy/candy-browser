package dev.sk2andy.materialbrowser.ui

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.VelocityTracker
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserWebView
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserScrollInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun tabPreviewCaptureRunsOutsideTheScrollPath() {
        GestureOnboardingStore(instrumentation.targetContext).markCompleted()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val testTabId = scenario.readActivity { activity ->
                activity.browserControllerForTesting().createTab("https://example.test/")
            }
            val webView = awaitAttachedWebView(scenario)
            instrumentation.runOnMainSync {
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <head>
                            <meta name="viewport"
                                content="width=device-width, initial-scale=1">
                          </head>
                          <body style="min-height:24000px;
                              background:repeating-linear-gradient(
                                  #f44336 0 240px, #2196f3 240px 480px)">
                            <div id="probe">preview capture test</div>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            awaitProbe(webView)
            awaitMaximumScrollY(webView)

            val initialRequestCount = scenario.readActivity { activity ->
                activity.browserControllerForTesting().previewCaptureRequestCountForTesting
            }
            val initialCapture = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().prepareTabOverview(initialCapture::countDown)
            }
            assertTrue("Initial preview capture timed out", initialCapture.await(5, TimeUnit.SECONDS))
            assertTrue(
                "Opening tab overview did not request a preview capture",
                scenario.readActivity { activity ->
                    activity.browserControllerForTesting().previewCaptureRequestCountForTesting ==
                        initialRequestCount + 1
                },
            )

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().previews.remove(testTabId)
                webView.scrollTo(0, webView.height * 2)
            }
            SystemClock.sleep(350)
            assertTrue(
                "Scrolling unexpectedly scheduled a preview capture",
                scenario.readActivity { activity ->
                    activity.browserControllerForTesting().previewCaptureRequestCountForTesting ==
                        initialRequestCount + 1
                },
            )

            val overviewRequestCount = initialRequestCount + 1
            val overviewCapture = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().prepareTabOverview(overviewCapture::countDown)
            }
            assertTrue("Overview preview capture timed out", overviewCapture.await(5, TimeUnit.SECONDS))
            assertTrue(
                "Opening tab overview did not request a fresh preview",
                scenario.readActivity { activity ->
                    activity.browserControllerForTesting().previewCaptureRequestCountForTesting ==
                        overviewRequestCount + 1
                },
            )

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().closeTab(testTabId)
            }
        }
    }

    @Test
    fun fullBrowserWindowKeepsNativeOppositeSwipes() {
        GestureOnboardingStore(instrumentation.targetContext).markCompleted()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val testTabId = scenario.readActivity { activity ->
                activity.browserControllerForTesting().createTab("https://example.test/")
            }
            val webView = awaitAttachedWebView(scenario)
            instrumentation.runOnMainSync {
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <head>
                            <meta name="viewport"
                                content="width=device-width, initial-scale=1">
                          </head>
                          <body style="min-height:24000px">
                            <div id="probe">full browser scroll test</div>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            awaitProbe(webView)
            val maximum = awaitMaximumScrollY(webView)
            assertTrue("Test page was not scrollable", maximum > webView.height * 2)

            repeat(30) { attempt ->
                val startScroll = maximum / 2
                instrumentation.runOnMainSync {
                    webView.flingScroll(0, 0)
                    webView.scrollTo(0, startScroll)
                }
                awaitScrollNear(webView, startScroll)

                val location = IntArray(2)
                instrumentation.runOnMainSync { webView.getLocationOnScreen(location) }
                val x = location[0] + webView.width / 2f
                val firstStartY = location[1] + webView.height * 0.65f
                val firstTravel = webView.height * -0.30f
                sendGesture(x, firstStartY, firstTravel)
                SystemClock.sleep(40)
                val beforeReverse = webView.scrollY
                assertTrue(
                    "Attempt $attempt lost the first native swipe: " +
                        "start=$startScroll beforeReverse=$beforeReverse",
                    beforeReverse > startScroll + 40,
                )

                val reverseStartY = location[1] + webView.height * 0.25f
                val reverseTravel = webView.height * 0.36f
                val reverseSamples = sendGesture(
                    x = x,
                    startY = reverseStartY,
                    travelY = reverseTravel,
                    sampleScrollY = { webView.scrollY },
                )
                val afterReverseDrag = reverseSamples.last()
                SystemClock.sleep(120)
                val afterReverseFling = webView.scrollY

                assertTrue(
                    "Attempt $attempt did not react to reverse MOVE events: " +
                        "before=$beforeReverse samples=$reverseSamples",
                    afterReverseDrag < reverseSamples.max() - 40,
                )
                assertTrue(
                    "Attempt $attempt lost native reverse momentum: " +
                        "drag=$afterReverseDrag fling=$afterReverseFling",
                    afterReverseFling < afterReverseDrag - 20,
                )
            }

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().closeTab(testTabId)
            }
        }
    }

    @Test
    fun busyLongPageKeepsEveryRapidAlternatingFlick() {
        GestureOnboardingStore(instrumentation.targetContext).markCompleted()

        val sessions = LocalPageServer(busyLongPageHtml()).use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val testTabId = scenario.readActivity { activity ->
                    activity.browserControllerForTesting().createTab(server.url)
                }
                try {
                    val webView = awaitAttachedWebView(scenario)
                    awaitBusyPage(webView)
                    val maximum = awaitMaximumScrollY(webView)
                    assertTrue(
                        "Busy fixture was not long enough: maximum=$maximum " +
                            "height=${webView.height}",
                        maximum > webView.height * 40,
                    )
                    List(LOCAL_STRESS_SESSIONS) { session ->
                        val result = runRapidFlickStress(
                            webView = webView,
                            maximum = maximum,
                            chainId = session,
                        )
                        val domTrace = evaluate(
                            webView,
                            "JSON.stringify(window.__candyTouchEvents())",
                        )
                        Log.i(TEST_LOG_TAG, "session=$session domTrace=$domTrace")
                        assertTrue(
                            "DOM touch streams had no terminal event: $domTrace",
                            evaluate(
                                webView,
                                "(() => { const trace = window.__candyTouchEvents(); " +
                                    "return trace.touchstart > 0 && trace.touchstart === " +
                                    "trace.touchend + trace.touchcancel; })()",
                            ) == "true",
                        )
                        result
                    }
                } finally {
                    scenario.onActivity { activity ->
                        activity.browserControllerForTesting().closeTab(testTabId)
                    }
                }
            }
        }
        val result = combineStressResults(sessions)
        Log.i(TEST_LOG_TAG, "busyLongPage $result")
        Log.i(TEST_LOG_TAG, "busyLongPageSummary ${result.summary}")
        assertRapidFlickStress(
            result = result,
            requiredCompleteChains = LOCAL_STRESS_SESSIONS,
            requiredSlowControls = LOCAL_STRESS_SESSIONS * REQUIRED_SLOW_CONTROLS,
        )
    }

    @Test
    fun liveDiscordPageKeepsEveryRapidAlternatingFlick() {
        assumeTrue(
            "Set instrumentation argument $LIVE_SCROLL_ARGUMENT=true to run live-site stress",
            InstrumentationRegistry.getArguments().getString(LIVE_SCROLL_ARGUMENT) == "true",
        )
        GestureOnboardingStore(instrumentation.targetContext).markCompleted()

        val sessions = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            List(LIVE_STRESS_SESSIONS) { session ->
                val testTabId = scenario.readActivity { activity ->
                    activity.browserControllerForTesting().createTab(LIVE_SCROLL_URL)
                }
                try {
                    val webView = awaitAttachedWebView(scenario)
                    val maximum = awaitLivePage(webView, minimumViewports = 5)
                    runRapidFlickStress(
                        webView = webView,
                        maximum = maximum,
                        chainId = session,
                    )
                } finally {
                    scenario.onActivity { activity ->
                        activity.browserControllerForTesting().closeTab(testTabId)
                    }
                }
            }
        }
        val result = combineStressResults(sessions)
        Log.i(TEST_LOG_TAG, "liveDiscordPage url=$LIVE_SCROLL_URL $result")
        Log.i(TEST_LOG_TAG, "liveDiscordPageSummary ${result.summary}")
        assertRapidFlickStress(
            result = result,
            requiredCompleteChains = LIVE_STRESS_SESSIONS,
            requiredSlowControls = LIVE_STRESS_SESSIONS * REQUIRED_SLOW_CONTROLS,
        )
    }

    private fun combineStressResults(
        sessions: List<RapidFlickStressResult>,
    ): RapidFlickStressResult = RapidFlickStressResult(
        outcomes = sessions.flatMap { it.outcomes },
        setupMisses = sessions.sumOf { it.setupMisses },
        completeChains = sessions.sumOf { it.completeChains },
        slowControlsPassed = sessions.sumOf { it.slowControlsPassed },
    )

    private fun runRapidFlickStress(
        webView: WebView,
        maximum: Int,
        chainId: Int,
    ): RapidFlickStressResult {
        val touchSamples = Collections.synchronizedList(mutableListOf<TouchSample>())
        val dispatchCompletions = Collections.synchronizedList(mutableListOf<DispatchCompletion>())
        val scrollSamples = Collections.synchronizedList(mutableListOf<ScrollSample>())
        val touchListener = View.OnTouchListener { view, event ->
            touchSamples += TouchSample(
                action = event.actionMasked,
                downTimeMs = event.downTime,
                eventTimeMs = event.eventTime,
                observedAtMs = SystemClock.uptimeMillis(),
                y = event.y,
                history = List(event.historySize) { index ->
                    TouchPoint(
                        eventTimeMs = event.getHistoricalEventTime(index),
                        y = event.getHistoricalY(index),
                    )
                },
                pointerCount = event.pointerCount,
                source = event.source,
                scrollY = view.scrollY,
            )
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val downTime = event.downTime
                view.post {
                    dispatchCompletions += DispatchCompletion(
                        downTimeMs = downTime,
                        observedAtMs = SystemClock.uptimeMillis(),
                        scrollY = view.scrollY,
                    )
                }
            }
            false
        }
        val scrollListener = ViewTreeObserver.OnScrollChangedListener {
            scrollSamples += ScrollSample(
                observedAtMs = SystemClock.uptimeMillis(),
                scrollY = webView.scrollY,
            )
        }
        instrumentation.runOnMainSync {
            webView.setOnTouchListener(touchListener)
            webView.viewTreeObserver.addOnScrollChangedListener(scrollListener)
        }
        val webViewLocation = IntArray(2)
        instrumentation.runOnMainSync { webView.getLocationOnScreen(webViewLocation) }

        val outcomes = mutableListOf<RapidFlickOutcome>()
        var setupMisses = 0
        var completeChains = 0
        val slowControlsPassed: Int
        try {
            awaitScrollIdle(webView)
            val startScroll = maximum / 2
            instrumentation.runOnMainSync {
                checkNotNull(webView as? BrowserWebView).scrollToVerticalOffset(startScroll)
            }
            awaitScrollNear(webView, startScroll)
            awaitScrollIdle(webView)

            var chainCompleted = false
            repeat(MAX_CHAIN_ATTEMPTS) chainAttempts@{ chainAttempt ->
                if (chainCompleted) return@chainAttempts
                if (chainAttempt > 0) {
                    awaitScrollIdle(webView)
                    instrumentation.runOnMainSync {
                        checkNotNull(webView as? BrowserWebView)
                            .scrollToVerticalOffset(startScroll)
                    }
                    awaitScrollNear(webView, startScroll)
                    awaitScrollIdle(webView)
                }
                var scrollDirection = if (chainId % 2 == 0) 1 else -1
                var momentumConfirmedAtMs: Long? = null
                repeat(MAX_SETUP_ATTEMPTS) setupAttempts@{ setupAttempt ->
                    if (momentumConfirmedAtMs != null) return@setupAttempts
                    if (setupAttempt > 0) {
                        awaitScrollIdle(webView)
                        instrumentation.runOnMainSync {
                            checkNotNull(webView as? BrowserWebView)
                                .scrollToVerticalOffset(startScroll)
                        }
                        awaitScrollNear(webView, startScroll)
                        awaitScrollIdle(webView)
                    }
                    val initialFlick = injectRapidFlick(
                        webView = webView,
                        webViewLocation = webViewLocation,
                        scrollDirection = scrollDirection,
                        touchSamples = touchSamples,
                        dispatchCompletions = dispatchCompletions,
                    )
                    momentumConfirmedAtMs = if (initialFlick.qualified) {
                        awaitActiveMomentum(
                            scrollSamples = scrollSamples,
                            afterMs = initialFlick.upDispatchAtMs,
                            direction = scrollDirection,
                        )
                    } else {
                        null
                    }
                    if (momentumConfirmedAtMs == null) {
                        setupMisses++
                        Log.i(
                            TEST_LOG_TAG,
                            "setupMiss chain=$chainId chainAttempt=$chainAttempt " +
                                "setupAttempt=$setupAttempt direction=$scrollDirection " +
                                "flick=$initialFlick scrollTail=" +
                                scrollSamples.snapshot().takeLast(8),
                        )
                    }
                }
                if (momentumConfirmedAtMs != null) {
                    var completedTurns = 0
                    for (turn in 0 until REVERSES_PER_CHAIN) {
                        val reverseDirection = -scrollDirection
                        val flick = injectRapidFlick(
                            webView = webView,
                            webViewLocation = webViewLocation,
                            scrollDirection = reverseDirection,
                            touchSamples = touchSamples,
                            dispatchCompletions = dispatchCompletions,
                        )
                        val startedDuringActiveMomentum = wasMomentumActiveAt(
                            scrollSamples = scrollSamples,
                            atMs = flick.downEventAtMs,
                            afterMs = checkNotNull(momentumConfirmedAtMs),
                            direction = scrollDirection,
                        )
                        val response = measureReverseResponse(
                            flick = flick,
                            scrollSamples = scrollSamples,
                            direction = reverseDirection,
                            thresholdPx = reverseResponseThresholdPx(webView),
                        )
                        val reverseMomentumConfirmedAtMs = if (flick.qualified) {
                            awaitActiveMomentum(
                                scrollSamples = scrollSamples,
                                afterMs = flick.upDispatchAtMs,
                                direction = reverseDirection,
                            )
                        } else {
                            null
                        }
                        val reverseMomentum = reverseMomentumConfirmedAtMs != null
                        val outcome = RapidFlickOutcome(
                            chain = chainId,
                            turn = turn,
                            direction = reverseDirection,
                            startedDuringActiveMomentum = startedDuringActiveMomentum,
                            completeTouch = flick.complete,
                            qualifiedFlick = flick.qualified,
                            moveSamples = flick.moveSamples,
                            durationMs = flick.durationMs,
                            velocityY = flick.velocityY,
                            duringGestureDeltaPx = response.duringGestureDeltaPx,
                            postUpWindowDeltaPx = response.postUpWindowDeltaPx,
                            responseLatencyMs = response.latencyMs,
                            reverseMomentum = reverseMomentum,
                        )
                        outcomes += outcome
                        Log.i(TEST_LOG_TAG, "rapidFlick chainAttempt=$chainAttempt $outcome")
                        if (
                            !startedDuringActiveMomentum ||
                            !reverseMomentum
                        ) {
                            break
                        }
                        momentumConfirmedAtMs = reverseMomentumConfirmedAtMs
                        scrollDirection = reverseDirection
                        completedTurns++
                    }
                    if (completedTurns == REVERSES_PER_CHAIN) {
                        completeChains++
                        chainCompleted = true
                    }
                }
            }
            slowControlsPassed = runSlowDragControls(
                webView = webView,
                maximum = maximum,
            )
        } finally {
            instrumentation.runOnMainSync {
                webView.setOnTouchListener(null)
                if (webView.viewTreeObserver.isAlive) {
                    webView.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
                }
            }
        }
        return RapidFlickStressResult(
            outcomes = outcomes,
            setupMisses = setupMisses,
            completeChains = completeChains,
            slowControlsPassed = slowControlsPassed,
        )
    }

    private fun injectRapidFlick(
        webView: WebView,
        webViewLocation: IntArray,
        scrollDirection: Int,
        touchSamples: MutableList<TouchSample>,
        dispatchCompletions: MutableList<DispatchCompletion>,
    ): InjectedFlick {
        val x = webViewLocation[0] + webView.width * 0.42f
        val startY = webViewLocation[1] + if (scrollDirection > 0) {
            webView.height * 0.68f
        } else {
            webView.height * 0.32f
        }
        val travelY = -scrollDirection * webView.height * RAPID_FLICK_TRAVEL_FRACTION
        val downTime = SystemClock.uptimeMillis()
        injectPointer(
            downTime = downTime,
            eventTime = downTime,
            action = MotionEvent.ACTION_DOWN,
            x = x,
            y = startY,
        )
        repeat(RAPID_FLICK_MOVE_COUNT) { index ->
            SystemClock.sleep(RAPID_FLICK_STEP_MS)
            injectPointer(
                downTime = downTime,
                eventTime = downTime + (index + 1) * RAPID_FLICK_STEP_MS,
                action = MotionEvent.ACTION_MOVE,
                x = x,
                y = startY + travelY * (index + 1) / RAPID_FLICK_MOVE_COUNT,
            )
        }
        SystemClock.sleep(RAPID_FLICK_STEP_MS)
        injectPointer(
            downTime = downTime,
            eventTime = downTime + (RAPID_FLICK_MOVE_COUNT + 1) * RAPID_FLICK_STEP_MS,
            action = MotionEvent.ACTION_UP,
            x = x,
            y = startY + travelY,
        )

        val gestureSamples = awaitGestureSamples(touchSamples, downTime)
        val down = gestureSamples.firstOrNull { it.action == MotionEvent.ACTION_DOWN }
        val up = gestureSamples.lastOrNull { it.action == MotionEvent.ACTION_UP }
        val moveCount = gestureSamples.sumOf { sample ->
            if (sample.action == MotionEvent.ACTION_MOVE) 1 + sample.history.size else 0
        }
        val completion = awaitDispatchCompletion(dispatchCompletions, downTime)
        val complete = down != null &&
            up != null &&
            completion != null &&
            moveCount >= RAPID_FLICK_MOVE_COUNT &&
            gestureSamples.none { it.action == MotionEvent.ACTION_CANCEL } &&
            gestureSamples.all { sample ->
                sample.pointerCount == 1 &&
                    sample.source and InputDevice.SOURCE_TOUCHSCREEN ==
                    InputDevice.SOURCE_TOUCHSCREEN
            } &&
            webView.isAttachedToWindow &&
            webView.hasWindowFocus()
        val configuration = ViewConfiguration.get(webView.context)
        val kinematics = measureFlickKinematics(
            gestureSamples = gestureSamples,
            maximumVelocity = configuration.scaledMaximumFlingVelocity.toFloat(),
        )
        val qualified = complete &&
            kinematics.durationMs in MIN_FLICK_DURATION_MS..MAX_FLICK_DURATION_MS &&
            kinematics.travelY.absoluteValue >= webView.height * MIN_FLICK_TRAVEL_FRACTION &&
            kinematics.velocityY.absoluteValue >= configuration.scaledMinimumFlingVelocity *
            MIN_FLING_VELOCITY_MULTIPLIER &&
            kinematics.velocityY * scrollDirection < 0f
        return InjectedFlick(
            downEventAtMs = down?.eventTimeMs ?: downTime,
            downObservedAtMs = down?.observedAtMs ?: downTime,
            downScrollY = down?.scrollY ?: webView.scrollY,
            upDispatchAtMs = completion?.observedAtMs ?: SystemClock.uptimeMillis(),
            upDispatchScrollY = completion?.scrollY ?: webView.scrollY,
            moveSamples = moveCount,
            durationMs = kinematics.durationMs,
            travelY = kinematics.travelY,
            velocityY = kinematics.velocityY,
            complete = complete,
            qualified = qualified,
        )
    }

    private fun measureFlickKinematics(
        gestureSamples: List<TouchSample>,
        maximumVelocity: Float,
    ): FlickKinematics {
        val down = gestureSamples.firstOrNull { it.action == MotionEvent.ACTION_DOWN }
            ?: return FlickKinematics.Empty
        val up = gestureSamples.lastOrNull { it.action == MotionEvent.ACTION_UP }
            ?: return FlickKinematics.Empty
        val tracker = VelocityTracker.obtain()
        try {
            gestureSamples.forEach { sample ->
                sample.history.forEach { point ->
                    tracker.addSyntheticMovement(
                        downTimeMs = down.eventTimeMs,
                        eventTimeMs = point.eventTimeMs,
                        action = MotionEvent.ACTION_MOVE,
                        y = point.y,
                    )
                }
                tracker.addSyntheticMovement(
                    downTimeMs = down.eventTimeMs,
                    eventTimeMs = sample.eventTimeMs,
                    action = sample.action,
                    y = sample.y,
                )
            }
            tracker.computeCurrentVelocity(1_000, maximumVelocity)
            return FlickKinematics(
                durationMs = up.eventTimeMs - down.eventTimeMs,
                travelY = up.y - down.y,
                velocityY = tracker.yVelocity,
            )
        } finally {
            tracker.recycle()
        }
    }

    private fun VelocityTracker.addSyntheticMovement(
        downTimeMs: Long,
        eventTimeMs: Long,
        action: Int,
        y: Float,
    ) {
        MotionEvent.obtain(downTimeMs, eventTimeMs, action, 0f, y, 0).also { event ->
            try {
                addMovement(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun awaitDispatchCompletion(
        dispatchCompletions: MutableList<DispatchCompletion>,
        downTime: Long,
    ): DispatchCompletion? {
        repeat(100) {
            dispatchCompletions.snapshot().lastOrNull { it.downTimeMs == downTime }?.let {
                return it
            }
            SystemClock.sleep(2)
        }
        return null
    }

    private fun injectPointer(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { event ->
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(
                    "Input injection failed for ${MotionEvent.actionToString(action)}",
                    instrumentation.uiAutomation.injectInputEvent(event, false),
                )
            } finally {
                event.recycle()
            }
        }
    }

    private fun awaitGestureSamples(
        touchSamples: MutableList<TouchSample>,
        downTime: Long,
    ): List<TouchSample> {
        repeat(250) {
            val samples = touchSamples.snapshot().filter { it.downTimeMs == downTime }
            if (samples.any { it.action == MotionEvent.ACTION_UP }) return samples
            SystemClock.sleep(2)
        }
        return touchSamples.snapshot().filter { it.downTimeMs == downTime }
    }

    private fun awaitActiveMomentum(
        scrollSamples: MutableList<ScrollSample>,
        afterMs: Long,
        direction: Int,
    ): Long? {
        repeat(30) {
            val samples = scrollSamples.snapshot()
                .filter { it.observedAtMs >= afterMs }
            val directedSteps = samples.zipWithNext { first, second ->
                (second.scrollY - first.scrollY) * direction
            }
            val directedDelta = samples.lastOrNull()?.let { last ->
                (last.scrollY - samples.first().scrollY) * direction
            } ?: 0
            if (
                samples.size >= 2 &&
                samples.last().observedAtMs - samples.first().observedAtMs >=
                MIN_MOMENTUM_SAMPLE_SPAN_MS &&
                SystemClock.uptimeMillis() - samples.last().observedAtMs <=
                MAX_MOMENTUM_SAMPLE_AGE_MS &&
                directedDelta >= MIN_MOMENTUM_DELTA_PX &&
                directedSteps.last() > 0 &&
                directedSteps.none { it < 0 }
            ) {
                return samples.last().observedAtMs
            }
            SystemClock.sleep(6)
        }
        return null
    }

    private fun wasMomentumActiveAt(
        scrollSamples: MutableList<ScrollSample>,
        atMs: Long,
        afterMs: Long,
        direction: Int,
    ): Boolean {
        val samples = scrollSamples.snapshot().filter {
            it.observedAtMs in (atMs - MOMENTUM_LOOKBACK_MS)..atMs
        }
        if (samples.size < 2) return false
        val directedSteps = samples.zipWithNext { first, second ->
            (second.scrollY - first.scrollY) * direction
        }
        return samples.last().observedAtMs - samples.first().observedAtMs >=
            MIN_MOMENTUM_SAMPLE_SPAN_MS &&
            samples.last().observedAtMs >= afterMs &&
            atMs - samples.last().observedAtMs <= MAX_MOMENTUM_SAMPLE_AGE_MS &&
            (samples.last().scrollY - samples.first().scrollY) * direction >=
            MIN_MOMENTUM_DELTA_PX &&
            directedSteps.last() > 0 &&
            directedSteps.none { it < 0 }
    }

    private fun measureReverseResponse(
        flick: InjectedFlick,
        scrollSamples: MutableList<ScrollSample>,
        direction: Int,
        thresholdPx: Int,
    ): ReverseResponse {
        val postUpCutoffMs = flick.upDispatchAtMs + POST_UP_RESPONSE_WINDOW_MS
        val remainingMs = postUpCutoffMs - SystemClock.uptimeMillis()
        if (remainingMs > 0) SystemClock.sleep(remainingMs)
        val samples = buildList {
            add(ScrollSample(flick.downObservedAtMs, flick.downScrollY))
            addAll(
                scrollSamples.snapshot().filter {
                    it.observedAtMs in flick.downObservedAtMs..postUpCutoffMs
                },
            )
            add(ScrollSample(flick.upDispatchAtMs, flick.upDispatchScrollY))
        }.sortedBy { it.observedAtMs }

        val during = reverseProgress(
            samples = samples.filter { it.observedAtMs <= flick.upDispatchAtMs },
            direction = direction,
            thresholdPx = thresholdPx,
            startScrollY = flick.downScrollY,
        )
        val withinPostUpWindow = reverseProgress(
            samples = samples,
            direction = direction,
            thresholdPx = thresholdPx,
            startScrollY = flick.downScrollY,
        )
        return ReverseResponse(
            duringGestureDeltaPx = during.deltaPx,
            postUpWindowDeltaPx = withinPostUpWindow.deltaPx,
            latencyMs = withinPostUpWindow.responseAtMs?.minus(flick.downObservedAtMs),
        )
    }

    private fun reverseProgress(
        samples: List<ScrollSample>,
        direction: Int,
        thresholdPx: Int,
        startScrollY: Int,
    ): ReverseProgress {
        var edge = startScrollY
        var reverseDelta = 0
        var responseAtMs: Long? = null
        samples.forEach { sample ->
            val delta = if (direction > 0) {
                edge = minOf(edge, sample.scrollY)
                sample.scrollY - edge
            } else {
                edge = maxOf(edge, sample.scrollY)
                edge - sample.scrollY
            }
            reverseDelta = maxOf(reverseDelta, delta)
            if (responseAtMs == null && delta >= thresholdPx) {
                responseAtMs = sample.observedAtMs
            }
        }
        return ReverseProgress(
            deltaPx = reverseDelta,
            responseAtMs = responseAtMs,
        )
    }

    private fun runSlowDragControls(
        webView: WebView,
        maximum: Int,
    ): Int {
        var passed = 0
        repeat(SLOW_CONTROL_DRAGS) { index ->
            awaitScrollIdle(webView)
            val startScroll = maximum / 2
            instrumentation.runOnMainSync {
                checkNotNull(webView as? BrowserWebView).scrollToVerticalOffset(startScroll)
            }
            awaitScrollNear(webView, startScroll)
            awaitScrollIdle(webView)
            val direction = if (index % 2 == 0) 1 else -1
            val location = IntArray(2)
            instrumentation.runOnMainSync { webView.getLocationOnScreen(location) }
            val x = location[0] + webView.width * 0.42f
            val startY = location[1] + webView.height * 0.5f
            val travelY = -direction * webView.height * SLOW_DRAG_TRAVEL_FRACTION
            val before = webView.scrollY
            sendSlowDrag(x = x, startY = startY, travelY = travelY)
            val delta = awaitDirectedScrollDelta(
                webView = webView,
                baseline = before,
                direction = direction,
            )
            Log.i(
                TEST_LOG_TAG,
                "slowControl index=$index direction=$direction delta=$delta",
            )
            if (delta >= reverseResponseThresholdPx(webView)) passed++
        }
        Log.i(TEST_LOG_TAG, "slowControls=$passed/$SLOW_CONTROL_DRAGS")
        return passed
    }

    private fun awaitDirectedScrollDelta(
        webView: WebView,
        baseline: Int,
        direction: Int,
    ): Int {
        var maximumDelta = 0
        repeat(30) {
            maximumDelta = maxOf(maximumDelta, (webView.scrollY - baseline) * direction)
            if (maximumDelta >= reverseResponseThresholdPx(webView)) return maximumDelta
            SystemClock.sleep(10)
        }
        return maximumDelta
    }

    private fun sendSlowDrag(x: Float, startY: Float, travelY: Float) {
        val downTime = SystemClock.uptimeMillis()
        sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, x, startY)
        repeat(SLOW_DRAG_MOVE_COUNT) { index ->
            SystemClock.sleep(SLOW_DRAG_STEP_MS)
            sendPointer(
                downTime = downTime,
                eventTime = downTime + (index + 1) * SLOW_DRAG_STEP_MS,
                action = MotionEvent.ACTION_MOVE,
                x = x,
                y = startY + travelY * (index + 1) / SLOW_DRAG_MOVE_COUNT,
            )
        }
        sendPointer(
            downTime = downTime,
            eventTime = downTime + (SLOW_DRAG_MOVE_COUNT + 1) * SLOW_DRAG_STEP_MS,
            action = MotionEvent.ACTION_UP,
            x = x,
            y = startY + travelY,
        )
    }

    private fun reverseResponseThresholdPx(webView: WebView): Int = maxOf(
        ViewConfiguration.get(webView.context).scaledTouchSlop * 2,
        (webView.resources.displayMetrics.density * 12f).roundToInt(),
    )

    private fun assertRapidFlickStress(
        result: RapidFlickStressResult,
        requiredCompleteChains: Int,
        requiredSlowControls: Int,
    ) {
        val verifiedOutcomes = result.outcomes.filter { it.startedDuringActiveMomentum }
        assertTrue(
            "Slow drag control failed: $result",
            result.slowControlsPassed >= requiredSlowControls,
        )
        assertTrue(
            "Input stream was incomplete: $result",
            result.outcomes.all { it.completeTouch },
        )
        assertTrue(
            "Too few reverse flicks overlapped confirmed active momentum: $result",
            verifiedOutcomes.size >= requiredCompleteChains * REVERSES_PER_CHAIN,
        )
        assertTrue(
            "One or more rapid reverse-flick chains were incomplete: $result",
            result.completeChains >= requiredCompleteChains,
        )
        assertTrue(
            "One or more rapid reverse flicks did not respond within the post-UP window: $result",
            verifiedOutcomes.all { it.respondedWithinPostUpWindow },
        )
        assertTrue(
            "One or more rapid reverse flicks lost new momentum: $result",
            verifiedOutcomes.filter { it.qualifiedFlick }.all { it.reverseMomentum },
        )
        assertTrue(
            "One or more gestures did not qualify as rapid flicks: $result",
            result.outcomes.all { it.qualifiedFlick },
        )
    }

    private fun awaitScrollIdle(webView: WebView) {
        var previous = webView.scrollY
        var stableSamples = 0
        repeat(250) {
            SystemClock.sleep(12)
            val current = webView.scrollY
            stableSamples = if (current == previous) stableSamples + 1 else 0
            if (stableSamples >= 6) return
            previous = current
        }
        throw AssertionError("WebView momentum did not settle; scrollY=${webView.scrollY}")
    }

    private fun awaitBusyPage(webView: WebView) {
        repeat(200) {
            if (evaluate(webView, "Boolean(window.__candyBusyReady)") == "true") return
            SystemClock.sleep(25)
        }
        throw AssertionError("Busy WebView fixture did not finish loading")
    }

    private fun awaitLivePage(webView: WebView, minimumViewports: Int): Int {
        var previousHeight = 0
        var stableSamples = 0
        repeat(120) {
            val state = evaluate(
                webView,
                "JSON.stringify([location.href, document.readyState, " +
                    "document.scrollingElement.scrollHeight])",
            )
            val height = LIVE_PAGE_HEIGHT_REGEX.find(state)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            stableSamples = if (height == previousHeight) stableSamples + 1 else 0
            if (
                state.contains("discord.com") &&
                state.contains("complete") &&
                height > 0 &&
                stableSamples >= 4
            ) {
                val blockedByOverlay = evaluate(
                    webView,
                    "(() => { let node = document.elementFromPoint(innerWidth * .42, " +
                        "innerHeight * .5); while (node && node !== document.body) { " +
                        "const style = getComputedStyle(node); const rect = " +
                        "node.getBoundingClientRect(); if (style.position === 'fixed' && " +
                        "style.pointerEvents !== 'none' && rect.height > innerHeight * .8 && " +
                        "rect.width > innerWidth * .8) return true; node = node.parentElement; " +
                        "} return false; })()",
                ) == "true"
                assertTrue("Live page was covered by a fixed overlay: $state", !blockedByOverlay)
                val maximum = awaitMaximumScrollY(webView)
                assertTrue(
                    "Live page was too short: maximum=$maximum height=${webView.height}",
                    maximum > webView.height * minimumViewports,
                )
                return maximum
            }
            previousHeight = height
            SystemClock.sleep(250)
        }
        throw AssertionError(
            "Live page did not settle: url=$LIVE_SCROLL_URL height=$previousHeight",
        )
    }

    private fun busyLongPageHtml(): String =
        """
            <!doctype html>
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                  :root { color-scheme: dark; --pulse: 0; }
                  * { box-sizing: border-box; }
                  body {
                    margin: 0;
                    background: #101218;
                    color: #f4f5f8;
                    font: 16px system-ui, sans-serif;
                  }
                  #cards { padding: 24px; }
                  article {
                    min-height: 320px;
                    margin: 0 0 18px;
                    padding: 28px;
                    border: 1px solid rgba(255,255,255,.16);
                    border-radius: 28px;
                    background:
                      radial-gradient(circle at 80% 20%, rgba(88,101,242,.42), transparent 34%),
                      linear-gradient(145deg, #252a36, #151820);
                    box-shadow: 0 18px 48px rgba(0,0,0,.28);
                  }
                  article:nth-child(3n) { min-height: 420px; }
                  .chips { display: flex; flex-wrap: wrap; gap: 9px; }
                  .chip {
                    padding: 9px 14px;
                    border-radius: 999px;
                    background: rgba(255,255,255,.10);
                  }
                  #render-layer {
                    position: fixed;
                    inset: 0;
                    z-index: 20;
                    pointer-events: none;
                    overflow: hidden;
                  }
                  #render-canvas { width: 100%; height: 100%; opacity: .18; }
                  .particle {
                    position: absolute;
                    width: 22px;
                    height: 22px;
                    border-radius: 50%;
                    background: #8ea1ff;
                    filter: blur(1px);
                    will-change: transform;
                  }
                </style>
              </head>
              <body>
                <main id="cards"><div id="probe">busy long page</div></main>
                <div id="render-layer"><canvas id="render-canvas"></canvas></div>
                <script>
                  const cards = document.getElementById('cards');
                  for (let index = 0; index < 120; index++) {
                    const article = document.createElement('article');
                    article.innerHTML = `<h2>Rendered section ${'$'}{index + 1}</h2>
                      <p>Long scrolling fixture with gradients, shadows and changing layers.</p>
                      <div class="chips">${'$'}{Array.from({length: 6}, (_, chip) =>
                        `<span class="chip">item ${'$'}{index + 1}.${'$'}{chip + 1}</span>`).join('')}</div>`;
                    cards.appendChild(article);
                  }
                  const layer = document.getElementById('render-layer');
                  const canvas = document.getElementById('render-canvas');
                  const context = canvas.getContext('2d');
                  const particles = Array.from({length: 8}, (_, index) => {
                    const particle = document.createElement('i');
                    particle.className = 'particle';
                    layer.appendChild(particle);
                    return {element: particle, index};
                  });
                  let frame = 0;
                  function render() {
                    frame++;
                    if (canvas.width !== innerWidth || canvas.height !== innerHeight) {
                      canvas.width = innerWidth;
                      canvas.height = innerHeight;
                    }
                    context.clearRect(0, 0, canvas.width, canvas.height);
                    particles.forEach(({element, index}) => {
                      const phase = frame * .018 + index * .43;
                      const x = (Math.sin(phase) * .45 + .5) * innerWidth;
                      const y = (Math.cos(phase * .73) * .45 + .5) * innerHeight;
                      element.style.transform = `translate3d(${'$'}{x}px, ${'$'}{y}px, 0)`;
                      context.fillStyle = `hsla(${'$'}{220 + index * 3}, 90%, 70%, .16)`;
                      context.fillRect(x - 18, y - 18, 36, 36);
                    });
                    if (frame % 4 === 0) {
                      document.documentElement.style.setProperty('--pulse', String(frame % 120));
                    }
                    requestAnimationFrame(render);
                  }
                  const touchEvents = {
                    touchstart: 0,
                    touchmove: 0,
                    touchend: 0,
                    touchcancel: 0,
                  };
                  ['touchstart', 'touchmove', 'touchend', 'touchcancel'].forEach(type => {
                    document.addEventListener(type, () => touchEvents[type]++, {passive: false});
                  });
                  requestAnimationFrame(() => requestAnimationFrame(() => {
                    window.__candyBusyReady = true;
                    window.__candyTouchEvents = () => ({...touchEvents});
                    render();
                  }));
                </script>
              </body>
            </html>
        """.trimIndent()

    @Test
    fun comparesMinimalMomentumStopStrategies() {
        GestureOnboardingStore(instrumentation.targetContext).markCompleted()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val testTabId = scenario.readActivity { activity ->
                activity.browserControllerForTesting().createTab("https://example.test/")
            }
            val webView = awaitAttachedWebView(scenario)
            instrumentation.runOnMainSync {
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <head>
                            <meta name="viewport"
                                content="width=device-width, initial-scale=1">
                          </head>
                          <body style="min-height:24000px">
                            <div id="probe">momentum strategy test</div>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            awaitProbe(webView)
            val maximum = awaitMaximumScrollY(webView)

            MomentumStopMode.entries.forEach { mode ->
                val listener = MomentumStopListener(mode)
                instrumentation.runOnMainSync {
                    webView.setOnTouchListener(listener.takeUnless {
                        mode == MomentumStopMode.Native
                    })
                }
                val result = compareSparseReverseGestures(webView, maximum)
                Log.i(TEST_LOG_TAG, "mode=$mode $result zeroCalls=${listener.zeroCalls}")

                assertTrue(
                    "Initial swipe stalled for $mode: $result",
                    result.firstSwipeStarted == SPARSE_ATTEMPTS,
                )
                assertTrue(
                    "Reverse gesture was lost for $mode: $result",
                    result.reverseEventuallyFollowed >= SPARSE_ATTEMPTS - 1,
                )
                assertTrue(
                    "Held pointer kept stale momentum for $mode: $result",
                    result.holdStayedInReverseDirection >= SPARSE_ATTEMPTS - 1,
                )
            }

            instrumentation.runOnMainSync { webView.setOnTouchListener(null) }
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().closeTab(testTabId)
            }
        }
    }

    private fun compareSparseReverseGestures(
        webView: WebView,
        maximum: Int,
    ): SparseComparison {
        var firstSwipeStarted = 0
        var firstReverseMoveFollowed = 0
        var reverseEventuallyFollowed = 0
        var holdStayedInReverseDirection = 0

        repeat(SPARSE_ATTEMPTS) {
            val startScroll = maximum / 2
            instrumentation.runOnMainSync {
                webView.flingScroll(0, 0)
                webView.scrollTo(0, startScroll)
            }
            awaitScrollNear(webView, startScroll)

            val location = IntArray(2)
            instrumentation.runOnMainSync { webView.getLocationOnScreen(location) }
            val x = location[0] + webView.width / 2f
            sendGesture(
                x = x,
                startY = location[1] + webView.height * 0.65f,
                travelY = webView.height * -0.30f,
            )
            SystemClock.sleep(40)
            if (webView.scrollY > startScroll + 40) firstSwipeStarted++

            val reverseStartY = location[1] + webView.height * 0.25f
            val downTime = SystemClock.uptimeMillis()
            sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, x, reverseStartY)
            val afterDown = webView.scrollY
            SystemClock.sleep(12)
            val reverseY = reverseStartY + webView.height * 0.36f
            sendPointer(
                downTime = downTime,
                eventTime = downTime + 12L,
                action = MotionEvent.ACTION_MOVE,
                x = x,
                y = reverseY,
            )
            val afterFirstMove = webView.scrollY
            if (afterFirstMove < afterDown - 5) firstReverseMoveFollowed++

            SystemClock.sleep(180)
            val afterHold = webView.scrollY
            if (afterHold <= afterFirstMove + 20) holdStayedInReverseDirection++

            sendPointer(
                downTime = downTime,
                eventTime = downTime + 204L,
                action = MotionEvent.ACTION_MOVE,
                x = x,
                y = reverseY + webView.height * 0.08f,
            )
            val afterSecondMove = webView.scrollY
            if (afterSecondMove < maxOf(afterFirstMove, afterHold) - 20) {
                reverseEventuallyFollowed++
            }
            sendPointer(
                downTime = downTime,
                eventTime = downTime + 216L,
                action = MotionEvent.ACTION_UP,
                x = x,
                y = reverseY + webView.height * 0.08f,
            )
        }
        return SparseComparison(
            firstSwipeStarted = firstSwipeStarted,
            firstReverseMoveFollowed = firstReverseMoveFollowed,
            reverseEventuallyFollowed = reverseEventuallyFollowed,
            holdStayedInReverseDirection = holdStayedInReverseDirection,
        )
    }

    private fun sendGesture(
        x: Float,
        startY: Float,
        travelY: Float,
        sampleScrollY: (() -> Int)? = null,
    ): List<Int> {
        val downTime = SystemClock.uptimeMillis()
        sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, x, startY)
        val samples = mutableListOf<Int>()
        repeat(5) { index ->
            SystemClock.sleep(12)
            val eventTime = downTime + (index + 1) * 12L
            sendPointer(
                downTime = downTime,
                eventTime = eventTime,
                action = MotionEvent.ACTION_MOVE,
                x = x,
                y = startY + travelY * (index + 1) / 5f,
            )
            sampleScrollY?.let { samples += it() }
        }
        sendPointer(
            downTime = downTime,
            eventTime = downTime + 72L,
            action = MotionEvent.ACTION_UP,
            x = x,
            y = startY + travelY,
        )
        return samples
    }

    private fun sendPointer(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { event ->
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(
                    "Input injection failed for ${MotionEvent.actionToString(action)}",
                    instrumentation.uiAutomation.injectInputEvent(event, false),
                )
            } finally {
                event.recycle()
            }
        }
    }

    private fun awaitAttachedWebView(
        scenario: ActivityScenario<MainActivity>,
    ): WebView {
        repeat(200) {
            val webView = scenario.readActivity { activity ->
                activity.browserControllerForTesting().selectedWebViewForTesting()
            }
            if (
                webView.isAttachedToWindow &&
                webView.isShown &&
                webView.width > 0 &&
                webView.height > 0
            ) {
                return webView
            }
            SystemClock.sleep(10)
        }
        throw AssertionError("Browser WebView was not attached")
    }

    private fun awaitProbe(webView: WebView) {
        repeat(100) {
            if (evaluate(webView, "Boolean(document.getElementById('probe'))") == "true") return
            SystemClock.sleep(50)
        }
        throw AssertionError("WebView test page did not finish loading")
    }

    @Suppress("DEPRECATION")
    private fun awaitMaximumScrollY(webView: WebView): Int {
        var previous = 0
        var stableSamples = 0
        repeat(200) {
            SystemClock.sleep(10)
            var current = 0
            instrumentation.runOnMainSync {
                current = (webView.contentHeight * webView.scale - webView.height)
                    .roundToInt()
                    .coerceAtLeast(0)
            }
            stableSamples = if (current == previous) stableSamples + 1 else 0
            if (current > webView.height && stableSamples >= 20) return current
            previous = current
        }
        throw AssertionError("WebView scroll range did not settle, last maximum was $previous")
    }

    private fun awaitScrollNear(webView: WebView, expected: Int) {
        repeat(100) {
            if ((webView.scrollY - expected).absoluteValue < 20) return
            SystemClock.sleep(10)
        }
        throw AssertionError("WebView scroll was ${webView.scrollY}, expected $expected")
    }

    private fun evaluate(webView: WebView, script: String): String {
        val result = AtomicReference<String>()
        val evaluated = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(script) { value ->
                result.set(value)
                evaluated.countDown()
            }
        }
        assertTrue(evaluated.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun <T : Any> ActivityScenario<MainActivity>.readActivity(
        block: (MainActivity) -> T,
    ): T {
        val value = AtomicReference<T>()
        onActivity { activity -> value.set(block(activity)) }
        return checkNotNull(value.get())
    }

    private enum class MomentumStopMode {
        Native,
        Down,
        FirstVerticalMove,
    }

    private class MomentumStopListener(
        private val mode: MomentumStopMode,
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var stopped = false
        var zeroCalls = 0
            private set

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val webView = view as WebView
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    stopped = false
                    if (mode == MomentumStopMode.Down) stop(webView)
                }

                MotionEvent.ACTION_MOVE -> if (
                    mode == MomentumStopMode.FirstVerticalMove &&
                    !stopped &&
                    (event.y - downY).absoluteValue > TOUCH_SLOP_PX &&
                    (event.y - downY).absoluteValue > (event.x - downX).absoluteValue
                ) {
                    stop(webView)
                }
            }
            return false
        }

        private fun stop(webView: WebView) {
            stopped = true
            zeroCalls++
            webView.flingScroll(0, 0)
        }
    }

    private data class SparseComparison(
        val firstSwipeStarted: Int,
        val firstReverseMoveFollowed: Int,
        val reverseEventuallyFollowed: Int,
        val holdStayedInReverseDirection: Int,
    )

    private data class TouchSample(
        val action: Int,
        val downTimeMs: Long,
        val eventTimeMs: Long,
        val observedAtMs: Long,
        val y: Float,
        val history: List<TouchPoint>,
        val pointerCount: Int,
        val source: Int,
        val scrollY: Int,
    )

    private data class TouchPoint(
        val eventTimeMs: Long,
        val y: Float,
    )

    private data class DispatchCompletion(
        val downTimeMs: Long,
        val observedAtMs: Long,
        val scrollY: Int,
    )

    private data class ScrollSample(
        val observedAtMs: Long,
        val scrollY: Int,
    )

    private data class InjectedFlick(
        val downEventAtMs: Long,
        val downObservedAtMs: Long,
        val downScrollY: Int,
        val upDispatchAtMs: Long,
        val upDispatchScrollY: Int,
        val moveSamples: Int,
        val durationMs: Long,
        val travelY: Float,
        val velocityY: Float,
        val complete: Boolean,
        val qualified: Boolean,
    )

    private data class FlickKinematics(
        val durationMs: Long,
        val travelY: Float,
        val velocityY: Float,
    ) {
        companion object {
            val Empty = FlickKinematics(durationMs = 0L, travelY = 0f, velocityY = 0f)
        }
    }

    private data class ReverseResponse(
        val duringGestureDeltaPx: Int,
        val postUpWindowDeltaPx: Int,
        val latencyMs: Long?,
    ) {
        val respondedWithinPostUpWindow: Boolean
            get() = latencyMs != null
    }

    private data class ReverseProgress(
        val deltaPx: Int,
        val responseAtMs: Long?,
    )

    private data class RapidFlickOutcome(
        val chain: Int,
        val turn: Int,
        val direction: Int,
        val startedDuringActiveMomentum: Boolean,
        val completeTouch: Boolean,
        val qualifiedFlick: Boolean,
        val moveSamples: Int,
        val durationMs: Long,
        val velocityY: Float,
        val duringGestureDeltaPx: Int,
        val postUpWindowDeltaPx: Int,
        val responseLatencyMs: Long?,
        val reverseMomentum: Boolean,
    ) {
        val respondedWithinPostUpWindow: Boolean
            get() = responseLatencyMs != null
    }

    private data class RapidFlickStressResult(
        val outcomes: List<RapidFlickOutcome>,
        val setupMisses: Int,
        val completeChains: Int,
        val slowControlsPassed: Int,
    ) {
        val reverseMomentum: Int
            get() = outcomes.count { it.reverseMomentum }

        val summary: String
            get() {
                val verified = outcomes.filter { it.startedDuringActiveMomentum }
                return "verified=${verified.size} " +
                    "lost=${verified.count { !it.reverseMomentum }} " +
                    "completeChains=$completeChains setupMisses=$setupMisses " +
                    "slowControlsPassed=$slowControlsPassed"
            }
    }

    private class LocalPageServer(
        html: String,
    ) : Closeable {
        private val body = html.toByteArray(Charsets.UTF_8)
        private val serverSocket = ServerSocket(
            0,
            8,
            InetAddress.getByName("127.0.0.1"),
        )
        private val serverThread = Thread({ serve() }, "candy-scroll-test-server").apply {
            isDaemon = true
            start()
        }

        val url: String = "http://127.0.0.1:${serverSocket.localPort}/busy.html"

        private fun serve() {
            while (!serverSocket.isClosed) {
                val socket = runCatching { serverSocket.accept() }.getOrNull() ?: return
                socket.use { connection ->
                    connection.soTimeout = 2_000
                    runCatching {
                        val input = connection.getInputStream()
                        var matchedHeaderBytes = 0
                        while (matchedHeaderBytes < HTTP_HEADER_END.size) {
                            val next = input.read()
                            if (next < 0) break
                            matchedHeaderBytes = if (
                                next == HTTP_HEADER_END[matchedHeaderBytes].toInt()
                            ) {
                                matchedHeaderBytes + 1
                            } else {
                                0
                            }
                        }
                        connection.getOutputStream().use { output ->
                            val headers = (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/html; charset=utf-8\r\n" +
                                    "Content-Length: ${body.size}\r\n" +
                                    "Cache-Control: no-store\r\n" +
                                    "Connection: close\r\n\r\n"
                                ).toByteArray(Charsets.US_ASCII)
                            output.write(headers)
                            output.write(body)
                            output.flush()
                        }
                    }
                }
            }
        }

        override fun close() {
            serverSocket.close()
            serverThread.join(2_000)
        }

        private companion object {
            val HTTP_HEADER_END = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        }
    }

    private fun <T> MutableList<T>.snapshot(): List<T> = synchronized(this) { toList() }

    private companion object {
        const val TEST_LOG_TAG = "CandyScrollStrategy"
        const val SPARSE_ATTEMPTS = 20
        const val TOUCH_SLOP_PX = 8f
        const val LOCAL_STRESS_SESSIONS = 5
        const val LIVE_STRESS_SESSIONS = 3
        const val REVERSES_PER_CHAIN = 4
        const val MAX_CHAIN_ATTEMPTS = 8
        const val MAX_SETUP_ATTEMPTS = 4
        const val RAPID_FLICK_MOVE_COUNT = 6
        const val RAPID_FLICK_STEP_MS = 9L
        const val RAPID_FLICK_TRAVEL_FRACTION = 0.30f
        const val MIN_FLICK_TRAVEL_FRACTION = 0.25f
        const val MIN_FLICK_DURATION_MS = 35L
        const val MAX_FLICK_DURATION_MS = 250L
        const val MIN_FLING_VELOCITY_MULTIPLIER = 4
        const val MIN_MOMENTUM_DELTA_PX = 10
        const val MIN_MOMENTUM_SAMPLE_SPAN_MS = 16L
        const val MAX_MOMENTUM_SAMPLE_AGE_MS = 40L
        const val MOMENTUM_LOOKBACK_MS = 80L
        const val POST_UP_RESPONSE_WINDOW_MS = 120L
        const val SLOW_CONTROL_DRAGS = 5
        const val SLOW_DRAG_MOVE_COUNT = 10
        const val SLOW_DRAG_STEP_MS = 30L
        const val SLOW_DRAG_TRAVEL_FRACTION = 0.20f
        const val REQUIRED_SLOW_CONTROLS = 5
        const val LIVE_SCROLL_ARGUMENT = "candy.liveScrollStress"
        const val LIVE_SCROLL_URL = "https://discord.com/"
        val LIVE_PAGE_HEIGHT_REGEX = Regex(""",(\d+)\]""")
    }
}
