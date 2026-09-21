package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.FavoriteFaviconStore
import dev.sk2andy.materialbrowser.data.FavoriteFolder
import dev.sk2andy.materialbrowser.data.FavoriteLibrary
import dev.sk2andy.materialbrowser.data.FavoriteMutation
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewChromeTestTags
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewTabFavoriteInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            controller = null
            clearSession()
            FavoriteFaviconStore(composeRule.activity).prune(emptySet())
        }
    }

    @Test
    fun favoriteOpensFromNewTabWhileAddressEditorIsVisible() {
        val favoriteUrl = "https://127.0.0.1/favorite"
        val favoriteTitle = "Example favorite"
        val browserController = createController(
            favorite = FavoriteEntry(
                url = favoriteUrl,
                title = favoriteTitle,
                addedAt = 1L,
            ),
        )
        setBrowserContent(browserController)

        composeRule.onNodeWithTag(
            AddressBarActionTestTags.action(AddressBarAction.NewTab),
        ).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.activeTabs.size == 2
        }
        val closeAddressDescription = composeRule.activity.getString(
            R.string.cd_close_address_input,
        )
        composeRule.onNodeWithContentDescription(closeAddressDescription).assertExists()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.favorites_title),
        ).assertIsDisplayed()

        composeRule.onNodeWithText(favoriteTitle)
            .assertHasClickAction()
            .performScrollTo()
            .performTouchInput { click() }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.selectedTab.url == favoriteUrl
        }
        composeRule.onNodeWithContentDescription(closeAddressDescription).assertDoesNotExist()
        assertEquals(favoriteUrl, browserController.selectedTab.url)
    }

    @Test
    fun newlyAddedFavoriteOpensAfterReturningToExistingNewTab() {
        val browserController = createController(
            favorite = FavoriteEntry(
                url = "https://existing-favorite.example/",
                title = "Existing favorite",
                addedAt = 1L,
            ),
        )
        setBrowserContent(browserController)
        val newFavoriteUrl = "https://new-favorite.example/"
        lateinit var blankTabId: String

        composeRule.runOnIdle {
            blankTabId = browserController.createTab()
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.selectedTabId == blankTabId
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            browserController.createTab(initialUrl = newFavoriteUrl)
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.selectedTab.url == newFavoriteUrl
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(requireNotNull(browserController.toggleFavorite()).added)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AddressBarTestTags.TabButton).performClick()
        composeRule.onNodeWithTag(TabOverviewChromeTestTags.Root).assertExists()
        composeRule.onNodeWithTag(SnoozeTestTags.overviewTab(blankTabId)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.selectedTabId == blankTabId &&
                browserController.favorites.any { it.url == newFavoriteUrl }
        }
        composeRule.onNodeWithTag(TabOverviewChromeTestTags.Root).assertDoesNotExist()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(NewTabFavoritesTestTags.favorite(newFavoriteUrl))
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.selectedTab.url == newFavoriteUrl
        }
        assertEquals(blankTabId, browserController.selectedTabId)
    }

    @Test
    fun openingFavoriteFromPrivateTabCreatesRegularTab() {
        val favorite = FavoriteEntry(
            url = "https://favorite.example/",
            title = "Favorite",
            addedAt = 1L,
        )
        val browserController = createController(favorite)

        composeRule.runOnIdle {
            browserController.createTab(isIncognito = true)
            assertTrue(browserController.selectedTab.isIncognito)

            assertTrue(browserController.openFavorite(favorite.url))
            assertFalse(browserController.selectedTab.isIncognito)
            assertEquals(favorite.url, browserController.selectedTab.url)
        }
    }

    @Test
    fun equalFavoriteReloadInvalidatesOlderUndo() {
        val favorite = FavoriteEntry(
            url = "https://favorite.example/",
            title = "Favorite",
            addedAt = 1L,
        )
        val browserController = createController(favorite)

        composeRule.runOnIdle {
            assertTrue(browserController.openUrl(favorite.url))
            val mutation = requireNotNull(browserController.toggleFavorite())
            browserController.reloadFavorites()

            assertFalse(browserController.undoFavorite(mutation))
        }
    }

    @Test
    fun committedReorderInvalidatesOlderFavoriteUndo() {
        val first = FavoriteEntry("https://first.example/", "First", 1L)
        val second = FavoriteEntry("https://second.example/", "Second", 2L)
        val third = FavoriteEntry("https://third.example/", "Third", 3L)
        val browserController = createController(FavoriteLibrary(listOf(first, second, third)))
        lateinit var mutation: FavoriteMutation

        composeRule.runOnIdle {
            assertTrue(browserController.openUrl(first.url))
            mutation = requireNotNull(browserController.toggleFavorite())
            assertTrue(browserController.reorderFavorite(second.id, destinationIndex = 1))
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.favorites.map(FavoriteEntry::id) == listOf(third.id, second.id)
        }
        composeRule.runOnIdle {
            assertFalse(browserController.undoFavorite(mutation))
            assertEquals(
                listOf(third.id, second.id),
                browserController.favorites.map(FavoriteEntry::id),
            )
        }
    }

    @Test
    fun storedFavoriteFaviconLoadsIntoNewTabState() {
        val favorite = FavoriteEntry(
            url = "https://favorite.example/",
            title = "Favorite",
            addedAt = 1L,
        )
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        composeRule.runOnIdle {
            FavoriteFaviconStore(composeRule.activity).apply {
                prune(emptySet())
                assertTrue(save(favorite.url, bitmap))
            }
        }
        bitmap.recycle()

        val browserController = createController(favorite)

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.favoriteFavicons[favorite.url]?.isRecycled == false
        }
        assertEquals(16, browserController.favoriteFavicons[favorite.url]?.width)
    }

    @Test
    fun nestedFolderNavigatesFromNewTabQuickPicker() {
        val outer = FavoriteFolder(id = "outer", title = "Outer")
        val nested = FavoriteFolder(id = "nested", title = "Nested", parentFolderId = outer.id)
        val favorite = FavoriteEntry(
            url = "https://nested.example/",
            title = "Nested favorite",
            addedAt = 1L,
            parentFolderId = nested.id,
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                NewTabPage(
                    favorites = emptyList(),
                    favoriteLibrary = FavoriteLibrary(listOf(outer, nested, favorite)),
                    incognito = false,
                    modeProgress = 0f,
                    revealOriginInRoot = androidx.compose.ui.geometry.Offset.Zero,
                    onSearch = {},
                    onFavorite = {},
                )
            }
        }

        composeRule.onNodeWithTag(NewTabFavoritesTestTags.folder(outer.id)).performClick()
        composeRule.onNodeWithTag(NewTabFavoritesTestTags.folder(nested.id)).performClick()
        composeRule.onNodeWithTag(NewTabFavoritesTestTags.favorite(favorite.url)).assertIsDisplayed()
    }

    private fun createController(favorite: FavoriteEntry): BrowserController =
        createController(FavoriteLibrary(listOf(favorite)))

    private fun createController(favoriteLibrary: FavoriteLibrary): BrowserController {
        lateinit var browserController: BrowserController
        composeRule.runOnIdle {
            clearSession()
            BrowserSessionStore(composeRule.activity).apply {
                saveFavoriteLaunchAnimationEnabled(false)
                saveFavoriteLibrary(favoriteLibrary)
                saveHistory(
                    listOf(
                        HistoryEntry(
                            url = "https://history.example/recent",
                            title = "Recent history",
                            lastVisitedAt = 2L,
                        ),
                    ),
                )
            }
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        return browserController
    }

    private fun setBrowserContent(browserController: BrowserController) {
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }
        composeRule.waitForIdle()
    }

    private fun clearSession() {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}
