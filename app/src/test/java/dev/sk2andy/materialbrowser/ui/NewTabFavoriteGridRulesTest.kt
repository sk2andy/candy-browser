package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.geometry.Offset
import dev.sk2andy.materialbrowser.browser.FavoriteAnimationSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class NewTabFavoriteGridRulesTest {
    @Test
    fun `four columns produce expected row boundaries`() {
        assertEquals(0, NewTabFavoriteGridRules.rowCount(0))
        assertEquals(1, NewTabFavoriteGridRules.rowCount(1))
        assertEquals(1, NewTabFavoriteGridRules.rowCount(4))
        assertEquals(2, NewTabFavoriteGridRules.rowCount(5))
        assertEquals(5, NewTabFavoriteGridRules.rowCount(20))
        assertEquals(6, NewTabFavoriteGridRules.rowCount(21))
    }

    @Test
    fun `container height grows through twenty favorites then stays bounded`() {
        val oneRowHeight = NewTabFavoriteGridRules.CELL_HEIGHT_DP +
            NewTabFavoriteGridRules.VERTICAL_CONTENT_PADDING_DP * 2
        val fiveRowHeight = NewTabFavoriteGridRules.CELL_HEIGHT_DP * 5 +
            NewTabFavoriteGridRules.VERTICAL_CONTENT_PADDING_DP * 2

        assertEquals(oneRowHeight, NewTabFavoriteGridRules.containerHeightDp(4))
        assertEquals(fiveRowHeight, NewTabFavoriteGridRules.containerHeightDp(20))
        assertEquals(fiveRowHeight, NewTabFavoriteGridRules.containerHeightDp(21))
    }

    @Test
    fun `folder navigation reserves a slot for parent shortcut`() {
        val oneRowHeight = NewTabFavoriteGridRules.CELL_HEIGHT_DP +
            NewTabFavoriteGridRules.VERTICAL_CONTENT_PADDING_DP * 2

        assertEquals(
            oneRowHeight,
            NewTabFavoriteGridRules.containerHeightDp(
                itemCount = 3,
                hasUpNavigation = true,
            ),
        )
        assertEquals(
            NewTabFavoriteGridRules.CELL_HEIGHT_DP * 2 +
                NewTabFavoriteGridRules.VERTICAL_CONTENT_PADDING_DP * 2,
            NewTabFavoriteGridRules.containerHeightDp(
                itemCount = 4,
                hasUpNavigation = true,
            ),
        )
    }

    @Test
    fun `visible favorites always use circles`() {
        val urls = (1..20).map { index -> "https://favorite-$index.example/" }
        val first = NewTabFavoriteShapeRules.startVariants(urls)
        val second = NewTabFavoriteShapeRules.startVariants(urls)

        assertEquals(first, second)
        assertEquals(List(urls.size) { NewTabFavoriteShapeVariant.Circle }, first)
    }

    @Test
    fun `shape phase keeps the icon circular`() {
        val start = NewTabFavoriteShapeVariant.Arch

        assertEquals(
            NewTabFavoriteMorphState(
                from = NewTabFavoriteShapeVariant.Circle,
                to = NewTabFavoriteShapeVariant.Circle,
                progress = 0f,
            ),
            NewTabFavoriteShapeRules.morphState(start, 0f),
        )
        assertEquals(
            NewTabFavoriteMorphState(
                from = NewTabFavoriteShapeVariant.Circle,
                to = NewTabFavoriteShapeVariant.Circle,
                progress = 0f,
            ),
            NewTabFavoriteShapeRules.morphState(start, 1.25f),
        )
        assertEquals(
            NewTabFavoriteMorphState(
                from = NewTabFavoriteShapeVariant.Circle,
                to = NewTabFavoriteShapeVariant.Circle,
                progress = 0f,
            ),
            NewTabFavoriteShapeRules.morphState(start, Float.NaN),
        )
    }

    @Test
    fun `elapsed time produces bounded morph progress`() {
        assertEquals(0f, NewTabFavoriteShapeRules.morphProgress(-1L, 3_600), 0.001f)
        assertEquals(0.5f, NewTabFavoriteShapeRules.morphProgress(1_800L, 3_600), 0.001f)
        assertEquals(1f, NewTabFavoriteShapeRules.morphProgress(9_600L, 3_600), 0.001f)
        assertEquals(1f, NewTabFavoriteShapeRules.morphProgress(0L, 0), 0.001f)
    }

    @Test
    fun `animation speed controls circular favorite launch`() {
        assertEquals(780, NewTabFavoriteLaunchMotionRules.durationMillis(FavoriteAnimationSpeed.Relaxed))
        assertEquals(620, NewTabFavoriteLaunchMotionRules.durationMillis(FavoriteAnimationSpeed.Normal))
        assertEquals(480, NewTabFavoriteLaunchMotionRules.durationMillis(FavoriteAnimationSpeed.Fast))
    }

    @Test
    fun `launch path starts and finishes at its anchors`() {
        val start = Offset(280f, 620f)
        val target = Offset(180f, 180f)

        val first = NewTabFavoriteLaunchMotionRules.position(
            start = start,
            target = target,
            progress = 0f,
            curveDistancePx = 54f,
        )
        val last = NewTabFavoriteLaunchMotionRules.position(
            start = start,
            target = target,
            progress = 1f,
            curveDistancePx = 54f,
        )

        assertEquals(start.x, first.x, 0.001f)
        assertEquals(start.y, first.y, 0.001f)
        assertEquals(target.x, last.x, 0.001f)
        assertEquals(target.y, last.y, 0.001f)
    }
}
