package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewTabPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyFavoritesHideContainer() {
        setNewTab(favorites = emptyList())

        composeRule.onNodeWithTag(NewTabFavoritesTestTags.Container).assertDoesNotExist()
    }

    @Test
    fun favoritesUseFourColumnsAndScrollInsideBoundedContainer() {
        val favorites = (1..21).map(::favorite)
        setNewTab(favorites = favorites)

        val firstRow = favorites.take(4).map { entry ->
            composeRule.onNodeWithTag(NewTabFavoritesTestTags.favorite(entry.url))
                .fetchSemanticsNode().boundsInRoot.center
        }
        val fifth = composeRule.onNodeWithTag(
            NewTabFavoritesTestTags.favorite(favorites[4].url),
        ).fetchSemanticsNode().boundsInRoot.center

        assertEquals(4, firstRow.map(Offset::x).distinct().size)
        assertEquals(1, firstRow.map(Offset::y).distinct().size)
        assertTrue(fifth.y > firstRow.first().y)

        composeRule.onNodeWithTag(NewTabFavoritesTestTags.Container)
            .performScrollToNode(
                hasTestTag(NewTabFavoritesTestTags.favorite(favorites.last().url)),
            )
        composeRule.onNodeWithTag(NewTabFavoritesTestTags.favorite(favorites.last().url))
            .assertIsDisplayed()
    }

    @Test
    fun shortViewportKeepsFavoritesInsideAndScrollable() {
        val favorites = (1..21).map(::favorite)
        setNewTab(
            favorites = favorites,
            viewportHeight = 360.dp,
        )

        val viewportBounds = composeRule.onNodeWithTag(SHORT_VIEWPORT_TAG)
            .fetchSemanticsNode().boundsInRoot
        val containerBounds = composeRule.onNodeWithTag(NewTabFavoritesTestTags.Container)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(containerBounds.bottom <= viewportBounds.bottom)

        composeRule.onNodeWithTag(NewTabFavoritesTestTags.Container)
            .performScrollToNode(
                hasTestTag(NewTabFavoritesTestTags.favorite(favorites.last().url)),
            )
        composeRule.onNodeWithTag(NewTabFavoritesTestTags.favorite(favorites.last().url))
            .assertIsDisplayed()
    }

    @Test
    fun storedBitmapRendersAsFavoriteFavicon() {
        val favorite = favorite(1)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        try {
            setNewTab(
                favorites = listOf(favorite),
                favicons = mapOf(favorite.url to bitmap),
            )

            composeRule.onNodeWithTag(
                NewTabFavoritesTestTags.favicon(favorite.url),
                useUnmergedTree = true,
            )
                .assertExists()
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun blankTabPreviewKeepsFavoriteFavicons() {
        val favorite = favorite(1)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        try {
            composeRule.setContent {
                MaterialBrowserTheme {
                    BlankTabPreview(
                        favorites = listOf(favorite),
                        favoriteFavicons = mapOf(favorite.url to bitmap),
                        favoritesAlpha = { 1f },
                    )
                }
            }

            composeRule.onNodeWithTag(
                NewTabFavoritesTestTags.favicon(favorite.url),
                useUnmergedTree = true,
            ).assertExists()
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun disabledLaunchAnimationOpensFavoriteImmediately() {
        val favorite = favorite(1)
        var openedUrl: String? = null
        setNewTab(
            favorites = listOf(favorite),
            favoriteLaunchAnimationEnabled = false,
            onFavorite = { openedUrl = it },
        )

        composeRule.onNodeWithTag(NewTabFavoritesTestTags.favorite(favorite.url)).performClick()

        composeRule.runOnIdle { assertEquals(favorite.url, openedUrl) }
        composeRule.onNodeWithTag(NewTabFavoritesTestTags.LaunchOverlay).assertDoesNotExist()
    }

    @Test
    fun enabledLaunchAnimationCompletesBeforeOpeningFavorite() {
        val favorite = favorite(1)
        var openedUrl: String? = null
        composeRule.mainClock.autoAdvance = false
        setNewTab(
            favorites = listOf(favorite),
            favoriteLaunchAnimationEnabled = true,
            onFavorite = { openedUrl = it },
        )

        composeRule.onNodeWithTag(NewTabFavoritesTestTags.favorite(favorite.url)).performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(
            NewTabFavoritesTestTags.LaunchOverlay,
            useUnmergedTree = true,
        ).assertExists()
        composeRule.runOnIdle { assertEquals(null, openedUrl) }

        composeRule.mainClock.advanceTimeBy(
            NewTabFavoriteLaunchMotionRules.DURATION_MILLIS.toLong() + 100L,
        )
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(favorite.url, openedUrl) }
    }

    @Test
    fun changingBlankTabCancelsItsPendingLaunch() {
        val favorite = favorite(1)
        var ownerTabId by mutableStateOf("first")
        var openedUrl: String? = null
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialBrowserTheme {
                key(ownerTabId) {
                    NewTabPage(
                        favorites = listOf(favorite),
                        incognito = false,
                        modeProgress = 0f,
                        revealOriginInRoot = Offset.Zero,
                        onSearch = {},
                        onFavorite = { openedUrl = it },
                        favoriteLaunchAnimationEnabled = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(NewTabFavoritesTestTags.favorite(favorite.url)).performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(
            NewTabFavoritesTestTags.LaunchOverlay,
            useUnmergedTree = true,
        ).assertExists()

        composeRule.runOnIdle { ownerTabId = "second" }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(NewTabFavoritesTestTags.LaunchOverlay).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(
            NewTabFavoriteLaunchMotionRules.DURATION_MILLIS.toLong() + 100L,
        )
        composeRule.runOnIdle { assertEquals(null, openedUrl) }
    }

    private fun setNewTab(
        favorites: List<FavoriteEntry>,
        favicons: Map<String, Bitmap> = emptyMap(),
        favoriteLaunchAnimationEnabled: Boolean = false,
        onFavorite: (String) -> Unit = {},
        viewportHeight: Dp? = null,
    ) {
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(
                    modifier = if (viewportHeight == null) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(viewportHeight)
                            .testTag(SHORT_VIEWPORT_TAG)
                    },
                ) {
                    NewTabPage(
                        favorites = favorites,
                        favicons = favicons,
                        incognito = false,
                        modeProgress = 0f,
                        revealOriginInRoot = Offset.Zero,
                        onSearch = {},
                        onFavorite = onFavorite,
                        favoriteLaunchAnimationEnabled = favoriteLaunchAnimationEnabled,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun favorite(index: Int): FavoriteEntry = FavoriteEntry(
        url = "https://favorite-$index.example/",
        title = "Favorite $index",
        addedAt = index.toLong(),
    )

    private companion object {
        const val SHORT_VIEWPORT_TAG = "new_tab_short_viewport"
    }
}
