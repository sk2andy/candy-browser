package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reproduces the stacking layout that made safe-area discovery consume the first expansion tap. */
@RunWith(AndroidJUnit4::class)
class BrowserPageExpansionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val store = BrowserSessionStore(context)
    private val preferences = context.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun systemWebViewExpandsAndScrollsAfterFirstTap() {
        verifyExpansion(AndroidBrowserEngineKind.SystemWebView)
    }

    @Test
    fun geckoExpandsAndScrollsAfterFirstTap() {
        verifyExpansion(AndroidBrowserEngineKind.GeckoView)
    }

    private fun verifyExpansion(engine: AndroidBrowserEngineKind) {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        store.saveStartupAnimationEnabled(false)
        assertTrue(store.saveAndroidBrowserEngineKind(engine))
        EdgeToEdgeSiteFixtureServer { FIXTURE_HTML }.use { server ->
            val tab = BrowserTab(
                id = "page-expansion-${engine.stableId}",
                lastAccessedAt = System.currentTimeMillis(),
                url = server.fixtureUrl("/site-matrix/expansion"),
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
            val intent = Intent(context, MainActivity::class.java)
                .setAction("dev.sk2andy.materialbrowser.TEST_PAGE_EXPANSION")
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                repeat(3) { iteration ->
                    if (iteration > 0) {
                        scenario.onActivity { activity ->
                            assertTrue(activity.browserControllerForTesting().openUrl(tab.url))
                        }
                    }
                    val before = awaitReport(scenario) { it.getString("state") == "collapsed" }
                    assertTrue(device.click(before.getInt("x"), before.getInt("y")))
                    val expanded = awaitReport(scenario) { it.getString("state") == "expanded" }
                    assertEquals("One native tap must invoke one handler", 1, expanded.getInt("clicks"))
                    assertTrue("Expanded answer must have its full height", expanded.getDouble("height") > 700)
                    assertEquals("Show more must leave layout", 0.0, expanded.getDouble("buttonHeight"), 0.5)
                    assertTrue(
                        device.swipe(
                            device.displayWidth / 2,
                            device.displayHeight * 2 / 3,
                            device.displayWidth / 2,
                            device.displayHeight / 3,
                            20,
                        ),
                    )
                    awaitReport(scenario) { it.getDouble("scrollY") > 50 }
                }
            }
        }
    }

    private fun awaitReport(
        scenario: ActivityScenario<MainActivity>,
        ready: (JSONObject) -> Boolean,
    ): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        var lastTitle = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                lastTitle = activity.browserControllerForTesting().selectedTabForTesting().title
            }
            val report = runCatching { JSONObject(lastTitle) }.getOrNull()
            if (report != null && ready(report)) return report
            SystemClock.sleep(50)
        }
        error("Page expansion did not become ready: $lastTitle")
    }

    private companion object {
        val FIXTURE_HTML = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              body { margin:0; background:#202124; color:white; font:20px sans-serif; }
              header { height:90px; padding:12px; }
              article { position:relative; margin:16px; max-height:130px; overflow:hidden; }
              article.expanded { max-height:none; }
              #answer { height:900px; background:linear-gradient(#314252,#233322); }
              #fade { position:absolute; bottom:0; width:100%; height:90px;
                      z-index:1; background:linear-gradient(transparent,#202124); }
              #moreWrap { position:absolute; bottom:8px; width:100%; }
              #morePosition { position:relative; z-index:2; }
              button { display:block; margin:auto; padding:16px; width:80%; }
              #tail { height:900px; }
            </style></head><body>
            <header>Google-like search header<br>AI Overview</header>
            <article id="overview"><div id="answer">Hello, World!<p>Origins and Meanings</p>
              <p>Programming Tradition</p><p>Companies and Brands</p></div>
              <div id="fade"></div><div id="moreWrap"><div id="morePosition">
                <button id="more">Show more</button>
              </div></div></article><div id="tail">Search results</div>
            <script>
              let clicks = 0;
              const more = document.querySelector('#more');
              const overview = document.querySelector('#overview');
              more.onclick = () => {
                clicks++;
                overview.classList.add('expanded');
                more.style.display = 'none';
              };
              function report() {
                const rect = more.getBoundingClientRect();
                document.title = JSON.stringify({
                  state: clicks ? 'expanded' : 'collapsed', clicks,
                  height: overview.getBoundingClientRect().height,
                  buttonHeight: rect.height,
                  x: Math.round((rect.left + rect.width / 2) * devicePixelRatio),
                  y: Math.round((rect.top + rect.height / 2) * devicePixelRatio),
                  scrollY: window.scrollY
                });
              }
              setInterval(report, 100);
              addEventListener('load', report);
            </script></body></html>
        """.trimIndent()
    }
}
