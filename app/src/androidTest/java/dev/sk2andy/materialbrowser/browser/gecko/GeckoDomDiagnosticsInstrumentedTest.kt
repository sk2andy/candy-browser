package dev.sk2andy.materialbrowser.browser.gecko

import android.os.SystemClock
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoDomDiagnosticsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() = assumeTrue(BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS)

    @Test
    fun pendingAndReadyResultsDisappearAcrossPrivateNavigationDeactivationAndClosure() {
        val owner = Any()
        val privateOwner = Any()
        var callback: ((String?) -> Unit)? = null
        var cancellations = 0
        instrumentation.runOnMainSync {
            GeckoDomDiagnostics.registerSession(owner, false)
            GeckoDomDiagnostics.bindProbe(owner, cancel = { cancellations++ }) { callback = it }
            GeckoDomDiagnostics.setActive(owner, true)
            try {
                assertEquals("pending", GeckoDomDiagnostics.request())
                assertEquals("busy", GeckoDomDiagnostics.request())
                val stale = requireNotNull(callback)
                GeckoDomDiagnostics.navigationChanged(owner)
                stale("{}")
                assertNull(GeckoDomDiagnostics.snapshot.payload)
                GeckoDomDiagnostics.request()
                requireNotNull(callback)("{}")
                assertEquals("ready", GeckoDomDiagnostics.snapshot.status)
                GeckoDomDiagnostics.registerSession(privateOwner, true)
                assertTrue(cancellations >= 3)
                assertEquals("private_session", GeckoDomDiagnostics.request())
                assertNull(GeckoDomDiagnostics.snapshot.payload)
                GeckoDomDiagnostics.unregisterSession(privateOwner)
                GeckoDomDiagnostics.request()
                val inactive = requireNotNull(callback)
                GeckoDomDiagnostics.setActive(owner, false)
                inactive("{}")
                assertNull(GeckoDomDiagnostics.snapshot.payload)
                assertEquals("target_unavailable", GeckoDomDiagnostics.request())
                GeckoDomDiagnostics.setActive(owner, true)
                GeckoDomDiagnostics.request()
                val closed = requireNotNull(callback)
                GeckoDomDiagnostics.unregisterSession(owner)
                closed("{}")
                assertNull(GeckoDomDiagnostics.snapshot.payload)
            } finally {
                GeckoDomDiagnostics.unregisterSession(owner)
                GeckoDomDiagnostics.unregisterSession(privateOwner)
            }
        }
    }

    @Test
    fun nativeBoundaryDropsUntrustedMetadataAndBoundsCandidates() {
        val raw = JSONObject()
            .put("version", 1).put("env", JSONObject().put("top", "secret"))
            .put("viewport", JSONObject()).put("html", JSONObject().put("tag", "secret").put("text", "secret"))
            .put("candidates", JSONArray()).put("url", "secret").put("darkPreferred", "true")
        val result = JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw)))
        assertFalse(result.toString().contains("secret"))
        assertTrue(result.getJSONObject("env").isNull("top"))
        assertFalse(result.getBoolean("darkPreferred"))
        raw.put("candidates", JSONArray().apply { repeat(17) { put(JSONObject()) } })
        assertNull(GeckoDomProbePayload.sanitize(raw))
    }

    @Test
    fun manualHitAndMutationMetadataStayBoundedAndDropAuthorValues() {
        val geometry = JSONObject().put("tag", "BUTTON").put("pointerEvents", "none").put("inert", "true")
        val diagnostics = JSONObject().put("lastEligibleMutationKind", "attributes")
            .put("lastQueuedMutationRootKind", "semantic-control")
        val keys = listOf(
            "eligibleAttributeRecordCount", "eligibleAdditionRecordCount", "acceptedImmediateMutationCount",
            "queuedMutationRootCount", "queuedSemanticRootCount",
        )
        for (key in keys) diagnostics.put(key, 65535)
        val raw = JSONObject().put("version", 1).put("env", JSONObject()).put("viewport", JSONObject())
            .put("html", geometry).put("candidates", JSONArray().put(geometry)).put("topRightCandidateIndex", 0)
            .put("cssSafeAreaDiagnostics", diagnostics)
        val valid = JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw)))
        assertEquals(0, valid.getInt("topRightCandidateIndex"))
        assertEquals("none", valid.getJSONObject("html").getString("pointerEvents"))
        assertFalse(valid.getJSONObject("html").getBoolean("inert"))
        for (key in keys) assertEquals(65535, valid.getJSONObject("cssSafeAreaDiagnostics").getInt(key))
        assertEquals("attributes", valid.getJSONObject("cssSafeAreaDiagnostics").getString("lastEligibleMutationKind"))
        for (invalid in listOf(-1, 0.5, "0", 65536, 1e200)) {
            raw.put("topRightCandidateIndex", invalid)
            for (key in keys) diagnostics.put(key, invalid)
            diagnostics.put("lastEligibleMutationKind", "secret-author-label")
            diagnostics.put("lastQueuedMutationRootKind", "secret-author-root")
            geometry.put("pointerEvents", "secret-author-pointer")
            val normalized = JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw)))
            assertTrue(normalized.isNull("topRightCandidateIndex"))
            for (key in keys) assertTrue(normalized.getJSONObject("cssSafeAreaDiagnostics").isNull(key))
            assertFalse(normalized.toString().contains("secret-author"))
        }
    }

    @Test
    fun stylesheetSourceCountersStayBoundedAndExcludePageMetadata() {
        val keys = listOf(
            "cssSourceCount", "cssLateSourceCount", "cssRulesVisited", "cssRulesApplied",
            "cssSecurityErrors", "cssUnsupportedRules", "cssBudgetHits", "cssScrollCancellations",
        )
        val diagnostics = JSONObject().put("stylesheetUrl", "secret-author-url")
            .put("selector", "secret-author-selector").put("cssText", "secret-author-css")
        val raw = JSONObject().put("version", 1).put("env", JSONObject()).put("viewport", JSONObject())
            .put("html", JSONObject()).put("candidates", JSONArray()).put("cssSafeAreaDiagnostics", diagnostics)
        for (value in listOf(0, 65535)) {
            for (key in keys) diagnostics.put(key, value)
            val normalized = JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw)))
            assertFalse(normalized.toString().contains("secret-author"))
            for (key in keys) assertEquals(value, normalized.getJSONObject("cssSafeAreaDiagnostics").getInt(key))
        }
        for (invalid in listOf(-1, 0.5, "0", 65536, 1e200, JSONObject.NULL)) {
            for (key in keys) diagnostics.put(key, invalid)
            val normalized = JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw)))
            assertFalse(normalized.toString().contains("secret-author"))
            for (key in keys) assertTrue(normalized.getJSONObject("cssSafeAreaDiagnostics").isNull(key))
        }
        for (key in keys) diagnostics.remove(key)
        val normalized = requireNotNull(GeckoDomProbePayload.sanitize(raw))
        assertTrue(normalized.length <= GeckoDomDiagnosticsRules.MAX_PAYLOAD_CHARS)
        for (key in keys) assertTrue(JSONObject(normalized).getJSONObject("cssSafeAreaDiagnostics").isNull(key))
    }

    @Test
    fun ownerDiscoveryMetadataAllowsOnlyFixedFocusLabelsAndBoundedCounts() {
        val diagnostics = JSONObject().put("author", "secret-author-value")
        val keys = listOf("visitedSemanticCount", "discoveryRectCount", "ownerQueuedCount")
        val raw = JSONObject().put("version", 1).put("env", JSONObject()).put("viewport", JSONObject())
            .put("html", JSONObject()).put("candidates", JSONArray()).put("cssSafeAreaDiagnostics", diagnostics)
        assertFalse(JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw))).has("activeElementTag"))
        for (tag in listOf("HTML", "BODY", "HEADER", "NAV", "MAIN", "DIV", "BUTTON", "A", "FORM", "INPUT", "SPAN", "IFRAME", "TEXTAREA", "SELECT", "NONE", "OTHER")) {
            raw.put("activeElementTag", tag)
            assertEquals(tag, JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw))).getString("activeElementTag"))
        }
        raw.put("activeElementTag", "secret-author-custom-tag")
        for (relation in listOf("not-semantic", "unrelated", "related")) {
            diagnostics.put("lastMutationFocusRelation", relation)
            for (key in keys) diagnostics.put(key, 65535)
            val valid = JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw)))
            assertEquals("other", valid.getString("activeElementTag"))
            assertFalse(valid.toString().contains("secret-author"))
            assertEquals(relation, valid.getJSONObject("cssSafeAreaDiagnostics").getString("lastMutationFocusRelation"))
            for (key in keys) assertEquals(65535, valid.getJSONObject("cssSafeAreaDiagnostics").getInt(key))
        }
        diagnostics.put("lastMutationFocusRelation", "secret-author-relation")
        for (invalid in listOf(-1, 0.5, "0", 65536, 1e200, JSONObject.NULL)) {
            for (key in keys) diagnostics.put(key, invalid)
            val normalized = JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw)))
            assertFalse(normalized.toString().contains("secret-author"))
            assertEquals("other", normalized.getJSONObject("cssSafeAreaDiagnostics").getString("lastMutationFocusRelation"))
            for (key in keys) assertTrue(normalized.getJSONObject("cssSafeAreaDiagnostics").isNull(key))
        }
    }

    @Test
    fun footprintDiagnosticsKeepFixedReasonsAndIndependentAttemptLimits() {
        val limits = listOf("lastFootprintNodeCount" to 513, "lastFootprintStyleCount" to 65, "lastFootprintControlCount" to 32)
        val diagnostics = JSONObject().put("author", JSONObject().put("text", "secret-author-value"))
        val raw = JSONObject().put("version", 1).put("env", JSONObject()).put("viewport", JSONObject())
            .put("html", JSONObject()).put("candidates", JSONArray()).put("cssSafeAreaDiagnostics", diagnostics)
        for (reason in listOf("not-scanned", "complete", "opaque-panel", "time-budget", "node-budget", "style-budget", "opaque-descendant", "closed-shadow", "center-unproven", "footprint-budget")) {
            diagnostics.put("lastFootprintRejectionReason", reason)
            for ((key, maximum) in limits) diagnostics.put(key, maximum)
            val valid = JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw)))
            assertFalse(valid.toString().contains("secret-author"))
            val normalized = valid.getJSONObject("cssSafeAreaDiagnostics")
            assertEquals(reason, normalized.getString("lastFootprintRejectionReason"))
            for ((key, maximum) in limits) assertEquals(maximum, normalized.getInt(key))
        }
        diagnostics.put("lastFootprintRejectionReason", "secret-author-custom-reason")
        for ((key, maximum) in limits) {
            diagnostics.put(key, 0)
            assertEquals(0, JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw)))
                .getJSONObject("cssSafeAreaDiagnostics").getInt(key))
            for (invalid in listOf(-1, 0.5, "0", maximum + 1, 1e200, JSONObject.NULL)) {
                diagnostics.put(key, invalid)
                val normalized = JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw)))
                assertFalse(normalized.toString().contains("secret-author"))
                assertEquals("other", normalized.getJSONObject("cssSafeAreaDiagnostics").getString("lastFootprintRejectionReason"))
                assertTrue(normalized.getJSONObject("cssSafeAreaDiagnostics").isNull(key))
            }
            diagnostics.remove(key)
        }
        val old = JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw))).getJSONObject("cssSafeAreaDiagnostics")
        for ((key, _) in limits) assertTrue(old.isNull(key))
        assertTrue(requireNotNull(GeckoDomProbePayload.sanitize(raw)).length <= GeckoDomDiagnosticsRules.MAX_PAYLOAD_CHARS)
    }

    @Test
    fun optionalDiagnosticsDropAuthorMetadataAndStrictlyNormalizeEnumsNumbersAndBooleans() {
        val geometry = JSONObject().put("tag", "IFRAME").put("position", "secret-author-position")
            .put("overflowX", "secret-author-overflow").put("overflowY", "clip")
            .put("x", "NaN").put("y", 1e200).put("width", 10_000_000).put("height", "12")
            .put("hasMovingEffects", "true").put("hasContainingBlockEffects", true)
            .put("hasAnimationEffects", "true").put("hasTransitionEffects", true)
            .put("transitionPropertyKind", "secret-author-property")
            .put("src", "secret-author-url").put("text", "secret-author-text")
        val raw = JSONObject().put("version", 1).put("env", JSONObject()).put("viewport", JSONObject())
            .put("html", geometry).put("candidates", JSONArray()).put("flowStart", JSONArray().put(geometry))
            .put("readyState", "secret-author-ready")
            .put("cssSafeArea", JSONObject().put("available", true).put("ready", "true").put("enabled", true)
                .put("insetPx", "156").put("author", "secret-author-config"))
            .put("cssSafeAreaDiagnostics", JSONObject().put("active", "true").put("initialized", true)
                .put("ownedCount", 1025).put("unknownCount", 1.5).put("firstAtMillis", -1)
                .put("pendingJobCount", 33).put("dirtyRootCount", "1").put("interactionActive", "true")
                .put("immediateMutationPending", true)
                .put("positionedClassifyCount", 65536).put("lastPositionedDecision", "secret-author-guard")
                .put("firstReadyState", "secret-author-state").put("lastBodyDecision", "applied")
                .put("lastPanelDecision", "secret-author-decision").put("lastAbsoluteDecision", "owner-guard-rejected"))
        val result = JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw)))
        assertFalse(result.toString().contains("secret-author"))
        assertEquals("other", result.getString("readyState"))
        val flow = result.getJSONArray("flowStart").getJSONObject(0)
        assertEquals("IFRAME", flow.getString("tag"))
        assertEquals("other", flow.getString("overflowX"))
        assertEquals("clip", flow.getString("overflowY"))
        assertTrue(flow.isNull("x"))
        assertTrue(flow.isNull("y"))
        assertTrue(flow.isNull("height"))
        assertEquals(10_000_000.0, flow.getDouble("width"), 0.0)
        assertFalse(flow.getBoolean("hasMovingEffects"))
        assertTrue(flow.getBoolean("hasContainingBlockEffects"))
        assertFalse(flow.getBoolean("hasAnimationEffects"))
        assertTrue(flow.getBoolean("hasTransitionEffects"))
        assertEquals("other", flow.getString("transitionPropertyKind"))
        val configuration = result.getJSONObject("cssSafeArea")
        assertFalse(configuration.getBoolean("ready"))
        assertTrue(configuration.isNull("insetPx"))
        val diagnostics = result.getJSONObject("cssSafeAreaDiagnostics")
        assertFalse(diagnostics.getBoolean("active"))
        assertTrue(diagnostics.getBoolean("initialized"))
        for (key in listOf("ownedCount", "unknownCount", "firstAtMillis", "pendingJobCount", "dirtyRootCount")) {
            assertTrue(diagnostics.isNull(key))
        }
        assertFalse(diagnostics.getBoolean("interactionActive"))
        assertTrue(diagnostics.getBoolean("immediateMutationPending"))
        assertTrue(diagnostics.isNull("positionedClassifyCount"))
        assertEquals("other", diagnostics.getString("lastPositionedDecision"))
        assertEquals("other", diagnostics.getString("lastPanelDecision"))
        assertEquals("applied", diagnostics.getString("lastBodyDecision"))
    }

    @Test
    fun optionalFlowShapeBoundsAndConfigurationInsetKeepVersionOnePayloadCap() {
        val raw = JSONObject().put("version", 1).put("env", JSONObject()).put("viewport", JSONObject())
            .put("html", JSONObject()).put("candidates", JSONArray())
        assertTrue(JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw))).isNull("flowStart"))
        raw.put("cssSafeArea", JSONObject().put("insetPx", 10_000))
        raw.put("flowStart", JSONArray().apply { repeat(8) { put(JSONObject().put("tag", "SELECT")) } })
        assertEquals(8, JSONObject(requireNotNull(GeckoDomProbePayload.sanitize(raw))).getJSONArray("flowStart").length())
        raw.put("flowStart", JSONArray().apply { repeat(9) { put(JSONObject()) } })
        assertNull(GeckoDomProbePayload.sanitize(raw))
        raw.put("flowStart", JSONArray().put("secret-not-geometry"))
        assertNull(GeckoDomProbePayload.sanitize(raw))
        raw.put("flowStart", "secret-not-array")
        assertNull(GeckoDomProbePayload.sanitize(raw))
        raw.put("flowStart", JSONArray()).put("cssSafeArea", "secret-not-object")
        assertNull(GeckoDomProbePayload.sanitize(raw))
        raw.put("cssSafeArea", JSONObject().put("insetPx", 10_001)).put("secret", "x".repeat(25 * 1024))
        val sanitized = requireNotNull(GeckoDomProbePayload.sanitize(raw))
        assertTrue(sanitized.length <= GeckoDomDiagnosticsRules.MAX_PAYLOAD_CHARS)
        assertTrue(JSONObject(sanitized).getJSONObject("cssSafeArea").isNull("insetPx"))
        assertFalse(JSONObject(sanitized).has("secret"))
        raw.put("version", 2)
        assertNull(GeckoDomProbePayload.sanitize(raw))
    }

    @Test
    fun manualProbeReadsRealGeckoEnvWithoutMovingUnawareHeader() {
        val settled = AtomicBoolean(false)
        val authorInset = AtomicReference<Double?>(null)
        EdgeToEdgeSiteFixtureServer { target ->
            if (target.startsWith("/site-matrix/dom-probe")) HTML else "<!doctype html><title>Resource</title>"
        }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "dom-probe-${UUID.randomUUID()}", isPrivate = false,
                        privacyPolicy = GeckoPrivacyPolicy.Disabled,
                    )
                    session.bindExtensionTab("dom-probe-${UUID.randomUUID()}", 1)
                    session.setStateListener { state ->
                        settled.set(!state.isLoading && state.title?.startsWith("DOM probe fixture") == true)
                        authorInset.set(state.title?.removePrefix("DOM probe fixture ")?.toDoubleOrNull())
                    }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    (view as GeckoViewInsetHost).updateInsets(
                        GeckoViewInsetRules.resolve(GeckoViewInsets.Zero, false, false, false, false),
                        WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.statusBars(), Insets.NONE).build(),
                    )
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/dom-probe")))
                }
                try {
                    await { settled.get() }
                    scenario.onActivity {
                        (view as GeckoViewInsetHost).updateInsets(
                            GeckoViewInsetRules.resolve(GeckoViewInsets(0, 144, 0, 0), false, false, false, false),
                            WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 144, 0, 0)).build(),
                        )
                    }
                    await { authorInset.get()?.let { kotlin.math.abs(it - 144) <= 0.5 } == true }
                    scenario.onActivity { GeckoDomDiagnostics.request() }
                    await { GeckoDomDiagnostics.snapshot.status != "pending" }
                    assertEquals("ready", GeckoDomDiagnostics.snapshot.status)
                    val report = JSONObject(requireNotNull(GeckoDomDiagnostics.snapshot.payload))
                    assertEquals(144.0, report.getJSONObject("env").getDouble("top") * report.getJSONObject("viewport").getDouble("density"), 0.5)
                    assertEquals(144, report.getJSONObject("native").getInt("topPx"))
                    assertEquals(0, report.getJSONObject("native").getInt("marginTopPx"))
                    val candidates = report.getJSONArray("candidates")
                    val header = (0 until candidates.length()).map { candidates.getJSONObject(it) }.first { it.getString("tag") == "HEADER" }
                    assertEquals("fixed", header.getString("position"))
                    assertEquals(0.0, header.getDouble("y"), 0.5)
                    assertEquals(0.0, header.getDouble("paddingTop"), 0.5)
                    scenario.onActivity { session.setActive(false) }
                    assertNull(GeckoDomDiagnostics.snapshot.payload)
                } finally {
                    scenario.onActivity {
                        session.releaseView(view)
                        session.close()
                    }
                }
            }
        }
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("DOM probe timed out; status=${GeckoDomDiagnostics.snapshot.status}")
    }

    private companion object {
        val HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <title>DOM probe fixture</title><style>html,body{margin:0;min-height:2000px}header{position:fixed;top:0;left:0;right:0;height:100px}</style>
            <body><header>Unaware header</header><main>Fixture content</main>
            <div id="env" style="position:fixed;left:-10000px;padding-top:env(safe-area-inset-top)"></div>
            <script>setInterval(()=>{document.title='DOM probe fixture '+(parseFloat(getComputedStyle(document.getElementById('env')).paddingTop)*devicePixelRatio)},100)</script>
            </body>
        """.trimIndent()
    }
}
