package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.CandyApplication
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticSafeAreaInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store by lazy { BrowserSessionStore(context) }
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        (context.applicationContext as CandyApplication).webContentSafeAreaSession.clear()
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        store.saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
    }

    @After
    fun tearDown() {
        (context.applicationContext as CandyApplication).webContentSafeAreaSession.clear()
        preferences.edit().clear().commit()
    }

    @Test
    fun geckoAutomaticallyProtectsUnsafeDomainsForTheSession() {
        assertAutomaticProtection(AndroidBrowserEngineKind.GeckoView)
    }

    @Test
    fun systemWebViewAutomaticallyProtectsUnsafeDomainsForTheSession() {
        assertAutomaticProtection(AndroidBrowserEngineKind.SystemWebView)
    }

    private fun assertAutomaticProtection(engine: AndroidBrowserEngineKind) {
        assertTrue(store.saveAndroidBrowserEngineKind(engine))
        EdgeToEdgeSiteFixtureServer { target ->
            when {
                target.contains("safe-start") -> safePage(SAFE_START_TITLE)
                target.contains("safe-next") -> safePage(SAFE_NEXT_TITLE)
                target.contains("safe-other") -> safePage(SAFE_OTHER_TITLE)
                target.contains("other") -> OTHER_UNSAFE_PAGE
                else -> AQARA_PAGE
            }
        }.use { server ->
            val aqaraUrl = server.fixtureUrl("/site-matrix/aqara")
            val safeStartUrl = server.fixtureUrl("/site-matrix/safe-start")
            val safeSameDomainUrl = server.fixtureUrl("/site-matrix/safe-next")
            val safeOtherDomainUrl = server.fixtureUrl("/site-matrix/safe-other")
                .replace("127.0.0.1", "localhost")
            val otherUnsafeUrl = server.fixtureUrl("/site-matrix/other").replace("127.0.0.1", "localhost")
            val tab = BrowserTab(
                id = "automatic-safe-area-fixture",
                lastAccessedAt = System.currentTimeMillis(),
                url = safeStartUrl,
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                awaitPage(scenario, SAFE_START_TITLE)
                awaitTopMargin(scenario, engine, expectedPresent = false)
                SystemClock.sleep(LAYOUT_STABILITY_MILLIS)
                awaitTopMargin(scenario, engine, expectedPresent = false)

                open(scenario, aqaraUrl)
                awaitPage(scenario, AQARA_TITLE)
                awaitTopMargin(scenario, engine, expectedPresent = true)
                awaitUnpaddedAqaraBanner(scenario, engine)

                open(scenario, safeSameDomainUrl)
                awaitPage(scenario, SAFE_NEXT_TITLE)
                awaitTopMargin(scenario, engine, expectedPresent = true)

                open(scenario, safeOtherDomainUrl)
                awaitPage(scenario, SAFE_OTHER_TITLE)
                awaitTopMargin(scenario, engine, expectedPresent = false)
                SystemClock.sleep(LAYOUT_STABILITY_MILLIS)
                awaitTopMargin(scenario, engine, expectedPresent = false)

                open(scenario, otherUnsafeUrl)
                awaitPage(scenario, OTHER_TITLE)
                awaitTopMargin(scenario, engine, expectedPresent = true)

                open(scenario, aqaraUrl)
                awaitPage(scenario, AQARA_TITLE)
                awaitTopMargin(scenario, engine, expectedPresent = true)
                awaitUnpaddedAqaraBanner(scenario, engine)
            }
        }
    }

    private fun open(scenario: ActivityScenario<MainActivity>, url: String) {
        scenario.onActivity { activity ->
            assertTrue(activity.browserControllerForTesting().openUrl(url))
        }
    }

    private fun awaitPage(scenario: ActivityScenario<MainActivity>, title: String) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                val tab = activity.browserControllerForTesting().selectedTab
                ready = (tab.title == title || tab.title.startsWith("$title|")) && !tab.isLoading
            }
            if (ready) return
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("Page did not load: $title")
    }

    private fun awaitUnpaddedAqaraBanner(
        scenario: ActivityScenario<MainActivity>,
        engine: AndroidBrowserEngineKind,
    ) {
        // Wait until the page has received the native inset change and republished its geometry.
        SystemClock.sleep(LAYOUT_STABILITY_MILLIS)
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var lastTitle = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                lastTitle = activity.browserControllerForTesting().selectedTab.title
            }
            val parts = lastTitle.split('|')
            val padding = parts.getOrNull(1)?.toDoubleOrNull()
            val bannerTop = parts.getOrNull(2)?.toDoubleOrNull()
            if (parts.firstOrNull() == AQARA_TITLE && padding != null && bannerTop != null &&
                kotlin.math.abs(padding) <= CSS_TOLERANCE && kotlin.math.abs(bannerTop) <= CSS_TOLERANCE
            ) return
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("$engine: expected zero body padding and banner top; title=$lastTitle")
    }

    private fun awaitTopMargin(
        scenario: ActivityScenario<MainActivity>,
        engine: AndroidBrowserEngineKind,
        expectedPresent: Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            var margin: Int? = null
            var expectedMargin: Int? = null
            var windowTop: Int? = null
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val view = when (engine) {
                    AndroidBrowserEngineKind.GeckoView ->
                        (controller.selectedGeckoViewForTesting() as? ViewGroup)?.getChildAt(0)
                    AndroidBrowserEngineKind.SystemWebView ->
                        controller.selectedBrowserEngineViewForTesting()?.findWebView()
                }
                margin = (view?.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin
                expectedMargin = if (expectedPresent) {
                    ViewCompat.getRootWindowInsets(activity.window.decorView)?.getInsets(
                        WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
                    )?.top
                } else {
                    0
                }
                if (view != null) {
                    val location = IntArray(2)
                    view.getLocationInWindow(location)
                    windowTop = location[1]
                }
            }
            if (expectedMargin != null && (!expectedPresent || expectedMargin!! > 0) &&
                margin == expectedMargin && windowTop == expectedMargin
            ) return
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("$engine: expected native top margin present=$expectedPresent")
    }

    private fun View.findWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findWebView()
        }
        else -> null
    }

    private companion object {
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.test.AUTOMATIC_SAFE_AREA"
        const val TIMEOUT_MILLIS = 30_000L
        const val POLL_MILLIS = 50L
        const val LAYOUT_STABILITY_MILLIS = 600L
        const val CSS_TOLERANCE = 0.5
        const val AQARA_TITLE = "Aqara-style announcement"
        const val SAFE_START_TITLE = "Safe cover before detection"
        const val SAFE_NEXT_TITLE = "Safe cover on remembered domain"
        const val SAFE_OTHER_TITLE = "Safe cover on other domain"
        const val OTHER_TITLE = "Unsafe fixed navigation"

        val AQARA_PAGE = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <title>$AQARA_TITLE</title>
            <style>html,body{margin:0} .announcement{height:30px;background:#ddd} header{height:48px}</style>
            <div class="announcement"><div><div><div><div><div><div><div><div><div>
              <p style="margin:0">Free shipping on orders over €99</p>
            </div></div></div></div></div></div></div></div></div></div>
            <header><button>Menu</button> Aqara Shop</header><main>Product</main>
            <script>
              setInterval(() => {
                const padding = parseFloat(getComputedStyle(document.body).paddingTop);
                const bannerTop = document.querySelector('.announcement').getBoundingClientRect().top;
                const title = '$AQARA_TITLE|' + padding + '|' + bannerTop;
                if (document.title !== title) document.title = title;
              }, 100);
            </script>
        """.trimIndent()

        fun safePage(title: String) = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <title>$title</title>
            <style>html,body{margin:0} body{padding-top:120px}</style>
            <main>Content safely below the status bar</main>
        """.trimIndent()

        val OTHER_UNSAFE_PAGE = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <title>$OTHER_TITLE</title>
            <style>html,body{margin:0} nav{position:fixed;top:0;height:40px;width:100%}</style>
            <nav><button>Open navigation</button></nav><main>Other website</main>
        """.trimIndent()
    }
}
