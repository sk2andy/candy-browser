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
    fun committedPeekUrlOpensInBackgroundWithoutSwitching() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val sourceTabId = controller.selectedTabId
                val originalIds = controller.tabs.mapTo(mutableSetOf(), BrowserTab::id)
                controller.contentActions.show(
                    target = WebContentTarget(linkUrl = "https://example.com/original"),
                    sourceTabId = sourceTabId,
                )

                assertTrue(
                    controller.openContextLinkInBackground(
                        "https://example.com/committed",
                    ),
                )

                val created = controller.tabs.single { it.id !in originalIds }
                assertEquals(sourceTabId, controller.selectedTabId)
                assertEquals("https://example.com/committed", created.url)
                assertEquals(sourceTabId, created.openerTabId)
                controller.closeTab(created.id)
            }
        }
    }

    @Test
    fun committedPeekUrlOpensInForegroundAndSwitches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val sourceTabId = controller.selectedTabId
                controller.contentActions.show(
                    target = WebContentTarget(linkUrl = "https://example.com/original"),
                    sourceTabId = sourceTabId,
                )

                assertTrue(
                    controller.openContextLinkInForeground(
                        "https://example.com/committed",
                    ),
                )

                val created = controller.selectedTab
                assertTrue(created.id != sourceTabId)
                assertEquals("https://example.com/committed", created.url)
                assertEquals(sourceTabId, created.openerTabId)
                controller.closeTab(created.id)
            }
        }
    }

    @Test
    fun contextFavoriteUsesCommittedUrlAndSupportsExistingUndo() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val sourceTabId = controller.selectedTabId
                val url = "https://favorite-${System.nanoTime()}.example/article"
                controller.contentActions.show(
                    target = WebContentTarget(linkUrl = "https://example.com/original"),
                    sourceTabId = sourceTabId,
                )

                val mutation = controller.toggleContextLinkFavorite(url, "Committed article")

                assertTrue(mutation?.added == true)
                assertTrue(controller.isFavorite(url))
                assertFalse(controller.contentActions.isVisible)
                assertTrue(controller.undoFavorite(requireNotNull(mutation)))
                assertFalse(controller.isFavorite(url))
            }
        }
    }

    @Test
    fun contextLinkSnoozeCreatesNoTemporaryActiveTabAndUndoRestoresInBackground() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val sourceTabId = controller.selectedTabId
                val originalIds = controller.tabs.map(BrowserTab::id)
                val nowMillis = 10_000L
                val url = "https://example.com/read-tomorrow"
                controller.contentActions.show(
                    target = WebContentTarget(linkUrl = url),
                    sourceTabId = sourceTabId,
                )
                controller.contentActions.dismiss()

                val token = controller.snoozeContextLink(
                    url = url,
                    title = "Read tomorrow",
                    wakeAtMillis = nowMillis + 60_000L,
                    sourceTabId = sourceTabId,
                    nowMillis = nowMillis,
                )

                assertEquals(originalIds, controller.tabs.map(BrowserTab::id))
                assertEquals(sourceTabId, controller.selectedTabId)
                assertEquals(url, requireNotNull(token).appliedSnoozedTab.tab.url)
                assertTrue(controller.snoozedTabs.any { it.tab.id == token.tabId })

                assertTrue(controller.undoSnooze(token, nowMillis = nowMillis + 1L))
                assertEquals(sourceTabId, controller.selectedTabId)
                assertTrue(controller.tabs.any { it.id == token.tabId && it.url == url })
                controller.closeTab(token.tabId)
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
    fun privateContextCannotPersistFavoriteOrSnooze() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assumeTrue(controller.canOpenLinkInPrivate)
                val regularTabId = controller.selectedTabId
                val privateTabId = controller.createTab(isIncognito = true)
                val url = "https://private-${System.nanoTime()}.example/secret"
                controller.contentActions.show(
                    target = WebContentTarget(linkUrl = url),
                    sourceTabId = privateTabId,
                )

                assertFalse(controller.canPersistContextLink)
                assertFalse(controller.canSnoozeContextLink)
                assertNull(controller.toggleContextLinkFavorite(url, "Secret"))
                assertNull(
                    controller.snoozeContextLink(
                        url = url,
                        title = "Secret",
                        wakeAtMillis = 20_000L,
                        sourceTabId = privateTabId,
                        nowMillis = 10_000L,
                    ),
                )
                assertFalse(controller.isFavorite(url))
                assertFalse(controller.snoozedTabs.any { it.tab.url == url })
                controller.closeTab(privateTabId)
                assertEquals(regularTabId, controller.selectedTabId)
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
