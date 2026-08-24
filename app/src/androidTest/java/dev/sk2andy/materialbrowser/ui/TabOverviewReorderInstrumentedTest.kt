package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class TabOverviewReorderInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.mainClock.autoAdvance = true
        composeRule.runOnIdle {
            controller?.destroy()
            controller = null
            clearSession()
        }
    }

    @Test
    fun heroTabsReorderAfterLongPressDrag() = verifyReorder(TabOverviewMode.Hero)

    @Test
    fun gridTabsReorderAfterLongPressDrag() = verifyReorder(TabOverviewMode.Grid)

    @Test
    fun listTabsReorderAfterLongPressDrag() = verifyReorder(TabOverviewMode.List)

    @Test
    fun quickReleaseAfterLongPressStillCommits() = verifyReorder(
        mode = TabOverviewMode.Hero,
        moveDurationMillis = 80L,
    )

    @Test
    fun heroCardMatchesAdaptiveSwitcherProportions() {
        lateinit var browserController: BrowserController
        lateinit var tabId: String
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
            tabId = browserController.selectedTabId
            browserController.updateTabOverviewMode(TabOverviewMode.Hero)
        }
        setOverviewContent(browserController)
        composeRule.waitForIdle()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val cardBounds = composeRule
            .onNodeWithTag(SnoozeTestTags.overviewTab(tabId))
            .fetchSemanticsNode()
            .boundsInRoot
        val density = composeRule.activity.resources.displayMetrics.density
        val expectedLayout = TabOverviewHeroRules.coverflowCardLayout(
            viewportWidth = rootBounds.width / density,
            viewportHeight = rootBounds.height / density,
        )

        assertEquals(expectedLayout.width * density, cardBounds.width, 8f)
        assertEquals(expectedLayout.aspectRatio, cardBounds.width / cardBounds.height, 0.001f)
        assertTrue(cardBounds.top >= rootBounds.top)
        assertTrue(cardBounds.bottom <= rootBounds.bottom)
    }

    @Test
    fun gridCardsMatchAdaptivePreviewProportions() {
        lateinit var browserController: BrowserController
        lateinit var tabIds: List<String>
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
            tabIds = listOf(
                browserController.selectedTabId,
                requireNotNull(browserController.createBackgroundTab("https://grid-two.example")),
                requireNotNull(browserController.createBackgroundTab("https://grid-three.example")),
            )
            browserController.updateTabOverviewMode(TabOverviewMode.Grid)
        }
        setOverviewContent(browserController)
        composeRule.waitForIdle()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val cardBounds = tabIds.map { tabId ->
            composeRule
                .onNodeWithTag(SnoozeTestTags.overviewTab(tabId))
                .fetchSemanticsNode()
                .boundsInRoot
        }
        val density = composeRule.activity.resources.displayMetrics.density
        val isLandscape = rootBounds.width > rootBounds.height
        val expectedPreviewAspectRatio = if (isLandscape) 1.6f else 0.72f
        val expectedCardHeight = cardBounds.first().width / expectedPreviewAspectRatio
        val expectedColumns = if (isLandscape && rootBounds.width / density >= 900f) 3 else 2
        val firstRowCount = cardBounds.count { bounds ->
            kotlin.math.abs(bounds.top - cardBounds.first().top) < 2f
        }
        val titleBounds = composeRule
            .onNodeWithTag(
                SnoozeTestTags.overviewTitle(tabIds.first()),
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .boundsInRoot
        val closeBounds = composeRule
            .onNodeWithTag(
                SnoozeTestTags.overviewClose(tabIds.first()),
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .boundsInRoot

        assertEquals(expectedCardHeight, cardBounds.first().height, 8f)
        assertEquals(expectedColumns, firstRowCount)
        assertEquals(isLandscape, cardBounds.first().width > cardBounds.first().height)
        assertTrue(titleBounds.left >= cardBounds.first().left)
        assertTrue(titleBounds.top >= cardBounds.first().top)
        assertTrue(closeBounds.right <= cardBounds.first().right)
        assertTrue(closeBounds.top >= cardBounds.first().top)
        assertTrue(titleBounds.right <= closeBounds.left + 1f)
        assertTrue(closeBounds.width >= 48f * density)
        assertTrue(closeBounds.height >= 48f * density)
    }

    @Test
    fun gridFloatingCloseClosesOnlyUnpinnedTab() {
        lateinit var browserController: BrowserController
        lateinit var closeTabId: String
        lateinit var pinnedTabId: String
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
            closeTabId = requireNotNull(
                browserController.createBackgroundTab("https://grid-close.example"),
            )
            pinnedTabId = requireNotNull(
                browserController.createBackgroundTab("https://grid-pinned.example"),
            )
            browserController.setTabPinned(pinnedTabId, true)
            browserController.updateTabOverviewMode(TabOverviewMode.Grid)
        }
        setOverviewContent(browserController)
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag(
                SnoozeTestTags.overviewClose(pinnedTabId),
                useUnmergedTree = true,
            )
            .assertDoesNotExist()
        composeRule
            .onNodeWithTag(
                SnoozeTestTags.overviewClose(closeTabId),
                useUnmergedTree = true,
            )
            .performClick()

        composeRule.waitUntil(timeoutMillis = 12_000L) {
            browserController.activeTabs.none { it.id == closeTabId }
        }
        assertTrue(browserController.activeTabs.any { it.id == pinnedTabId })
    }

    @Test
    fun gridNeighborPreviewFadesInBeforeEntryHeroCompletes() {
        lateinit var browserController: BrowserController
        lateinit var neighborTabId: String
        val entryCompleted = AtomicBoolean()
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
            neighborTabId = requireNotNull(
                browserController.createBackgroundTab("https://grid-neighbor.example"),
            )
            browserController.previews[browserController.selectedTabId] = solidPreview(
                android.graphics.Color.RED,
            )
            browserController.previews[neighborTabId] = solidPreview(
                android.graphics.Color.GREEN,
            )
            browserController.updateTabOverviewMode(TabOverviewMode.Grid)
        }
        composeRule.mainClock.autoAdvance = false
        setOverviewContent(
            browserController = browserController,
            onEntryHeroCompleted = { entryCompleted.set(true) },
        )
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.advanceTimeBy(112L)

        assertFalse(entryCompleted.get())
        val neighborBounds = composeRule
            .onNodeWithTag(SnoozeTestTags.overviewTab(neighborTabId))
            .fetchSemanticsNode()
            .boundsInRoot
        val previewCenterX = neighborBounds.center.x.toInt()
        val previewCenterY = neighborBounds.center.y.toInt()
        val pixel = composeRule.onRoot().captureToImage().toPixelMap()[
            previewCenterX,
            previewCenterY,
        ]

        assertTrue(
            "Neighbor preview did not fade during entry: $pixel",
            pixel.green > pixel.red + 0.12f && pixel.green > pixel.blue + 0.12f,
        )
    }

    @Test
    fun gridSelectedPreviewKeepsCropAtHeroHandoff() {
        lateinit var browserController: BrowserController
        lateinit var selectedTabId: String
        val entryCompleted = AtomicBoolean()
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
            selectedTabId = requireNotNull(
                browserController.createBackgroundTab("https://grid-crop.example"),
            )
            browserController.selectTab(selectedTabId)
            browserController.previews[selectedTabId] = verticalGradientPreview()
            browserController.updateTabOverviewMode(TabOverviewMode.Grid)
        }
        composeRule.mainClock.autoAdvance = false
        setOverviewContent(
            browserController = browserController,
            onEntryHeroCompleted = { entryCompleted.set(true) },
        )
        var frameCount = 0
        while (!entryCompleted.get() && frameCount < 20) {
            composeRule.mainClock.advanceTimeByFrame()
            frameCount++
        }
        assertTrue("Entry hero did not complete after $frameCount frames", entryCompleted.get())

        val cardBounds = composeRule
            .onNodeWithTag(SnoozeTestTags.overviewTab(selectedTabId))
            .fetchSemanticsNode()
            .boundsInRoot
        val previewTop = cardBounds.top
        val beforeHandoff = composeRule.onRoot().captureToImage().toPixelMap()
        composeRule.mainClock.advanceTimeByFrame()
        val afterHandoff = composeRule.onRoot().captureToImage().toPixelMap()
        val sampleDistances = listOf(0.35f, 0.5f, 0.65f, 0.8f).map { fraction ->
            val x = cardBounds.center.x.toInt()
            val y = (previewTop + (cardBounds.bottom - previewTop) * fraction).toInt()
            colorDistance(beforeHandoff[x, y], afterHandoff[x, y])
        }

        assertTrue(
            "Grid preview crop changed at hero handoff: $sampleDistances",
            sampleDistances.all { it < 0.08f },
        )
    }

    @Test
    fun heroPagerDrawAreaExtendsBehindProfileSwitcher() {
        lateinit var browserController: BrowserController
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
            browserController.updateDismissResistancePercent(90)
            browserController.updateTabOverviewMode(TabOverviewMode.Hero)
        }
        setOverviewContent(browserController)
        composeRule.waitForIdle()

        val card = composeRule.onNodeWithTag(
            SnoozeTestTags.overviewTab(browserController.selectedTabId),
        )
        val cardBounds = card.fetchSemanticsNode().boundsInRoot
        val pagerBounds = composeRule
            .onNodeWithTag(TabOverviewChromeTestTags.HeroPager)
            .fetchSemanticsNode()
            .boundsInRoot
        val profileBounds = composeRule
            .onNodeWithTag(ProfileSwitcherTestTags.Switcher)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(pagerBounds.top <= profileBounds.top)
        assertTrue(pagerBounds.bottom > profileBounds.bottom)

        val density = composeRule.activity.resources.displayMetrics.density
        val sourceX = cardBounds.center.x
        val sourceY = cardBounds.top + 12f * density
        val requestedTargetY = profileBounds.bottom - 32f * density
        val requiredVisualDistance = sourceY - requestedTargetY
        assertTrue(requiredVisualDistance > 0f)
        var rawDragDistance = 0f
        while (
            TabDismissPhysics.visualDistance(rawDragDistance) < requiredVisualDistance &&
            rawDragDistance < cardBounds.width
        ) {
            rawDragDistance += 1f
        }
        val visualDistance = TabDismissPhysics.visualDistance(rawDragDistance)
        val dismissThreshold = cardBounds.width *
            TabDismissPhysics.CARD_DISMISS_THRESHOLD_FRACTION
        assertFalse(
            "Drag must remain below dismiss threshold: raw=$rawDragDistance " +
                "threshold=$dismissThreshold requiredVisual=$requiredVisualDistance",
            TabDismissPhysics.hasClearedResistance(
                rawDistance = rawDragDistance,
                dismissThreshold = dismissThreshold,
                resistanceFraction = 0.9f,
            ),
        )
        val restingPixels = composeRule.onRoot().captureToImage().toPixelMap()
        card.performTouchInput {
            down(center)
            moveBy(Offset(0f, -rawDragDistance), delayMillis = 500L)
        }
        composeRule.waitForIdle()
        val draggedPixels = composeRule.onRoot().captureToImage().toPixelMap()
        val targetY = sourceY - visualDistance
        val colorDistance = colorDistance(
            restingPixels[sourceX.toInt(), sourceY.toInt()],
            draggedPixels[sourceX.toInt(), targetY.toInt()],
        )

        assertTrue(targetY < profileBounds.bottom - 30f * density)
        assertTrue("Dragged card top was clipped: color distance=$colorDistance", colorDistance < 0.3f)
        card.performTouchInput { up() }
    }

    @Test
    fun selectingTabReportsExitHeroVisibility() {
        lateinit var browserController: BrowserController
        val activeReports = AtomicInteger()
        val exitHeroVisible = AtomicBoolean()
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
            browserController.updateTabOverviewMode(TabOverviewMode.List)
        }
        setOverviewContent(browserController) { visible ->
            if (visible) activeReports.incrementAndGet()
            exitHeroVisible.set(visible)
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag(SnoozeTestTags.overviewTab(browserController.selectedTabId))
            .performClick()

        composeRule.waitUntil(timeoutMillis = 12_000L) { activeReports.get() > 0 }
        composeRule.waitUntil(timeoutMillis = 12_000L) { !exitHeroVisible.get() }
    }

    private fun verifyReorder(
        mode: TabOverviewMode,
        moveDurationMillis: Long = 240L,
    ) {
        lateinit var browserController: BrowserController
        lateinit var sourceTabId: String
        lateinit var destinationTabId: String
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
            sourceTabId = requireNotNull(
                browserController.createBackgroundTab("https://source-${mode.wireValue}.example"),
            )
            destinationTabId = requireNotNull(
                browserController.createBackgroundTab("https://target-${mode.wireValue}.example"),
            )
            browserController.selectTab(sourceTabId)
            browserController.updateTabOverviewMode(mode)
        }
        setOverviewContent(browserController)
        composeRule.waitForIdle()

        val sourceNode = composeRule.onNodeWithTag(SnoozeTestTags.overviewTab(sourceTabId))
        val sourceBounds = sourceNode.fetchSemanticsNode().boundsInRoot
        val destinationBounds = composeRule
            .onNodeWithTag(SnoozeTestTags.overviewTab(destinationTabId))
            .fetchSemanticsNode()
            .boundsInRoot
        val destinationInSource = Offset(
            x = destinationBounds.center.x - sourceBounds.left,
            y = destinationBounds.center.y - sourceBounds.top,
        )
        sourceNode.performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveTo(destinationInSource, delayMillis = moveDurationMillis)
            up()
        }

        composeRule.waitUntil(timeoutMillis = 12_000L) {
            browserController.activeTabs.indexOfFirst { it.id == sourceTabId } ==
                browserController.activeTabs.indexOfFirst { it.id == destinationTabId } + 1
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf(destinationTabId, sourceTabId),
                browserController.activeTabs
                    .filter { it.id == sourceTabId || it.id == destinationTabId }
                    .map(BrowserTab::id),
            )
        }
    }

    private fun setOverviewContent(
        browserController: BrowserController,
        onEntryHeroCompleted: () -> Unit = {},
        onExitHeroVisibilityChanged: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            val bottomBarTop = remember { mutableFloatStateOf(2_000f) }
            MaterialBrowserTheme {
                TabOverview(
                    controller = browserController,
                    visible = true,
                    bottomBarTopPx = bottomBarTop,
                    onClose = {},
                    onSelect = {},
                    onNewTab = {},
                    destinationChromeVisible = true,
                    onEntryHeroStarted = {},
                    onEntryHeroCompleted = onEntryHeroCompleted,
                    onExitHeroVisibilityChanged = onExitHeroVisibilityChanged,
                    candyTrailTabId = null,
                    candyTrailSourceBounds = null,
                    candyTrailBackProgress = 0f,
                    candyTrailBackEdgeSign = 1,
                    candyTrailPredictiveBackCommitted = false,
                    onOpenCandyTrail = { _, _ -> },
                    onCloseCandyTrail = {},
                    onToggleFavoriteTab = {},
                    onAddSiteCapsule = {},
                    onSnoozeTab = {},
                )
            }
        }
    }

    private fun clearSession() {
        composeRule.activity
            .getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun solidPreview(color: Int): Bitmap =
        Bitmap.createBitmap(120, 240, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }

    private fun verticalGradientPreview(): Bitmap {
        val width = 120
        val height = 300
        val pixels = IntArray(width * height) { index ->
            val fraction = (index / width).toFloat() / (height - 1)
            android.graphics.Color.rgb(
                (255f * (1f - fraction)).toInt(),
                0,
                (255f * fraction).toInt(),
            )
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun colorDistance(first: Color, second: Color): Float =
        kotlin.math.abs(first.red - second.red) +
            kotlin.math.abs(first.green - second.green) +
            kotlin.math.abs(first.blue - second.blue)
}
