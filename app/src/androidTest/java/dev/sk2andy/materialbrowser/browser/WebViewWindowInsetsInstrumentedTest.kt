package dev.sk2andy.materialbrowser.browser

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewWindowInsetsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun coverPageOnlyDrawsEdgeToEdgeAfterExplicitOptIn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    updateWebContentEdgeToEdgeEnabled(false)
                    submitAddress("https://example.test/")
                }
            }
            val webView = awaitSelectedWebView(scenario)
            val expectedTopCssPixels = AtomicReference<Float>()
            val expectedTopPixels = AtomicInteger()
            val density = AtomicReference<Float>()
            scenario.onActivity { activity ->
                val topPixels = ViewCompat.getRootWindowInsets(webView)
                    ?.getInsets(
                        WindowInsetsCompat.Type.systemBars() or
                            WindowInsetsCompat.Type.displayCutout(),
                    )
                    ?.top
                    ?: 0
                expectedTopPixels.set(topPixels)
                expectedTopCssPixels.set(topPixels / activity.resources.displayMetrics.density)
                density.set(activity.resources.displayMetrics.density)
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
                          </head>
                          <body>
                            <div id="probe" style="padding-top:env(safe-area-inset-top)"></div>
                            <button id="open-app" style="position:fixed;top:12px;right:12px">
                              App öffnen
                            </button>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }

            awaitProbe(webView)
            awaitWebViewTop(webView, expectedTopPixels.get())
            val topInset = evaluate(
                webView,
                "parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop)",
            ).toFloat()
            val controlTopCssPixels = evaluate(
                webView,
                "document.getElementById('open-app').getBoundingClientRect().top",
            ).toFloat()
            val pageWasMutated = evaluate(
                webView,
                "Boolean(document.getElementById('candy-browser-content-top-inset') || " +
                    "document.body.hasAttribute('data-candy-browser-status-inset'))",
            )

            assertTrue(expectedTopCssPixels.get() > 0f)
            assertEquals(0f, topInset, 0.1f)
            assertTrue(
                expectedTopPixels.get() +
                    (controlTopCssPixels * density.get()).roundToInt() >= expectedTopPixels.get(),
            )
            assertEquals("false", pageWasMutated)

            scenario.onActivity { activity ->
                activity.browserControllerForTesting()
                    .updateWebContentEdgeToEdgeEnabled(true)
            }
            awaitWebViewTop(webView, 0)
            assertEquals(0, previewTopInset(scenario))
            assertEquals(
                expectedTopCssPixels.get(),
                evaluate(
                    webView,
                    "parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop)",
                ).toFloat(),
                0.5f,
            )

            scenario.onActivity { activity ->
                activity.browserControllerForTesting()
                    .updateWebContentEdgeToEdgeEnabled(true)
            }
            awaitWebViewTop(webView, 0)

            val coverTabId = AtomicReference<String>()
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    coverTabId.set(selectedTabId)
                    createTab(isIncognito = false)
                    submitAddress("https://foreground.test/")
                }
            }
            awaitSelectedWebView(scenario)
            assertFalse(webView.isAttachedToWindow)

            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                controller.updateWebContentEdgeToEdgeEnabled(false)
                assertEquals(
                    expectedTopPixels.get(),
                    controller.previewTopInsetPx(coverTabId.get()),
                )
                controller.updateWebContentEdgeToEdgeEnabled(true)
                assertEquals(0, controller.previewTopInsetPx(coverTabId.get()))
            }
        }
    }

    @Test
    fun pageWithoutCoverUsesScrollableDocumentInsetWithoutScrollRelayout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    updateWebContentEdgeToEdgeEnabled(true)
                    submitAddress("https://example.test/")
                }
            }
            val webView = awaitWebView(scenario)
            val expectedTopPixels = AtomicInteger()
            scenario.onActivity {
                expectedTopPixels.set(
                    ViewCompat.getRootWindowInsets(webView)
                        ?.getInsets(
                            WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.displayCutout(),
                        )
                        ?.top
                        ?: 0,
                )
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                          <body style="min-height:4000px;margin:0">
                            <div id="flow">Flow content</div>
                            <div id="sticky" style="position:sticky;top:0">Sticky action</div>
                            <div id="probe" style="padding-top:env(safe-area-inset-top)"></div>
                            <iframe id="child" srcdoc="<html><body>Child frame</body></html>"></iframe>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }

            awaitProbe(webView)
            awaitWebViewTop(webView, 0)
            awaitDocumentTop(webView, "#flow", expectedTopPixels.get())
            val topInset = evaluate(
                webView,
                "parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop)",
            ).toFloat()

            assertTrue(expectedTopPixels.get() > 0)
            assertEquals(0f, topInset, 0.1f)
            assertEquals(
                "false",
                evaluate(
                    webView,
                    "Boolean(document.getElementById('child').contentDocument." +
                        "getElementById('candy-browser-content-top-inset'))",
                ),
            )
            awaitWebViewBottomAtParentBottom(webView)
            assertEquals(expectedTopPixels.get(), previewTopInset(scenario))

            val insetDispatchCount = AtomicInteger()
            val initialHeight = AtomicInteger()
            scenario.onActivity {
                ViewCompat.setOnApplyWindowInsetsListener(webView) { _, insets ->
                    insetDispatchCount.incrementAndGet()
                    insets
                }
                initialHeight.set(webView.height)
            }

            evaluate(webView, "window.scrollTo(0, 1000)")
            awaitWebViewScrollY(webView, minimumScrollY = 1000)
            awaitWebViewTop(webView, 0)
            assertEquals(0, previewTopInset(scenario))
            scenario.onActivity {
                val layoutParams = webView.layoutParams as ViewGroup.MarginLayoutParams
                assertEquals(0, layoutParams.topMargin)
                assertEquals(initialHeight.get(), webView.height)
                assertFalse(webView.isLayoutRequested)
                assertEquals(0, insetDispatchCount.get())
            }
            assertEquals(
                0f,
                evaluate(webView, "document.getElementById('sticky').getBoundingClientRect().top")
                    .toFloat(),
                0.1f,
            )

            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.setForceSafeArea(controller.selectedTabId, true))
            }
            awaitWebViewTop(webView, expectedTopPixels.get())
            awaitDocumentTop(webView, "#sticky", 0)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.setForceSafeArea(controller.selectedTabId, false))
            }
            awaitWebViewTop(webView, 0)

            evaluate(webView, "window.scrollTo(0, 0)")
            awaitWebViewScrollY(webView, minimumScrollY = 0, exact = true)
            awaitWebViewTop(webView, 0)
            awaitDocumentTop(webView, "#flow", expectedTopPixels.get())
            assertEquals(expectedTopPixels.get(), previewTopInset(scenario))

            evaluate(
                webView,
                "document.querySelector('meta[name=viewport]').content = 'viewport-fit=cover'",
            )
            awaitWebViewTop(webView, 0)
            awaitDocumentTop(webView, "#flow", 0)

            evaluate(
                webView,
                "document.head.insertAdjacentHTML('beforeend', " +
                    "'<meta id=secondary-viewport name=viewport content=width=device-width>')",
            )
            awaitDocumentTop(webView, "#flow", expectedTopPixels.get())
            evaluate(webView, "document.getElementById('secondary-viewport').remove()")
            awaitDocumentTop(webView, "#flow", 0)

            evaluate(
                webView,
                "document.querySelector('meta[name=viewport]').content = " +
                    "'viewport-fit=cover, viewport-fit=contain'",
            )
            awaitDocumentTop(webView, "#flow", expectedTopPixels.get())

            evaluate(
                webView,
                "document.querySelector('meta[name=viewport]').content = " +
                    "'viewport-fit=cover; width=device-width'",
            )
            awaitDocumentTop(webView, "#flow", expectedTopPixels.get())

            evaluate(
                webView,
                "document.querySelector('meta[name=viewport]').content = 'viewport-fit=cover'",
            )
            awaitDocumentTop(webView, "#flow", 0)

            evaluate(webView, "document.querySelector('meta[name=viewport]').remove()")
            awaitDocumentTop(webView, "#flow", expectedTopPixels.get())
        }
    }

    @Test
    fun rootScrollContainerUsesTargetedFlowInsetWithoutLosingEdgeToEdge() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    updateWebContentEdgeToEdgeEnabled(true)
                    submitAddress("https://example.test/")
                }
            }
            val webView = awaitWebView(scenario)
            val expectedTopPixels = AtomicInteger()
            scenario.onActivity {
                expectedTopPixels.set(
                    ViewCompat.getRootWindowInsets(webView)
                        ?.getInsets(
                            WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.displayCutout(),
                        )
                        ?.top
                        ?: 0,
                )
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html style="height:100%">
                          <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <style>html::before { position:absolute !important }</style>
                          </head>
                          <body style="height:100%;margin:0;overflow:auto scroll">
                            <div id="page-shell" style="position:relative;min-height:4000px">
                              <header id="probe" style="height:56px">Header</header>
                              <button id="menu" style="position:absolute;left:4px;top:4px">
                                Menu
                              </button>
                            </div>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }

            awaitProbe(webView)
            awaitDocumentReady(webView)
            awaitWebViewTop(webView, 0)
            awaitDocumentTop(webView, "#page-shell", expectedTopPixels.get())
            assertEquals(
                "\"true\"",
                evaluate(
                    webView,
                    "document.getElementById('page-shell')." +
                        "getAttribute('data-candy-browser-top-inset-flow-target')",
                ),
            )
            val initialHeaderTop = evaluate(
                webView,
                "document.getElementById('probe').getBoundingClientRect().top",
            ).toFloat()
            evaluate(webView, "window.scrollTo(0, 500)")
            awaitWebViewScrollY(webView, minimumScrollY = 500)
            awaitWebViewTop(webView, 0)
            val scrolledHeaderTop = evaluate(
                webView,
                "document.getElementById('probe').getBoundingClientRect().top",
            ).toFloat()
            assertTrue(scrolledHeaderTop < initialHeaderTop)
        }
    }

    @Test
    fun strictStyleCspStillGetsScrollableDocumentInset() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    updateWebContentEdgeToEdgeEnabled(true)
                    submitAddress("https://example.test/")
                }
            }
            val webView = awaitWebView(scenario)
            val expectedTopPixels = AtomicInteger()
            scenario.onActivity {
                expectedTopPixels.set(
                    ViewCompat.getRootWindowInsets(webView)
                        ?.getInsets(
                            WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.displayCutout(),
                        )
                        ?.top
                        ?: 0,
                )
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <head>
                            <meta http-equiv="Content-Security-Policy"
                                content="default-src 'none'; style-src 'none'">
                          </head>
                          <body><div id="probe">Strict CSP</div></body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }

            awaitProbe(webView)
            awaitPseudoInset(webView, expectedTopPixels.get())
        }
    }

    @Test
    fun responseHeaderCspKeepsContentInSafeArea() {
        HeaderCspPageServer().use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().run {
                        updateWebContentEdgeToEdgeEnabled(true)
                        submitAddress(server.url)
                    }
                }
                val webView = awaitWebView(scenario)
                val expectedTopPixels = AtomicInteger()
                scenario.onActivity {
                    expectedTopPixels.set(
                        ViewCompat.getRootWindowInsets(webView)
                            ?.getInsets(
                                WindowInsetsCompat.Type.systemBars() or
                                    WindowInsetsCompat.Type.displayCutout(),
                            )
                            ?.top
                            ?: 0,
                    )
                }

                awaitProbe(webView)
                awaitNativeOrDocumentInset(webView, expectedTopPixels.get())
            }
        }
    }

    @Test
    fun fixedRootUiFallsBackToNativeSafeArea() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    updateWebContentEdgeToEdgeEnabled(true)
                    submitAddress("https://example.test/")
                }
            }
            val webView = awaitWebView(scenario)
            val expectedTopPixels = AtomicInteger()
            scenario.onActivity {
                expectedTopPixels.set(
                    ViewCompat.getRootWindowInsets(webView)
                        ?.getInsets(
                            WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.displayCutout(),
                        )
                        ?.top
                        ?: 0,
                )
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <body style="margin:0;min-height:4000px">
                            <div id="probe" style="position:fixed;inset:0"></div>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }

            awaitProbe(webView)
            awaitWebViewTop(webView, expectedTopPixels.get())
        }
    }

    @Test
    fun topLeftAbsoluteUiKeepsEdgeToEdgeWithLocalOffset() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    updateWebContentEdgeToEdgeEnabled(true)
                    submitAddress("https://example.test/")
                }
            }
            val webView = awaitWebView(scenario)
            val expectedTopPixels = AtomicInteger()
            scenario.onActivity {
                expectedTopPixels.set(
                    ViewCompat.getRootWindowInsets(webView)
                        ?.getInsets(
                            WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.displayCutout(),
                        )
                        ?.top
                        ?: 0,
                )
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <body style="margin:0;min-height:4000px">
                            <main id="probe">Flow content</main>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }

            awaitProbe(webView)
            awaitDocumentReady(webView)
            awaitPseudoInset(webView, expectedTopPixels.get())
            evaluate(
                webView,
                """
                    (() => {
                      const root = document.documentElement;
                      const inset = parseFloat(
                        getComputedStyle(root).getPropertyValue(
                          '--candy-browser-content-top-inset',
                        ),
                      );
                      const nav = document.createElement('nav');
                      nav.id = 'top-control';
                      nav.style.cssText =
                        'position:absolute;left:16px;top:' + Math.max(inset - 8, 1) +
                        'px;width:24px;height:24px;transform:scale(1)';
                      nav.innerHTML = '<button style="width:100%;height:100%">Menu</button>';
                      document.body.appendChild(nav);
                    })()
                """.trimIndent(),
            )
            awaitWebViewTop(webView, 0)
            awaitDocumentTop(webView, "#top-control", expectedTopPixels.get())
            assertEquals(
                "\"matrix(1, 0, 0, 1, 0, 0)\"",
                evaluate(webView, "getComputedStyle(document.getElementById('top-control')).transform"),
            )
        }
    }

    @Test
    fun lateFixedUiAfterScrollKeepsEdgeToEdgeWithLocalOffset() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    updateWebContentEdgeToEdgeEnabled(true)
                    submitAddress("https://example.test/")
                }
            }
            val webView = awaitWebView(scenario)
            val expectedTopPixels = AtomicInteger()
            scenario.onActivity {
                expectedTopPixels.set(
                    ViewCompat.getRootWindowInsets(webView)
                        ?.getInsets(
                            WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.displayCutout(),
                        )
                        ?.top
                        ?: 0,
                )
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <body style="margin:0;min-height:4000px">
                            <main id="probe">Flow content</main>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }

            awaitProbe(webView)
            awaitDocumentReady(webView)
            awaitPseudoInset(webView, expectedTopPixels.get())
            evaluate(webView, "window.scrollTo(0, 500)")
            awaitWebViewScrollY(webView, minimumScrollY = 500)
            evaluate(
                webView,
                "document.body.insertAdjacentHTML('beforeend', " +
                    "'<button id=top-control style=\"position:fixed;left:16px;top:16px;" +
                    "width:24px;height:24px\">Menu</button>')",
            )
            awaitWebViewTop(webView, 0)
            awaitDocumentTop(webView, "#top-control", expectedTopPixels.get())
            awaitWebViewScrollY(webView, minimumScrollY = 500)
        }
    }

    @Test
    fun cssToggledFixedDrawerKeepsItsFirstActionBelowStatusBar() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    updateWebContentEdgeToEdgeEnabled(true)
                    submitAddress("https://example.test/")
                }
            }
            val webView = awaitWebView(scenario)
            val expectedTopPixels = AtomicInteger()
            scenario.onActivity {
                expectedTopPixels.set(
                    ViewCompat.getRootWindowInsets(webView)
                        ?.getInsets(
                            WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.displayCutout(),
                        )
                        ?.top
                        ?: 0,
                )
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <style>
                              body { margin: 0; min-height: 4000px }
                              #drawer, #drawer-mask { visibility: hidden }
                              body.drawer-open #drawer,
                              body.drawer-open #drawer-mask { visibility: visible }
                              #drawer {
                                box-sizing: border-box;
                                position: fixed;
                                inset: 0 auto 0 0;
                                width: 70vw;
                                overflow: auto;
                                z-index: 2;
                              }
                              #drawer-mask {
                                position: fixed;
                                inset: 0;
                                background: rgb(0 0 0 / 50%);
                                z-index: 1;
                              }
                              #first-drawer-action, #last-drawer-action {
                                display: block;
                                height: 56px;
                              }
                            </style>
                          </head>
                          <body>
                            <main id="probe">Flow content</main>
                            <button
                              id="drawer-toggle"
                              onclick="setTimeout(() => document.body.classList.add('drawer-open'), 150)"
                            >
                              Menu
                            </button>
                            <label id="drawer-mask"></label>
                            <aside id="drawer">
                              <a id="first-drawer-action" href="#first">Home</a>
                              <div style="height:2000px">Drawer content</div>
                              <a id="last-drawer-action" href="#last">Settings</a>
                            </aside>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }

            awaitProbe(webView)
            awaitDocumentReady(webView)
            awaitWebViewTop(webView, 0)
            evaluate(webView, "document.getElementById('drawer-toggle').click()")
            awaitDocumentTop(webView, "#first-drawer-action", expectedTopPixels.get())
            assertEquals(
                "\"true\"",
                evaluate(
                    webView,
                    "document.getElementById('drawer')." +
                        "getAttribute('data-candy-browser-top-inset-panel')",
                ),
            )
            assertTrue(
                evaluate(
                    webView,
                    "document.getElementById('drawer').getBoundingClientRect().bottom <= " +
                        "window.innerHeight + 0.5",
                ) == "true",
            )
            evaluate(
                webView,
                "document.getElementById('last-drawer-action').scrollIntoView(false)",
            )
            assertTrue(
                evaluate(
                    webView,
                    "document.getElementById('last-drawer-action')." +
                        "getBoundingClientRect().bottom <= window.innerHeight + 0.5",
                ) == "true",
            )
            evaluate(
                webView,
                "document.body.classList.remove('drawer-open');" +
                    "const shell = document.createElement('div');" +
                    "shell.style.cssText = 'position:fixed;inset:0;z-index:1';" +
                    "document.body.appendChild(shell)",
            )
            awaitWebViewTop(webView, expectedTopPixels.get())
        }
    }

    @Test
    fun forcedSafeAreaKeepsCoverPageBelowStatusBarWhileScrolling() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    updateWebContentEdgeToEdgeEnabled(true)
                    submitAddress("https://example.test/")
                }
            }
            val webView = awaitSelectedWebView(scenario)
            val expectedTopPixels = AtomicInteger()
            scenario.onActivity { activity ->
                expectedTopPixels.set(
                    ViewCompat.getRootWindowInsets(webView)
                        ?.getInsets(
                            WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.displayCutout(),
                        )
                        ?.top
                        ?: 0,
                )
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <head>
                            <meta name="viewport" content="width=device-width, viewport-fit=cover">
                          </head>
                          <body style="min-height:4000px">
                            <div id="probe" style="position:fixed;top:0">Sticky action</div>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }

            awaitProbe(webView)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.setForceSafeArea(controller.selectedTabId, true))
            }
            awaitWebViewTop(webView, expectedTopPixels.get())
            evaluate(webView, "window.scrollTo(0, 1000)")
            awaitWebViewTop(webView, expectedTopPixels.get())

            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.setForceSafeArea(controller.selectedTabId, false))
            }
            awaitWebViewTop(webView, 0)
        }
    }

    @Test
    fun browserChromeOwnedImeFramesAreDeduplicatedAndRestored() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().submitAddress("https://example.test/")
            }
            val webView = awaitWebView(scenario)
            val dispatchCount = AtomicInteger()
            val lastImeBottom = AtomicInteger(-1)

            scenario.onActivity { activity ->
                ViewCompat.setOnApplyWindowInsetsListener(webView) { _, insets ->
                    dispatchCount.incrementAndGet()
                    lastImeBottom.set(
                        insets.getInsets(WindowInsetsCompat.Type.ime()).bottom,
                    )
                    insets
                }
                val controller = activity.browserControllerForTesting()
                controller.onWindowInsetsChanged(imeInsets(bottom = 0, visible = false))
                controller.setBrowserChromeOwnsIme(true)
                val countAfterOwnership = dispatchCount.get()

                controller.onWindowInsetsChanged(imeInsets(bottom = 400, visible = true))
                controller.onWindowInsetsChanged(imeInsets(bottom = 700, visible = true))

                assertEquals(countAfterOwnership, dispatchCount.get())
                assertEquals(0, lastImeBottom.get())

                controller.setBrowserChromeOwnsIme(false)
                assertEquals(countAfterOwnership + 1, dispatchCount.get())
                assertEquals(700, lastImeBottom.get())

                controller.onWindowInsetsChanged(imeInsets(bottom = 0, visible = false))
                assertEquals(countAfterOwnership + 2, dispatchCount.get())
                assertEquals(0, lastImeBottom.get())
            }
        }
    }

    private fun imeInsets(bottom: Int, visible: Boolean): WindowInsetsCompat =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
            .setVisible(WindowInsetsCompat.Type.ime(), visible)
            .build()

    private fun awaitWebView(scenario: ActivityScenario<MainActivity>): WebView {
        val result = AtomicReference<WebView>()
        repeat(200) {
            scenario.onActivity { activity ->
                result.compareAndSet(null, findWebView(activity.window.decorView))
            }
            result.get()?.let { return it }
            SystemClock.sleep(50)
        }
        assertNotNull("WebView was not attached", result.get())
        return result.get()
    }

    private fun awaitSelectedWebView(scenario: ActivityScenario<MainActivity>): WebView {
        val result = AtomicReference<WebView>()
        repeat(200) {
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().selectedWebViewForTesting()
                    .takeIf { webView ->
                        webView.isAttachedToWindow && webView.width > 0 && webView.height > 0
                    }
                    ?.let { webView -> result.compareAndSet(null, webView) }
            }
            result.get()?.let { return it }
            SystemClock.sleep(50)
        }
        return checkNotNull(result.get()) { "Selected WebView was not attached" }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun awaitProbe(webView: WebView) {
        repeat(100) {
            if (evaluate(webView, "Boolean(document.getElementById('probe'))") == "true") return
            SystemClock.sleep(50)
        }
        throw AssertionError("WebView test page did not finish loading")
    }

    private fun awaitDocumentReady(webView: WebView) {
        repeat(100) {
            if (evaluate(webView, "document.readyState") == "\"complete\"") return
            SystemClock.sleep(50)
        }
        throw AssertionError("WebView test page did not reach complete ready state")
    }

    private fun previewTopInset(scenario: ActivityScenario<MainActivity>): Int {
        val result = AtomicInteger()
        scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            result.set(controller.previewTopInsetPx(controller.selectedTabId))
        }
        return result.get()
    }

    private fun awaitWebViewTop(webView: WebView, expectedTop: Int) {
        val location = IntArray(2)
        repeat(100) {
            instrumentation.runOnMainSync { webView.getLocationInWindow(location) }
            if (location[1] == expectedTop) return
            SystemClock.sleep(50)
        }
        val layoutParams = webView.layoutParams as? ViewGroup.MarginLayoutParams
        throw AssertionError(
            "WebView top was ${location[1]}, expected $expectedTop; " +
                "scrollY=${webView.scrollY}, margin=${layoutParams?.topMargin}, " +
                "translationY=${webView.translationY}",
        )
    }

    private fun awaitWebViewScrollY(
        webView: WebView,
        minimumScrollY: Int,
        exact: Boolean = false,
    ) {
        repeat(100) {
            var scrollY = 0
            instrumentation.runOnMainSync { scrollY = webView.scrollY }
            if ((exact && scrollY == minimumScrollY) || (!exact && scrollY >= minimumScrollY)) return
            SystemClock.sleep(50)
        }
        throw AssertionError(
            "WebView scrollY was ${webView.scrollY}, expected " +
                if (exact) "$minimumScrollY" else "at least $minimumScrollY",
        )
    }

    private fun awaitDocumentTop(
        webView: WebView,
        selector: String,
        expectedTopPixels: Int,
    ) {
        repeat(100) {
            val topPixels = evaluate(
                webView,
                "document.querySelector('$selector').getBoundingClientRect().top * " +
                    "window.devicePixelRatio",
            ).toFloat().roundToInt()
            if (topPixels == expectedTopPixels) return
            SystemClock.sleep(50)
        }
        val topPixels = evaluate(
            webView,
            "document.querySelector('$selector').getBoundingClientRect().top * " +
                "window.devicePixelRatio",
        ).toFloat().roundToInt()
        throw AssertionError(
            "Document element $selector top was $topPixels, expected $expectedTopPixels",
        )
    }

    private fun awaitPseudoInset(webView: WebView, expectedTopPixels: Int) {
        repeat(100) {
            val inset = evaluate(
                webView,
                "parseFloat(getComputedStyle(document.documentElement, '::before').height) * " +
                    "window.devicePixelRatio",
            ).toFloatOrNull()?.roundToInt()
            if (inset == expectedTopPixels) return
            SystemClock.sleep(50)
        }
        throw AssertionError(
            "Document root spacer did not reach $expectedTopPixels physical pixels",
        )
    }

    private fun awaitNativeOrDocumentInset(webView: WebView, expectedTopPixels: Int) {
        val location = IntArray(2)
        repeat(100) {
            instrumentation.runOnMainSync { webView.getLocationInWindow(location) }
            val documentInset = evaluate(
                webView,
                "parseFloat(getComputedStyle(document.documentElement, '::before').height) * " +
                    "window.devicePixelRatio",
            ).toFloatOrNull()?.roundToInt() ?: 0
            if (location[1] == expectedTopPixels || documentInset == expectedTopPixels) return
            SystemClock.sleep(50)
        }
        throw AssertionError(
            "Neither native nor document inset kept the page below $expectedTopPixels pixels",
        )
    }

    private fun awaitWebViewBottomAtParentBottom(webView: WebView) {
        val webViewLocation = IntArray(2)
        val parentLocation = IntArray(2)
        repeat(100) {
            var webViewBottom = 0
            var parentBottom = 0
            instrumentation.runOnMainSync {
                val parent = webView.parent as View
                webView.getLocationInWindow(webViewLocation)
                parent.getLocationInWindow(parentLocation)
                webViewBottom = webViewLocation[1] + webView.height
                parentBottom = parentLocation[1] + parent.height
            }
            if (webViewBottom == parentBottom) return
            SystemClock.sleep(50)
        }
        throw AssertionError("WebView did not reach its parent's bottom edge")
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

    private class HeaderCspPageServer : Closeable {
        private val body =
            "<!doctype html><html><body><div id=probe>Header CSP</div></body></html>"
                .toByteArray(Charsets.UTF_8)
        private val serverSocket = ServerSocket(
            0,
            8,
            InetAddress.getByName("127.0.0.1"),
        )
        private val serverThread = Thread({ serve() }, "candy-inset-test-server").apply {
            isDaemon = true
            start()
        }

        val url: String = "http://127.0.0.1:${serverSocket.localPort}/strict-csp.html"

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
                                    "Content-Security-Policy: default-src 'none'; " +
                                    "style-src 'none'\r\n" +
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

        companion object {
            private val HTTP_HEADER_END = byteArrayOf(13, 10, 13, 10)
        }
    }
}
