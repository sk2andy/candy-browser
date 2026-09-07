package dev.sk2andy.materialbrowser.browser.gecko

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserController
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
        val targetUrl = "https://example.com/from-gecko"
        activityRule.scenario.onActivity { activity ->
            controller = BrowserController(activity)
            sourceTabId = controller.selectedTabId
            controller.updateLinkLongPressAction(LinkLongPressAction.OpenInNewTab)
            controller.dispatchSelectedGeckoContentTargetForTesting(
                WebContentTarget(linkUrl = targetUrl),
            )
        }
        instrumentation.waitForIdleSync()

        activityRule.scenario.onActivity {
            assertEquals(sourceTabId, controller.selectedTabId)
            assertTrue(controller.tabs.any { tab -> tab.url == targetUrl })
            assertFalse(controller.contentActions.isVisible)
            controller.destroy()
        }
    }

    @Test
    fun configuredLinkActionFallsBackToContextForImageOnlyGeckoTarget() {
        lateinit var controller: BrowserController
        activityRule.scenario.onActivity { activity ->
            controller = BrowserController(activity)
            controller.updateLinkLongPressAction(LinkLongPressAction.OpenInNewTab)
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
            controller.destroy()
        }
    }
}
