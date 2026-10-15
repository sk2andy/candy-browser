package dev.sk2andy.materialbrowser.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.work.WorkManager
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.SiteWidgetProvider
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetFormat
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetSnapshot
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetRefreshScheduler
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import dev.sk2andy.materialbrowser.data.SiteWidgetStore
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SiteWidgetFlowInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val sessionStore = BrowserSessionStore(context)
    private val widgetStore = SiteWidgetStore(context)
    private val manager = AppWidgetManager.getInstance(context)
    private val preferences = context.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val createdWidgetIds = mutableSetOf<Int>()

    @Before
    fun setUp() {
        val supported = manager.isRequestPinAppWidgetSupported
        if (Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") {
            assertTrue("The dedicated emulator launcher must support widget pinning", supported)
        } else {
            assumeTrue("Launcher does not support widget pinning", supported)
        }
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        sessionStore.saveStartupAnimationEnabled(false)
        sessionStore.saveOpenHomeOnStartupEnabled(false)
        sessionStore.saveSearchSuggestionProvider(SearchSuggestionProvider.None)
        sessionStore.saveRecallEnabled(false)
        sessionStore.saveProfilesEnabled(true)
        sessionStore.saveProfiles(
            listOf(
                BrowserProfile(id = SOURCE_PROFILE_ID, emoji = "🏠"),
                BrowserProfile(id = OTHER_PROFILE_ID, emoji = "💼"),
            ),
            SOURCE_PROFILE_ID,
        )
        assertTrue(sessionStore.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.SystemWebView))
    }

    @After
    fun tearDown() {
        createdWidgetIds.forEach {
            SiteWidgetRefreshScheduler.cancel(context, it)
            widgetStore.deleteWidget(it)
        }
        preferences.edit().clear().commit()
    }

    @Test
    fun selectedWebsiteAreaPinsOpensSourceProfileAndCanBeReselected() {
        EdgeToEdgeSiteFixtureServer { path ->
            dashboardHtml(if (path.substringBefore('?') == PAGE_PATH) PAGE_TITLE else OTHER_TITLE)
        }.use { server ->
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                val activityReference = AtomicReference<MainActivity>()
                val originalIntent = AtomicReference<Intent>()
                scenario.onActivity { activity ->
                    activityReference.set(activity)
                    originalIntent.set(Intent(activity.intent))
                }
                try {
                    val url = server.fixtureUrl(PAGE_PATH)
                    val previousIds = widgetIds()
                    scenario.onActivity { activity ->
                        activity.browserControllerForTesting().submitAddress(url)
                    }
                    awaitPage(scenario, url, PAGE_TITLE, SOURCE_PROFILE_ID)
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        assertTrue(controller.canCreateSiteWidget)
                        controller.requestSiteWidgetEditor()
                    }
                    awaitEditor()
                    setSelectionSize(2f)
                    composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Interval).performScrollTo()
                        .performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
                    saveScreenshot("site-widget-editor.png")
                    composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Submit).performScrollTo()
                        .performSemanticsAction(SemanticsActions.OnClick) { it() }

                    val confirmation = device.wait(
                        Until.findObject(
                            By.text(
                                Pattern.compile(
                                    "Add( to home screen| automatically)?|Hinzufügen|Zum Startbildschirm hinzufügen",
                                    Pattern.CASE_INSENSITIVE,
                                ),
                            ).clickable(true),
                        ),
                        TIMEOUT_MS,
                    ) ?: run {
                        saveScreenshot("site-widget-pin-window.png")
                        device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "site-widget-pin-window.xml"))
                        throw AssertionError("Launcher did not show its widget pin confirmation")
                    }
                    confirmation.click()

                    var pinnedWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                    awaitCondition("Launcher did not bind the selected website crop") {
                        val candidate = (widgetIds() - previousIds).firstOrNull { id ->
                            widgetStore.snapshotForWidget(id) != null
                        }
                        if (candidate != null) pinnedWidgetId = candidate
                        candidate != null
                    }
                    createdWidgetIds += pinnedWidgetId
                    val snapshot = requireNotNull(widgetStore.snapshotForWidget(pinnedWidgetId))
                    assertSnapshot(snapshot, url)
                    assertEquals(SiteWidgetFormat.FourByThree, snapshot.format)
                    assertEquals(30, snapshot.refreshIntervalMinutes)
                    awaitCondition("Chosen refresh interval was not scheduled") {
                        val scheduled = WorkManager.getInstance(context)
                            .getWorkInfosForUniqueWork("site_widget_periodic_$pinnedWidgetId")
                            .get(5, TimeUnit.SECONDS)
                        scheduled.singleOrNull()?.periodicityInfo?.repeatIntervalMillis == 1_800_000L
                    }
                    assertTrue(snapshot.crop.width < 1f)
                    assertTrue(snapshot.crop.top > 0f)

                    // A same-profile click must navigate even when another document is selected.
                    val otherUrl = server.fixtureUrl(OTHER_PATH)
                    scenario.onActivity { it.browserControllerForTesting().submitAddress(otherUrl) }
                    awaitPage(scenario, otherUrl, OTHER_TITLE, SOURCE_PROFILE_ID)
                    val widgetImage = showWidgetOnHome()
                    saveScreenshot("site-widget-home.png")
                    widgetImage.click()
                    awaitPage(scenario, url, PAGE_TITLE, SOURCE_PROFILE_ID)

                    // A different active profile must never receive the widget's website.
                    scenario.onActivity { activity ->
                        assertTrue(activity.browserControllerForTesting().selectProfile(OTHER_PROFILE_ID))
                    }
                    showWidgetOnHome().click()
                    awaitPage(scenario, url, PAGE_TITLE, SOURCE_PROFILE_ID)

                    showWidgetOnHome()
                    val refresh = device.wait(
                        Until.findObject(By.desc(context.getString(R.string.site_widget_refresh))),
                        TIMEOUT_MS,
                    ) ?: throw AssertionError("Pinned widget has no refresh action")
                    val beforeManualRefresh = requireNotNull(widgetStore.snapshotForWidget(pinnedWidgetId))
                    refresh.click()
                    awaitCondition("Manual refresh did not replace the snapshot") {
                        widgetStore.snapshotForWidget(pinnedWidgetId)?.id?.let { it != beforeManualRefresh.id } == true
                    }
                    val refreshed = requireNotNull(widgetStore.snapshotForWidget(pinnedWidgetId))
                    assertEquals(snapshot.crop, refreshed.crop)
                    assertEquals(snapshot.source, refreshed.source)
                    context.startActivity(
                        Intent(context, dev.sk2andy.materialbrowser.SiteWidgetConfigurationActivity::class.java)
                            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pinnedWidgetId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    awaitEditor()
                    listOf(SiteWidgetCropEditorTestTags.Columns, SiteWidgetCropEditorTestTags.Rows).forEach { tag ->
                        composeRule.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled()
                    }
                    setSelectionSize(3f)
                    composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Submit).performScrollTo()
                        .performSemanticsAction(SemanticsActions.OnClick) { it() }
                    awaitCondition("Reselecting did not replace the existing widget binding") {
                        widgetStore.snapshotForWidget(pinnedWidgetId)?.id?.let { it != refreshed.id } == true
                    }
                    val replacement = requireNotNull(widgetStore.snapshotForWidget(pinnedWidgetId))
                    assertSnapshot(replacement, url)
                    assertEquals(snapshot.format, replacement.format)
                    assertTrue(replacement.crop.width < snapshot.crop.width)
                    assertNull("Replaced screenshot must be deleted", widgetStore.load(snapshot.id))
                    composeRule.waitUntil(TIMEOUT_MS) {
                        !composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Screen).isDisplayed()
                    }
                    instrumentation.waitForIdleSync()
                } finally {
                    finishActivity(requireNotNull(activityReference.get()), requireNotNull(originalIntent.get()))
                }
            }
        }
    }

    private fun finishActivity(activity: MainActivity, originalIntent: Intent) {
        instrumentation.runOnMainSync {
            if (!activity.isDestroyed) {
                // Widget OPEN/EDIT calls setIntent. Restore the launch identity so ActivityScenario
                // can observe the final lifecycle transition before its normal close operation.
                activity.intent = Intent(originalIntent)
                activity.finish()
            }
        }
        awaitCondition("Widget flow activity did not finish") {
            var destroyed = false
            instrumentation.runOnMainSync { destroyed = activity.isDestroyed }
            destroyed
        }
        instrumentation.waitForIdleSync()
    }

    private fun awaitEditor() {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Preview).isDisplayed()
        }
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Preview).assertIsDisplayed()
    }

    private fun setSelectionSize(fraction: Float) {
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Zoom).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(fraction) }
        composeRule.waitForIdle()
    }

    private fun widgetIds(): Set<Int> = manager.getAppWidgetIds(
        SiteWidgetProvider.component(context, SiteWidgetFormat.FourByThree),
    ).toSet()

    private fun assertSnapshot(snapshot: SiteWidgetSnapshot, url: String) {
        assertEquals(url, snapshot.source.url)
        assertEquals(PAGE_TITLE, snapshot.source.title)
        assertEquals(SOURCE_PROFILE_ID, snapshot.source.profileId)
        assertFalse(snapshot.source.isPrivate)
        val image = requireNotNull(widgetStore.loadBitmap(snapshot.id))
        try {
            assertTrue(image.width in 1..1_280)
            assertTrue(image.height in 1..1_280)
            assertTrue("Saved crop must have physical 4:3 aspect", abs(image.width - image.height * 4f / 3f) <= 2f)
            assertTrue("Dashboard crop must contain visible color", image.getPixel(image.width / 2, image.height / 2) != 0)
        } finally {
            image.recycle()
        }
    }

    private fun showWidgetOnHome(): UiObject2 {
        device.pressHome()
        val selector = By.desc(PAGE_TITLE)
        repeat(4) {
            device.wait(Until.findObject(selector), 2_000L)?.let { return it }
            device.swipe(
                (device.displayWidth * 0.85f).toInt(),
                device.displayHeight / 2,
                (device.displayWidth * 0.15f).toInt(),
                device.displayHeight / 2,
                20,
            )
        }
        throw AssertionError("Pinned website crop is not visible on the home screen")
    }

    private fun awaitPage(
        scenario: ActivityScenario<MainActivity>,
        url: String,
        title: String,
        profileId: String,
    ) {
        awaitCondition("Website did not open in its source profile: $title") {
            var ready = false
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val view = controller.selectedBrowserEngineViewForTesting()
                ready = controller.selectedTab.url == url && controller.selectedTab.title == title &&
                    controller.activeProfileId == profileId && !controller.selectedTab.isLoading &&
                    view != null && view.isAttachedToWindow && view.isShown &&
                    view.width > 0 && view.height > 0
            }
            ready
        }
        instrumentation.waitForIdleSync()
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50L)
        }
        throw AssertionError(message)
    }

    private fun saveScreenshot(name: String) {
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val directory = requireNotNull(context.getExternalFilesDir(null))
            File(directory, name).outputStream().use { output ->
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            image.recycle()
        }
    }

    private fun dashboardHtml(title: String): String = """
        <!doctype html>
        <html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta name="color-scheme" content="light only">
        <title>$title</title>
        <style>
          *{box-sizing:border-box} html,body{margin:0;min-height:100vh;color-scheme:light}
          body{background:#edf3f9;color:#192b43;font:16px system-ui;padding:30px 22px}
          .eyebrow{font-size:11px;letter-spacing:3px;text-transform:uppercase;color:#668099}
          h1{font-size:34px;letter-spacing:-1.5px;margin:10px 0 6px} .intro{color:#6b7e91;margin:0 0 30px}
          .panel{background:#142d47;border-radius:24px;padding:24px;color:#fff;box-shadow:0 12px 30px #14304b18}
          .label{font-size:12px;color:#acc7db;letter-spacing:1px;text-transform:uppercase}
          .status{display:flex;align-items:center;gap:8px;font-size:13px;margin:18px 0 6px;color:#95f2cd}
          .dot{width:8px;height:8px;background:#76e5ba;border-radius:50%}
          .temperature{font-size:64px;font-weight:650;letter-spacing:-4px;line-height:1.15}
          .sub{font-size:13px;color:#a3c3d8;margin:8px 0 22px}
          .grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}
          .tile{padding:16px;background:#ffffff12;border:1px solid #ffffff12;border-radius:16px}
          .tile strong{display:block;font-size:24px;margin-top:8px;letter-spacing:-1px}
          .tile span{color:#abc5da;font-size:12px}
          .footer{display:flex;justify-content:space-between;margin-top:18px;color:#acc7db;font-size:11px}
          .note{margin:24px 6px;color:#698199;font-size:13px;line-height:1.6}
        </style></head><body>
          <div class="eyebrow">Candy · Connected home</div>
          <h1>Home, at a glance.</h1>
          <p class="intro">A little calm. Everything connected.</p>
          <section class="panel">
            <div class="label">Living room</div>
            <div class="status"><span class="dot"></span> All systems comfortable</div>
            <div class="temperature">21.5°</div>
            <p class="sub">Feels just right · Humidity 48%</p>
            <div class="grid">
              <div class="tile"><span>Solar today</span><strong>8.4 <small>kWh</small></strong></div>
              <div class="tile"><span>Battery</span><strong>87<small>%</small></strong></div>
              <div class="tile"><span>Air quality</span><strong>Excellent</strong></div>
              <div class="tile"><span>Devices online</span><strong>12 <small>/ 12</small></strong></div>
            </div>
            <div class="footer"><span>LOCAL CONNECTION</span><span>Updated just now</span></div>
          </section>
          <p class="note">Your favorite piece of the web.<br>Right where you need it.</p>
        </body></html>
    """.trimIndent()

    private companion object {
        const val SOURCE_PROFILE_ID = "site-widget-source-profile"
        const val OTHER_PROFILE_ID = "site-widget-other-profile"
        const val PAGE_PATH = "/home-status"
        const val OTHER_PATH = "/other-document"
        const val PAGE_TITLE = "Candy Home Status"
        const val OTHER_TITLE = "Other website document"
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.TEST_SITE_WIDGET_FLOW"
        const val TIMEOUT_MS = 20_000L
    }
}
