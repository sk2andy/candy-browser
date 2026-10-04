package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyXRayEngineInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            preferences().edit().clear().commit()
        }
    }

    @Test
    fun geckoExplainsMissingExtensionTelemetryInsteadOfClaimingNoBlocks() {
        showSiteInfo(AndroidBrowserEngineKind.GeckoView)
        try {
            composeRule.onNode(
                SemanticsMatcher.keyIsDefined(SemanticsActions.Expand) and
                    hasAnyAncestor(hasTestTag(PrivacyXRayTestTags.Sheet)),
                useUnmergedTree = true,
            ).performTouchInput { click(center) }
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.privacy_gecko_request_blocking)).performScrollTo().assertExists()
            composeRule.onNodeWithText(
                composeRule.activity.getString(R.string.filter_studio_open),
            ).assertDoesNotExist()
        } finally {
            saveScreenshot("gecko-xray-source.png")
        }
    }

    @Test
    fun systemWebViewKeepsCandyCountersAndStudio() {
        showSiteInfo(AndroidBrowserEngineKind.SystemWebView)
        composeRule.onNodeWithTag(PrivacyXRayTestTags.Total).performScrollTo().assertExists()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.filter_studio_open),
        ).assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.privacy_gecko_request_blocking)).assertDoesNotExist()
        saveScreenshot("system-webview-xray.png")
    }

    private fun showSiteInfo(engine: AndroidBrowserEngineKind) {
        lateinit var browser: BrowserController
        composeRule.runOnIdle {
            preferences().edit().clear()
                .putString(BrowserSessionStore.KEY_ANDROID_BROWSER_ENGINE, engine.stableId)
                .commit()
            browser = BrowserController(composeRule.activity)
            controller = browser
        }
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(Modifier.fillMaxSize()) {
                    BrowserModalSurfaces(
                        controller = browser,
                        privacyXRayTabId = browser.selectedTabId,
                        permissionRadarTabId = null,
                        permissionRadarOrigin = null,
                        filterStudioVisible = false,
                        filterStudioSelectedRuleId = null,
                        browserContentBlurTarget = null,
                        visibleProfiles = browser.profiles,
                        favoriteFeedbackEvent = null,
                        feedbackSnackbarHostState = SnackbarHostState(),
                        snoozedTabsVisible = false,
                        visibleSnoozedTabs = emptyList(),
                        onOpenFilterStudio = {},
                        onPrivacyXRayDismiss = {},
                        onClearSiteData = {},
                        onPermissionOriginSelected = {},
                        onPermissionRadarDismiss = {},
                        onFilterStudioDismiss = {},
                        onFavoriteFeedbackFinished = {},
                        onSnoozedTabsDismiss = {},
                    )
                }
            }
        }
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
}
