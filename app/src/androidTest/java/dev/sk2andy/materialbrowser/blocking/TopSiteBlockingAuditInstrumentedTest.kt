package dev.sk2andy.materialbrowser.blocking

import android.app.LocaleManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.LocaleList
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in, emulator-only audit for reproducible top-site blocker measurements.
 *
 * This is deliberately skipped during normal connected tests. Run it with:
 *
 * ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=\
 * dev.sk2andy.materialbrowser.blocking.TopSiteBlockingAuditInstrumentedTest \
 * -Pandroid.testInstrumentationRunnerArguments.candyAudit=true \
 * -Pandroid.testInstrumentationRunnerArguments.auditPass=current \
 * -Pandroid.testInstrumentationRunnerArguments.buildId=<apk-sha256> \
 * -Pandroid.testInstrumentationRunnerArguments.startRank=1 \
 * -Pandroid.testInstrumentationRunnerArguments.siteCount=300
 */
@RunWith(AndroidJUnit4::class)
class TopSiteBlockingAuditInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun auditTopSitesInCandyBrowser() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString(ARG_ENABLED) == "true")
        assertTrue("Top-site audit must run on an emulator", isEmulator())
        assertTrue(
            "Top-site audit requires isolated WebView profiles",
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE),
        )
        assertTrue(
            "Top-site audit requires document-start scripts for frame coverage",
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT),
        )

        val pass = AuditPass.parse(arguments.getString(ARG_PASS))
        if (pass == AuditPass.Candidate) validateCandidateAssets()
        val buildId = arguments.getString(ARG_BUILD_ID)?.trim()?.takeIf(String::isNotEmpty)
            ?: error("buildId is required for reproducible audit output")
        val captureScreenshots = arguments.getString(ARG_CAPTURE_SCREENSHOTS) == "true"
        val startRank = arguments.getString(ARG_START_RANK)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val siteCount = arguments.getString(ARG_SITE_COUNT)?.toIntOrNull()
            ?.coerceIn(1, MAX_SITES_PER_RUN)
            ?: DEFAULT_SITE_COUNT
        val requestedRanks = parseRequestedRanks(arguments.getString(ARG_TARGET_RANKS))
        val targets = if (requestedRanks.isEmpty()) {
            loadTargets(startRank, siteCount).also { loaded ->
                assertEquals("Incomplete Tranco range", siteCount, loaded.size)
                assertEquals(
                    "Non-contiguous Tranco ranks",
                    (startRank until startRank + siteCount).toList(),
                    loaded.map(AuditTarget::rank),
                )
            }
        } else {
            loadTargets(requestedRanks).also { loaded ->
                assertEquals(
                    "Incomplete explicit Tranco ranks",
                    requestedRanks,
                    loaded.map(AuditTarget::rank),
                )
            }
        }

        val targetContext = instrumentation.targetContext
        targetContext.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(AUDIT_LOCALE)
        GestureOnboardingStore(targetContext).markCompleted()

        val auditRoot = File(
            checkNotNull(targetContext.getExternalFilesDir(null)),
            AUDIT_DIRECTORY,
        ).apply { mkdirs() }
        val screenshotRoot = File(auditRoot, "screenshots/${pass.argument}").apply { mkdirs() }
        val outputFile = File(
            auditRoot,
            "sites-${pass.argument}-${targets.first().rank}-${targets.last().rank}.jsonl",
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            configurePass(scenario, pass)
            var previousAuditTabId: String? = null
            val safeAreaAuditTabId = if (pass == AuditPass.SafeArea) {
                prepareSafeAreaAuditTab(scenario)
            } else {
                null
            }
            var unsafeLayoutCount = 0
            var unmeasuredLayoutCount = 0
            outputFile.bufferedWriter().use { writer ->
                targets.forEach { target ->
                    if (safeAreaAuditTabId != null) {
                        resetSafeAreaAuditTab(scenario, safeAreaAuditTabId)
                    } else {
                        previousAuditTabId = prepareFreshAuditTab(scenario, previousAuditTabId)
                    }
                    val startedAt = System.currentTimeMillis()
                    navigate(scenario, target)
                    val auditTabId = safeAreaAuditTabId ?: previousAuditTabId
                    val page = awaitPage(
                        scenario,
                        target,
                        PAGE_TIMEOUT_MILLIS,
                        auditTabId,
                    )
                    checkNotNull(auditTabId)
                    val webViewAttached = selectSafeAreaAuditTab(
                        scenario,
                        auditTabId,
                        stopLoading = false,
                    )
                    if (webViewAttached) Thread.sleep(SETTLE_MILLIS)
                    val probe = if (webViewAttached) evaluateProbe(scenario) else null
                    val verticalScroll = if (pass == AuditPass.SafeArea || !webViewAttached) {
                        null
                    } else {
                        evaluateVerticalScroll(scenario)
                    }
                    val safeAreaLayout = if (pass == AuditPass.SafeArea) {
                        auditSafeAreaLayout(
                            scenario,
                            scrollApplicable = probe?.optBoolean("html") == true,
                        ).also { layout ->
                            if (
                                layout.optBoolean("applicable") &&
                                !layout.optBoolean("passed")
                            ) {
                                unsafeLayoutCount++
                            }
                            if (!layout.optBoolean("applicable")) unmeasuredLayoutCount++
                        }
                    } else {
                        null
                    }
                    val snapshot = scenario.readActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        controller.privacySnapshot(controller.selectedTabId)
                    }
                    val screenshot = if (
                        captureScreenshots && probe?.needsScreenshot() == true
                    ) {
                        captureScreenshot(scenario, screenshotRoot, target)
                    } else {
                        null
                    }
                    val record = JSONObject()
                        .put("schemaVersion", AUDIT_SCHEMA_VERSION)
                        .put("rank", target.rank)
                        .put("domain", target.domain)
                        .put("pass", pass.argument)
                        .put("buildId", buildId)
                        .put("requestedUrl", "https://${target.domain}/")
                        .put("finalUrl", sanitizeUrl(page.url))
                        .put("title", page.title.take(MAX_TITLE_LENGTH))
                        .put("durationMillis", System.currentTimeMillis() - startedAt)
                        .put("loadingTimedOut", page.loadingTimedOut)
                        .put(
                            "mainFrameError",
                            page.error ?: if (webViewAttached) JSONObject.NULL else WEBVIEW_NOT_ATTACHED,
                        )
                        .put("blockedTotal", snapshot.totalBlocked)
                        .put("blockedDomains", snapshot.domains.toJson())
                        .put("probe", probe ?: JSONObject.NULL)
                        .put("verticalScroll", verticalScroll ?: JSONObject.NULL)
                        .put("safeAreaLayout", safeAreaLayout ?: JSONObject.NULL)
                        .put("screenshot", screenshot ?: JSONObject.NULL)
                    if (pass == AuditPass.ForceScroll && webViewAttached) {
                        record.put(
                            "forceVerticalScroll",
                            evaluateForcedVerticalScroll(scenario, target, auditTabId),
                        )
                    } else if (pass == AuditPass.ForceScroll) {
                        record.put(
                            "forceVerticalScroll",
                            JSONObject().put("enabled", false).put("reason", WEBVIEW_NOT_ATTACHED),
                        )
                    }
                    writer.append(record.toString()).append('\n')
                    writer.flush()
                    instrumentation.sendStatus(
                        STATUS_PROGRESS,
                        Bundle().apply {
                            putInt("rank", target.rank)
                            putString("domain", target.domain)
                            putString("pass", pass.argument)
                        },
                    )
                }
            }
            assertEquals("Unsafe WebView layouts", 0, unsafeLayoutCount)
            assertEquals("Unmeasured WebView layouts", 0, unmeasuredLayoutCount)
        }

        assertTrue("Audit output was not written", outputFile.isFile && outputFile.length() > 0L)
    }

    private fun configurePass(
        scenario: ActivityScenario<MainActivity>,
        pass: AuditPass,
    ) {
        scenario.onActivity { activity ->
            activity.browserControllerForTesting().apply {
                updateBlockerSettings(pass.settings)
                if (pass == AuditPass.SafeArea) updateWebContentEdgeToEdgeEnabled(false)
            }
        }
        Thread.sleep(PASS_SETUP_MILLIS)
    }

    private fun prepareFreshAuditTab(
        scenario: ActivityScenario<MainActivity>,
        previousAuditTabId: String?,
    ): String = scenario.readActivity { activity ->
        val controller = activity.browserControllerForTesting()
        previousAuditTabId?.let(controller::closeTab)
        controller.createTab(isIncognito = true).also {
            WebViewCompat.addDocumentStartJavaScript(
                controller.selectedWebViewForTesting(),
                FRAME_PROBE_SCRIPT,
                setOf("*"),
            )
        }
    }.also {
        Thread.sleep(NEW_PROFILE_SETTLE_MILLIS)
    }

    private fun prepareSafeAreaAuditTab(
        scenario: ActivityScenario<MainActivity>,
    ): String {
        return scenario.readActivity { activity ->
            val controller = activity.browserControllerForTesting()
            controller.selectedTabId.also {
                WebViewCompat.addDocumentStartJavaScript(
                    controller.selectedWebViewForTesting(),
                    FRAME_PROBE_SCRIPT,
                    setOf("*"),
                )
            }
        }
    }

    private fun resetSafeAreaAuditTab(
        scenario: ActivityScenario<MainActivity>,
        auditTabId: String,
    ) = selectSafeAreaAuditTab(scenario, auditTabId, stopLoading = true)

    private fun selectSafeAreaAuditTab(
        scenario: ActivityScenario<MainActivity>,
        auditTabId: String,
        stopLoading: Boolean,
    ): Boolean {
        scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            controller.selectTab(auditTabId)
            controller.activeTabs
                .filter { tab -> tab.id != auditTabId }
                .map(BrowserTab::id)
                .forEach(controller::closeTab)
            if (stopLoading) controller.selectedWebViewForTesting().stopLoading()
        }
        return stopLoading || awaitAttachedWebView(scenario)
    }

    private fun awaitAttachedWebView(scenario: ActivityScenario<MainActivity>): Boolean {
        repeat(ATTACH_POLL_ATTEMPTS) {
            val attached = scenario.readActivity { activity ->
                activity.browserControllerForTesting().selectedWebViewForTesting().let { webView ->
                    webView.isAttachedToWindow && webView.width > 0 && webView.height > 0
                }
            }
            if (attached) return true
            Thread.sleep(POLL_MILLIS)
        }
        return false
    }

    private fun navigate(
        scenario: ActivityScenario<MainActivity>,
        target: AuditTarget,
    ) {
        scenario.onActivity { activity ->
            activity.browserControllerForTesting().submitAddress("https://${target.domain}/")
        }
    }

    private fun awaitPage(
        scenario: ActivityScenario<MainActivity>,
        target: AuditTarget,
        timeoutMillis: Long,
        auditTabId: String?,
    ): PageResult {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var state: PageState
        while (System.currentTimeMillis() < deadline) {
            state = scenario.pageState(auditTabId)
            val leftBlankPage = state.url != BLANK_URL && state.url.isNotBlank()
            if (leftBlankPage && (!state.isLoading || state.error != null)) {
                return PageResult.from(state, loadingTimedOut = false)
            }
            Thread.sleep(POLL_MILLIS)
        }
        scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            auditTabId?.let(controller::selectTab)
            controller.selectedWebViewForTesting().stopLoading()
        }
        state = scenario.pageState(auditTabId)
        return PageResult.from(
            state.copy(error = state.error ?: "timeout:${target.domain}"),
            loadingTimedOut = true,
        )
    }

    private fun evaluateProbe(scenario: ActivityScenario<MainActivity>): JSONObject? {
        return evaluateJson(scenario, DOM_PROBE_SCRIPT)
    }

    private fun evaluateJson(
        scenario: ActivityScenario<MainActivity>,
        script: String,
    ): JSONObject? {
        val rawResult = AtomicReference<String>()
        val evaluated = CountDownLatch(1)
        scenario.onActivity { activity ->
            val webView = activity.browserControllerForTesting().selectedWebViewForTesting()
            webView.evaluateJavascript(script) { value ->
                rawResult.set(value)
                evaluated.countDown()
            }
        }
        if (!evaluated.await(JS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) return null
        val raw = rawResult.get() ?: return null
        return runCatching {
            when (val value = JSONTokener(raw).nextValue()) {
                is JSONObject -> value
                is String -> JSONObject(value)
                else -> null
            }
        }.getOrNull()
    }

    private fun evaluateVerticalScroll(
        scenario: ActivityScenario<MainActivity>,
    ): JSONObject? {
        evaluateJson(scenario, SCROLL_RESET_SCRIPT) ?: return null
        Thread.sleep(SCROLL_SETTLE_MILLIS)
        val attempt = evaluateJson(scenario, SCROLL_ATTEMPT_SCRIPT) ?: return null
        Thread.sleep(SCROLL_SETTLE_MILLIS)
        val result = evaluateJson(scenario, SCROLL_RESULT_SCRIPT) ?: return null
        val webViewCanScrollDown = scenario.readActivity { activity ->
            activity.browserControllerForTesting().selectedWebViewForTesting()
                .canScrollVertically(1)
        }
        val maximum = attempt.optDouble("maximum", 0.0)
        val before = attempt.optDouble("before", 0.0)
        val after = result.optDouble("after", before)
        val applicable = maximum > MIN_SCROLL_RANGE_PX
        val windowMoved = after > before + MIN_SCROLL_MOVEMENT_PX
        return JSONObject()
            .put("applicable", applicable)
            .put("before", before)
            .put("after", after)
            .put("maximum", maximum)
            .put("windowMoved", windowMoved)
            .put("webViewCanScrollDown", webViewCanScrollDown)
            .put("worked", !applicable || windowMoved || webViewCanScrollDown)
    }

    private fun evaluateForcedVerticalScroll(
        scenario: ActivityScenario<MainActivity>,
        target: AuditTarget,
        auditTabId: String?,
    ): JSONObject {
        val enabled = scenario.readActivity { activity ->
            activity.browserControllerForTesting().let { controller ->
                controller.setForceVerticalScrolling(controller.selectedTabId, true)
            }
        }
        if (!enabled) {
            return JSONObject().put("enabled", false).put("reason", "override-not-applied")
        }
        val page = awaitPage(scenario, target, PAGE_TIMEOUT_MILLIS, auditTabId)
        Thread.sleep(SETTLE_MILLIS)
        return JSONObject()
            .put("enabled", true)
            .put("finalUrl", sanitizeUrl(page.url))
            .put("loadingTimedOut", page.loadingTimedOut)
            .put("mainFrameError", page.error ?: JSONObject.NULL)
            .put("probe", evaluateProbe(scenario) ?: JSONObject.NULL)
            .put("verticalScroll", evaluateVerticalScroll(scenario) ?: JSONObject.NULL)
    }

    private fun captureScreenshot(
        scenario: ActivityScenario<MainActivity>,
        root: File,
        target: AuditTarget,
    ): String? {
        val file = File(root, "%04d-%s.png".format(target.rank, target.domain.safeFileName()))
        val bitmap = AtomicReference<Bitmap>()
        scenario.onActivity { activity ->
            val webView = activity.browserControllerForTesting().selectedWebViewForTesting()
            if (webView.width <= 0 || webView.height <= 0) return@onActivity
            bitmap.set(
                Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888).also {
                    webView.draw(Canvas(it))
                },
            )
        }
        val captured = bitmap.get() ?: return null
        return runCatching {
            FileOutputStream(file).use { output ->
                check(captured.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            file.relativeTo(checkNotNull(root.parentFile?.parentFile)).path
        }.getOrNull().also { captured.recycle() }
    }

    private fun auditSafeAreaLayout(
        scenario: ActivityScenario<MainActivity>,
        scrollApplicable: Boolean,
    ): JSONObject {
        if (!awaitAttachedWebView(scenario)) {
            return JSONObject()
                .put("applicable", false)
                .put("reason", "webview-not-attached")
                .put("passed", JSONObject.NULL)
        }
        scenario.onActivity { activity ->
            activity.browserControllerForTesting().selectedWebViewForTesting().scrollTo(0, 0)
        }
        Thread.sleep(SAFE_AREA_SCROLL_RESET_SETTLE_MILLIS)
        val before = scenario.webViewGeometry()
        if (!scrollApplicable) {
            return JSONObject()
                .put("applicable", true)
                .put("safeTopPx", before.safeTopPx)
                .put("beforeWebViewTopPx", before.webViewTopPx)
                .put("afterWebViewTopPx", before.webViewTopPx)
                .put("scrollApplicable", false)
                .put("scroll", JSONObject.NULL)
                .put(
                    "passed",
                    before.webViewTopPx == before.safeTopPx && before.safeTopPx > 0,
                )
        }
        val scrollResult = AtomicReference<String>()
        val evaluated = CountDownLatch(1)
        scenario.onActivity { activity ->
            activity.browserControllerForTesting().selectedWebViewForTesting()
                .evaluateJavascript(
                    """
                        (() => {
                          document.documentElement.style.setProperty(
                            'scroll-behavior', 'auto', 'important'
                          );
                          document.documentElement.style.setProperty(
                            'overflow-y', 'visible', 'important'
                          );
                          document.body?.style.setProperty('overflow-y', 'visible', 'important');
                          if (document.body && !document.getElementById('__candy_safe_area_probe')) {
                            const spacer = document.createElement('div');
                            spacer.id = '__candy_safe_area_probe';
                            spacer.style.cssText =
                              'display:block!important;height:2000px!important;pointer-events:none!important';
                            document.body.appendChild(spacer);
                          }
                          const before = scrollY;
                          const maximum = Math.max(0, document.documentElement.scrollHeight - innerHeight);
                          scrollTo({top: Math.min(maximum, before + 1000), behavior: 'instant'});
                          return {before, after: scrollY, maximum};
                        })()
                    """.trimIndent(),
                ) { value ->
                    scrollResult.set(value)
                    evaluated.countDown()
                }
        }
        if (!evaluated.await(JS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return JSONObject()
                .put("applicable", false)
                .put("reason", "scroll-probe-timeout")
                .put("passed", JSONObject.NULL)
        }
        scenario.onActivity { activity ->
            activity.browserControllerForTesting().selectedWebViewForTesting()
                .scrollTo(0, SAFE_AREA_NATIVE_SCROLL_PX)
        }
        Thread.sleep(SAFE_AREA_SCROLL_SETTLE_MILLIS)
        val after = scenario.webViewGeometry()
        val scroll = runCatching { JSONObject(checkNotNull(scrollResult.get())) }.getOrNull()
        val javascriptScrolled = scroll?.let {
            it.optDouble("after", 0.0) > it.optDouble("before", 0.0) + 0.5
        } ?: false
        val nativeWebViewScrolled = after.webViewScrollYPx > before.webViewScrollYPx
        val passed =
            before.webViewTopPx == before.safeTopPx &&
                after.webViewTopPx == after.safeTopPx &&
                before.safeTopPx > 0 &&
                (javascriptScrolled || nativeWebViewScrolled)
        return JSONObject()
            .put("applicable", true)
            .put("safeTopPx", before.safeTopPx)
            .put("beforeWebViewTopPx", before.webViewTopPx)
            .put("afterWebViewTopPx", after.webViewTopPx)
            .put("beforeWebViewScrollYPx", before.webViewScrollYPx)
            .put("afterWebViewScrollYPx", after.webViewScrollYPx)
            .put("scrollApplicable", true)
            .put("scroll", scroll ?: JSONObject.NULL)
            .put("passed", passed)
    }

    private fun ActivityScenario<MainActivity>.webViewGeometry(): WebViewGeometry =
        readActivity { activity ->
            val webView = activity.browserControllerForTesting().selectedWebViewForTesting()
            val location = IntArray(2)
            webView.getLocationInWindow(location)
            val safeTop = ViewCompat.getRootWindowInsets(webView)
                ?.getInsets(SAFE_AREA_INSET_TYPES)
                ?.top
                ?: 0
            WebViewGeometry(
                safeTopPx = safeTop,
                webViewTopPx = location[1],
                webViewScrollYPx = webView.scrollY,
            )
        }

    private fun loadTargets(startRank: Int, siteCount: Int): List<AuditTarget> =
        loadAllTargets().asSequence()
            .filter { it.rank >= startRank }
            .take(siteCount)
            .toList()

    private fun loadTargets(ranks: List<Int>): List<AuditTarget> {
        val targetsByRank = loadAllTargets().associateBy(AuditTarget::rank)
        return ranks.mapNotNull(targetsByRank::get)
    }

    private fun loadAllTargets(): List<AuditTarget> =
        instrumentation.context.assets.open(TRANCO_ASSET).bufferedReader().useLines { lines ->
            lines.map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith('#') }
                .mapNotNull { line ->
                    val fields = line.split(',', limit = 2)
                    val rank = fields.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                    val domain = fields.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty)
                        ?: return@mapNotNull null
                    AuditTarget(rank, domain)
                }
                .toList()
        }

    private fun parseRequestedRanks(value: String?): List<Int> = value.orEmpty()
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 1..MAX_TRANCO_RANK }
        .distinct()
        .sorted()

    private fun isEmulator(): Boolean =
        android.os.Build.FINGERPRINT.startsWith("generic") ||
            android.os.Build.FINGERPRINT.contains("emulator") ||
            android.os.Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
            android.os.Build.HARDWARE.contains("ranchu", ignoreCase = true)

    private fun ActivityScenario<MainActivity>.pageState(tabId: String?): PageState =
        readActivity { activity ->
            val controller = activity.browserControllerForTesting()
            val tab = tabId
                ?.let { requestedId -> controller.activeTabs.firstOrNull { it.id == requestedId } }
                ?: controller.selectedTab
            PageState.from(tab)
        }

    private fun <T : Any> ActivityScenario<MainActivity>.readActivity(
        block: (MainActivity) -> T,
    ): T {
        val value = AtomicReference<T>()
        onActivity { activity -> value.set(block(activity)) }
        return checkNotNull(value.get())
    }

    private fun sanitizeUrl(value: String): String = runCatching {
        val uri = URI(value)
        URI(uri.scheme, uri.authority, uri.path, null, null).toString()
    }.getOrDefault(value.substringBefore('?').substringBefore('#'))

    private fun String.safeFileName(): String =
        lowercase().replace(Regex("[^a-z0-9.-]+"), "-").take(MAX_FILE_COMPONENT_LENGTH)

    private fun validateCandidateAssets() {
        val assets = instrumentation.targetContext.assets
        assets.open("candy_default_rules.txt").bufferedReader().use { reader ->
            BundledCandyRules.parse(reader.readText())
        }
    }

    private fun JSONObject.needsScreenshot(): Boolean =
        (optJSONObject("cookie")?.optInt("visibleCount", 0) ?: 0) > 0 ||
            (optJSONObject("ads")?.optInt("visibleCount", 0) ?: 0) > 0 ||
            optBoolean("challenge", false)

    private fun List<PrivacyDomainSummary>.toJson(): JSONArray = JSONArray().also { array ->
        forEach { domain ->
            array.put(
                JSONObject()
                    .put("host", domain.host)
                    .put("blockedCount", domain.blockedCount)
                    .put("category", domain.category.name.lowercase())
                    .put("party", domain.partyRelation.name.lowercase()),
            )
        }
    }

    private data class AuditTarget(val rank: Int, val domain: String)

    private data class WebViewGeometry(
        val safeTopPx: Int,
        val webViewTopPx: Int,
        val webViewScrollYPx: Int,
    )

    private data class PageState(
        val url: String,
        val title: String,
        val isLoading: Boolean,
        val error: String?,
    ) {
        companion object {
            fun from(tab: BrowserTab): PageState = PageState(
                url = tab.url,
                title = tab.title,
                isLoading = tab.isLoading,
                error = tab.error,
            )
        }
    }

    private data class PageResult(
        val url: String,
        val title: String,
        val error: String?,
        val loadingTimedOut: Boolean,
    ) {
        companion object {
            fun from(state: PageState, loadingTimedOut: Boolean) = PageResult(
                url = state.url,
                title = state.title,
                error = state.error,
                loadingTimedOut = loadingTimedOut,
            )
        }
    }

    private enum class AuditPass(
        val argument: String,
        val settings: BlockerSettings,
    ) {
        Baseline(
            argument = "baseline",
            settings = BlockerSettings(
                blockAdsAndTrackers = false,
                hideCookieConsent = false,
                blockThirdPartyCookies = false,
            ),
        ),
        Current(
            argument = "current",
            settings = BlockerSettings(),
        ),
        Candidate(
            argument = "candidate",
            settings = BlockerSettings(),
        ),
        ForceScroll(
            argument = "force-scroll",
            settings = BlockerSettings(),
        ),
        SafeArea(
            argument = "safe-area",
            settings = BlockerSettings(),
        );

        companion object {
            fun parse(value: String?): AuditPass = entries.firstOrNull { it.argument == value }
                ?: error(
                    "auditPass must be baseline, current, candidate, force-scroll, or safe-area",
                )
        }
    }

    private companion object {
        const val ARG_ENABLED = "candyAudit"
        const val ARG_PASS = "auditPass"
        const val ARG_BUILD_ID = "buildId"
        const val ARG_START_RANK = "startRank"
        const val ARG_SITE_COUNT = "siteCount"
        const val ARG_TARGET_RANKS = "targetRanks"
        const val ARG_CAPTURE_SCREENSHOTS = "captureScreenshots"
        const val AUDIT_DIRECTORY = "top-site-audit"
        const val AUDIT_LOCALE = "de-DE"
        const val TRANCO_ASSET = "tranco_PYG5J_top_10000.csv"
        const val BLANK_URL = "about:blank"
        const val WEBVIEW_NOT_ATTACHED = "webview-not-attached"
        const val DEFAULT_SITE_COUNT = 25
        const val ATTACH_POLL_ATTEMPTS = 100
        const val SAFE_AREA_SCROLL_SETTLE_MILLIS = 250L
        const val SAFE_AREA_SCROLL_RESET_SETTLE_MILLIS = 100L
        const val SAFE_AREA_NATIVE_SCROLL_PX = 1_000
        val SAFE_AREA_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        const val MAX_SITES_PER_RUN = 10_000
        const val MAX_TRANCO_RANK = 10_000
        const val MAX_TITLE_LENGTH = 200
        const val MAX_FILE_COMPONENT_LENGTH = 120
        const val PAGE_TIMEOUT_MILLIS = 12_000L
        const val SETTLE_MILLIS = 2_000L
        const val PASS_SETUP_MILLIS = 1_500L
        const val NEW_PROFILE_SETTLE_MILLIS = 250L
        const val POLL_MILLIS = 250L
        const val JS_TIMEOUT_SECONDS = 8L
        const val STATUS_PROGRESS = 2
        const val AUDIT_SCHEMA_VERSION = 2
        const val SCROLL_SETTLE_MILLIS = 250L
        const val MIN_SCROLL_RANGE_PX = 32.0
        const val MIN_SCROLL_MOVEMENT_PX = 1.0

        val SCROLL_RESET_SCRIPT = """
            (() => {
              scrollTo({top: 0, left: 0, behavior: 'instant'});
              return JSON.stringify({reset: true});
            })();
        """.trimIndent()

        val SCROLL_ATTEMPT_SCRIPT = """
            (() => {
              const root = document.scrollingElement || document.documentElement;
              const before = Number(root?.scrollTop || scrollY || 0);
              const height = Math.max(
                root?.scrollHeight || 0,
                document.documentElement?.scrollHeight || 0,
                document.body?.scrollHeight || 0
              );
              const maximum = Math.max(0, height - innerHeight);
              scrollTo({top: Math.min(maximum, 512), left: 0, behavior: 'instant'});
              return JSON.stringify({before, maximum});
            })();
        """.trimIndent()

        val SCROLL_RESULT_SCRIPT = """
            (() => {
              const root = document.scrollingElement || document.documentElement;
              return JSON.stringify({after: Number(root?.scrollTop || scrollY || 0)});
            })();
        """.trimIndent()

        val DOM_PROBE_SCRIPT = """
            (() => {
              const viewportArea = Math.max(1, innerWidth * innerHeight);
              const cookieWords = /(cookie|consent|privacy|datenschutz|einwilligung|zustimmen|akzeptieren|ablehnen|cookies|confidentialit[eé]|privacidad|consentement|соглас|куки|隐私|同意)/i;
              const cmpMarker = /(onetrust|cookiebot|didomi|usercentrics|sourcepoint|iubenda|osano|quantcast|cmplz|borlabs|axeptio|cmpbox|cookieyes|cky-|trustarc|consent-manager|fides)/i;
              const adMarker = /(^|[\s_-])(ad|ads|advert|advertising|banner-ad|sponsor|sponsored|werbung|publicit[eé])([\s_-]|$)/i;
              const adVendor = /(doubleclick|googlesyndication|googleadservices|amazon-adsystem|adnxs|criteo|taboola|outbrain|rubiconproject|pubmatic|adsrvr|adform|smartadserver)/i;
              const seenCookie = new Set();
              const seenAds = new Set();
              const cookieNodes = [];
              const adNodes = [];

              function details(el) {
                const style = getComputedStyle(el);
                const rect = el.getBoundingClientRect();
                const ownBox = style.display !== 'none' && style.visibility !== 'hidden' &&
                  Number(style.opacity || '1') > 0 && rect.width > 1 && rect.height > 1;
                const rendered = ownBox && (!el.checkVisibility || el.checkVisibility({
                  checkOpacity: true,
                  checkVisibilityCSS: true
                }));
                const inViewport = rendered && rect.bottom > 0 && rect.right > 0 &&
                  rect.top < innerHeight && rect.left < innerWidth;
                const id = (el.id || '').slice(0, 160);
                const classes = Array.from(el.classList || []).slice(0, 5);
                let selector = el.tagName.toLowerCase();
                if (id) selector = '#' + CSS.escape(id);
                else if (classes.length) selector += '.' + classes.map(value => CSS.escape(value)).join('.');
                let sourceHost = '';
                const source = el.currentSrc || el.src || el.getAttribute('src') || '';
                if (source) {
                  try { sourceHost = new URL(source, location.href).hostname.toLowerCase(); } catch (_) {}
                }
                return {
                  selector,
                  tag: el.tagName.toLowerCase(),
                  id,
                  classes,
                  role: (el.getAttribute('role') || '').slice(0, 80),
                  ariaLabel: (el.getAttribute('aria-label') || '').slice(0, 120),
                  text: (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 180),
                  sourceHost,
                  position: style.position,
                  zIndex: style.zIndex,
                  width: Math.round(rect.width),
                  height: Math.round(rect.height),
                  viewportShare: Number(((rect.width * rect.height) / viewportArea).toFixed(3)),
                  rendered,
                  inViewport
                };
              }

              function addCookie(el, known) {
                if (!el || seenCookie.has(el) || cookieNodes.length >= 20) return;
                seenCookie.add(el);
                const item = details(el);
                item.knownCmp = known;
                cookieNodes.push(item);
              }

              const knownCmpSelectors = [
                '#onetrust-consent-sdk', '#CybotCookiebotDialog', '#didomi-notice', '#didomi-host',
                '#usercentrics-root', '[data-testid="uc-default-wall"]', '[id^="sp_message_container_"]',
                '#iubenda-cs-banner', '.osano-cm-window', '.qc-cmp2-container', '.cmplz-cookiebanner',
                '#BorlabsCookieBox', '#axeptio_overlay', '[class^="axeptio_widget"]', '#cmpbox',
                '.cky-consent-container', '.cky-overlay'
              ];
              knownCmpSelectors.forEach(selector => {
                try { document.querySelectorAll(selector).forEach(el => addCookie(el, true)); } catch (_) {}
              });
              document.querySelectorAll('div,section,aside,dialog,form').forEach(el => {
                if (cookieNodes.length >= 20) return;
                const marker = [el.id, el.className, el.getAttribute('aria-label'), el.getAttribute('role')]
                  .filter(value => typeof value === 'string').join(' ');
                const style = getComputedStyle(el);
                const text = (el.innerText || el.textContent || '').slice(0, 1200);
                const floating = style.position === 'fixed' || style.position === 'sticky' || el.tagName === 'DIALOG';
                if (cmpMarker.test(marker) || (floating && cookieWords.test(text))) addCookie(el, false);
              });

              function addAd(el, evidence) {
                if (!el || seenAds.has(el) || adNodes.length >= 20) return;
                seenAds.add(el);
                const item = details(el);
                item.evidence = evidence;
                adNodes.push(item);
              }
              document.querySelectorAll('iframe,ins,img,div,aside,section').forEach(el => {
                if (adNodes.length >= 20) return;
                const marker = [el.id, el.className, el.getAttribute('aria-label'), el.getAttribute('data-ad-slot')]
                  .filter(value => typeof value === 'string').join(' ');
                const source = el.currentSrc || el.src || el.getAttribute('src') || '';
                const text = (el.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 240);
                if (adVendor.test(source)) addAd(el, 'vendor-host');
                else if (el.matches('ins.adsbygoogle,[data-ad-slot]')) addAd(el, 'ad-api');
                else if (adMarker.test(marker) && /^(IFRAME|INS|IMG|DIV|ASIDE|SECTION)$/.test(el.tagName)) {
                  addAd(el, cookieWords.test(text) ? 'marker-cookie-text' : 'marker');
                } else if (/^(ad|advertisement|werbung|sponsored)$/i.test(text)) {
                  addAd(el, 'label');
                }
              });

              const resourceCounts = {};
              performance.getEntriesByType('resource').forEach(entry => {
                try {
                  const host = new URL(entry.name).hostname.toLowerCase();
                  if (host && host !== location.hostname) resourceCounts[host] = (resourceCounts[host] || 0) + 1;
                } catch (_) {}
              });
              const resourceHosts = Object.entries(resourceCounts)
                .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
                .slice(0, 80)
                .map(([host, count]) => ({host, count}));
              const bodyText = (document.body?.innerText || '').replace(/\s+/g, ' ').trim();
              const now = Date.now();
              const frameReports = Object.values(window.__candyAuditFrameReports || {})
                .filter(frame => now - frame.reportedAt <= 2500)
                .map(frame => ({
                  host: frame.host,
                  cookieVisible: frame.cookieVisible,
                  adVisible: frame.adVisible
                }));
              const frameCookieVisible = frameReports.reduce(
                (total, frame) => total + Math.max(0, Number(frame.cookieVisible) || 0),
                0
              );
              const frameAdVisible = frameReports.reduce(
                (total, frame) => total + Math.max(0, Number(frame.adVisible) || 0),
                0
              );
              const challenge = /(captcha|verify you are human|checking your browser|access denied|unusual traffic|robot check)/i
                .test((document.title || '') + ' ' + bodyText.slice(0, 2000));
              return JSON.stringify({
                contentType: document.contentType || '',
                html: /html/i.test(document.contentType || '') && !!document.documentElement,
                textLength: bodyText.length,
                challenge,
                scrollLocked: [document.documentElement, document.body].filter(Boolean).some(element => {
                  const style = getComputedStyle(element);
                  return ['hidden', 'clip'].includes(style.overflow) ||
                    ['hidden', 'clip'].includes(style.overflowY);
                }),
                viewport: {width: innerWidth, height: innerHeight, devicePixelRatio},
                documentHeight: Math.max(document.documentElement.scrollHeight, document.body?.scrollHeight || 0),
                cookie: {
                  detectedCount: cookieNodes.length + frameCookieVisible,
                  visibleCount: cookieNodes.filter(item => item.rendered).length + frameCookieVisible,
                  viewportCount: cookieNodes.filter(item => item.inViewport).length + frameCookieVisible,
                  nodes: cookieNodes
                },
                ads: {
                  detectedCount: adNodes.length + frameAdVisible,
                  visibleCount: adNodes.filter(item => item.rendered).length + frameAdVisible,
                  viewportCount: adNodes.filter(item => item.inViewport).length + frameAdVisible,
                  nodes: adNodes
                },
                frameReports,
                resourceHosts
              });
            })();
        """.trimIndent()

        val FRAME_PROBE_SCRIPT = """
            (() => {
              const marker = 'candy-audit-frame-v1';
              if (window.top === window) {
                window.__candyAuditFrameReports = Object.create(null);
                addEventListener('message', event => {
                  const report = event.data;
                  if (!report || report.marker !== marker ||
                      typeof report.frameKey !== 'string' ||
                      typeof report.host !== 'string') return;
                  if (report.removed === true) {
                    delete window.__candyAuditFrameReports[report.frameKey];
                    return;
                  }
                  window.__candyAuditFrameReports[report.frameKey] = {
                    host: report.host.slice(0, 253),
                    cookieVisible: Math.min(20, Math.max(0, Number(report.cookieVisible) || 0)),
                    adVisible: Math.min(20, Math.max(0, Number(report.adVisible) || 0)),
                    reportedAt: Date.now()
                  };
                });
                return;
              }

              const frameKey = location.hostname + ':' + Math.random().toString(36).slice(2);
              const visible = element => {
                const style = getComputedStyle(element);
                const rect = element.getBoundingClientRect();
                return style.display !== 'none' && style.visibility !== 'hidden' &&
                  Number(style.opacity || '1') > 0 && rect.width > 1 && rect.height > 1;
              };
              const safeCount = selector => {
                try { return Array.from(document.querySelectorAll(selector)).filter(visible).length; }
                catch (_) { return 0; }
              };
              const report = () => {
                const knownCmp = [
                  '#onetrust-consent-sdk', '#CybotCookiebotDialog', '#didomi-notice',
                  '#usercentrics-root', '[id^="sp_message_container_"]', '#iubenda-cs-banner',
                  '.osano-cm-window', '.qc-cmp2-container', '.cmplz-cookiebanner',
                  '#fides-banner-container', '#fides-overlay', '.fides-modal-overlay',
                  '#BorlabsCookieBox', '#axeptio_overlay', '#cmpbox',
                  '.cky-consent-container', '.cky-overlay'
                ].reduce((total, selector) => total + safeCount(selector), 0);
                const cookieWords = /(cookie|consent|privacy|datenschutz|einwilligung|akzeptieren|ablehnen)/i;
                const heuristicCmp = knownCmp ? 0 : Array.from(
                  document.querySelectorAll('div,section,aside,dialog,form')
                ).filter(element => {
                  if (!visible(element)) return false;
                  const style = getComputedStyle(element);
                  if (style.position !== 'fixed' && style.position !== 'sticky' &&
                      element.tagName !== 'DIALOG') return false;
                  return cookieWords.test((element.innerText || element.textContent || '').slice(0, 1200));
                }).slice(0, 20).length;
                const adVisible = safeCount(
                  'ins.adsbygoogle,[data-ad-slot],[data-ad-element="outer_ad_container"],' +
                  '[data-testid="StandardAd"],iframe[src*="doubleclick"],' +
                  'iframe[src*="googlesyndication"],iframe[src*="amazon-adsystem"]'
                );
                top.postMessage({
                  marker,
                  frameKey,
                  host: location.hostname.toLowerCase(),
                  cookieVisible: Math.min(20, knownCmp + heuristicCmp),
                  adVisible: Math.min(20, adVisible)
                }, '*');
              };
              const removeReport = () => top.postMessage({
                marker,
                frameKey,
                host: location.hostname.toLowerCase(),
                removed: true
              }, '*');
              let queued = false;
              const schedule = () => {
                if (queued) return;
                queued = true;
                setTimeout(() => {
                  queued = false;
                  report();
                }, 100);
              };
              const observer = new MutationObserver(schedule);
              observer.observe(document, { childList: true, subtree: true });
              if (document.readyState === 'loading') {
                addEventListener('DOMContentLoaded', report, { once: true });
              } else {
                report();
              }
              setTimeout(report, 500);
              setTimeout(report, 1500);
              const interval = setInterval(report, 1000);
              setTimeout(() => {
                clearInterval(interval);
                observer.disconnect();
              }, 15000);
              addEventListener('pagehide', () => {
                clearInterval(interval);
                observer.disconnect();
                removeReport();
              }, { once: true });
            })();
        """.trimIndent()
    }
}
