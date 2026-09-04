package dev.sk2andy.materialbrowser.browser

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkPeekTabOpeningInstrumentedTest {
    @Test
    fun acceptedPeekOpensOneBackgroundTabAndKeepsCurrentNavigationSemantics() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val source = controller.selectedTab
                val sourceTabId = source.id
                val sourceUrl = source.url
                val originalIds = controller.tabs.mapTo(mutableSetOf(), BrowserTab::id)

                controller.contentActions.show(
                    target = WebContentTarget(linkUrl = "https://example.com/link-peek-target"),
                    sourceTabId = sourceTabId,
                )
                controller.openContextLinkInBackground()

                val createdIds = controller.tabs.map(BrowserTab::id).toSet() - originalIds
                assertEquals(1, createdIds.size)
                val created = controller.tabs.single { it.id in createdIds }
                assertEquals(sourceTabId, controller.selectedTabId)
                assertEquals(sourceUrl, controller.selectedTab.url)
                assertEquals("https://example.com/link-peek-target", created.url)
                assertEquals(source.profileId, created.profileId)
                assertEquals(source.isIncognito, created.isIncognito)
                assertFalse(controller.contentActions.isVisible)

                controller.openContextLinkInBackground()

                assertEquals(createdIds, controller.tabs.map(BrowserTab::id).toSet() - originalIds)
                assertTrue(controller.tabs.any { it.id == sourceTabId })
                controller.closeTab(created.id)
            }
        }
    }

    @Test
    fun privateActionOpensSelectedIncognitoTabForPreviewUrl() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assumeTrue(controller.canOpenLinkInPrivate)
                val sourceTabId = controller.selectedTabId
                val originalIds = controller.tabs.mapTo(mutableSetOf(), BrowserTab::id)
                val previewUrl = "https://example.com/link-peek-private"
                controller.contentActions.show(
                    target = WebContentTarget(linkUrl = previewUrl),
                    sourceTabId = sourceTabId,
                )

                assertTrue(controller.openLinkInPrivate(previewUrl))

                val createdIds = controller.tabs.map(BrowserTab::id).toSet() - originalIds
                assertEquals(1, createdIds.size)
                val created = controller.tabs.single { it.id in createdIds }
                assertEquals(created.id, controller.selectedTabId)
                assertEquals(sourceTabId, created.openerTabId)
                assertEquals(previewUrl, created.url)
                assertTrue(created.isIncognito)
                assertFalse(controller.contentActions.isVisible)
                controller.closeTab(created.id)
            }
        }
    }

    @Test
    fun switchingTabsDismissesSourceBoundLinkPeek() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val sourceTabId = controller.selectedTabId
                val targetTabId = requireNotNull(
                    controller.createBackgroundTab("https://example.com/other"),
                )
                controller.contentActions.show(
                    target = WebContentTarget(linkUrl = "https://example.com/data.json"),
                    sourceTabId = sourceTabId,
                )

                controller.selectTab(targetTabId)

                assertFalse(controller.contentActions.isVisible)
                assertNull(controller.contentActions.sourceTabId)
                controller.closeTab(sourceTabId)
            }
        }
    }
}
