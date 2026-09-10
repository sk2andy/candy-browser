package dev.sk2andy.materialbrowser.browser.gecko

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.actions.LinkLongPressAction
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.reader.ReaderExtractionFailure
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoLinkPeekPreviewInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun releaseIsIdempotentAndControllerDestroyDrainsActivePreviews() {
        activityRule.scenario.onActivity { activity ->
            val controller = BrowserController(activity)
            assertTrue(controller.usesGeckoEngine)
            val first = controller.createLinkPeekPreviewView(
                url = "https://example.com/first",
                onProgressChanged = {},
                onCommittedUrlChanged = {},
            )
            assertEquals(1, controller.activeLinkPeekPreviewCountForTesting)

            controller.releaseLinkPeekPreviewView(first)
            controller.releaseLinkPeekPreviewView(first)
            assertEquals(0, controller.activeLinkPeekPreviewCountForTesting)

            controller.createLinkPeekPreviewView(
                url = "https://example.com/second",
                onProgressChanged = {},
                onCommittedUrlChanged = {},
            )
            assertEquals(1, controller.activeLinkPeekPreviewCountForTesting)
            controller.destroy()
            assertEquals(0, controller.activeLinkPeekPreviewCountForTesting)
        }
    }

    @Test
    fun loadingPreviewCannotBePersistedToReader() {
        activityRule.scenario.onActivity { activity ->
            val controller = BrowserController(activity)
            val url = "https://example.com/article"
            controller.contentActions.show(
                target = WebContentTarget(linkUrl = url),
                sourceTabId = controller.selectedTabId,
            )
            val preview = controller.createLinkPeekPreviewView(
                url = url,
                onProgressChanged = {},
                onCommittedUrlChanged = {},
            )
            var result: ReaderExtractionResult? = null

            controller.saveLinkPeekToReader(preview, url) { result = it }

            assertEquals(
                ReaderExtractionResult.Failure(ReaderExtractionFailure.InvalidResponse),
                result,
            )
            assertEquals(1, controller.activeLinkPeekPreviewCountForTesting)
            controller.destroy()
        }
    }

    @Test
    fun privatePreviewCannotBePersistedToReader() {
        activityRule.scenario.onActivity { activity ->
            val controller = BrowserController(activity)
            assumeTrue(controller.canOpenLinkInPrivate)
            val url = "https://example.com/private-article"
            val privateTabId = controller.createTab(isIncognito = true)
            controller.contentActions.show(
                target = WebContentTarget(linkUrl = url),
                sourceTabId = privateTabId,
            )
            val preview = controller.createLinkPeekPreviewView(
                url = url,
                onProgressChanged = {},
                onCommittedUrlChanged = {},
            )
            var result: ReaderExtractionResult? = null

            controller.saveLinkPeekToReader(preview, url) { result = it }

            assertEquals(
                ReaderExtractionResult.Failure(ReaderExtractionFailure.UnsupportedPage),
                result,
            )
            controller.destroy()
        }
    }

    @Test
    fun configuredLongPressActionRunsThroughGeckoContentTarget() {
        lateinit var controller: BrowserController
        lateinit var sourceTabId: String
        lateinit var sourceProfileId: String
        lateinit var originalTabIds: Set<String>
        var pulseNonceBefore = 0
        var hapticNonceBefore = 0
        val targetUrl = "https://example.com/from-gecko"
        activityRule.scenario.onActivity { activity ->
            controller = BrowserController(activity)
            controller.onStart()
            controller.onResume()
            sourceTabId = controller.selectedTabId
            sourceProfileId = controller.selectedTab.profileId
            originalTabIds = controller.tabs.map(BrowserTab::id).toSet()
            pulseNonceBefore = controller.contentActions.addressBarPulseNonce
            hapticNonceBefore = controller.contentActions.longPressActionHapticNonce
            controller.updateLinkLongPressAction(LinkLongPressAction.OpenInNewTabInBackground)
            controller.dispatchSelectedGeckoContentTargetForTesting(
                WebContentTarget(linkUrl = targetUrl),
            )
        }
        instrumentation.waitForIdleSync()

        activityRule.scenario.onActivity {
            val created = controller.tabs.single { tab -> tab.id !in originalTabIds }
            assertEquals(sourceTabId, controller.selectedTabId)
            assertEquals(targetUrl, created.url)
            assertEquals(sourceTabId, created.openerTabId)
            assertEquals(sourceProfileId, created.profileId)
            assertFalse(created.isIncognito)
            assertEquals(pulseNonceBefore + 1, controller.contentActions.addressBarPulseNonce)
            assertEquals(
                hapticNonceBefore + 1,
                controller.contentActions.longPressActionHapticNonce,
            )
            assertFalse(controller.contentActions.isVisible)
            controller.destroy()
        }
    }

    @Test
    fun configuredDownloadActionUsesContextPathWithoutOpeningTab() {
        lateinit var controller: BrowserController
        lateinit var originalTabIds: Set<String>
        var pulseNonceBefore = 0
        var hapticNonceBefore = 0
        activityRule.scenario.onActivity { activity ->
            controller = BrowserController(activity)
            controller.onStart()
            controller.onResume()
            originalTabIds = controller.tabs.map(BrowserTab::id).toSet()
            pulseNonceBefore = controller.contentActions.addressBarPulseNonce
            hapticNonceBefore = controller.contentActions.longPressActionHapticNonce
            controller.updateLinkLongPressAction(LinkLongPressAction.DownloadLink)
            controller.dispatchSelectedGeckoContentTargetForTesting(
                WebContentTarget(linkUrl = "https://example.invalid/direct-download.bin"),
            )
        }
        instrumentation.waitForIdleSync()

        activityRule.scenario.onActivity {
            assertEquals(originalTabIds, controller.tabs.map(BrowserTab::id).toSet())
            assertEquals(pulseNonceBefore, controller.contentActions.addressBarPulseNonce)
            assertEquals(
                hapticNonceBefore + 1,
                controller.contentActions.longPressActionHapticNonce,
            )
            assertFalse(controller.contentActions.isVisible)
            controller.destroy()
        }
    }

    @Test
    fun configuredForegroundActionSelectsRegularChildTab() {
        lateinit var controller: BrowserController
        lateinit var sourceTabId: String
        lateinit var sourceProfileId: String
        var pulseNonceBefore = 0
        var hapticNonceBefore = 0
        val targetUrl = "https://example.com/foreground-from-gecko"
        activityRule.scenario.onActivity { activity ->
            controller = BrowserController(activity)
            controller.onStart()
            controller.onResume()
            sourceTabId = controller.selectedTabId
            sourceProfileId = controller.selectedTab.profileId
            pulseNonceBefore = controller.contentActions.addressBarPulseNonce
            hapticNonceBefore = controller.contentActions.longPressActionHapticNonce
            controller.updateLinkLongPressAction(LinkLongPressAction.OpenInNewTabInForeground)
            controller.dispatchSelectedGeckoContentTargetForTesting(
                WebContentTarget(linkUrl = targetUrl),
            )
        }
        instrumentation.waitForIdleSync()

        activityRule.scenario.onActivity {
            val created = controller.selectedTab
            assertTrue(created.id != sourceTabId)
            assertEquals(targetUrl, created.url)
            assertEquals(sourceTabId, created.openerTabId)
            assertEquals(sourceProfileId, created.profileId)
            assertFalse(created.isIncognito)
            assertEquals(pulseNonceBefore, controller.contentActions.addressBarPulseNonce)
            assertEquals(
                hapticNonceBefore + 1,
                controller.contentActions.longPressActionHapticNonce,
            )
            assertFalse(controller.contentActions.isVisible)
            controller.destroy()
        }
    }

    @Test
    fun configuredPrivateBackgroundActionKeepsRegularSourceSelected() {
        lateinit var controller: BrowserController
        lateinit var sourceTabId: String
        lateinit var sourceProfileId: String
        lateinit var originalTabIds: Set<String>
        var pulseNonceBefore = 0
        var hapticNonceBefore = 0
        val targetUrl = "https://example.com/private-background-from-gecko"
        activityRule.scenario.onActivity { activity ->
            controller = BrowserController(activity)
            controller.onStart()
            controller.onResume()
            assumeTrue(controller.canOpenLinkInPrivate)
            sourceTabId = controller.selectedTabId
            sourceProfileId = controller.selectedTab.profileId
            originalTabIds = controller.tabs.map(BrowserTab::id).toSet()
            pulseNonceBefore = controller.contentActions.addressBarPulseNonce
            hapticNonceBefore = controller.contentActions.longPressActionHapticNonce
            controller.updateLinkLongPressAction(
                LinkLongPressAction.OpenInPrivateTabInBackground,
            )
            controller.dispatchSelectedGeckoContentTargetForTesting(
                WebContentTarget(linkUrl = targetUrl),
            )
        }
        instrumentation.waitForIdleSync()

        activityRule.scenario.onActivity {
            val created = controller.tabs.single { tab -> tab.id !in originalTabIds }
            assertEquals(sourceTabId, controller.selectedTabId)
            assertEquals(targetUrl, created.url)
            assertEquals(sourceTabId, created.openerTabId)
            assertEquals(sourceProfileId, created.profileId)
            assertTrue(created.isIncognito)
            assertEquals(pulseNonceBefore + 1, controller.contentActions.addressBarPulseNonce)
            assertEquals(
                hapticNonceBefore + 1,
                controller.contentActions.longPressActionHapticNonce,
            )
            assertFalse(controller.contentActions.isVisible)
            controller.destroy()
        }
    }

    @Test
    fun configuredPrivateForegroundActionSelectsPrivateChildTab() {
        lateinit var controller: BrowserController
        lateinit var sourceTabId: String
        lateinit var sourceProfileId: String
        var pulseNonceBefore = 0
        var hapticNonceBefore = 0
        val targetUrl = "https://example.com/private-foreground-from-gecko"
        activityRule.scenario.onActivity { activity ->
            controller = BrowserController(activity)
            controller.onStart()
            controller.onResume()
            assumeTrue(controller.canOpenLinkInPrivate)
            sourceTabId = controller.selectedTabId
            sourceProfileId = controller.selectedTab.profileId
            pulseNonceBefore = controller.contentActions.addressBarPulseNonce
            hapticNonceBefore = controller.contentActions.longPressActionHapticNonce
            controller.updateLinkLongPressAction(
                LinkLongPressAction.OpenInPrivateTabInForeground,
            )
            controller.dispatchSelectedGeckoContentTargetForTesting(
                WebContentTarget(linkUrl = targetUrl),
            )
        }
        instrumentation.waitForIdleSync()

        activityRule.scenario.onActivity {
            val created = controller.selectedTab
            assertTrue(created.id != sourceTabId)
            assertEquals(targetUrl, created.url)
            assertEquals(sourceTabId, created.openerTabId)
            assertEquals(sourceProfileId, created.profileId)
            assertTrue(created.isIncognito)
            assertEquals(pulseNonceBefore, controller.contentActions.addressBarPulseNonce)
            assertEquals(
                hapticNonceBefore + 1,
                controller.contentActions.longPressActionHapticNonce,
            )
            assertFalse(controller.contentActions.isVisible)
            controller.destroy()
        }
    }

    @Test
    fun configuredLinkActionFallsBackToContextForImageOnlyGeckoTarget() {
        lateinit var controller: BrowserController
        var hapticNonceBefore = 0
        activityRule.scenario.onActivity { activity ->
            controller = BrowserController(activity)
            controller.onStart()
            controller.onResume()
            hapticNonceBefore = controller.contentActions.longPressActionHapticNonce
            controller.updateLinkLongPressAction(LinkLongPressAction.OpenInNewTabInBackground)
            controller.dispatchSelectedGeckoContentTargetForTesting(
                WebContentTarget(imageUrl = "https://example.com/image.png"),
            )
        }
        instrumentation.waitForIdleSync()

        activityRule.scenario.onActivity {
            assertTrue(controller.contentActions.isVisible)
            assertEquals(
                "https://example.com/image.png",
                controller.contentActions.target?.imageUrl,
            )
            assertEquals(
                hapticNonceBefore + 1,
                controller.contentActions.longPressActionHapticNonce,
            )
            controller.destroy()
        }
    }
}
