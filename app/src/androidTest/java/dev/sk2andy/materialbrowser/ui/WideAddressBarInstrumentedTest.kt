package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewChromeTestTags
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class WideAddressBarInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @Before
    fun requireTabletWidth() {
        assumeTrue(
            composeRule.activity.resources.configuration.screenWidthDp.toFloat() >=
                AddressBarWideLayoutRules.MIN_WINDOW_WIDTH_DP,
        )
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            if (controller != null) {
                controller?.destroy()
                controller = null
                clearSession()
            }
        }
    }

    @Test
    fun tabletChromeKeepsFloatingActionsAndMenuAroundScrollableTabs() {
        val browserController = createBrowserWithTabs()
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(WideAddressTabStripTestTags.Strip)
                .fetchSemanticsNodes().isNotEmpty()
        }
        ensureBrowserChromeVisible()
        val tabsActionVisible = runCatching {
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                runCatching {
                    composeRule.onNodeWithTag(AddressBarTestTags.TabButton)
                        .assertIsDisplayed()
                }.isSuccess
            }
        }.isSuccess
        assertTrue(
            "Action layout: ${browserController.addressBarActionLayout}\n" +
                composeRule.onRoot().printToString(),
            tabsActionVisible,
        )

        val strip = composeRule.onNodeWithTag(WideAddressTabStripTestTags.Strip)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val tabsAction = composeRule.onNodeWithTag(AddressBarTestTags.TabButton)
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val newTabAction = composeRule.onNodeWithTag(
            AddressBarActionTestTags.action(AddressBarAction.NewTab),
        ).assertIsDisplayed().getUnclippedBoundsInRoot()
        val more = composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.cd_more_options),
        ).assertIsDisplayed().getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()

        assertTrue(tabsAction.right <= strip.left)
        assertTrue(strip.right <= newTabAction.left)
        assertTrue(newTabAction.right <= more.left)
        assertTrue(strip.top > (root.bottom - root.top) * 0.7f)
        assertTrue(strip.bottom < root.bottom)

        composeRule.onNodeWithTag(tabTag("wide-second")).performClick()
        composeRule.onNodeWithTag(tabTag("wide-second")).assertIsSelected()
        composeRule.onNodeWithTag(WideAddressTabStripTestTags.Strip)
            .performTouchInput { swipeLeft() }
        composeRule.runOnIdle {
            assertEquals("wide-second", browserController.selectedTab.id)
        }
        composeRule.onNodeWithTag(tabTag("wide-second")).performClick()
        composeRule.onNodeWithTag(AddressBarTestTags.Editor).assertIsFocused()
    }

    @Test
    fun swipeUpFromTabletTabStripOpensOverview() {
        val browserController = createBrowserWithTabs()
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(WideAddressTabStripTestTags.Strip)
                .fetchSemanticsNodes().isNotEmpty()
        }
        ensureBrowserChromeVisible()

        composeRule.onNodeWithTag(WideAddressTabStripTestTags.Strip)
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, -200f), delayMillis = 200L)
                up()
            }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(TabOverviewChromeTestTags.Root)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun currentTabAnimatesIntoFocusedEditorAndBack() {
        val browserController = createBrowserWithTabs()
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(WideAddressTabStripTestTags.Strip)
                .fetchSemanticsNodes().isNotEmpty()
        }
        ensureBrowserChromeVisible()
        composeRule.mainClock.autoAdvance = false
        repeat(18) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.autoAdvance = true

        val currentTab = composeRule.onNodeWithTag(tabTag("wide-first"))
            .assertIsSelected()
        val field = composeRule.onNodeWithTag(AddressBarTestTags.PrimaryField)
        val fieldBounds = field.fetchSemanticsNode().boundsInRoot
        val tabBounds = currentTab.fetchSemanticsNode().boundsInRoot
        val tabSampleX = (tabBounds.center.x - fieldBounds.left).toInt()
        val tabSampleY = (tabBounds.top - fieldBounds.top + 6f).toInt()
        val moreDescription = composeRule.activity.getString(R.string.cd_more_options)
        composeRule.onNodeWithContentDescription(moreDescription).assertIsDisplayed()
        val restingField = fieldColors(tabSampleX, tabSampleY)
        val restingContrast = colorDistance(restingField.first, restingField.second)
        assertTrue("Selected chip must contrast with the field", restingContrast > 0.05f)

        var settledEditorColor = restingField.second
        composeRule.mainClock.autoAdvance = false
        try {
            currentTab.performClick()
            val openingFrames = (0 until 18).map {
                composeRule.mainClock.advanceTimeByFrame()
                fieldColors(tabSampleX, tabSampleY)
            }
            settledEditorColor = openingFrames.last().second
            assertTrue(
                "Address field color must animate into edit mode: " +
                    "start=${restingField.second}, frames=${openingFrames.map { it.second }}",
                hasIntermediateColor(
                    openingFrames.map { it.second },
                    restingField.second,
                    settledEditorColor,
                ),
            )
            assertTrue(
                "Selected chip must fade during the transition",
                openingFrames.any { frame ->
                    val contrast = colorDistance(frame.first, frame.second)
                    contrast in (restingContrast * 0.15f)..(restingContrast * 0.85f)
                },
            )
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.onNodeWithTag(AddressBarTestTags.Editor).assertIsFocused()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithContentDescription(
                composeRule.activity.getString(R.string.cd_close_address_input),
            ).performClick()
            val closingColors = (0 until 18).map {
                composeRule.mainClock.advanceTimeByFrame()
                fieldColors(tabSampleX, tabSampleY).second
            }
            assertTrue(
                "Address field color must animate back to the tab strip",
                hasIntermediateColor(
                    closingColors,
                    settledEditorColor,
                    closingColors.last(),
                ),
            )
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.onNodeWithTag(tabTag("wide-first")).assertIsSelected()
        composeRule.onNodeWithContentDescription(moreDescription).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) != true
        }
    }

    private fun fieldColors(tabSampleX: Int, tabSampleY: Int): Pair<Color, Color> {
        val pixels = composeRule.onNodeWithTag(AddressBarTestTags.PrimaryField)
            .captureToImage().toPixelMap()
        return pixels[
            tabSampleX.coerceIn(0, pixels.width - 1),
            tabSampleY.coerceIn(0, pixels.height - 1),
        ] to pixels[pixels.width * 3 / 4, pixels.height / 2]
    }

    private fun hasIntermediateColor(
        frames: List<Color>,
        start: Color,
        end: Color,
    ): Boolean {
        val totalDistance = colorDistance(start, end)
        return totalDistance > 0.04f && frames.any { color ->
            colorDistance(color, start) > totalDistance * 0.15f &&
                colorDistance(color, end) > totalDistance * 0.15f
        }
    }

    private fun colorDistance(first: Color, second: Color): Float =
        abs(first.red - second.red) +
            abs(first.green - second.green) +
            abs(first.blue - second.blue) +
            abs(first.alpha - second.alpha)

    private fun ensureBrowserChromeVisible() {
        val tabsActionTag = AddressBarTestTags.TabButton
        val chromeReady = runCatching {
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                composeRule.onAllNodesWithTag(
                    TabOverviewChromeTestTags.Root,
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty() ||
                    runCatching {
                        composeRule.onNodeWithTag(tabsActionTag).assertIsDisplayed()
                    }.isSuccess
            }
        }.isSuccess
        assertTrue(
            composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 7),
            chromeReady,
        )
        if (
            composeRule.onAllNodesWithTag(
                TabOverviewChromeTestTags.Root,
                useUnmergedTree = true,
            )
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            composeRule.onNodeWithTag("overview_tab:wide-first").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithTag(TabOverviewChromeTestTags.Root)
                    .fetchSemanticsNodes().isEmpty()
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeRule.onNodeWithTag(tabsActionTag).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun createBrowserWithTabs(): BrowserController {
        lateinit var browserController: BrowserController
        composeRule.runOnIdle {
            clearSession()
            val tabs = listOf(
                BrowserTab(
                    id = "wide-first",
                    lastAccessedAt = 1L,
                    title = "First",
                    url = "https://first.example.test/",
                ),
                BrowserTab(
                    id = "wide-second",
                    lastAccessedAt = 2L,
                    title = "Second",
                    url = "https://second.example.test/",
                ),
            )
            BrowserSessionStore(composeRule.activity)
                .saveTabsImmediately(tabs, tabs.first().id)
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        return browserController
    }

    private fun clearSession() {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    private fun tabTag(id: String) = WideAddressTabStripTestTags.TabPrefix + id

}
