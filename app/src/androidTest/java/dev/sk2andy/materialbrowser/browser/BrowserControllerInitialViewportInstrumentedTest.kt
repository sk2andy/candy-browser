package dev.sk2andy.materialbrowser.browser

import android.os.SystemClock
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class BrowserControllerInitialViewportInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null
    private var originalEngineKind: AndroidBrowserEngineKind? = null
    private var originalTabs: List<BrowserTab>? = null
    private var originalSelectedTabId: String? = null
    private var originalProfiles: List<BrowserProfile>? = null
    private var originalActiveProfileId: String? = null
    private var originalHistoryRecordingMode: HistoryRecordingMode? = null
    private var activeTestTabId: String? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            try {
                activeTestTabId?.let { tabId -> controller?.closeTab(tabId) }
                activeTestTabId = null
                controller?.destroy()
                controller = null
            } finally {
                val store = BrowserSessionStore(composeRule.activity)
                originalEngineKind?.let { kind ->
                    assertTrue(store.saveAndroidBrowserEngineKind(kind))
                }
                originalTabs?.let { tabs ->
                    assertTrue(
                        store.saveTabsImmediately(
                            tabs = tabs,
                            selectedTabId = originalSelectedTabId.orEmpty(),
                        ),
                    )
                }
                originalProfiles?.let { profiles ->
                    store.saveProfiles(
                        profiles = profiles,
                        activeProfileId = requireNotNull(originalActiveProfileId),
                    )
                    assertTrue(store.flush())
                }
                originalHistoryRecordingMode?.let { mode ->
                    assertTrue(store.saveHistoryRecordingMode(mode))
                }
            }
        }
    }

    @Test
    fun initialNavigationUsesAttachedViewportScaleWithoutReload() {
        EdgeToEdgeSiteFixtureServer { target ->
            when {
                target.startsWith(WARMUP_PATH) -> WARMUP_HTML
                target.startsWith(FIXTURE_PATH) -> HTML
                else -> null
            }
        }.use { server ->
            lateinit var host: FrameLayout
            composeRule.runOnIdle {
                val store = BrowserSessionStore(composeRule.activity)
                originalEngineKind = store.loadAndroidBrowserEngineKind()
                originalHistoryRecordingMode = store.loadHistoryRecordingMode()
                store.loadTabs().let { (tabs, selectedTabId) ->
                    originalTabs = tabs
                    originalSelectedTabId = selectedTabId
                }
                store.loadProfiles().let { (profiles, activeProfileId) ->
                    originalProfiles = profiles
                    originalActiveProfileId = activeProfileId
                }
                assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
                assertTrue(store.saveHistoryRecordingMode(HistoryRecordingMode.Disabled))
                assertTrue(store.saveTabsImmediately(emptyList(), ""))

                val browserController = BrowserController(composeRule.activity)
                controller = browserController
                activeTestTabId = browserController.createTab(
                    initialUrl = BLANK_URL,
                    isIncognito = true,
                )
                host = FrameLayout(composeRule.activity)
                composeRule.activity.addContentView(
                    host,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )

                browserController.submitAddress(server.fixtureUrl(WARMUP_PATH))
                browserController.attachSelectedBrowserEngineView(host)
            }
            composeRule.waitUntil(timeoutMillis = 30_000L) {
                controller?.selectedTabForTesting()?.title == WARMUP_TITLE
            }

            composeRule.runOnIdle {
                val warmController = requireNotNull(controller)
                warmController.detachBrowserEngineView(host)
                requireNotNull(activeTestTabId).let(warmController::closeTab)
                activeTestTabId = null
                warmController.destroy()

                val browserController = BrowserController(composeRule.activity)
                controller = browserController
                activeTestTabId = browserController.createTab(
                    initialUrl = BLANK_URL,
                    isIncognito = true,
                )
                browserController.submitAddress(server.fixtureUrl(FIXTURE_PATH))
                assertTrue(browserController.isInitialNavigationWaitingForRendererForTesting())
                assertEquals(0, server.documentRequestCount.get())
            }

            SystemClock.sleep(1_000)
            assertEquals(
                "Initial navigation must wait for renderer geometry",
                0,
                server.documentRequestCount.get(),
            )

            composeRule.runOnIdle {
                requireNotNull(controller).attachSelectedBrowserEngineView(host)
            }
            composeRule.waitUntil(timeoutMillis = 30_000L) {
                controller?.selectedTabForTesting()?.title?.startsWith(REPORT_PREFIX) == true
            }

            val report = JSONObject(
                requireNotNull(controller)
                    .selectedTabForTesting()
                    .title
                    .removePrefix(REPORT_PREFIX),
            )
            assertEquals(0.8, report.getDouble("scale"), 0.05)
            assertTrue(report.getDouble("visualWidth") > report.getDouble("innerWidth"))
            assertFalse(
                requireNotNull(controller).isInitialNavigationWaitingForRendererForTesting(),
            )
            assertEquals(
                "Initial viewport must not need a healing reload",
                1,
                server.documentRequestCount.get(),
            )
        }
    }

    private companion object {
        const val WARMUP_PATH = "/warmup"
        const val WARMUP_TITLE = "Candy Gecko warm"
        const val FIXTURE_PATH = "/site-matrix/initial-viewport"
        const val REPORT_PREFIX = "Candy initial viewport: "
        const val WARMUP_HTML = """
            <!doctype html>
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Candy Gecko warm</title>
              </head>
              <body>Warm Gecko runtime</body>
            </html>
        """
        const val HTML = """
            <!doctype html>
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=0.8">
                <title>Preparing initial viewport</title>
              </head>
              <body>
                <main>Initial viewport fixture</main>
                <script>
                  addEventListener('load', () => {
                    requestAnimationFrame(() => requestAnimationFrame(() => {
                      document.title = 'Candy initial viewport: ' + JSON.stringify({
                        scale: visualViewport.scale,
                        innerWidth,
                        visualWidth: visualViewport.width
                      });
                    }));
                  }, { once: true });
                </script>
              </body>
            </html>
        """
    }
}
