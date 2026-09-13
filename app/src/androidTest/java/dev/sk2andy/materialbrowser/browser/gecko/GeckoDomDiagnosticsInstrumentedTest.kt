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
