package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
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
    fun edgeTabClickRestoresAndRequestsAddressEditor() {
        val restores = AtomicInteger()
        val addressRequests = AtomicInteger()
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
                    onRestoreAndEdit = {
                        restores.incrementAndGet()
                        addressRequests.incrementAndGet()
                    },
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
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab).performClick()

        assertEquals(1, restores.get())
        assertEquals(1, addressRequests.get())
    }

    @Test
    fun edgeTabDiagonalDragMovesToLeftEdgeAndPersistsVerticalPosition() {
        val settledPlacement = AtomicReference<AddressBarDockPlacement>()
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
                    onRestoreAndEdit = {},
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
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressBarDockTestTags.EdgeTab)
            .performTouchInput {
                swipe(
                    start = center,
                    end = Offset(center.x - 220f, center.y - 120f),
                    durationMillis = 300,
                )
            }

        assertEquals(AddressBarDockEdge.Left, settledPlacement.get().edge)
        assertTrue(settledPlacement.get().verticalFraction > 0.3f)
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
                    onRestoreAndEdit = {},
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
    fun parkedPillClickRestoresAndFocusesAddressEditor() {
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
            composeRule.onAllNodesWithTag(AddressBarTestTags.Editor)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(AddressBarTestTags.Editor).assertIsFocused()
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
                swipe(
                    start = center,
                    end = Offset(center.x - 900f, center.y - 100f),
                    durationMillis = 400,
                )
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
}
