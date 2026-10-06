package dev.sk2andy.materialbrowser.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionActionKey
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionActionKind
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionActionState
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import eightbitlab.com.blurview.BlurTarget
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMainMenuInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun usesApprovedGroupsAndDismissesAfterAction() {
        val dismissals = AtomicInteger()
        val dockActions = AtomicInteger()
        val duplicateActions = AtomicInteger()
        val cookieChanges = AtomicInteger()
        val scrollChanges = AtomicInteger()
        val popupChanges = AtomicInteger()
        val desktopViewChanges = AtomicInteger()
        val zoomChanges = AtomicInteger()
        val safeAreaChanges = AtomicInteger()
        val extensionActionKey = GeckoExtensionActionKey(
            extensionId = "site-addon@example.test",
            tabId = "tab",
            kind = GeckoExtensionActionKind.Browser,
        )
        val firefoxExtensionAction = AtomicReference<GeckoExtensionActionKey>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var setMenuExpanded: (Boolean) -> Unit = {}
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val configuration = LocalConfiguration.current
            val shortConfiguration = remember(configuration) {
                Configuration(configuration).apply {
                    screenWidthDp = 320
                    screenHeightDp = 450
                }
            }
            CompositionLocalProvider(LocalConfiguration provides shortConfiguration) {
                MaterialBrowserTheme(
                    settings = AppearanceSettings(
                        surfaceStyle = BrowserSurfaceStyle.Frosted,
                        frostedBlurPercent = 100,
                    ),
                ) {
                    var expanded by remember { mutableStateOf(true) }
                    var blurTarget by remember { mutableStateOf<BlurTarget?>(null) }
                    setMenuExpanded = { expanded = it }
                    var cookieRemovalEnabled by remember { mutableStateOf(false) }
                    var forceVerticalScrolling by remember { mutableStateOf(false) }
                    var alwaysBlockPopups by remember { mutableStateOf(false) }
                    var desktopView by remember { mutableStateOf(false) }
                    var forcePageZooming by remember { mutableStateOf(false) }
                    var forceSafeArea by remember { mutableStateOf(false) }
                    Box {
                        BrowserContentBlurTarget(
                            enabled = true,
                            onTargetAttached = { blurTarget = it },
                            onTargetReleased = { if (blurTarget === it) blurTarget = null },
                        ) {}
                        BrowserMainMenu(
                            expanded = expanded,
                            backdropSource = blurTarget.asCandyChromeBackdropSource(),
                            onDismissRequest = {
                                if (expanded) dismissals.incrementAndGet()
                                expanded = false
                            },
                            pageSubtitle = "developer.android.com",
                            canGoBack = false,
                            canGoForward = false,
                            isLoading = false,
                            canToggleFavorite = true,
                            isFavorite = false,
                            isPinned = false,
                            canUsePageActions = true,
                            canOpenReader = true,
                            canTranslatePage = true,
                            canToggleDomainMute = true,
                            isDomainMuted = false,
                            canToggleAlwaysBlockPopups = true,
                            isAlwaysBlockPopupsEnabled = alwaysBlockPopups,
                            canToggleDesktopView = true,
                            isDesktopView = desktopView,
                            canToggleCookieBannerRemoval = true,
                            isCookieBannerRemovalEnabled = cookieRemovalEnabled,
                            canToggleForceVerticalScrolling = true,
                            isForceVerticalScrollingEnabled = forceVerticalScrolling,
                            canToggleForcePageZooming = true,
                            isForcePageZoomingEnabled = forcePageZooming,
                            canToggleForceSafeArea = true,
                            isForceSafeAreaEnabled = forceSafeArea,
                            canAddSiteCapsule = true,
                            canSnooze = true,
                            snoozedTabCount = 2,
                            onBack = {},
                            onForward = {},
                            onReloadOrStop = {},
                            onToggleFavorite = {},
                            onTogglePinned = {},
                            onShare = {},
                            onOpenExternal = {},
                            onPrint = {},
                            onOpenReader = {},
                            onTranslate = {},
                            onDomainMutedChange = {},
                            onAlwaysBlockPopupsChange = { enabled ->
                                popupChanges.incrementAndGet()
                                alwaysBlockPopups = enabled
                            },
                            onDesktopViewChange = { enabled ->
                                desktopViewChanges.incrementAndGet()
                                desktopView = enabled
                            },
                            onCookieBannerRemovalEnabledChange = { enabled ->
                                cookieChanges.incrementAndGet()
                                cookieRemovalEnabled = enabled
                            },
                            onForceVerticalScrollingChange = { enabled ->
                                scrollChanges.incrementAndGet()
                                forceVerticalScrolling = enabled
                            },
                            onForcePageZoomingChange = { enabled ->
                                zoomChanges.incrementAndGet()
                                forcePageZooming = enabled
                            },
                            onForceSafeAreaChange = { enabled ->
                                safeAreaChanges.incrementAndGet()
                                forceSafeArea = enabled
                            },
                            onOpenCandyTrail = {},
                            onAddSiteCapsule = {},
                            onSummarize = {},
                            onSnooze = {},
                            onSnoozedTabs = {},
                            onDuplicateTab = duplicateActions::incrementAndGet,
                            onDockAddressBar = dockActions::incrementAndGet,
                            onHistory = {},
                            firefoxExtensionActions = listOf(
                                GeckoExtensionActionState(
                                    key = extensionActionKey,
                                    title = "Site add-on",
                                    enabled = true,
                                    badgeText = null,
                                    badgeBackgroundColor = null,
                                    badgeTextColor = null,
                                ),
                            ),
                            onFirefoxExtensionAction = firefoxExtensionAction::set,
                            onSettings = {},
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(200L)

        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertExists()
        composeRule.onAllNodesWithTag(BrowserChromeSurfaceTestTags.BackdropBlur)
            .assertCountEquals(1)
        val menuBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu)
            .fetchSemanticsNode().boundsInRoot
        val favoriteBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Favorite)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val pinBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Pin)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val backBounds = composeRule.onNodeWithContentDescription(
            context.getString(R.string.action_back),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue(menuBounds.width <= 320f * density + 1f)
        assertEquals(favoriteBounds.width, favoriteBounds.height, 1f)
        assertEquals(pinBounds.width, pinBounds.height, 1f)
        assertTrue(backBounds.width >= 48f * density - 1f)
        assertTrue(backBounds.height >= 48f * density - 1f)
        composeRule.onAllNodesWithText(context.getString(R.string.action_back))
            .assertCountEquals(0)
        assertTrue(favoriteBounds.left >= menuBounds.left)
        assertTrue(pinBounds.right <= menuBounds.right)
        val fixedTags = listOf(
            BrowserMainMenuTestTags.Favorite,
            BrowserMainMenuTestTags.Pin,
            BrowserMainMenuTestTags.Toolbar,
            BrowserMainMenuTestTags.Footer,
        )
        val fixedBounds = fixedTags.associateWith { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
        }
        val fixedDescriptions = listOf(
            R.string.action_back,
            R.string.action_forward,
            R.string.action_reload,
        ).associate { label ->
            val description = context.getString(label)
            description to composeRule.onNodeWithContentDescription(description)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        val libraryBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.LibraryGroup)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val scrollBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.ScrollContent)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(libraryBounds.top >= scrollBounds.top - 1f)
        assertTrue(libraryBounds.bottom <= scrollBounds.bottom + 1f)
        assertTrue(scrollBounds.bottom <= backBounds.top + 1f)
        val libraryTags = listOf(
            BrowserMainMenuTestTags.SnoozedTabs,
            BrowserMainMenuTestTags.Favorites,
            BrowserMainMenuTestTags.Downloads,
            BrowserMainMenuTestTags.History,
            BrowserMainMenuTestTags.Settings,
        )
        libraryTags.forEach { tag -> composeRule.onNodeWithTag(tag).assertIsDisplayed() }
        val favoritesBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Favorites)
            .fetchSemanticsNode().boundsInRoot
        val downloadsBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Downloads)
            .fetchSemanticsNode().boundsInRoot
        val historyBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.History)
            .fetchSemanticsNode().boundsInRoot
        val settingsTop = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Settings)
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals(downloadsBounds.center.y, historyBounds.center.y, 1f)
        assertEquals(historyBounds.center.y, favoritesBounds.center.y, 1f)
        assertEquals(downloadsBounds.height, historyBounds.height, 1f)
        assertEquals(historyBounds.height, favoritesBounds.height, 1f)
        assertTrue(downloadsBounds.right <= historyBounds.left + 1f)
        assertTrue(favoritesBounds.right <= downloadsBounds.left + 1f)
        assertEquals(favoritesBounds.top, settingsTop, 1f)
        val pageGroup = hasTestTag(BrowserMainMenuTestTags.PageGroup)
        composeRule.onNode(
            pageGroup and
                hasAnyDescendant(hasText(context.getString(R.string.reader_open_action))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_duplicate_tab))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_translate_page))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_share))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_open_in_app))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_print))) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.CookieBannerRemoval)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.ForceVerticalScrolling)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.AlwaysBlockPopups)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.DesktopView)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.ForcePageZooming)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.ForceSafeArea)) and
                hasAnyDescendant(hasTestTag(DomainMuteMenuTestTags.Item)),
        ).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.action_mute_domain)).assertExists()
        val candyGroup = hasTestTag(BrowserMainMenuTestTags.CandyGroup)
        composeRule.onNode(
            candyGroup and
                hasAnyDescendant(hasText(context.getString(R.string.action_open_candy_trail))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_add_site_capsule))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_summarize))) and
                hasAnyDescendant(hasText(context.getString(R.string.action_snooze_tab))),
        ).assertExists()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.CandyGroup)
            .onChildren()
            .assertCountEquals(4)
        composeRule.onNodeWithText(
            context.getString(R.string.browser_menu_browser_group),
        ).assertExists()
        composeRule.onNode(
            hasTestTag(BrowserMainMenuTestTags.BrowserGroup) and
                hasAnyDescendant(hasText(context.getString(R.string.action_dock_address_bar))),
        ).assertExists()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.BrowserGroup)
            .onChildren()
            .assertCountEquals(1)
        composeRule.onNode(
            hasTestTag(BrowserMainMenuTestTags.LibraryGroup) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.SnoozedTabs)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.Favorites)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.Downloads)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.History)) and
                hasAnyDescendant(hasTestTag(BrowserMainMenuTestTags.Settings)),
        ).assertExists()
        composeRule.onNodeWithTag(FirefoxExtensionChromeTestTags.SectionTitle).assertExists()
        composeRule.onNodeWithTag(
            FirefoxExtensionChromeTestTags.action(extensionActionKey.saveableId),
        ).assertExists()

        val menuHeight = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu)
            .fetchSemanticsNode().boundsInRoot.height
        val maximumMenuHeight = 450f * context.resources.displayMetrics.density * 0.8f
        assertTrue(menuHeight <= maximumMenuHeight + 1f)
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Settings)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.CookieBannerRemoval)
            .performScrollTo()
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Checkbox,
                ),
            )
            .assertIsOff()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.CookieBannerRemoval).assertIsOn()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForceVerticalScrolling)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForceVerticalScrolling).assertIsOn()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.AlwaysBlockPopups)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.AlwaysBlockPopups).assertIsOn()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DesktopView)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DesktopView).assertIsOn()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForcePageZooming)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForcePageZooming).assertIsOn()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForceSafeArea)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForceSafeArea).assertIsOn()
        composeRule.onNodeWithTag(DomainMuteMenuTestTags.Item)
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Checkbox,
                ),
            )
            .assertIsOff()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertExists()
        assertEquals(1, cookieChanges.get())
        assertEquals(1, scrollChanges.get())
        assertEquals(1, popupChanges.get())
        assertEquals(1, desktopViewChanges.get())
        assertEquals(1, zoomChanges.get())
        assertEquals(1, safeAreaChanges.get())

        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DockAddressBar).performScrollTo()
        fixedBounds.forEach { (tag, before) ->
            val after = composeRule.onNodeWithTag(tag).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertEquals(before.top, after.top, 1f)
            assertEquals(before.bottom, after.bottom, 1f)
        }
        fixedDescriptions.forEach { (description, before) ->
            val after = composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertEquals(before.top, after.top, 1f)
            assertEquals(before.bottom, after.bottom, 1f)
        }
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.LibraryGroup).assertIsNotDisplayed()
        libraryTags.forEach { tag -> composeRule.onNodeWithTag(tag).assertIsNotDisplayed() }
        composeRule
            .onNodeWithTag(FirefoxExtensionChromeTestTags.SectionTitle)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DockAddressBar)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DockAddressBar).performClick()

        assertEquals(1, dismissals.get())
        assertEquals(1, dockActions.get())
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertExists()
        assertEquals(1, dockActions.get())
        composeRule.runOnUiThread { setMenuExpanded(true) }
        composeRule.mainClock.advanceTimeBy(5_000L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertIsDisplayed()
        composeRule.runOnUiThread { setMenuExpanded(false) }
        composeRule.mainClock.advanceTimeBy(5_000L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
        assertEquals(1, dockActions.get())
        composeRule.mainClock.autoAdvance = true

        composeRule.runOnIdle { setMenuExpanded(true) }
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DuplicateTab)
            .assertIsDisplayed()
            .performClick()

        assertEquals(2, dismissals.get())
        assertEquals(1, duplicateActions.get())
        assertEquals(null, firefoxExtensionAction.get())

        composeRule.runOnIdle { setMenuExpanded(true) }
        composeRule.onNodeWithTag(
            FirefoxExtensionChromeTestTags.action(extensionActionKey.saveableId),
        ).performScrollTo().performClick()

        assertEquals(3, dismissals.get())
        assertEquals(extensionActionKey, firefoxExtensionAction.get())
    }

    @Test
    fun wrapsShortContentAndPreservesFavoriteAndPinAcrossReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = context.resources.displayMetrics.density
        val dismissals = AtomicInteger()
        var reopenMenu: () -> Unit = {}
        composeRule.setContent {
            var expanded by remember { mutableStateOf(true) }
            var favorite by remember { mutableStateOf(false) }
            var pinned by remember { mutableStateOf(false) }
            reopenMenu = { expanded = true }
            ShortBrowserMainMenu(
                expanded = expanded,
                isFavorite = favorite,
                isPinned = pinned,
                onDismiss = {
                    dismissals.incrementAndGet()
                    expanded = false
                },
                onToggleFavorite = { favorite = !favorite },
                onTogglePinned = { pinned = !pinned },
            )
        }

        val menuBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(menuBounds.height < 800f * density * 0.8f - 48f * density)
        val footerBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Footer)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val favoriteBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Favorite)
            .assertIsDisplayed().assertIsNotSelected().fetchSemanticsNode().boundsInRoot
        val pinBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Pin)
            .assertIsDisplayed().assertIsNotSelected().fetchSemanticsNode().boundsInRoot
        assertEquals(footerBounds.center.y, favoriteBounds.center.y, 1f)
        assertEquals(favoriteBounds.center.y, pinBounds.center.y, 1f)
        assertEquals(menuBounds.bottom, footerBounds.bottom, 1f)
        val duplicateBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.DuplicateTab)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val scrollBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.ScrollContent)
            .fetchSemanticsNode().boundsInRoot
        val libraryBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.LibraryGroup)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(libraryBounds.top >= scrollBounds.top - 1f)
        assertTrue(libraryBounds.bottom <= duplicateBounds.top + 1f)
        assertTrue(scrollBounds.bottom - duplicateBounds.bottom <= 32f * density)

        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Favorite).performClick()
        assertEquals(1, dismissals.get())
        composeRule.runOnIdle { reopenMenu() }
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Favorite).assertIsSelected()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_remove_favorite))
            .assertIsDisplayed()

        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Pin).performClick()
        assertEquals(2, dismissals.get())
        composeRule.runOnIdle { reopenMenu() }
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Favorite).assertIsSelected()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Pin).assertIsSelected()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_remove_pin))
            .assertIsDisplayed()
    }

    @Test
    fun keepsNarrowMenuTargetsAtLeast48DpInsideMenu() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = context.resources.displayMetrics.density
        composeRule.setContent {
            ShortBrowserMainMenu(screenWidthDp = 284)
        }

        val menuBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val tags = listOf(
            BrowserMainMenuTestTags.SnoozedTabs,
            BrowserMainMenuTestTags.Favorites,
            BrowserMainMenuTestTags.Downloads,
            BrowserMainMenuTestTags.History,
            BrowserMainMenuTestTags.Settings,
            BrowserMainMenuTestTags.Favorite,
            BrowserMainMenuTestTags.Pin,
        )
        tags.forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$tag width", bounds.width >= 48f * density - 1f)
            assertTrue("$tag height", bounds.height >= 48f * density - 1f)
            assertTrue("$tag left", bounds.left >= menuBounds.left - 1f)
            assertTrue("$tag right", bounds.right <= menuBounds.right + 1f)
            assertTrue("$tag bottom", bounds.bottom <= menuBounds.bottom + 1f)
        }
        listOf(R.string.action_back, R.string.action_forward, R.string.action_reload).forEach { label ->
            val bounds = composeRule.onNodeWithContentDescription(context.getString(label))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= 48f * density - 1f)
            assertTrue(bounds.height >= 48f * density - 1f)
            assertTrue(bounds.left >= menuBounds.left - 1f)
            assertTrue(bounds.right <= menuBounds.right + 1f)
        }
        val reloadBounds = composeRule.onNodeWithContentDescription(
            context.getString(R.string.action_reload),
        ).fetchSemanticsNode().boundsInRoot
        val favoriteBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Favorite)
            .fetchSemanticsNode().boundsInRoot
        val pinBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Pin)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(favoriteBounds.top >= reloadBounds.bottom)
        assertTrue(pinBounds.top >= reloadBounds.bottom)
        assertEquals(favoriteBounds.center.y, pinBounds.center.y, 1f)

        val downloadsBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Downloads)
            .fetchSemanticsNode().boundsInRoot
        val historyBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.History)
            .fetchSemanticsNode().boundsInRoot
        val settingsBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Settings)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(historyBounds.top >= downloadsBounds.bottom - 1f)
        assertTrue(settingsBounds.top >= downloadsBounds.bottom - 1f)
        assertEquals(historyBounds.center.y, settingsBounds.center.y, 1f)
        assertEquals(historyBounds.height, settingsBounds.height, 1f)
    }

    @Test
    fun keepsFooterVisibleWhileScrollingInShortWindow() {
        val density = InstrumentationRegistry.getInstrumentation().targetContext
            .resources.displayMetrics.density
        composeRule.setContent {
            ShortBrowserMainMenu(screenHeightDp = 300)
        }

        val menuBounds = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(menuBounds.height <= 188f * density + 1f)
        val footerBefore = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Footer)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DuplicateTab)
            .performScrollTo().assertIsDisplayed()
        val footerAfter = composeRule.onNodeWithTag(BrowserMainMenuTestTags.Footer)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(footerBefore.top, footerAfter.top, 1f)
        assertEquals(footerBefore.bottom, footerAfter.bottom, 1f)
        assertTrue(footerAfter.bottom <= menuBounds.bottom + 1f)
    }

    @Composable
    private fun ShortBrowserMainMenu(
        screenWidthDp: Int = 400,
        screenHeightDp: Int = 800,
        expanded: Boolean = true,
        isFavorite: Boolean = false,
        isPinned: Boolean = false,
        onDismiss: () -> Unit = {},
        onToggleFavorite: () -> Unit = {},
        onTogglePinned: () -> Unit = {},
    ) {
        val configuration = LocalConfiguration.current
        val menuConfiguration = remember(configuration, screenWidthDp) {
            Configuration(configuration).apply {
                this.screenWidthDp = screenWidthDp
                this.screenHeightDp = screenHeightDp
            }
        }
        val menuLayout = remember {
            val visibleEntries = setOf(
                BrowserMenuEntry.Back,
                BrowserMenuEntry.Forward,
                BrowserMenuEntry.Reload,
                BrowserMenuEntry.Favorite,
                BrowserMenuEntry.Pin,
                BrowserMenuEntry.DuplicateTab,
                BrowserMenuEntry.OpenSnoozedTabs,
                BrowserMenuEntry.OpenFavorites,
                BrowserMenuEntry.OpenDownloads,
                BrowserMenuEntry.OpenHistory,
                BrowserMenuEntry.OpenSettings,
            )
            BrowserMenuLayout(
                locations = BrowserMenuEntry.entries.associateWith { entry ->
                    if (entry in visibleEntries) {
                        BrowserMenuLocation.Tab
                    } else {
                        BrowserMenuLocation.Nowhere
                    }
                },
            )
        }
        CompositionLocalProvider(LocalConfiguration provides menuConfiguration) {
            MaterialBrowserTheme {
                BrowserMainMenu(
                    expanded = expanded,
                    backdropSource = null,
                    onDismissRequest = onDismiss,
                    pageSubtitle = "example.test",
                    canGoBack = true,
                    canGoForward = true,
                    isLoading = false,
                    canToggleFavorite = true,
                    isFavorite = isFavorite,
                    isPinned = isPinned,
                    canUsePageActions = true,
                    canOpenReader = true,
                    canTranslatePage = true,
                    canToggleDomainMute = true,
                    isDomainMuted = false,
                    canToggleAlwaysBlockPopups = true,
                    isAlwaysBlockPopupsEnabled = false,
                    canToggleDesktopView = true,
                    isDesktopView = false,
                    canToggleCookieBannerRemoval = true,
                    isCookieBannerRemovalEnabled = false,
                    canToggleForceVerticalScrolling = true,
                    isForceVerticalScrollingEnabled = false,
                    canToggleForcePageZooming = true,
                    isForcePageZoomingEnabled = false,
                    canToggleForceSafeArea = true,
                    isForceSafeAreaEnabled = false,
                    canAddSiteCapsule = true,
                    canSnooze = true,
                    snoozedTabCount = 0,
                    onBack = {},
                    onForward = {},
                    onReloadOrStop = {},
                    onToggleFavorite = onToggleFavorite,
                    onTogglePinned = onTogglePinned,
                    onShare = {},
                    onOpenExternal = {},
                    onPrint = {},
                    onOpenReader = {},
                    onTranslate = {},
                    onDomainMutedChange = {},
                    onAlwaysBlockPopupsChange = {},
                    onDesktopViewChange = {},
                    onCookieBannerRemovalEnabledChange = {},
                    onForceVerticalScrollingChange = {},
                    onForcePageZoomingChange = {},
                    onForceSafeAreaChange = {},
                    onOpenCandyTrail = {},
                    onAddSiteCapsule = {},
                    onSummarize = {},
                    onSnooze = {},
                    onSnoozedTabs = {},
                    onDockAddressBar = {},
                    onHistory = {},
                    menuLayout = menuLayout,
                    onSettings = {},
                )
            }
        }
    }

    @Test
    fun disablesReaderForUnsupportedPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val invocations = AtomicInteger()
        val favoriteInvocations = AtomicInteger()
        var setMenuExpanded: (Boolean) -> Unit = {}
        val command = UserScriptMenuCommand(
            tabId = "tab",
            scriptId = "script",
            scriptName = "Password helper",
            commandId = "reveal",
            caption = "Reveal passwords",
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                var expanded by remember { mutableStateOf(true) }
                setMenuExpanded = { expanded = it }
                BrowserMainMenu(
                    expanded = expanded,
                    backdropSource = null,
                    onDismissRequest = { expanded = false },
                    pageSubtitle = "New tab",
                    canGoBack = false,
                    canGoForward = false,
                    isLoading = false,
                    canToggleFavorite = false,
                    isFavorite = false,
                    isPinned = false,
                    canUsePageActions = false,
                    canOpenReader = false,
                    canTranslatePage = false,
                    canToggleDomainMute = false,
                    isDomainMuted = false,
                    canToggleAlwaysBlockPopups = false,
                    isAlwaysBlockPopupsEnabled = false,
                    canToggleDesktopView = false,
                    isDesktopView = false,
                    canToggleCookieBannerRemoval = false,
                    isCookieBannerRemovalEnabled = false,
                    canToggleForceVerticalScrolling = false,
                    isForceVerticalScrollingEnabled = false,
                    canToggleForcePageZooming = false,
                    isForcePageZoomingEnabled = false,
                    canToggleForceSafeArea = false,
                    isForceSafeAreaEnabled = false,
                    canAddSiteCapsule = false,
                    canSnooze = false,
                    snoozedTabCount = 0,
                    userScriptMenuCommands = listOf(command),
                    onBack = {},
                    onForward = {},
                    onReloadOrStop = {},
                    onToggleFavorite = {},
                    onTogglePinned = {},
                    onShare = {},
                    onOpenExternal = {},
                    onPrint = {},
                    onOpenReader = {},
                    onTranslate = {},
                    onDomainMutedChange = {},
                    onAlwaysBlockPopupsChange = {},
                    onDesktopViewChange = {},
                    onCookieBannerRemovalEnabledChange = {},
                    onForceVerticalScrollingChange = {},
                    onForcePageZoomingChange = {},
                    onForceSafeAreaChange = {},
                    onOpenCandyTrail = {},
                    onAddSiteCapsule = {},
                    onSummarize = {},
                    onSnooze = {},
                    onSnoozedTabs = {},
                    onUserScriptMenuCommand = { selected ->
                        if (selected == command) invocations.incrementAndGet()
                    },
                    onDockAddressBar = {},
                    onFavorites = favoriteInvocations::incrementAndGet,
                    onHistory = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.reader_open_action),
        ).assertIsNotEnabled()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DuplicateTab).assertIsNotEnabled()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Translate).assertIsNotEnabled()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.CookieBannerRemoval)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForceVerticalScrolling)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.AlwaysBlockPopups)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.DesktopView)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ForcePageZooming)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.ToppingsGroup).assertExists()
        composeRule.onNodeWithText("Password helper").assertExists()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.userScriptCommand("reveal"))
            .performScrollTo()
            .performClick()
        assertEquals(1, invocations.get())
        composeRule.runOnIdle { setMenuExpanded(true) }
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Favorites)
            .performClick()
        assertEquals(1, favoriteInvocations.get())
    }
}
