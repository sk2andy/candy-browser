package dev.sk2andy.materialbrowser.ui.feeds

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.feeds.FeedLaunchContract
import dev.sk2andy.materialbrowser.feeds.FeedOfflineStatus
import dev.sk2andy.materialbrowser.feeds.FeedParser
import dev.sk2andy.materialbrowser.feeds.FeedPlaybackManager
import dev.sk2andy.materialbrowser.feeds.FeedReadingMode
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedRules
import dev.sk2andy.materialbrowser.feeds.FeedSettings
import dev.sk2andy.materialbrowser.shared.ui.BrowserMainMenuTestTags
import dev.sk2andy.materialbrowser.shared.ui.ReaderStudioTestTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedsBrowserFlowInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    init {
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        val store = BrowserSessionStore(context)
        store.saveAppearanceSettings(store.loadAppearanceSettings().copy(animationsEnabled = false))
    }

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private var repository: FeedRepository? = null

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            FeedPlaybackManager.get(context).invalidate()
        }
        composeRule.activityRule.scenario.close()
        repository?.let {
            runBlocking { FeedRepository.deleteProfile(context, it.profileId).getOrThrow() }
        }
    }

    @Test
    fun overflowFeedsAndLibraryOpenSingleHostAndReopenAfterBack() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            if (controller.selectedTab.isIncognito) controller.createTab(isIncognito = false)
        }
        val closeInput = context.getString(R.string.cd_close_address_input)
        if (
            composeRule
                .onAllNodesWithContentDescription(closeInput)
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            composeRule.onNodeWithContentDescription(closeInput).performClick()
        }
        listOf(R.string.browser_menu_feeds, R.string.browser_menu_library, R.string.browser_menu_feeds)
            .forEach { destination ->
                openMenuItem(destination)
                composeRule.onAllNodesWithTag("feeds_screen").assertCountEquals(1)
                composeRule.onNodeWithTag("feeds_screen").assertIsDisplayed()
                Espresso.pressBack()
                composeRule.onNodeWithTag("feeds_screen").assertDoesNotExist()
            }
    }

    @Test
    fun discoverIntentReopensCatalogAfterReturningToCollection() {
        val launchIntent = composeRule.activity.intent
        repeat(2) {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.startActivity(FeedLaunchContract.discoverIntent(activity))
            }
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithTag("feed_catalog_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithTag("feed_catalog_screen").assertCountEquals(1)
            composeRule.onNodeWithTag("feed_catalog_screen").assertIsDisplayed()
            composeRule.onNodeWithTag("feed_catalog_back").performClick()
            composeRule.onNodeWithTag("feed_catalog_screen").assertDoesNotExist()
            composeRule.onNodeWithTag("feeds_screen").assertIsDisplayed()
        }
        composeRule.activityRule.scenario.onActivity { it.intent = launchIntent }
    }

    @Test
    fun directBookmarksAndListeningQueuePersistAndReturnToReader() {
        var profileId = ""
        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            if (controller.selectedTab.isIncognito) controller.createTab(isIncognito = false)
            profileId = controller.activeProfileId
        }
        val feeds = FeedRepository.get(context, profileId)
        repository = feeds
        runBlocking {
            feeds.updateSettings(FeedSettings(refreshOnOpen = false)).getOrThrow()
            feeds
                .subscribe(
                    FeedParser.parse(
                        """<rss><channel><title>Field Notes</title><item><title>Weniger Tabs. Mehr Gedanken.</title><link>https://example.org/quiet-web</link><description>Die besten Geschichten brauchen keinen endlosen Strom. Ein Moment zum Lesen, Aufheben und Zuhören.</description></item><item><title>Zehn Minuten, die dir gehören</title><link>https://example.org/slow-morning</link><description>Über kleine Leserituale und einen bewussteren Morgen. Deine Vorleseliste nimmt die guten Gedanken mit.</description></item></channel></rss>""",
                        "https://example.org/feed",
                    )
                )
                .getOrThrow()
            feeds.state.value.articles.forEach { article ->
                feeds
                    .updateOffline(article.id, FeedOfflineStatus.Ready, FeedRules.document(article))
                    .getOrThrow()
            }
        }
        val first = feeds.state.value.articles.first { it.title.startsWith("Weniger") }
        val second = feeds.state.value.articles.first { it.id != first.id }
        runBlocking { feeds.updateProgress(first.id, 0.42f).getOrThrow() }
        val closeInput = context.getString(R.string.cd_close_address_input)
        if (
            composeRule
                .onAllNodesWithContentDescription(closeInput)
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            composeRule.onNodeWithContentDescription(closeInput).performClick()
        }
        openMenuItem(R.string.browser_menu_feeds)
        composeRule.onNodeWithTag("feed_bookmark_${first.id}").performScrollTo().assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/candy_feeds_card_target.png")
            .close()
        composeRule.onNodeWithTag("feed_bookmark_${first.id}").performSemanticsAction(
            SemanticsActions.OnClick
        ) {
            it()
        }
        composeRule.waitUntil(10_000) {
            feeds.state.value.articles.first { it.id == first.id }.isStarred
        }
        composeRule.onNodeWithTag("feed_bookmark_${first.id}").assertIsOn()
        composeRule.onNodeWithTag("feed_queue_${first.id}").performSemanticsAction(
            SemanticsActions.OnClick
        ) {
            it()
        }
        composeRule.waitUntil(10_000) { first.id in feeds.state.value.queue }
        composeRule.onNodeWithTag("feed_queue_${first.id}").assertIsOn()
        composeRule.onNodeWithTag("feed_queue_${second.id}").performSemanticsAction(
            SemanticsActions.OnClick
        ) {
            it()
        }
        composeRule.waitUntil(10_000) { feeds.state.value.queue == listOf(first.id, second.id) }
        composeRule.onNodeWithTag("feed_queue_${second.id}").assertIsOn()
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/candy_feeds_cards.png")
            .close()
        composeRule.onNodeWithTag("feeds_open_queue").performClick()
        composeRule.onNodeWithTag("feeds_queue_current_title").assertTextEquals(first.title)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.reader_speech_start))
            .assertExists()
        composeRule.waitUntil(15_000) {
            composeRule
                .onAllNodesWithText(context.getString(R.string.feeds_player_preparing))
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeRule.onNodeWithTag("feeds_queue_play").assertIsEnabled()
        composeRule.onNodeWithTag("feeds_queue_player").performScrollToIndex(0)
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/candy_feeds_player.png")
            .close()
        composeRule
            .onNodeWithTag("feeds_queue_player")
            .performScrollToNode(hasTestTag("feeds_queue_up_${second.id}"))
        composeRule.onNodeWithTag("feeds_queue_up_${second.id}").performClick()
        composeRule.waitUntil(10_000) { feeds.state.value.queue.firstOrNull() == second.id }
        composeRule.onNodeWithTag("feeds_queue_remove_${second.id}").performClick()
        composeRule.waitUntil(10_000) { feeds.state.value.queue == listOf(first.id) }
        composeRule.onNodeWithTag("feeds_queue_player").performScrollToIndex(0)
        dismissQueue()
        assertEquals(0.42f, feeds.state.value.articles.first { it.id == first.id }.readingProgress)
        assertFalse(feeds.state.value.articles.any { it.isRead })

        composeRule.onNodeWithTag("feed_article_${first.id}").performClick()
        composeRule.waitUntil(15_000) {
            composeRule
                .onAllNodesWithTag(ReaderStudioTestTags.Article)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(ReaderStudioTestTags.Settings).performClick()
        composeRule.onNodeWithTag("feed_reader_open_queue").performScrollTo().performClick()
        composeRule.onNodeWithTag("feeds_queue_player").assertExists()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertDoesNotExist()
        dismissQueue()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertExists()
    }

    @Test
    fun mediaNotificationEntryOpensAndReopensListeningPlayerWithoutAutoplay() {
        val launchIntent = composeRule.activity.intent
        var profileId = ""
        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            if (controller.selectedTab.isIncognito) controller.createTab(isIncognito = false)
            profileId = controller.activeProfileId
        }
        val feeds = FeedRepository.get(context, profileId)
        repository = feeds
        runBlocking { feeds.updateSettings(FeedSettings(refreshOnOpen = false)).getOrThrow() }
        fun openPlayer() {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.startActivity(
                    Intent(activity, MainActivity::class.java)
                        .setAction(FeedLaunchContract.ACTION_OPEN)
                        .putExtra(FeedLaunchContract.EXTRA_PROFILE, profileId)
                        .putExtra(FeedLaunchContract.EXTRA_QUEUE, true)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
        }
        openPlayer()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("feeds_queue_player").fetchSemanticsNodes().isNotEmpty()
        }
        dismissQueue()
        composeRule.onNodeWithTag("feeds_queue_player").assertDoesNotExist()
        openPlayer()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("feeds_queue_player").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(FeedPlaybackManager.get(context).state.value.isActive)
        // Restore ActivityScenario identity extras replaced by MainActivity.onNewIntent.
        composeRule.activityRule.scenario.onActivity { it.intent = launchIntent }
    }

    @Test
    fun browserFeedsReaderAndDirectLibraryPreserveNavigation() {
        var profileId = ""
        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            if (controller.selectedTab.isIncognito) controller.createTab(isIncognito = false)
            profileId = controller.activeProfileId
        }
        val feeds = FeedRepository.get(context, profileId)
        repository = feeds
        runBlocking {
            feeds.updateSettings(FeedSettings(refreshOnOpen = false)).getOrThrow()
            val preview =
                FeedParser.parse(
                    """<rss><channel><title>Test source</title><item><title>Candy RSS navigation</title><link>https://example.org/candy-navigation</link><description>This article verifies Candy's browser, feed inbox, Reader surface and shared library without leaving the original browser tab behind.</description></item></channel></rss>""",
                    "https://example.org/feed",
                )
            feeds.subscribe(preview).getOrThrow()
            feeds.setStarred(feeds.state.value.articles.single().id, true).getOrThrow()
        }
        val closeInput = context.getString(R.string.cd_close_address_input)
        if (
            composeRule
                .onAllNodesWithContentDescription(closeInput)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
            composeRule.onNodeWithContentDescription(closeInput).performClick()
        openMenuItem(R.string.browser_menu_feeds)
        composeRule
            .onNodeWithText(context.getString(R.string.feeds_all))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
            .assertIsSelected()
        composeRule
            .onNodeWithTag("feed_article_${feeds.state.value.articles.single().id}")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitUntil(15_000) {
            composeRule
                .onAllNodesWithTag(ReaderStudioTestTags.Article)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertIsDisplayed()
        Espresso.pressBack()
        composeRule.waitUntil(15_000) {
            composeRule.onNodeWithText("Candy RSS navigation").isDisplayed()
        }
        composeRule
            .onNodeWithTag("feed_article_${feeds.state.value.articles.single().id}")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitUntil(15_000) {
            composeRule
                .onAllNodesWithTag(ReaderStudioTestTags.Article)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(ReaderStudioTestTags.Settings).performClick()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Save).performScrollTo().performClick()
        composeRule.waitUntil(15_000) {
            feeds.state.value.articles.single().offlineStatus == FeedOfflineStatus.Ready
        }
        composeRule
            .onNodeWithText(context.getString(R.string.reader_original))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("feeds_screen").fetchSemanticsNodes().isEmpty()
        }
        Espresso.pressBack()
        try {
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithTag("feeds_screen").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            composeRule.onRoot(useUnmergedTree = true).printToLog("FeedFlowDebug")
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/candy_feeds_flow_failure.png")
                .close()
            throw error
        }
        composeRule.waitUntil(15_000) {
            composeRule.onNodeWithText("Candy RSS navigation").isDisplayed()
        }
        Espresso.pressBack()
        composeRule.onNodeWithTag("feeds_screen").assertDoesNotExist()
        openMenuItem(R.string.browser_menu_library)
        composeRule.onNodeWithText(context.getString(R.string.feeds_inbox)).assertDoesNotExist()
        composeRule.waitUntil(15_000) {
            composeRule.onNodeWithText("Candy RSS navigation").isDisplayed()
        }
        runBlocking {
            feeds
                .updateSource(
                    feeds.state.value.sources.single().copy(readingMode = FeedReadingMode.Original)
                )
                .getOrThrow()
        }
        composeRule
            .onNodeWithTag("feed_article_${feeds.state.value.articles.single().id}")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitUntil(15_000) {
            composeRule
                .onAllNodesWithTag(ReaderStudioTestTags.Article)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithTag("feeds_screen").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/candy_feeds_library.png")
            .close()
    }

    private fun dismissQueue() {
        composeRule.onNodeWithTag("feeds_queue_handle", useUnmergedTree = true).performTouchInput {
            swipe(start = center, end = center + Offset(0f, 800f), durationMillis = 200)
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("feeds_queue_player").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun openMenuItem(label: Int) {
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.cd_more_options))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitUntil(15_000) {
            composeRule
                .onAllNodesWithText(context.getString(label))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val item =
            composeRule.onNodeWithTag(
                if (label == R.string.browser_menu_feeds) BrowserMainMenuTestTags.Feeds
                else BrowserMainMenuTestTags.Library
            )
        item.performScrollTo()
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/candy_feeds_menu_target.png")
            .close()
        // Menu hit targets have their own tests; this flow isolates native navigation from IME
        // motion.
        item.performSemanticsAction(SemanticsActions.OnClick) { it() }
        try {
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithTag("feeds_screen").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/candy_feeds_flow_failure.png")
                .close()
            throw error
        }
    }
}
