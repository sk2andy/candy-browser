package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabOverviewHeroRulesTest {
    @Test
    fun `portrait coverflow keeps compact card geometry`() {
        val layout = TabOverviewHeroRules.coverflowCardLayout(
            viewportWidth = 400f,
            viewportHeight = 800f,
        )

        assertEquals(296f, layout.width, 0.001f)
        assertEquals(0.45f, layout.aspectRatio, 0f)
    }

    @Test
    fun `tablet landscape coverflow uses wide card with room for neighbors`() {
        val layout = TabOverviewHeroRules.coverflowCardLayout(
            viewportWidth = 1_067f,
            viewportHeight = 667f,
        )

        assertEquals(704.352f, layout.width, 0.001f)
        assertEquals(1.6f, layout.aspectRatio, 0f)
        assertTrue(layout.width < 1_067f)
    }

    @Test
    fun `short landscape coverflow limits card by viewport height`() {
        val layout = TabOverviewHeroRules.coverflowCardLayout(
            viewportWidth = 800f,
            viewportHeight = 360f,
        )

        assertEquals(380.16f, layout.width, 0.001f)
        assertEquals(237.6f, layout.width / layout.aspectRatio, 0.001f)
    }

    @Test
    fun `invalid coverflow dimensions collapse to deterministic layout`() {
        val layout = TabOverviewHeroRules.coverflowCardLayout(
            viewportWidth = Float.NaN,
            viewportHeight = Float.POSITIVE_INFINITY,
        )

        assertEquals(0f, layout.width, 0f)
        assertEquals(0.45f, layout.aspectRatio, 0f)
    }

    @Test
    fun `incognito veil crossfades before hero travel`() {
        assertEquals(0f, TabOverviewHeroRules.incognitoVeilAlpha(0f), 0.001f)
        assertEquals(0.5f, TabOverviewHeroRules.incognitoVeilAlpha(0.12f), 0.001f)
        assertEquals(1f, TabOverviewHeroRules.incognitoVeilAlpha(0.24f), 0.001f)
        assertEquals(1f, TabOverviewHeroRules.incognitoVeilAlpha(1f), 0.001f)
    }

    @Test
    fun `hero waits for target bounds`() {
        assertFalse(TabOverviewHeroRules.canStart(hasTargetBounds = false))
        assertTrue(TabOverviewHeroRules.canStart(hasTargetBounds = true))
    }

    @Test
    fun `initial card stays hidden while hero is visible`() {
        assertTrue(TabOverviewHeroRules.isHeroVisible(hasTargetBounds = true, progress = 0.5f))
        assertFalse(TabOverviewHeroRules.isCardVisible(isInitialCard = true, progress = 0.5f))
    }

    @Test
    fun `initial card replaces hero at completion`() {
        assertFalse(TabOverviewHeroRules.isHeroVisible(hasTargetBounds = true, progress = 0.995f))
        assertTrue(TabOverviewHeroRules.isCardVisible(isInitialCard = true, progress = 0.995f))
    }

    @Test
    fun `neighbor cards remain visible`() {
        assertTrue(TabOverviewHeroRules.isCardVisible(isInitialCard = false, progress = 0f))
    }

    @Test
    fun `exit target card is replaced by hero immediately`() {
        assertFalse(
            TabOverviewHeroRules.isCardVisible(
                isInitialCard = true,
                progress = 1f,
                isExitTarget = true,
            ),
        )
    }

    @Test
    fun `background remains opaque throughout exit`() {
        assertEquals(
            0.4f,
            TabOverviewHeroRules.backgroundAlpha(entryProgress = 0.4f, isExiting = false),
            0f,
        )
        assertEquals(
            1f,
            TabOverviewHeroRules.backgroundAlpha(entryProgress = 0.4f, isExiting = true),
            0f,
        )
    }

    @Test
    fun `overview content clears early during exit`() {
        assertEquals(
            1f,
            TabOverviewHeroRules.contentAlpha(
                exitProgress = 0f,
                isExiting = true,
            ),
            0f,
        )
        assertEquals(
            0f,
            TabOverviewHeroRules.contentAlpha(
                exitProgress = 0.25f,
                isExiting = true,
            ),
            0f,
        )
    }

    @Test
    fun `neighbor tabs enter late`() {
        assertEquals(
            0f,
            TabOverviewHeroRules.neighborAlpha(entryProgress = 0.55f),
            0f,
        )
        assertEquals(
            1f,
            TabOverviewHeroRules.neighborAlpha(entryProgress = 1f),
            0f,
        )
    }

    @Test
    fun `compact chrome crossfades only near compact endpoint`() {
        assertEquals(0f, TabOverviewHeroRules.compactChromeAlpha(0.62f), 0f)
        assertEquals(0.5f, TabOverviewHeroRules.compactChromeAlpha(0.81f), 0.001f)
        assertEquals(1f, TabOverviewHeroRules.compactChromeAlpha(1f), 0f)
    }

    @Test
    fun `coverflow preview uses one continuously moving frame`() {
        val target = TabOverviewHeroRules.CoverflowPreviewLayout(
            sourceTopPx = 300f,
            sourceHeightPx = 1_200f,
        )
        assertEquals(
            TabOverviewHeroRules.CoverflowPreviewLayout(72f, 2_000f),
            TabOverviewHeroRules.coverflowPreviewFrame(72f, 2_000f, target, 0f),
        )
        assertEquals(
            TabOverviewHeroRules.CoverflowPreviewLayout(186f, 1_600f),
            TabOverviewHeroRules.coverflowPreviewFrame(72f, 2_000f, target, 0.5f),
        )
        assertEquals(
            target,
            TabOverviewHeroRules.coverflowPreviewFrame(72f, 2_000f, target, 1f),
        )
    }

    @Test
    fun `landscape coverflow keeps full oversized source frame`() {
        val target = TabOverviewHeroRules.coverflowPreviewLayout(
            rootWidthPx = 2_400f,
            rootHeightPx = 1_080f,
            targetWidthPx = 1_536f,
            targetHeightPx = 960f,
            cropTopFraction = 0.25f,
        )

        assertEquals(1_500f, target.sourceHeightPx, 0f)
        assertEquals(-105f, target.sourceTopPx, 0f)
        assertTrue(target.sourceTopPx + target.sourceHeightPx >= 1_080f)
    }

    @Test
    fun `blank tab favorites fade before hero card handoff`() {
        assertEquals(1f, TabOverviewHeroRules.blankFavoritesAlpha(0.35f), 0f)
        assertEquals(0.5f, TabOverviewHeroRules.blankFavoritesAlpha(0.565f), 0.001f)
        assertEquals(0f, TabOverviewHeroRules.blankFavoritesAlpha(0.78f), 0f)
        assertEquals(0f, TabOverviewHeroRules.blankFavoritesAlpha(1f), 0f)
    }

    @Test
    fun `blank preview uses full root height instead of inset configuration height`() {
        assertEquals(
            2400f,
            TabOverviewHeroRules.blankPreviewSourceExtentPx(
                rootViewExtentPx = 2400,
                configurationExtentPx = 2268f,
            ),
            0f,
        )
        assertEquals(
            2268f,
            TabOverviewHeroRules.blankPreviewSourceExtentPx(
                rootViewExtentPx = 0,
                configurationExtentPx = 2268f,
            ),
            0f,
        )
    }

    @Test
    fun `coverflow endpoint preview maps exactly onto target card`() {
        val rootWidth = 1080f
        val rootHeight = 2400f
        val targetLeft = 210f
        val targetTop = 620f
        val targetWidth = 660f
        val targetHeight = 1245f
        val targetScale = targetWidth / rootWidth
        val layout = TabOverviewHeroRules.coverflowPreviewLayout(
            rootWidthPx = rootWidth,
            rootHeightPx = rootHeight,
            targetWidthPx = targetWidth,
            targetHeightPx = targetHeight,
            cropTopFraction = 0.25f,
        )
        val heroTranslationX = targetLeft
        val heroTranslationY = targetTop - layout.sourceTopPx * targetScale

        assertEquals(targetWidth, rootWidth * targetScale, 0.001f)
        assertEquals(targetHeight, layout.sourceHeightPx * targetScale, 0.001f)
        assertEquals(targetLeft, heroTranslationX, 0.001f)
        assertEquals(
            targetTop,
            layout.sourceTopPx * targetScale + heroTranslationY,
            0.001f,
        )
    }
}
