package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtension
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionPopupIdentity
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyXRayRules
import dev.sk2andy.materialbrowser.browser.gecko.GeckoRuntimeOwner
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoPrivacyXRayPopupInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null
    private var xRayTabId by mutableStateOf<String?>(null)

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            preferences().edit().clear().commit()
        }
    }

    @Test
    fun siteInfoOpensRealUblockPopupAndRespectsSelectedPrivateOwner() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val uBlock = awaitSignedUblock(scope)
            assertTrue(uBlock.enabled)
            assertFalse(uBlock.isBuiltIn)
            assertFalse("This fixture requires denied private extension access", uBlock.allowedInPrivateBrowsing)
            assertTrue("The popup must belong to Mozilla-signed uBlock", uBlock.signedState >= 2)

            val scriptRequests = ConcurrentHashMap<String, AtomicInteger>()
            EdgeToEdgeSiteFixtureServer { target ->
                val phase = target.substringAfter("phase=", "")
                when (target.substringBefore('?')) {
                    BLOCKED_PATH -> {
                        scriptRequests.computeIfAbsent(phase) { AtomicInteger() }.incrementAndGet()
                        "/* A legitimate script response if uBlock allows the request. */"
                    }
                    "/owner" -> """
                        <!doctype html><title>$OWNER_TITLE $phase</title>
                        <body>$OWNER_TITLE<script src="$BLOCKED_PATH?phase=$phase"></script></body>
                    """.trimIndent()
                    "/other" -> "<!doctype html><title>$OTHER_TITLE</title><body>$OTHER_TITLE</body>"
                    else -> "<!doctype html><title>$PRIVATE_TITLE</title><body>$PRIVATE_TITLE</body>"
                }
            }.use { server ->
                lateinit var browser: BrowserController
                composeRule.runOnIdle {
                    preferences().edit().clear()
                        .putString(
                            BrowserSessionStore.KEY_ANDROID_BROWSER_ENGINE,
                            AndroidBrowserEngineKind.GeckoView.stableId,
                        )
                        .commit()
                    browser = BrowserController(composeRule.activity)
                    controller = browser
                    browser.onResume()
                    browser.submitAddress(server.fixtureUrl("/owner?phase=1"))
                }
                val popupDrawn = AtomicBoolean()
                val captureFailure = AtomicReference<Throwable>()
                val capturePending = AtomicBoolean()
                composeRule.setContent {
                    MaterialBrowserTheme {
                        val snackbar = remember { SnackbarHostState() }
                        val selectedTabId = browser.selectedTabId
                        Box(Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { context -> FrameLayout(context) },
                                update = { container ->
                                    // Read selection in composition so tab switches rebind the native view.
                                    if (selectedTabId == browser.selectedTabId) {
                                        browser.attachSelectedBrowserEngineView(container)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            BrowserModalSurfaces(
                                controller = browser,
                                privacyXRayTabId = xRayTabId,
                                permissionRadarTabId = null,
                                permissionRadarOrigin = null,
                                filterStudioVisible = false,
                                filterStudioSelectedRuleId = null,
                                browserContentBlurTarget = null,
                                visibleProfiles = browser.profiles,
                                favoriteFeedbackEvent = null,
                                feedbackSnackbarHostState = snackbar,
                                snoozedTabsVisible = false,
                                visibleSnoozedTabs = emptyList(),
                                onOpenFilterStudio = {},
                                onPrivacyXRayDismiss = { xRayTabId = null },
                                onClearSiteData = {},
                                onPermissionOriginSelected = {},
                                onPermissionRadarDismiss = {},
                                onFilterStudioDismiss = {},
                                onFavoriteFeedbackFinished = {},
                                onSnoozedTabsDismiss = {},
                            )
                            FirefoxExtensionChrome(browser)
                        }
                    }
                }
                // Signed installation can precede uBlock's filter-engine initialization. A fresh
                // bounded navigation retries the real request; no badge or Candy data is injected.
                val blockedPhase = (1..5).firstOrNull { attempt ->
                    if (attempt > 1) {
                        composeRule.runOnIdle {
                            browser.submitAddress(server.fixtureUrl("/owner?phase=$attempt"))
                        }
                    }
                    waitForController {
                        it.selectedTab.title == "$OWNER_TITLE $attempt" && !it.selectedTab.isLoading
                    }
                    awaitObservedUblockBlock(browser)
                }
                assertTrue("Signed uBlock did not publish an observed block", blockedPhase != null)
                assertEquals(0, scriptRequests[checkNotNull(blockedPhase).toString()]?.get() ?: 0)
                var ownerTabId = ""
                composeRule.runOnIdle {
                    ownerTabId = browser.selectedTabId
                    assertEquals(0, browser.privacySnapshot(ownerTabId).totalBlocked)
                    xRayTabId = ownerTabId
                }
                composeRule.onNode(
                    SemanticsMatcher.keyIsDefined(SemanticsActions.Expand) and
                        hasAnyAncestor(hasTestTag(PrivacyXRayTestTags.Sheet)),
                    useUnmergedTree = true,
                ).performTouchInput { click(center) }
                composeRule.onNodeWithTag(PrivacyXRayTestTags.RequestBlocker).performScrollTo()
                saveScreenshot("gecko-xray-native-entry.png")
                composeRule.onNodeWithTag(PrivacyXRayTestTags.RequestBlocker).assertIsDisplayed()
                composeRule.onNodeWithTag(PrivacyXRayTestTags.RequestBlocker).performClick()

                waitForController { it.firefoxExtensionPopupView is GeckoView }
                composeRule.onNodeWithTag(FirefoxExtensionChromeTestTags.Popup).assertIsDisplayed()
                composeRule.runOnIdle {
                    assertEquals(null, xRayTabId)
                    val popupIdentity = BrowserController::class.java
                        .getDeclaredField("firefoxExtensionPopupIdentity")
                        .apply { isAccessible = true }
                        .get(browser) as GeckoExtensionPopupIdentity
                    assertEquals(U_BLOCK_ID, popupIdentity.extensionId)
                    assertEquals(ownerTabId, popupIdentity.owner.tabId)
                    assertFalse(popupIdentity.owner.isPrivate)
                }
                composeRule.waitUntil(timeoutMillis = 20_000) {
                    if (!popupDrawn.get() && capturePending.compareAndSet(false, true)) {
                        composeRule.runOnIdle {
                            val popup = browser.firefoxExtensionPopupView as GeckoView
                            popup.capturePixels().accept(
                                { pixels ->
                                    try {
                                        popupDrawn.set(pixels != null && hasRenderedPopupContent(pixels))
                                    } finally {
                                        pixels?.recycle()
                                        capturePending.set(false)
                                    }
                                },
                                { error ->
                                    captureFailure.set(error)
                                    capturePending.set(false)
                                },
                            )
                        }
                    }
                    popupDrawn.get() || captureFailure.get() != null
                }
                captureFailure.get()?.let { error -> throw AssertionError("Popup capture failed", error) }
                saveScreenshot("gecko-xray-native-ublock-popup.png")

                composeRule.runOnIdle {
                    browser.createTab(initialUrl = server.fixtureUrl("/other"), isIncognito = false)
                }
                waitForController { it.selectedTab.title == OTHER_TITLE && it.firefoxExtensionPopupView == null }
                composeRule.onNodeWithTag(FirefoxExtensionChromeTestTags.Popup).assertDoesNotExist()
                // The old sheet must not borrow the now-selected tab's uBlock action.
                composeRule.runOnIdle { xRayTabId = ownerTabId }
                composeRule.onNodeWithTag(PrivacyXRayTestTags.RequestBlocker).assertDoesNotExist()

                composeRule.runOnIdle {
                    xRayTabId = null
                    browser.createTab(initialUrl = server.fixtureUrl("/private"), isIncognito = true)
                }
                waitForController { it.selectedTab.title == PRIVATE_TITLE && !it.selectedTab.isLoading }
                composeRule.runOnIdle {
                    assertTrue(browser.selectedTab.isIncognito)
                    xRayTabId = browser.selectedTabId
                }
                composeRule.onNodeWithTag(PrivacyXRayTestTags.RequestBlocker).assertDoesNotExist()
                composeRule.onNodeWithTag(FirefoxExtensionChromeTestTags.Popup).assertDoesNotExist()
            }
        } finally {
            scope.cancel()
        }
    }

    private fun awaitSignedUblock(scope: CoroutineScope): GeckoExtension {
        val installed = AtomicReference<GeckoExtension>()
        val failure = AtomicReference<Throwable>()
        composeRule.runOnIdle {
            scope.launch {
                try {
                    installed.set(
                        GeckoRuntimeOwner.getOrCreate(composeRule.activity).extensions.listInstalled()
                            .firstOrNull { it.id == U_BLOCK_ID }
                            ?: error("Signed bundled uBlock is missing"),
                    )
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 90_000) { installed.get() != null || failure.get() != null }
        failure.get()?.let { error -> throw AssertionError("Default extension provisioning failed", error) }
        return checkNotNull(installed.get())
    }

    private fun waitForController(condition: (BrowserController) -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.runOnIdle { condition(checkNotNull(controller)) }
        }
    }

    private fun awaitObservedUblockBlock(browser: BrowserController): Boolean {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            val observed = composeRule.runOnIdle {
                val key = GeckoPrivacyXRayRules.requestBlockerAction(
                    browser.firefoxExtensionActions,
                    browser.selectedTabId,
                )
                browser.firefoxExtensionActions.firstOrNull { it.key == key }
                    ?.badgeText?.toIntOrNull()?.let { count -> count > 0 } == true
            }
            if (observed) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun hasRenderedPopupContent(pixels: Bitmap): Boolean {
        // Read native pixels only: a newly attached Surface is a flat placeholder before uBlock paints.
        val colors = mutableSetOf<Int>()
        for (y in 0 until pixels.height step 12) {
            for (x in 0 until pixels.width step 12) {
                colors += pixels.getPixel(x, y)
                if (colors.size > 12) return true
            }
        }
        return false
    }

    private fun preferences() = composeRule.activity.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), name)
                .outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val U_BLOCK_ID = "uBlock0@raymondhill.net"
        // Exact /ads/cbr.js$script filter is packaged in signed uBlock's EasyList.
        const val BLOCKED_PATH = "/ads/cbr.js"
        const val OWNER_TITLE = "Candy X-Ray popup owner"
        const val OTHER_TITLE = "Candy X-Ray other tab"
        const val PRIVATE_TITLE = "Candy X-Ray private tab"
    }
}
