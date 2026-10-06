package dev.sk2andy.materialbrowser.browser.gecko

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
import java.net.URI
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
    fun knownHeaderTracksUntrustedInlineAndAncestorTopChangesAcrossScroll() {
        val title = AtomicReference<String?>(null)
        val policy = GeckoPrivacyPolicy.Disabled.copy(cssSafeAreaTopInsetPx = NATIVE_TOP_PX)
        EdgeToEdgeSiteFixtureServer { _ -> KNOWN_HEADER_TOP_MUTATION_HTML }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-known-header-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = policy,
                    )
                    session.bindExtensionTab("safe-area-known-header-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/known-header-top-mutation")))
                }
                try {
                    awaitReport(title) { it.getBoolean("loaded") }
                    scenario.onActivity { updateNativeTop(view) }
                    val updated = awaitReport(title) {
                        it.getInt("stage") == 3 && abs(it.getDouble("top") - it.getDouble("env") - 16) < 0.5
                    }
                    val safeTop = NATIVE_TOP_PX / updated.getDouble("density")
                    assertEquals(safeTop + 64, updated.getDouble("initialTop"), 0.5)
                    assertEquals(safeTop + 64, updated.getDouble("initialY"), 0.5)
                    assertEquals("Normal inline resets use the current author top", safeTop, updated.getDouble("resetTop"), 0.5)
                    assertEquals(safeTop, updated.getDouble("resetY"), 0.5)
                    assertEquals("Ancestor class changes receive exactly one inset", safeTop + 16, updated.getDouble("top"), 0.5)
                    assertEquals(safeTop + 16, updated.getDouble("y"), 0.5)
                    assertEquals("", updated.getString("authorTop"))
                    assertEquals(0, updated.getInt("trustedClicks"))

                    scenario.onActivity { session.scrollToVerticalOffset(600) }
                    val hidden = awaitReport(title) {
                        it.getBoolean("hidden") && it.getDouble("scroll") > 80 && abs(it.getDouble("top") + 64) < 0.5
                    }
                    assertEquals("Negative scroll states keep their authored position", -64.0, hidden.getDouble("y"), 0.5)
                    assertEquals(0, hidden.getInt("trustedClicks"))

                    scenario.onActivity { session.scrollToVerticalOffset(0) }
                    val restored = awaitReport(title) {
                        !it.getBoolean("hidden") && it.getDouble("scroll") < 1 &&
                            abs(it.getDouble("top") - safeTop - 16) < 0.5
                    }
                    assertEquals("Returning from a negative state restores one inset", safeTop + 16, restored.getDouble("y"), 0.5)
                    assertEquals("", restored.getString("authorTop"))
                    assertEquals(0, restored.getInt("trustedClicks"))
                    println("Prototype known header top changes: $restored")
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

    @Test
    fun coverHeaderKeepsItsGeometryAcrossRepeatedClassChanges() {
        val title = AtomicReference<String?>(null)
        val policy = GeckoPrivacyPolicy.Disabled.copy(
            cssSafeAreaTopInsetPx = NATIVE_TOP_PX,
            geckoSafeAreaSettings = GeckoSafeAreaSettings(maxElementsPerBatch = 4, maxBatchDurationMillis = 1),
        )
        EdgeToEdgeSiteFixtureServer { _ -> COVER_HEADER_MUTATION_HTML }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-cover-mutation-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = policy,
                    )
                    session.bindExtensionTab("safe-area-cover-mutation-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/cover-header-mutation")))
                }
                try {
                    awaitReport(title) { it.getBoolean("loaded") }
                    scenario.onActivity { updateNativeTop(view) }
                    val report = awaitReport(title) { it.getBoolean("done") }
                    val safeTop = NATIVE_TOP_PX / report.getDouble("density")
                    assertEquals(5, report.getInt("mutations"))
                    assertEquals("Cover rechecks keep the existing protection stylesheet", 0, report.getInt("layerRemovals"))
                    assertTrue("Every frame through each cover recheck is sampled", report.getInt("samples") >= 30)
                    assertEquals("Cover header never loses its inset", safeTop, report.getDouble("minTop"), 0.5)
                    assertEquals("Cover header never gets a second inset", safeTop, report.getDouble("maxTop"), 0.5)
                    assertEquals("Class changes do not scroll the page", 0.0, report.getDouble("maxScroll"), 0.5)
                    println("Prototype cover header mutations: $report")
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

    @Test
    fun coverBodyScrollLockKeepsStickyHeaderBelowTheSafeAreaOnEveryClick() {
        val title = AtomicReference<String?>(null)
        val policy = GeckoPrivacyPolicy.Disabled.copy(
            cssSafeAreaTopInsetPx = NATIVE_TOP_PX,
            geckoSafeAreaSettings = GeckoSafeAreaSettings(maxElementsPerBatch = 4, maxBatchDurationMillis = 1),
        )
        EdgeToEdgeSiteFixtureServer { _ -> COVER_BODY_SCROLL_LOCK_HTML }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-cover-scroll-lock-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = policy,
                    )
                    session.bindExtensionTab("safe-area-cover-scroll-lock-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/cover-body-scroll-lock")))
                }
                try {
                    awaitReport(title) { it.getBoolean("loaded") }
                    scenario.onActivity { updateNativeTop(view) }
                    var report = awaitReport(title) { it.getBoolean("started") }
                    for (click in 1..4) {
                        tapButton(scenario, view, report)
                        report = awaitReport(title) { it.getInt("settledClicks") >= click }
                    }
                    val safeTop = NATIVE_TOP_PX / report.getDouble("density")
                    println("Prototype cover body scroll lock: $report")
                    assertEquals(4, report.getInt("trustedClicks"))
                    assertTrue("Both scroll-lock openings are sampled before and after worker settlement", report.getInt("fixedSamples") >= 20)
                    assertEquals("The first scroll lock never drops the sticky header", safeTop, report.getDouble("minTop"), 0.5)
                    assertEquals("Repeated scroll locks never duplicate the header inset", safeTop, report.getDouble("maxTop"), 0.5)
                    assertEquals("SVG header content does not acquire body padding", 0.0, report.getDouble("maxPadding"), 0.5)
                    assertEquals("Opening and closing the menu does not scroll the document", 0.0, report.getDouble("maxScroll"), 0.5)
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

    @Test
    fun negativeTopStatesStayHiddenAndDeveloperSettingRestoresTheInsetLive() {
        val title = AtomicReference<String?>(null)
        val policy = GeckoPrivacyPolicy.Disabled.copy(cssSafeAreaTopInsetPx = NATIVE_TOP_PX)
        EdgeToEdgeSiteFixtureServer { _ -> NEGATIVE_TOP_HTML }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-negative-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = policy.copy(geckoSafeAreaSettings = GeckoSafeAreaSettings(enabled = false)),
                    )
                    session.bindExtensionTab("safe-area-negative-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/negative-top")))
                }
                try {
                    awaitReport(title) { it.getBoolean("loaded") }
                    scenario.onActivity { updateNativeTop(view) }
                    awaitReport(title) { it.getDouble("env") > 0 }
                    fun updatePolicy(next: GeckoPrivacyPolicy) {
                        val ready = CountDownLatch(1)
                        scenario.onActivity {
                            session.updatePrivacyPolicy(
                                next.copy(pageHost = URI(server.fixtureUrl("/")).host),
                                onReady = ready::countDown,
                            )
                        }
                        assertTrue("Prototype policy acknowledgement", ready.await(30, TimeUnit.SECONDS))
                    }
                    updatePolicy(policy)
                    val protected = awaitReport(title) {
                        abs(it.getDouble("movingInline") - it.getDouble("env") - 8) < 0.02 &&
                            abs(it.getDouble("movingClass") - it.getDouble("env") - 8) < 0.02
                    }
                    assertEquals(-64.0, protected.getDouble("inlineFixed"), 0.02)
                    assertEquals(-8.0, protected.getDouble("inlineSticky"), 0.02)
                    assertEquals(-64.0, protected.getDouble("selectorFixed"), 0.02)
                    assertEquals(-8.0, protected.getDouble("selectorSticky"), 0.02)

                    scenario.onActivity { session.scrollToVerticalOffset(600) }
                    val hidden = awaitReport(title) {
                        it.getBoolean("hidden") && abs(it.getDouble("movingInline") + 64) < 0.02 &&
                            abs(it.getDouble("movingClass") + 64) < 0.02
                    }
                    assertEquals(0, hidden.getInt("trustedClicks"))
                    assertEquals("-64px", hidden.getString("movingInlineAuthorTop"))
                    assertEquals("", hidden.getString("movingClassAuthorTop"))

                    updatePolicy(policy.copy(geckoSafeAreaSettings = GeckoSafeAreaSettings(addInsetToNegativeTop = true)))
                    val legacy = awaitReport(title) {
                        abs(it.getDouble("inlineFixed") - it.getDouble("env") + 64) < 0.02 &&
                            abs(it.getDouble("inlineSticky") - it.getDouble("env") + 8) < 0.02 &&
                            abs(it.getDouble("selectorFixed") - it.getDouble("env") + 64) < 0.02 &&
                            abs(it.getDouble("selectorSticky") - it.getDouble("env") + 8) < 0.02
                    }
                    assertEquals(0, legacy.getInt("trustedClicks"))
                    assertEquals("-64px", legacy.getString("movingInlineAuthorTop"))
                    assertEquals("", legacy.getString("movingClassAuthorTop"))

                    updatePolicy(policy)
                    val restored = awaitReport(title) {
                        abs(it.getDouble("inlineFixed") + 64) < 0.02 &&
                            abs(it.getDouble("inlineSticky") + 8) < 0.02 &&
                            abs(it.getDouble("selectorFixed") + 64) < 0.02 &&
                            abs(it.getDouble("selectorSticky") + 8) < 0.02 &&
                            abs(it.getDouble("movingInline") + 64) < 0.02 &&
                            abs(it.getDouble("movingClass") + 64) < 0.02
                    }
                    assertTrue(restored.getBoolean("hidden"))
                    assertEquals(0, restored.getInt("trustedClicks"))
                    assertEquals("-64px", restored.getString("movingInlineAuthorTop"))
                    assertEquals("", restored.getString("movingClassAuthorTop"))
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

    @Test
    fun rootAbsoluteHeaderStackKeepsItsSpacingBelowTheTopSafeArea() {
        val title = AtomicReference<String?>(null)
        val policy = GeckoPrivacyPolicy.Disabled.copy(cssSafeAreaTopInsetPx = NATIVE_TOP_PX)
        EdgeToEdgeSiteFixtureServer { _ -> ABSOLUTE_HEADER_STACK_HTML }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-absolute-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = policy,
                    )
                    session.bindExtensionTab("safe-area-absolute-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/absolute-header-stack")))
                }
                try {
                    awaitReport(title) { it.getBoolean("loaded") }
                    scenario.onActivity { updateNativeTop(view) }
                    val report = awaitReport(title) {
                        val safeTop = NATIVE_TOP_PX / it.getDouble("density")
                        abs(it.getDouble("topHeader") - safeTop) < 0.5 &&
                            abs(it.getDouble("mainHeader") - safeTop - 31) < 0.5
                    }
                    val safeTop = NATIVE_TOP_PX / report.getDouble("density")
                    assertEquals(safeTop, report.getDouble("bodyPadding"), 0.5)
                    assertEquals(safeTop, report.getDouble("topHeader"), 0.5)
                    assertEquals(safeTop + 31, report.getDouble("mainHeader"), 0.5)
                    assertEquals(0.0, report.getDouble("logoTop"), 0.5)
                    assertEquals(0.0, report.getDouble("heroTop"), 0.5)
                    assertEquals("BODY", report.getString("topOffsetParent"))
                    assertEquals("BODY", report.getString("mainOffsetParent"))
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

    @Test
    fun bottomAnchoredFixedNavigationKeepsItsPositionWhenTopProtectionStarts() {
        val title = AtomicReference<String?>(null)
        val policy = GeckoPrivacyPolicy.Disabled.copy(cssSafeAreaTopInsetPx = NATIVE_TOP_PX)
        EdgeToEdgeSiteFixtureServer { _ -> BOTTOM_NAVIGATION_HTML }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-bottom-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = policy.copy(geckoSafeAreaSettings = GeckoSafeAreaSettings(enabled = false)),
                    )
                    session.bindExtensionTab("safe-area-bottom-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/bottom-navigation")))
                }
                try {
                    awaitReport(title) { it.getBoolean("loaded") }
                    scenario.onActivity { updateNativeTop(view) }
                    val original = awaitReport(title) {
                        it.getDouble("env") > 0 && it.getDouble("navigationBottom") == 0.0
                    }
                    val ready = CountDownLatch(1)
                    scenario.onActivity {
                        session.updatePrivacyPolicy(
                            policy.copy(pageHost = URI(server.fixtureUrl("/")).host),
                            onReady = ready::countDown,
                        )
                    }
                    assertTrue("Prototype policy acknowledgement", ready.await(30, TimeUnit.SECONDS))
                    val protected = awaitReport(title) {
                        abs(it.getDouble("headerTop") - it.getDouble("env") - 8) < 0.5
                    }
                    assertEquals(original.getDouble("navigationTop"), protected.getDouble("navigationTop"), 0.5)
                    assertEquals(0.0, protected.getDouble("navigationBottom"), 0.5)
                    assertEquals(original.getDouble("navigationRectBottom"), protected.getDouble("navigationRectBottom"), 0.5)
                    assertEquals(original.getDouble("panelTop"), protected.getDouble("panelTop"), 0.5)
                    assertEquals("", protected.getString("navigationInlineTop"))
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

    @Test
    fun initialAndAutomaticFullViewportModalsStayBelowStatusBar() {
        val title = AtomicReference<String?>(null)
        val policy = GeckoPrivacyPolicy.Disabled.copy(cssSafeAreaTopInsetPx = NATIVE_TOP_PX)
        EdgeToEdgeSiteFixtureServer { _ -> FULL_VIEWPORT_MODAL_HTML }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-modal-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = policy,
                    )
                    session.bindExtensionTab("safe-area-modal-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/full-viewport-modal")))
                }
                try {
                    awaitReport(title) { it.getBoolean("loaded") }
                    scenario.onActivity { updateNativeTop(view) }
                    val report = awaitReport(title) {
                        it.getDouble("env") > 0 && it.getBoolean("lateModalReady") &&
                            abs(it.getDouble("initialTop") - it.getDouble("env")) < 0.5 &&
                            abs(it.getDouble("lateTop") - it.getDouble("env")) < 0.5
                    }
                    assertEquals(0.0, report.getDouble("scrimTop"), 0.5)
                    assertEquals(0.0, report.getDouble("navigationBottom"), 0.5)
                    assertEquals("", report.getString("navigationInlineTop"))
                    assertEquals("0px", report.getString("lateInlineTop"))
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

    @Test
    fun reactModalFullscreenContentKeepsBackdropBehindStatusBar() {
        val title = AtomicReference<String?>(null)
        val policy = GeckoPrivacyPolicy.Disabled.copy(cssSafeAreaTopInsetPx = NATIVE_TOP_PX)
        EdgeToEdgeSiteFixtureServer { _ -> REACT_MODAL_FULLSCREEN_HTML }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-react-modal-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = policy,
                    )
                    session.bindExtensionTab("safe-area-react-modal-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/react-modal-fullscreen")))
                }
                try {
                    awaitReport(title) { it.getBoolean("loaded") }
                    scenario.onActivity { updateNativeTop(view) }
                    val report = awaitReport(title) {
                        it.getDouble("env") > 0 && it.getBoolean("modalReady") &&
                            abs(it.getDouble("contentPadding") - it.getDouble("env")) < 0.5
                    }
                    assertEquals(0.0, report.getDouble("overlayTop"), 0.5)
                    assertEquals(0.0, report.getDouble("overlayBottom"), 0.5)
                    assertEquals(0.0, report.getDouble("contentTop"), 0.5)
                    assertTrue(report.getDouble("headingTop") >= report.getDouble("env") - 0.5)
                    assertTrue(report.getDouble("closeTop") >= report.getDouble("env") - 0.5)
                    assertEquals("", report.getString("contentInlinePadding"))
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

    @Test
    fun coverPageAlreadyUsingNativeInsetKeepsItsAuthorPositions() {
        val title = AtomicReference<String?>(null)
        val policy = GeckoPrivacyPolicy.Disabled.copy(cssSafeAreaTopInsetPx = NATIVE_TOP_PX)
        EdgeToEdgeSiteFixtureServer { _ -> COVER_AWARE_HTML }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-cover-aware-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = policy,
                    )
                    session.bindExtensionTab("safe-area-cover-aware-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/cover-aware")))
                }
                try {
                    awaitReport(title) { it.getBoolean("loaded") }
                    scenario.onActivity { updateNativeTop(view) }
                    val report = awaitReport(title) {
                        it.getBoolean("loaded") && it.getDouble("env") > 0 &&
                            abs(it.getDouble("env") * it.getDouble("density") - NATIVE_TOP_PX) < 0.5 &&
                            abs(it.getDouble("headerTop") - it.getDouble("env")) < 0.5 &&
                            abs(it.getDouble("bodyPadding") - it.getDouble("env")) < 0.5
                    }
                    assertEquals(report.getDouble("env"), report.getDouble("bodyPadding"), 0.5)
                    assertEquals(report.getDouble("env"), report.getDouble("headerTop"), 0.5)
                    assertEquals(report.getDouble("env"), report.getDouble("mainTop"), 0.5)
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

    @Test
    fun coverPageWithoutInsetReceivesTopProtection() {
        val title = AtomicReference<String?>(null)
        val policy = GeckoPrivacyPolicy.Disabled.copy(cssSafeAreaTopInsetPx = NATIVE_TOP_PX)
        EdgeToEdgeSiteFixtureServer { _ -> COVER_UNAWARE_HTML }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-cover-unaware-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = policy,
                    )
                    session.bindExtensionTab("safe-area-cover-unaware-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/cover-unaware")))
                }
                try {
                    awaitReport(title) { it.getBoolean("loaded") }
                    scenario.onActivity { updateNativeTop(view) }
                    val report = awaitReport(title) {
                        it.getBoolean("loaded") && it.getDouble("bodyPadding") > 4 &&
                            it.getDouble("headerTop") > 8
                    }
                    scenario.onActivity {
                        val margins = (view as ViewGroup).getChildAt(0).layoutParams as ViewGroup.MarginLayoutParams
                        assertEquals("Cover fallback keeps the renderer edge to edge", 0, margins.topMargin)
                    }
                    val safeTop = NATIVE_TOP_PX / report.getDouble("density")
                    assertEquals(safeTop, report.getDouble("bodyPadding"), 0.5)
                    assertEquals(safeTop + 8, report.getDouble("headerTop"), 0.5)
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

    @Test
    fun coverStickyHeaderIsProtectedWhileParserIsBlockedOnceCssPolicyIsReady() {
        val title = AtomicReference<String?>(null)
        val releaseParser = CountDownLatch(1)
        EdgeToEdgeSiteFixtureServer { path ->
            when (path) {
                "/cover-parser-hold.js" -> {
                    releaseParser.await(20, TimeUnit.SECONDS)
                    "/* Controlled parser hold. */"
                }
                "/site-matrix/cover-parser-load" -> COVER_PARSER_LOAD_HTML
                else -> INITIAL_BOOTSTRAP_HTML
            }
        }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "safe-area-cover-parser-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(
                            cssSafeAreaTopInsetPx = NATIVE_TOP_PX,
                            geckoSafeAreaSettings = GeckoSafeAreaSettings(maxElementsPerBatch = 4, maxBatchDurationMillis = 1),
                        ),
                    )
                    session.bindExtensionTab("safe-area-cover-parser-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/cover-parser-bootstrap")))
                }
                try {
                    awaitReport(title) { it.getBoolean("bootstrap") && it.getBoolean("loaded") }
                    scenario.onActivity { updateNativeTop(view) }
                    awaitReport(title) { abs(it.getDouble("env") * it.getDouble("density") - NATIVE_TOP_PX) < 0.5 }
                    scenario.onActivity { assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/cover-parser-load"))) }
                    val held = awaitReport(title) {
                        !it.getBoolean("bootstrap") && it.getInt("loadingSamples") >= 8
                    }
                    val safeTop = NATIVE_TOP_PX / held.getDouble("density")
                    println("Prototype cover parser hold: $held")
                    assertEquals("First header frame is sampled before DOM readiness", "loading", held.getString("firstReadyState"))
                    assertEquals("Native inset is ready in the first visible frame", safeTop, held.getDouble("firstEnv"), 0.5)
                    assertEquals("First policy-ready frame is sampled before DOM readiness", "loading", held.getString("firstPolicyReadyState"))
                    assertEquals("Header clears the safe area from the first policy-ready frame", safeTop, held.getDouble("firstPolicyHeaderY"), 0.5)
                    assertEquals("Header never enters the safe area while parsing is blocked", safeTop, held.getDouble("minHeaderY"), 0.5)
                    assertEquals("Header receives only one inset while parsing is blocked", safeTop, held.getDouble("maxHeaderY"), 0.5)
                    assertEquals("SVG-first header does not acquire body padding", 0.0, held.getDouble("maxBodyPadding"), 0.5)
                    releaseParser.countDown()
                    val loaded = awaitReport(title) {
                        !it.getBoolean("bootstrap") && it.getBoolean("loaded") && it.getInt("afterLoadSamples") >= 8
                    }
                    assertEquals("DOM readiness adds no header jump", safeTop, loaded.getDouble("minHeaderY"), 0.5)
                    assertEquals("Load completion adds no header jump", safeTop, loaded.getDouble("maxHeaderY"), 0.5)
                    assertEquals("Load completion adds no body padding", 0.0, loaded.getDouble("maxBodyPadding"), 0.5)
                    println("Prototype cover parser completion: $loaded")
                } finally {
                    releaseParser.countDown()
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
    fun bodyAndHeaderSettleBeforeHeldDocumentLoadCompletes() {
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
                    assertEquals("Body protected while load is pending", held.getDouble("env"), held.getDouble("body"), 0.02)
                    assertEquals("Header protected while load is pending", held.getDouble("env") + 8, held.getDouble("protectedHeaderY"), 0.5)
                    assertEquals("Header remains stable while load is pending", held.getDouble("minHeaderY"), held.getDouble("maxHeaderY"), 0.5)
                    assertTrue("Header protected within initial-load deadline", held.getDouble("protectedAt") - held.getDouble("firstAt") < 400)
                    releaseLoad.countDown()
                    val loaded = awaitReport(title) { !it.getBoolean("bootstrap") && it.getBoolean("loaded") && it.getInt("afterLoadSamples") >= 5 }
                    assertEquals("Load completion adds no body offset", held.getDouble("body"), loaded.getDouble("body"), 0.02)
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
                        scenario.onActivity {
                            session.updatePrivacyPolicy(
                                next.copy(pageHost = URI(server.fixtureUrl("/")).host),
                                onReady = ready::countDown,
                            )
                        }
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

    private fun tapButton(scenario: ActivityScenario<GeckoScrollTestActivity>, view: View, report: JSONObject) {
        val location = IntArray(2)
        scenario.onActivity { view.getLocationOnScreen(location) }
        val density = report.getDouble("density").toFloat()
        val x = location[0] + report.getDouble("buttonX").toFloat() * density
        val y = location[1] + report.getDouble("buttonY").toFloat() * density
        val downTime = SystemClock.uptimeMillis()
        injectTouch(MotionEvent.ACTION_DOWN, downTime, downTime, x, y)
        SystemClock.sleep(50)
        injectTouch(MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(), x, y)
    }

    private fun injectTouch(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { event ->
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(
                    "Input injection failed for ${MotionEvent.actionToString(action)}",
                    instrumentation.uiAutomation.injectInputEvent(event, true),
                )
            } finally {
                event.recycle()
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
        val KNOWN_HEADER_TOP_MUTATION_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              html,body { margin:0; }
              #probe { position:absolute; visibility:hidden; padding-top:env(safe-area-inset-top); }
              #tracked-header { position:fixed; left:0; width:200px; height:32px; background:#cc071e; }
              #tracked-header { top:0; }
              #header-container.compact #tracked-header { top:16px; }
              #header-container.hidden #tracked-header { top:-64px; }
              main { height:3000px; }
            </style>
            <body><div id="probe"></div><div id="header-container">
              <header id="tracked-header" style="top:64px">Tracked header</header>
            </div><main>Content</main></body>
            <script>
              const header = document.getElementById('tracked-header');
              const container = document.getElementById('header-container');
              let stage = 0, pending = false, trustedClicks = 0;
              let initialTop = null, initialY = null, resetTop = null, resetY = null;
              document.addEventListener('click', event => { if (event.isTrusted) trustedClicks++; });
              window.addEventListener('scroll', () => {
                if (stage === 3) container.classList.toggle('hidden', scrollY > 80);
              });
              setInterval(() => {
                const env = parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop);
                const top = parseFloat(getComputedStyle(header).top);
                const y = header.getBoundingClientRect().top;
                if (!pending && document.readyState === 'complete' && env > 0) {
                  if (stage === 0 && Math.abs(top - env - 64) < 0.5) {
                    initialTop = top; initialY = y; pending = true;
                    setTimeout(() => { header.style.top = '0px'; stage = 1; pending = false; }, 150);
                  } else if (stage === 1 && Math.abs(top - env) < 0.5) {
                    resetTop = top; resetY = y; pending = true;
                    setTimeout(() => { header.style.removeProperty('top'); stage = 2; pending = false; }, 150);
                  } else if (stage === 2 && Math.abs(top - env) < 0.5) {
                    pending = true;
                    setTimeout(() => { container.classList.add('compact'); stage = 3; pending = false; }, 150);
                  }
                }
                document.title = '${REPORT_PREFIX}' + JSON.stringify({
                  loaded:document.readyState === 'complete', density:devicePixelRatio, env,
                  stage, initialTop, initialY, resetTop, resetY, top, y,
                  authorTop:header.style.top, hidden:container.classList.contains('hidden'),
                  trustedClicks, scroll:scrollY,
                });
              }, 50);
            </script>
        """.trimIndent()
        val COVER_HEADER_MUTATION_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>
              html,body { margin:0; }
              header { position:sticky; top:0; height:100px; background:#cc071e; }
              header.open { background:#cc071f; }
              main { height:3000px; }
            </style>
            <body><header><button>Menu</button></header><main>Content</main></body>
            <script>
              const header = document.querySelector('header');
              for (let index = 0; index < 24; index++) {
                const anchor = document.createElement('div');
                anchor.style.cssText = 'position:fixed;top:8px;width:2px;height:2px;';
                document.body.appendChild(anchor);
              }
              let started = false, done = false, mutations = 0, samples = 0;
              let layerRemovals = 0;
              let minTop = Infinity, maxTop = -Infinity, maxScroll = 0;
              const protectionLayers = new Set();
              new MutationObserver(records => {
                if (!started || done) return;
                for (const record of records) {
                  for (const node of record.removedNodes) {
                    if (protectionLayers.has(node)) layerRemovals++;
                  }
                }
              }).observe(document.documentElement, { childList:true });
              function frame() {
                const top = header.getBoundingClientRect().top;
                if (!started && document.readyState === 'complete' && top > 20) {
                  started = true;
                  // Candy inserts its per-element rules through CSSOM; the
                  // fixture's author stylesheet has nonempty source text.
                  for (const style of document.querySelectorAll('style')) {
                    if (!style.textContent.trim()) protectionLayers.add(style);
                  }
                  const timer = setInterval(() => {
                    header.classList.toggle('open');
                    if (++mutations === 5) {
                      clearInterval(timer);
                      setTimeout(() => { done = true; }, 500);
                    }
                  }, 300);
                }
                if (started && !done) {
                  samples++;
                  minTop = Math.min(minTop, top);
                  maxTop = Math.max(maxTop, top);
                  maxScroll = Math.max(maxScroll, Math.abs(scrollY));
                }
                requestAnimationFrame(frame);
              }
              requestAnimationFrame(frame);
              setInterval(() => {
                document.title = '${REPORT_PREFIX}' + JSON.stringify({
                  loaded:document.readyState === 'complete', density:devicePixelRatio,
                  done, mutations, samples, minTop, maxTop, maxScroll, layerRemovals,
                  headerTop:header.getBoundingClientRect().top,
                  bodyPadding:getComputedStyle(document.body).paddingTop,
                  cssTop:getComputedStyle(header).top,
                });
              }, 50);
            </script>
        """.trimIndent()
        val COVER_BODY_SCROLL_LOCK_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>
              html,body { margin:0; }
              body.locked { position:fixed; top:0; width:100%; height:100vh; overflow:hidden; }
              header { position:sticky; top:0; height:100px; background:#cc071e; }
              header svg { width:60px; height:50px; }
              button { position:absolute; right:20px; top:20px; width:120px; height:60px; }
              main { height:3000px; }
            </style>
            <body><header><svg viewBox="0 0 60 50"><rect width="60" height="50" fill="white"/></svg><button>Menu</button></header><main>Content</main></body>
            <script>
              const header = document.querySelector('header');
              const button = document.querySelector('button');
              let started = false, trustedClicks = 0, settledClicks = 0, fixedSamples = 0;
              let minTop = Infinity, maxTop = -Infinity, maxPadding = 0, maxScroll = 0;
              button.addEventListener('click', event => {
                if (!event.isTrusted || !started) return;
                document.body.classList.toggle('locked');
                const click = ++trustedClicks;
                setTimeout(() => { settledClicks = click; }, 500);
              });
              function frame() {
                const top = header.getBoundingClientRect().top;
                const safeTop = $NATIVE_TOP_PX / devicePixelRatio;
                if (!started && document.readyState === 'complete' && Math.abs(top - safeTop) < 0.5) started = true;
                if (started && trustedClicks > 0) {
                  minTop = Math.min(minTop, top);
                  maxTop = Math.max(maxTop, top);
                  maxPadding = Math.max(maxPadding, parseFloat(getComputedStyle(document.body).paddingTop));
                  maxScroll = Math.max(maxScroll, Math.abs(scrollY));
                  if (getComputedStyle(document.body).position === 'fixed') fixedSamples++;
                }
                requestAnimationFrame(frame);
              }
              requestAnimationFrame(frame);
              setInterval(() => {
                const rect = button.getBoundingClientRect();
                document.title = '${REPORT_PREFIX}' + JSON.stringify({
                  loaded:document.readyState === 'complete', density:devicePixelRatio,
                  started, trustedClicks, settledClicks, fixedSamples, minTop, maxTop, maxPadding, maxScroll,
                  buttonX:rect.left + rect.width / 2, buttonY:rect.top + rect.height / 2,
                });
              }, 50);
            </script>
        """.trimIndent()
        val NEGATIVE_TOP_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              html,body { margin:0; }
              #selector-fixed { position:fixed; top:-64px; }
              #selector-sticky { position:sticky; top:-8px; }
              #moving-class { position:fixed; top:8px; }
              #moving-class.hidden { top:-64px; }
              #probe { position:absolute; padding-top:env(safe-area-inset-top,0px); }
              #tail { height:3000px; }
            </style>
            <body>
              <div id="probe"></div>
              <div id="inline-fixed" style="position:fixed;top:-64px">Inline fixed</div>
              <div id="inline-sticky" style="position:sticky;top:-8px">Inline sticky</div>
              <div id="selector-fixed">Selector fixed</div><div id="selector-sticky">Selector sticky</div>
              <div id="moving-inline" style="position:fixed;top:8px">Moving inline</div>
              <div id="moving-class">Moving class</div><div id="tail"></div>
            </body>
            <script>
              let hidden = false, trustedClicks = 0;
              document.addEventListener('click', event => { if (event.isTrusted) trustedClicks++; });
              window.addEventListener('scroll', () => {
                if (hidden || scrollY <= 80) return;
                document.getElementById('moving-inline').style.top = '-64px';
                document.getElementById('moving-class').classList.add('hidden');
                hidden = true;
              });
              const report = () => {
                const number = (id, property = 'top') => parseFloat(getComputedStyle(document.getElementById(id))[property]);
                document.title = '${REPORT_PREFIX}' + JSON.stringify({loaded:document.readyState === 'complete',
                  env:number('probe','paddingTop'), inlineFixed:number('inline-fixed'), inlineSticky:number('inline-sticky'),
                  selectorFixed:number('selector-fixed'), selectorSticky:number('selector-sticky'),
                  movingInline:number('moving-inline'), movingClass:number('moving-class'), hidden, trustedClicks,
                  movingInlineAuthorTop:document.getElementById('moving-inline').style.top,
                  movingClassAuthorTop:document.getElementById('moving-class').style.top});
              };
              setInterval(report,100); report();
            </script>
        """.trimIndent()
        val ABSOLUTE_HEADER_STACK_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              html,body { margin:0; }
              #top-header { position:absolute; top:0; left:0; right:0; height:31px; }
              #main-header { position:absolute; top:31px; left:0; right:0; height:80px; }
              #logo { position:absolute; top:0; left:0; width:80px; height:80px; }
              #hero { position:absolute; top:0; left:0; right:0; height:700px; }
            </style>
            <body><div id="wrapper"><div id="top-header">Contact</div>
              <header id="main-header"><div id="logo">Logo</div></header>
              <div id="hero">Hero</div></div>
            <script>
              setInterval(() => {
                const top = document.getElementById('top-header');
                const main = document.getElementById('main-header');
                document.title = '${REPORT_PREFIX}' + JSON.stringify({
                  loaded:document.readyState === 'complete', density:devicePixelRatio,
                  bodyPadding:parseFloat(getComputedStyle(document.body).paddingTop),
                  topHeader:parseFloat(getComputedStyle(top).top),
                  mainHeader:parseFloat(getComputedStyle(main).top),
                  logoTop:parseFloat(getComputedStyle(document.getElementById('logo')).top),
                  heroTop:parseFloat(getComputedStyle(document.getElementById('hero')).top),
                  topOffsetParent:top.offsetParent?.tagName, mainOffsetParent:main.offsetParent?.tagName,
                });
              }, 50);
            </script>
        """.trimIndent()
        val COVER_AWARE_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>
              html,body { margin:0; }
              body { padding-top:env(safe-area-inset-top); }
              header { position:fixed; top:env(safe-area-inset-top); left:0; height:32px; width:100%; }
              #probe { position:absolute; visibility:hidden; padding-top:env(safe-area-inset-top); }
            </style>
            <body><div id="probe"></div><header>Protected header</header><main>Protected body</main>
            <script>
              setInterval(() => {
                document.title = '${REPORT_PREFIX}' + JSON.stringify({
                  loaded:document.readyState === 'complete', density:devicePixelRatio,
                  env:parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop),
                  bodyPadding:parseFloat(getComputedStyle(document.body).paddingTop),
                  headerTop:document.querySelector('header').getBoundingClientRect().top,
                  mainTop:document.querySelector('main').getBoundingClientRect().top,
                });
              }, 50);
            </script>
        """.trimIndent()
        val COVER_UNAWARE_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>
              html,body { margin:0; }
              body { padding-top:4px; }
              header { position:fixed; top:8px; left:0; height:32px; width:100%; }
              #probe { position:absolute; visibility:hidden; padding-top:env(safe-area-inset-top); }
            </style>
            <body><div id="probe"></div><header>Unprotected header</header><main>Unprotected body</main>
            <script>
              setInterval(() => {
                document.title = '${REPORT_PREFIX}' + JSON.stringify({
                  loaded:document.readyState === 'complete', density:devicePixelRatio,
                  env:parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop),
                  bodyPadding:parseFloat(getComputedStyle(document.body).paddingTop),
                  headerTop:document.querySelector('header').getBoundingClientRect().top,
                });
              }, 50);
            </script>
        """.trimIndent()
        val BOTTOM_NAVIGATION_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              html,body { margin:0; }
              #probe { position:absolute; padding-top:env(safe-area-inset-top); visibility:hidden; }
              #header { position:fixed; top:8px; height:24px; }
              #navigation { position:fixed; bottom:0; height:48px; left:0; right:0; }
              #panel { position:fixed; bottom:0; height:calc(100vh - 100px); width:8px; }
            </style>
            <div id="probe"></div><div id="header">Header</div><nav id="navigation">Navigation</nav><div id="panel"></div>
            <script>
              setInterval(() => {
                const navigation = document.getElementById('navigation');
                const style = getComputedStyle(navigation);
                document.title = '${REPORT_PREFIX}' + JSON.stringify({
                  loaded:document.readyState === 'complete',
                  env:parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop),
                  headerTop:parseFloat(getComputedStyle(document.getElementById('header')).top),
                  navigationTop:parseFloat(style.top), navigationBottom:parseFloat(style.bottom),
                  navigationRectBottom:navigation.getBoundingClientRect().bottom,
                  panelTop:parseFloat(getComputedStyle(document.getElementById('panel')).top),
                  navigationInlineTop:navigation.style.top,
                });
              }, 50);
            </script>
        """.trimIndent()
        val FULL_VIEWPORT_MODAL_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              html,body { margin:0; }
              #probe { position:absolute; visibility:hidden; padding-top:env(safe-area-inset-top); }
              #scrim { position:fixed; inset:0; background:rgba(0,0,0,.45); }
              #initial { position:fixed; inset:0; background:white; }
              #navigation { position:fixed; bottom:0; height:48px; left:0; right:0; }
            </style>
            <body><div id="probe"></div><div id="scrim" class="modal-backdrop"></div>
              <div id="initial" role="dialog"><div>Initial modal</div></div>
              <nav id="navigation">Navigation</nav>
            <script>
              setTimeout(() => {
                const late = document.createElement('div');
                late.id = 'late';
                late.setAttribute('role', 'dialog');
                late.style.position = 'fixed';
                late.style.inset = '0';
                late.innerHTML = '<div>Automatic modal</div>';
                document.body.append(late);
              }, 500);
              setInterval(() => {
                const late = document.getElementById('late');
                const navigation = document.getElementById('navigation');
                document.title = '${REPORT_PREFIX}' + JSON.stringify({
                  loaded:document.readyState === 'complete',
                  env:parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop),
                  initialTop:parseFloat(getComputedStyle(document.getElementById('initial')).top),
                  lateModalReady:!!late,
                  lateTop:late ? parseFloat(getComputedStyle(late).top) : null,
                  scrimTop:parseFloat(getComputedStyle(document.getElementById('scrim')).top),
                  navigationBottom:parseFloat(getComputedStyle(navigation).bottom),
                  navigationInlineTop:navigation.style.top,
                  lateInlineTop:late?.style.top || '',
                });
              }, 50);
            </script>
        """.trimIndent()
        val REACT_MODAL_FULLSCREEN_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              html,body { margin:0; }
              #probe { position:absolute; visibility:hidden; padding-top:env(safe-area-inset-top); }
              .ReactModal__Overlay { position:fixed; inset:0; background:rgba(0,0,0,.4); }
              .ReactModal__Content { position:absolute; top:50%; left:50%; right:auto; bottom:auto;
                transform:translate(-50%,-50%); width:100%; height:100%; box-sizing:border-box;
                background:white; overflow:auto; padding:20px; }
              .ReactModal__Content header { margin:0; height:48px; }
              .modal-close-button { position:absolute; top:16px; right:16px; width:32px; height:32px; }
            </style>
            <body><div id="probe"></div><script>
              setTimeout(() => {
                const portal = document.createElement('div');
                portal.innerHTML = '<div class="ReactModal__Overlay"><div class="ReactModal__Content" role="dialog"><header>Preisentwicklung<button class="modal-close-button">Close</button></header></div></div>';
                document.body.append(portal);
              }, 500);
              setInterval(() => {
                const overlay = document.querySelector('.ReactModal__Overlay');
                const content = document.querySelector('.ReactModal__Content');
                document.title = '${REPORT_PREFIX}' + JSON.stringify({
                  loaded:document.readyState === 'complete',
                  env:parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop),
                  modalReady:!!content,
                  overlayTop:overlay ? overlay.getBoundingClientRect().top : null,
                  overlayBottom:overlay ? parseFloat(getComputedStyle(overlay).bottom) : null,
                  contentTop:content ? content.getBoundingClientRect().top : null,
                  contentPadding:content ? parseFloat(getComputedStyle(content).paddingTop) : null,
                  headingTop:content ? content.querySelector('header').getBoundingClientRect().top : null,
                  closeTop:content ? content.querySelector('button').getBoundingClientRect().top : null,
                  contentInlinePadding:content?.style.paddingTop || '',
                });
              }, 50);
            </script>
        """.trimIndent()
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
        val COVER_PARSER_LOAD_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>
              html,body { margin:0; }
              header { position:sticky; top:0; height:100px; background:#cc071e; }
              header svg { width:60px; height:50px; }
              main { height:3000px; }
              #probe { position:absolute; visibility:hidden; padding-top:env(safe-area-inset-top); }
            </style>
            <body><header><svg viewBox="0 0 60 50"><rect width="60" height="50" fill="white"/></svg></header><main>Content</main><div id="probe"></div>
            <script>
              const header = document.querySelector('header');
              const probe = document.getElementById('probe');
              let loadingSamples = 0, afterLoadSamples = 0;
              let firstHeaderY = null, firstReadyState = null, firstEnv = null, firstCandyInset = null;
              let firstPolicyHeaderY = null, firstPolicyReadyState = null, prePolicySamples = 0;
              let minHeaderY = Infinity, maxHeaderY = -Infinity, maxBodyPadding = 0;
              function frame() {
                const rect = header.getBoundingClientRect();
                const env = parseFloat(getComputedStyle(probe).paddingTop);
                const loaded = document.readyState === 'complete';
                const candyInset = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--candy-safe-area-inset-top')) || 0;
                if (rect.width > 1 && rect.height > 1) {
                  if (firstHeaderY === null) {
                    firstHeaderY = rect.top;
                    firstReadyState = document.readyState;
                    firstEnv = env;
                    firstCandyInset = candyInset;
                  }
                  // The extension policy arrives asynchronously in each new document.
                  // Keep raw first-frame diagnostics; verify parser-time protection once it is published.
                  if (firstPolicyHeaderY === null && env > 0 && Math.abs(candyInset - env) < 0.5) {
                    firstPolicyHeaderY = rect.top;
                    firstPolicyReadyState = document.readyState;
                  }
                  if (firstPolicyHeaderY !== null) {
                    minHeaderY = Math.min(minHeaderY, rect.top);
                    maxHeaderY = Math.max(maxHeaderY, rect.top);
                    maxBodyPadding = Math.max(maxBodyPadding, parseFloat(getComputedStyle(document.body).paddingTop));
                    if (document.readyState === 'loading') loadingSamples++;
                    if (loaded) afterLoadSamples++;
                  } else {
                    prePolicySamples++;
                  }
                  document.title = '${REPORT_PREFIX}' + JSON.stringify({
                    bootstrap:false, loaded, density:devicePixelRatio, env, loadingSamples, afterLoadSamples,
                    firstHeaderY, firstReadyState, firstEnv, firstCandyInset, prePolicySamples,
                    firstPolicyHeaderY, firstPolicyReadyState, minHeaderY, maxHeaderY, maxBodyPadding,
                  });
                }
                requestAnimationFrame(frame);
              }
              requestAnimationFrame(frame);
            </script><script src="/cover-parser-hold.js"></script>
        """.trimIndent()
        val INITIAL_LOAD_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
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
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
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
