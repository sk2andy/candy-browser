package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.AddressBarDockEdge
import dev.sk2andy.materialbrowser.data.AddressBarDockPlacement
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarDockInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            controller = null
            clearSession()
        }
    }

    @Test
    fun edgeTabIsAccessibleAndRestoresOnce() {
        val restores = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(Modifier.size(width = 52.dp, height = 48.dp)) {
                    AddressBarEdgeTab(
                        edge = AddressBarDockEdge.Right,
                        onRestore = restores::incrementAndGet,
                        dockDragEnabled = false,
                        onDockDragStarted = {},
                        onDockDrag = {},
                        onDockDragStopped = {},
                        onDockDragCancelled = {},
                        onSwipeUp = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab)
            .assertHasClickAction()
            .assertWidthIsEqualTo(52.dp)
            .assertHeightIsEqualTo(48.dp)
            .performClick()

        assertEquals(1, restores.get())
    }

    @Test
    fun edgeTabClickRestoresThroughInteractionState() {
        val restores = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                val interaction = rememberAddressBarDockInteractionState(
                    presentation = AddressBarPresentation.Docked,
                    placement = AddressBarDockPlacement.Default,
                    enabled = true,
                    horizontalTravelPx = 200f,
                    verticalTravelPx = 400f,
                    density = Density(1f),
                    onPlacementChanged = {},
                    onRestore = restores::incrementAndGet,
                )
                Box(Modifier.size(width = 52.dp, height = 48.dp)) {
                    AddressBarEdgeTab(
                        edge = AddressBarDockEdge.Right,
                        onRestore = interaction.onRestoreClick,
                        dockDragEnabled = true,
                        onDockDragStarted = interaction.onDragStarted,
                        onDockDrag = interaction.onDrag,
                        onDockDragStopped = interaction.onDragStopped,
                        onDockDragCancelled = interaction.onDragCancelled,
                        onSwipeUp = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab).performClick()

        assertEquals(1, restores.get())
    }

    @Test
    fun quickSwipeUpOpensOverviewWithoutStartingRepositioning() {
        val swipeUps = AtomicInteger()
        val dragStarts = AtomicInteger()
        val dragUpdates = AtomicInteger()
        val dragStops = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(Modifier.size(width = 52.dp, height = 48.dp)) {
                    AddressBarEdgeTab(
                        edge = AddressBarDockEdge.Right,
                        onRestore = {},
                        dockDragEnabled = true,
                        onDockDragStarted = dragStarts::incrementAndGet,
                        onDockDrag = { dragUpdates.incrementAndGet() },
                        onDockDragStopped = dragStops::incrementAndGet,
                        onDockDragCancelled = {},
                        onSwipeUp = swipeUps::incrementAndGet,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab)
            .performTouchInput { swipeUp(durationMillis = 180L) }

        assertEquals(1, swipeUps.get())
        assertEquals(0, dragStarts.get())
        assertEquals(0, dragUpdates.get())
        assertEquals(0, dragStops.get())
    }

    @Test
    fun parkedPillSwipeUpHidesKeyboard() {
        val swipeUps = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                Column {
                    BasicTextField(
                        value = "Keyboard target",
                        onValueChange = {},
                        modifier = Modifier
                            .size(width = 200.dp, height = 48.dp)
                            .testTag(ImeTargetTag),
                    )
                    Box(Modifier.size(width = 52.dp, height = 48.dp)) {
                        AddressBarEdgeTab(
                            edge = AddressBarDockEdge.Right,
                            onRestore = {},
                            dockDragEnabled = true,
                            onDockDragStarted = {},
                            onDockDrag = {},
                            onDockDragStopped = {},
                            onDockDragCancelled = {},
                            onSwipeUp = swipeUps::incrementAndGet,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(ImeTargetTag).performClick()
        assertImeVisible()
        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab)
            .performTouchInput { swipeUp(durationMillis = 180L) }

        assertImeHidden()
        assertEquals(1, swipeUps.get())
    }

    @Test
    fun quickDragDoesNotRepositionParkedPill() {
        val dragStarts = AtomicInteger()
        val dragUpdates = AtomicInteger()
        val dragStops = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(Modifier.size(width = 52.dp, height = 48.dp)) {
                    AddressBarEdgeTab(
                        edge = AddressBarDockEdge.Right,
                        onRestore = {},
                        dockDragEnabled = true,
                        onDockDragStarted = dragStarts::incrementAndGet,
                        onDockDrag = { dragUpdates.incrementAndGet() },
                        onDockDragStopped = dragStops::incrementAndGet,
                        onDockDragCancelled = {},
                        onSwipeUp = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab)
            .performTouchInput {
                swipe(
                    start = center,
                    end = Offset(center.x - 220f, center.y - 80f),
                    durationMillis = 200L,
                )
            }

        assertEquals(0, dragStarts.get())
        assertEquals(0, dragUpdates.get())
        assertEquals(0, dragStops.get())
    }

    @Test
    fun longPressWithoutMovementActivatesThenCancelsWithoutRestoringOrDropping() {
        val activationHaptics = AtomicInteger()
        val confirmHaptics = AtomicInteger()
        val placementChanges = AtomicInteger()
        val restores = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                val interaction = rememberAddressBarDockInteractionState(
                    presentation = AddressBarPresentation.Docked,
                    placement = AddressBarDockPlacement.Default,
                    enabled = true,
                    horizontalTravelPx = 200f,
                    verticalTravelPx = 400f,
                    density = Density(1f),
                    onPlacementChanged = { placementChanges.incrementAndGet() },
                    onRestore = restores::incrementAndGet,
                    haptics = AddressBarDockHaptics(
                        activate = { activationHaptics.incrementAndGet() },
                        startMovement = {},
                        stopMovement = {},
                        confirm = { confirmHaptics.incrementAndGet() },
                    ),
                )
                Box(Modifier.size(width = 52.dp, height = 48.dp)) {
                    AddressBarEdgeTab(
                        edge = AddressBarDockEdge.Right,
                        onRestore = interaction.onRestoreClick,
                        dockDragEnabled = true,
                        onDockDragStarted = interaction.onDragStarted,
                        onDockDrag = interaction.onDrag,
                        onDockDragStopped = interaction.onDragStopped,
                        onDockDragCancelled = interaction.onDragCancelled,
                        onSwipeUp = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab)
            .performTouchInput { longClick() }

        assertEquals(1, activationHaptics.get())
        assertEquals(0, confirmHaptics.get())
        assertEquals(0, placementChanges.get())
        assertEquals(0, restores.get())
    }

    @Test
    fun edgeTabDiagonalDragMovesToLeftEdgeAndPersistsVerticalPosition() {
        val settledPlacement = AtomicReference<AddressBarDockPlacement>()
        val swipeUps = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                val interaction = rememberAddressBarDockInteractionState(
                    presentation = AddressBarPresentation.Docked,
                    placement = AddressBarDockPlacement.Default,
                    enabled = true,
                    horizontalTravelPx = 100f,
                    verticalTravelPx = 200f,
                    density = Density(1f),
                    onPlacementChanged = settledPlacement::set,
                    onRestore = {},
                )
                Box(Modifier.size(width = 52.dp, height = 48.dp)) {
                    AddressBarEdgeTab(
                        edge = AddressBarDockEdge.Right,
                        onRestore = interaction.onRestoreClick,
                        dockDragEnabled = true,
                        onDockDragStarted = interaction.onDragStarted,
                        onDockDrag = interaction.onDrag,
                        onDockDragStopped = interaction.onDragStopped,
                        onDockDragCancelled = interaction.onDragCancelled,
                        onSwipeUp = swipeUps::incrementAndGet,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab)
            .performTouchInput {
                down(center)
                advanceEventTime(
                    AddressBarGestureRules.PARKED_REPOSITION_LONG_PRESS_MILLIS + 40L,
                )
                moveTo(Offset(center.x - 220f, center.y - 120f), delayMillis = 160L)
                up()
            }

        assertEquals(AddressBarDockEdge.Left, settledPlacement.get().edge)
        assertTrue(settledPlacement.get().verticalFraction > 0.3f)
        assertEquals(0, swipeUps.get())
    }

    @Test
    fun parkedPillSnapsVisiblyToNormalAddressBarAnchorOnDrag() {
        lateinit var interaction: AddressBarDockInteractionState
        val settledPlacement = AtomicReference<AddressBarDockPlacement>()
        val activationHaptics = AtomicInteger()
        val confirmHaptics = AtomicInteger()
        val sessionStore = BrowserSessionStore(composeRule.activity)
        composeRule.setContent {
            MaterialBrowserTheme {
                interaction = rememberAddressBarDockInteractionState(
                    presentation = AddressBarPresentation.Docked,
                    placement = AddressBarDockPlacement(
                        edge = AddressBarDockEdge.Right,
                        verticalFraction = 0.5f,
                    ),
                    enabled = true,
                    horizontalTravelPx = 200f,
                    verticalTravelPx = 400f,
                    density = Density(1f),
                    onPlacementChanged = { placement ->
                        settledPlacement.set(placement)
                        sessionStore.saveAddressBarDockPlacement(placement)
                    },
                    onRestore = {},
                    haptics = AddressBarDockHaptics(
                        activate = { activationHaptics.incrementAndGet() },
                        startMovement = {},
                        stopMovement = {},
                        confirm = { confirmHaptics.incrementAndGet() },
                    ),
                )
                Box(Modifier.size(width = 52.dp, height = 48.dp)) {
                    AddressBarEdgeTab(
                        edge = AddressBarDockEdge.Right,
                        onRestore = interaction.onRestoreClick,
                        dockDragEnabled = true,
                        onDockDragStarted = interaction.onDragStarted,
                        onDockDrag = interaction.onDrag,
                        onDockDragStopped = interaction.onDragStopped,
                        onDockDragCancelled = interaction.onDragCancelled,
                        onSwipeUp = {},
                    )
                }
            }
        }

        composeRule.runOnIdle {
            interaction.onDragStarted()
            interaction.onDrag(Offset(0f, 260f))
            interaction.onDragStopped()
        }

        composeRule.runOnIdle {
            assertEquals(0f, interaction.position.y, 0.001f)
            assertEquals(0f, settledPlacement.get().verticalFraction, 0.001f)
            assertEquals(settledPlacement.get(), sessionStore.loadAddressBarDockPlacement())
            assertEquals(1, activationHaptics.get())
            assertEquals(2, confirmHaptics.get())
        }
    }

    @Test
    fun normalAnchorStretchesBeforeSpringCatchUp() {
        lateinit var interaction: AddressBarDockInteractionState
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialBrowserTheme {
                interaction = rememberAddressBarDockInteractionState(
                    presentation = AddressBarPresentation.Docked,
                    placement = AddressBarDockPlacement.Default,
                    enabled = true,
                    horizontalTravelPx = 200f,
                    verticalTravelPx = 400f,
                    density = Density(1f),
                    onPlacementChanged = {},
                    onRestore = {},
                )
            }
        }

        composeRule.runOnIdle {
            interaction.onDragStarted()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            interaction.onDrag(Offset(0f, -36f))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            assertEquals(0.5f, interaction.normalAnchorResistanceProgress, 0.001f)
            assertEquals(0.0126f, interaction.position.y, 0.001f)
            interaction.onDrag(Offset(0f, -40f))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            assertEquals(0f, interaction.normalAnchorResistanceProgress, 0.001f)
            assertTrue(interaction.position.y < 0.1f)
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.runOnIdle {
            assertEquals(0.19f, interaction.position.y, 0.001f)
            interaction.onDragCancelled()
        }
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun parkedPillClickRestoresWithoutOpeningAddressEditor() {
        lateinit var browserController: BrowserController
        composeRule.runOnIdle {
            clearSession()
            val tab = BrowserTab(
                id = "dock-focus-tab",
                lastAccessedAt = 1L,
                title = "Example",
                url = "https://example.test/page",
            )
            BrowserSessionStore(composeRule.activity).apply {
                saveTabsImmediately(listOf(tab), tab.id)
                saveAddressBarDockPlacement(AddressBarDockPlacement.Default)
            }
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(AddressBarDockTestTags.EdgeTab)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(AddressBarDockTestTags.EdgeTab)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(AddressBarTestTags.TabButton).assertIsDisplayed()
        composeRule.onNodeWithTag(AddressBarTestTags.Editor).assertDoesNotExist()
    }

    @Test
    fun videoOnlyPresentationKeepsParkedPillHiddenUntilReturnCompletes() {
        lateinit var browserController: BrowserController
        val webViewVideoOnlyPresentation = mutableStateOf(false)
        val videoOnlyPresentation = mutableStateOf(false)
        composeRule.runOnIdle {
            clearSession()
            val tab = BrowserTab(
                id = "video-only-dock-tab",
                lastAccessedAt = 1L,
                title = "Video",
                url = "https://media.example/video",
            )
            BrowserSessionStore(composeRule.activity).apply {
                saveTabsImmediately(listOf(tab), tab.id)
                saveAddressBarDockPlacement(
                    AddressBarDockPlacement(
                        edge = AddressBarDockEdge.Right,
                        verticalFraction = 0.44f,
                    ),
                )
            }
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(
                    controller = browserController,
                    webViewVideoOnlyPresentation = webViewVideoOnlyPresentation.value,
                    videoOnlyPresentation = videoOnlyPresentation.value,
                )
            }
        }

        val initialBounds = composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.runOnIdle {
            webViewVideoOnlyPresentation.value = true
            videoOnlyPresentation.value = true
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab).assertDoesNotExist()

        composeRule.runOnIdle { webViewVideoOnlyPresentation.value = false }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab).assertDoesNotExist()

        composeRule.runOnIdle { videoOnlyPresentation.value = false }

        val returnedBounds = composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(initialBounds, returnedBounds)
    }

    @Test
    fun configuredRightParkActionMovesRememberedLeftPlacementToRight() {
        lateinit var browserController: BrowserController
        val rememberedPlacement = AddressBarDockPlacement(
            edge = AddressBarDockEdge.Left,
            verticalFraction = 0.42f,
        )
        composeRule.runOnIdle {
            clearSession()
            val tab = BrowserTab(
                id = "right-park-action-tab",
                lastAccessedAt = 1L,
                title = "Example",
                url = "https://example.test/page",
            )
            BrowserSessionStore(composeRule.activity).apply {
                saveTabsImmediately(listOf(tab), tab.id)
                saveAddressBarDockPlacement(rememberedPlacement)
                saveAddressBarDockPlacement(null)
                saveAddressBarActionLayout(
                    AddressBarActionLayout(
                        beforeAddress = listOf(AddressBarAction.ParkRight),
                        afterAddress = emptyList(),
                    ),
                )
            }
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }

        composeRule.onNodeWithTag(
            AddressBarActionTestTags.action(AddressBarAction.ParkRight),
        ).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(AddressBarDockTestTags.EdgeTab)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle {
            assertEquals(
                rememberedPlacement.copy(edge = AddressBarDockEdge.Right),
                browserController.addressBarDockPlacement,
            )
        }
    }

    @Test
    fun blankTabQrScannerMatchesDistributionCapability() {
        lateinit var browserController: BrowserController
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.address_empty_hint))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(AddressBarTestTags.Editor)
                .fetchSemanticsNodes().isNotEmpty()
        }

        if (BuildConfig.FOSS_DISTRIBUTION) {
            composeRule.onNodeWithTag(AddressBarTestTags.QrScanner).assertDoesNotExist()
        } else {
            composeRule.onNodeWithTag(AddressBarTestTags.QrScanner)
                .assertExists()
                .assertHasClickAction()
        }
    }

    @Test
    fun blankTabIncognitoToggleStaysBesideFocusedAddressEditor() {
        lateinit var browserController: BrowserController
        composeRule.runOnIdle {
            clearSession()
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.address_empty_hint))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(AddressBarTestTags.Editor)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(AddressBarTestTags.Editor).assertIsFocused()
        assertImeVisible()

        val editorBounds = composeRule.onNodeWithTag(AddressBarTestTags.Editor)
            .fetchSemanticsNode().boundsInRoot
        val toggleBounds = composeRule.onNodeWithTag(AddressBarTestTags.IncognitoToggle)
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
            .fetchSemanticsNode().boundsInRoot
        val gapPx = toggleBounds.left - editorBounds.right
        val maximumGapPx = with(composeRule.density) { 1.dp.toPx() }

        assertTrue(
            "Incognito toggle must follow editor: $editorBounds vs $toggleBounds",
            gapPx in -maximumGapPx..maximumGapPx,
        )
    }

    @Test
    fun centeredPillPlacesParkActionOnRememberedLeftSide() {
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(Modifier.size(width = 128.dp, height = 48.dp)) {
                    AddressBarCompactContent(
                        domain = "google.com",
                        showCastButton = false,
                        dockingEnabled = true,
                        dockTargetEdge = AddressBarDockEdge.Left,
                        onDock = {},
                    )
                }
            }
        }

        val parkActionBounds = composeRule.onNodeWithTag(AddressBarDockTestTags.ParkAction)
            .fetchSemanticsNode().boundsInRoot
        val parkIconBounds = composeRule.onNodeWithTag(
            testTag = AddressBarDockTestTags.ParkIcon,
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        val addressBounds = composeRule.onNodeWithTag(AddressBarDockTestTags.CompactAddress)
            .fetchSemanticsNode().boundsInRoot
        val contentBounds = composeRule.onNodeWithTag(AddressBarDockTestTags.CompactContent)
            .fetchSemanticsNode().boundsInRoot
        val maximumVisualGapPx = with(composeRule.density) { 8.dp.toPx() }
        val maximumOuterGapPx = with(composeRule.density) { 20.dp.toPx() }
        val minimumTrailingGapPx = with(composeRule.density) { 12.dp.toPx() }

        assertTrue(
            "Park action must precede address: $parkActionBounds vs $addressBounds",
            parkActionBounds.center.x < addressBounds.center.x,
        )
        assertTrue(
            "Park icon too far from address: ${addressBounds.left - parkIconBounds.right}px",
            addressBounds.left - parkIconBounds.right <= maximumVisualGapPx,
        )
        assertTrue(
            "Too much space left of chevron: ${parkIconBounds.left - contentBounds.left}px",
            parkIconBounds.left - contentBounds.left <= maximumOuterGapPx,
        )
        assertTrue(
            "Too little space right of address: ${contentBounds.right - addressBounds.right}px",
            contentBounds.right - addressBounds.right >= minimumTrailingGapPx,
        )
    }

    @Test
    fun centeredPillWithoutParkActionCentersAddressVertically() {
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(Modifier.size(width = 128.dp, height = 48.dp)) {
                    AddressBarCompactContent(
                        domain = "google.com",
                        showCastButton = false,
                        dockingEnabled = false,
                        dockTargetEdge = AddressBarDockEdge.Left,
                        onDock = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.CompactContent)
            .assertHeightIsEqualTo(48.dp)
        val contentBounds = composeRule.onNodeWithTag(AddressBarDockTestTags.CompactContent)
            .fetchSemanticsNode().boundsInRoot
        val addressBounds = composeRule.onNodeWithTag(AddressBarDockTestTags.CompactAddress)
            .fetchSemanticsNode().boundsInRoot

        assertEquals(contentBounds.center.y, addressBounds.center.y, 1f)
    }

    @Test
    fun persistedHighEdgePillCanBeDraggedAgainFromItsRenderedPosition() {
        lateinit var browserController: BrowserController
        composeRule.runOnIdle {
            clearSession()
            val tab = BrowserTab(
                id = "dock-hit-test-tab",
                lastAccessedAt = 1L,
                title = "Example",
                url = "https://example.test/page",
            )
            BrowserSessionStore(composeRule.activity).apply {
                saveTabsImmediately(listOf(tab), tab.id)
                saveAddressBarDockPlacement(
                    AddressBarDockPlacement(
                        edge = AddressBarDockEdge.Right,
                        verticalFraction = 0.45f,
                    ),
                )
            }
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(AddressBarDockTestTags.EdgeTab)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab)
            .performTouchInput {
                down(center)
                advanceEventTime(
                    AddressBarGestureRules.PARKED_REPOSITION_LONG_PRESS_MILLIS + 40L,
                )
                moveTo(Offset(center.x - 900f, center.y - 100f), delayMillis = 200L)
                up()
            }

        composeRule.waitForIdle()
        val placement = requireNotNull(browserController.addressBarDockPlacement)
        assertTrue(
            "Unexpected placement after full-host drag: $placement",
            placement.edge == AddressBarDockEdge.Left,
        )
        assertTrue(
            placement.verticalFraction > 0.45f,
        )
    }

    @Test
    fun lastPillPositionSurvivesRestoreDisableAndControllerRestart() {
        lateinit var browserController: BrowserController
        val rememberedPlacement = AddressBarDockPlacement(
            edge = AddressBarDockEdge.Left,
            verticalFraction = 0.42f,
        )
        composeRule.runOnIdle {
            clearSession()
            BrowserSessionStore(composeRule.activity)
                .saveAddressBarDockPlacement(rememberedPlacement)
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }

        composeRule.runOnIdle {
            browserController.updateAddressBarDocked(false)
            assertNull(browserController.addressBarDockPlacement)
            browserController.destroy()

            browserController = BrowserController(composeRule.activity)
            controller = browserController
            assertNull(browserController.addressBarDockPlacement)
            browserController.updateAddressBarDocked(true)
            assertEquals(rememberedPlacement, browserController.addressBarDockPlacement)

            browserController.updateAddressBarDockingEnabled(false)
            assertFalse(browserController.isAddressBarDockingEnabled)
            assertNull(browserController.addressBarDockPlacement)
            browserController.destroy()

            browserController = BrowserController(composeRule.activity)
            controller = browserController
            assertFalse(browserController.isAddressBarDockingEnabled)
            browserController.updateAddressBarDockingEnabled(true)
            browserController.updateAddressBarDocked(true)
            assertEquals(rememberedPlacement, browserController.addressBarDockPlacement)
        }
    }

    private fun clearSession() {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    private fun assertImeVisible() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
    }

    private fun assertImeHidden() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) != true
        }
    }

    private companion object {
        const val ImeTargetTag = "address_bar_dock_ime_target"
    }
}
