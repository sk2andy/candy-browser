package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoSafeAreaDomainNavigationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store by lazy { BrowserSessionStore(context) }
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        store.saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun domainNavigationClearsAutomaticTopMarginBeforeTheNextLoad() {
        val sourceTitle = "Domain source fixture"
        val targetTitle = "Domain target fixture"
        EdgeToEdgeSiteFixtureServer { request ->
            val title = if (request.contains("domain-target")) targetTitle else sourceTitle
            "<!doctype html><title>$title</title><body>Domain navigation</body>"
        }.use { server ->
            val sourceUrl = server.fixtureUrl("/site-matrix/domain-source")
            val targetUrl = server.fixtureUrl("/site-matrix/domain-target").replace("127.0.0.1", "localhost")
            val tab = BrowserTab(
                id = "gecko-domain-safe-area-fixture",
                lastAccessedAt = System.currentTimeMillis(),
                url = sourceUrl,
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                awaitSelectedTabTitle(scenario, sourceTitle)
                awaitViewReady(scenario)
                for ((url, topHeader) in listOf(targetUrl to false, sourceUrl to true)) {
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        controller.onWindowInsetsChanged(
                            WindowInsetsCompat.Builder()
                                .setInsets(
                                    WindowInsetsCompat.Type.statusBars(),
                                    Insets.of(0, STATUS_BAR_INSET_PX, 0, 0),
                                )
                                .build(),
                        )
                        val generation = controller.selectedWebContentSafeAreaNavigationGenerationForTesting
                        controller.dispatchSelectedGeckoPrivacyEventForTesting(
                            GeckoPrivacyEvent(
                                requestUrl = "",
                                pageUrl = controller.selectedTab.url,
                                ruleId = null,
                                wasBlocked = false,
                                isBuiltIn = false,
                                isCompatibilityObservation = false,
                                safeAreaFallbackNavigationGeneration = generation,
                                safeAreaFallbackIsTopHeader = topHeader,
                            ),
                        )
                        val view = requireNotNull(controller.selectedGeckoViewForTesting()).engineView()
                        assertMargins(view, left = 0, top = STATUS_BAR_INSET_PX, right = 0, bottom = 0)
                        controller.submitAddress(url)
                        assertMargins(view, left = 0, top = 0, right = 0, bottom = 0)
                        controller.dispatchSelectedGeckoPrivacyEventForTesting(
                            GeckoPrivacyEvent(
                                requestUrl = "",
                                pageUrl = sourceUrl,
                                ruleId = null,
                                wasBlocked = false,
                                isBuiltIn = false,
                                isCompatibilityObservation = false,
                                safeAreaFallbackNavigationGeneration = generation,
                            ),
                        )
                        assertMargins(view, left = 0, top = 0, right = 0, bottom = 0)
                    }
                    awaitSelectedTabTitle(scenario, if (url == sourceUrl) sourceTitle else targetTitle)
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertTrue(controller.setForceSafeArea(controller.selectedTabId, true))
                }
                awaitNativeTopMargin(scenario, expectedPresent = true)
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    controller.submitAddress(targetUrl)
                    assertEquals(0, controller.previewTopInsetPx(controller.selectedTabId))
                    assertMargins(
                        requireNotNull(controller.selectedGeckoViewForTesting()).engineView(),
                        left = 0, top = 0, right = 0, bottom = 0,
                    )
                }
                awaitSelectedTabTitle(scenario, targetTitle)
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    controller.submitAddress(sourceUrl)
                    assertTrue("Explicit source override survives the domain reset", controller.previewTopInsetPx(controller.selectedTabId) > 0)
                }
                awaitSelectedTabTitle(scenario, sourceTitle)
                awaitNativeTopMargin(scenario, expectedPresent = true)
            }
        }
    }

    private fun awaitNativeTopMargin(scenario: ActivityScenario<MainActivity>, expectedPresent: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            var present = false
            scenario.onActivity { activity ->
                val view = requireNotNull(activity.browserControllerForTesting().selectedGeckoViewForTesting())
                present = (view.engineView().layoutParams as ViewGroup.MarginLayoutParams).topMargin > 0
            }
            if (present == expectedPresent) return
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("Expected native top margin present=$expectedPresent")
    }

    private fun awaitSelectedTabTitle(scenario: ActivityScenario<MainActivity>, expectedTitle: String) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                val tab = activity.browserControllerForTesting().selectedTab
                ready = tab.title == expectedTitle && !tab.isLoading
            }
            if (ready) return
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("Expected completed page title=$expectedTitle")
    }

    private fun awaitViewReady(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.browserControllerForTesting().selectedGeckoViewForTesting()
                    ?.let { view -> view.isAttachedToWindow && view.width > 0 && view.height > 0 } == true
            }
            if (ready) return
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("Gecko viewport did not attach")
    }

    private fun View.engineView(): View = (this as ViewGroup).getChildAt(0)

    private fun assertMargins(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        val margins = view.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(left, margins.leftMargin)
        assertEquals(top, margins.topMargin)
        assertEquals(right, margins.rightMargin)
        assertEquals(bottom, margins.bottomMargin)
    }

    private companion object {
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.test.GECKO_DOMAIN_SAFE_AREA"
        const val STATUS_BAR_INSET_PX = 96
        const val TIMEOUT_MILLIS = 30_000L
        const val POLL_MILLIS = 50L
    }
}
