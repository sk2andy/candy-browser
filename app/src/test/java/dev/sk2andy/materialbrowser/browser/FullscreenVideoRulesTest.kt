package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenVideoRulesTest {
    @Test
    fun `system pip keeps web view in its existing browser host`() {
        assertFalse(
            FullscreenVideoRules.hostsSourceInOverlay(
                host = FullscreenVideoHost.Browser,
                videoOnlyPresentation = true,
            ),
        )
        assertTrue(
            FullscreenVideoRules.hostsSourceInOverlay(
                host = FullscreenVideoHost.Overlay,
                videoOnlyPresentation = true,
            ),
        )
        assertTrue(
            FullscreenVideoRules.hostsSourceInOverlay(
                host = FullscreenVideoHost.Browser,
                videoOnlyPresentation = false,
            ),
        )
        assertTrue(
            FullscreenVideoRules.hostsSourceInOverlay(
                host = FullscreenVideoHost.Overlay,
                videoOnlyPresentation = false,
            ),
        )
    }

    @Test
    fun `missing session has no presentation`() {
        assertNull(
            FullscreenVideoRules.placement(
                sessionTabId = null,
                selectedTabId = "selected",
                minimizedByUser = false,
                videoOnlyPresentation = false,
            ),
        )
    }

    @Test
    fun `selected session starts expanded`() {
        assertEquals(
            FullscreenVideoPlacement.Expanded,
            FullscreenVideoRules.placement(
                sessionTabId = "selected",
                selectedTabId = "selected",
                minimizedByUser = false,
                videoOnlyPresentation = false,
            ),
        )
    }

    @Test
    fun `background or user minimized session uses mini player`() {
        assertEquals(
            FullscreenVideoPlacement.MiniPlayer,
            FullscreenVideoRules.placement(
                sessionTabId = "video",
                selectedTabId = "other",
                minimizedByUser = false,
                videoOnlyPresentation = false,
            ),
        )
        assertEquals(
            FullscreenVideoPlacement.MiniPlayer,
            FullscreenVideoRules.placement(
                sessionTabId = "video",
                selectedTabId = "video",
                minimizedByUser = true,
                videoOnlyPresentation = false,
            ),
        )
    }

    @Test
    fun `system picture in picture expands minimized background session`() {
        assertEquals(
            FullscreenVideoPlacement.Expanded,
            FullscreenVideoRules.placement(
                sessionTabId = "video",
                selectedTabId = "other",
                minimizedByUser = true,
                videoOnlyPresentation = true,
            ),
        )
    }

    @Test
    fun `only owning web view stays resumed`() {
        assertTrue(FullscreenVideoRules.keepsWebViewResumed("video", "video", isPrivate = false))
        assertFalse(FullscreenVideoRules.keepsWebViewResumed("video", "other", isPrivate = false))
        assertFalse(FullscreenVideoRules.keepsWebViewResumed("video", "video", isPrivate = true))
        assertFalse(FullscreenVideoRules.keepsWebViewResumed(null, "video", isPrivate = false))
    }

    @Test
    fun `private fullscreen video never auto enters system picture in picture`() {
        assertTrue(
            FullscreenVideoRules.isPictureInPictureEligible(
                sessionTabId = "regular",
                isPrivate = false,
            ),
        )
        assertFalse(
            FullscreenVideoRules.isPictureInPictureEligible(
                sessionTabId = "private",
                isPrivate = true,
            ),
        )
        assertFalse(
            FullscreenVideoRules.isPictureInPictureEligible(
                sessionTabId = null,
                isPrivate = null,
            ),
        )
    }

    @Test
    fun `picture in picture source is centered and matches landscape video aspect`() {
        assertEquals(
            FullscreenVideoBounds(left = 0, top = 896, right = 1_080, bottom = 1_503),
            FullscreenVideoRules.pictureInPictureSourceBounds(
                windowBounds = FullscreenVideoBounds(0, 0, 1_080, 2_400),
                aspectWidth = 16,
                aspectHeight = 9,
            ),
        )
        assertEquals(
            FullscreenVideoBounds(left = 0, top = 0, right = 1_920, bottom = 1_080),
            FullscreenVideoRules.pictureInPictureSourceBounds(
                windowBounds = FullscreenVideoBounds(0, 0, 1_920, 1_080),
                aspectWidth = 16,
                aspectHeight = 9,
            ),
        )
    }

    @Test
    fun `picture in picture source rejects invalid bounds`() {
        assertNull(
            FullscreenVideoRules.pictureInPictureSourceBounds(
                windowBounds = FullscreenVideoBounds(0, 0, 0, 100),
                aspectWidth = 16,
                aspectHeight = 9,
            ),
        )
    }

    @Test
    fun `picture in picture return waits for target layout within tolerance`() {
        assertFalse(
            FullscreenVideoRules.isPictureInPictureReturnLayoutReady(
                width = 598,
                height = 336,
                targetWidth = 1_080,
                targetHeight = 2_400,
                tolerance = 21,
            ),
        )
        assertTrue(
            FullscreenVideoRules.isPictureInPictureReturnLayoutReady(
                width = 1_080,
                height = 2_380,
                targetWidth = 1_080,
                targetHeight = 2_400,
                tolerance = 21,
            ),
        )
        assertFalse(
            FullscreenVideoRules.isPictureInPictureReturnLayoutReady(
                width = 1_080,
                height = 2_378,
                targetWidth = 1_080,
                targetHeight = 2_400,
                tolerance = 21,
            ),
        )
    }

    @Test
    fun `mini player drag stays inside available bounds`() {
        assertEquals(
            FullscreenVideoOffset(x = -120f, y = -80f),
            FullscreenVideoRules.clampMiniPlayerOffset(
                proposedX = -180f,
                proposedY = -80f,
                maxLeftTravel = 120f,
                maxUpTravel = 240f,
            ),
        )
        assertEquals(
            FullscreenVideoOffset(x = 0f, y = -240f),
            FullscreenVideoRules.clampMiniPlayerOffset(
                proposedX = 30f,
                proposedY = -300f,
                maxLeftTravel = 120f,
                maxUpTravel = 240f,
            ),
        )
    }

    @Test
    fun `accessible move action cycles mini player corners`() {
        val bottomRight = FullscreenVideoOffset(x = 0f, y = 0f)
        val bottomLeft = FullscreenVideoRules.nextMiniPlayerAnchor(bottomRight, 120f, 240f)
        val topLeft = FullscreenVideoRules.nextMiniPlayerAnchor(bottomLeft, 120f, 240f)
        val topRight = FullscreenVideoRules.nextMiniPlayerAnchor(topLeft, 120f, 240f)

        assertEquals(FullscreenVideoOffset(x = -120f, y = 0f), bottomLeft)
        assertEquals(FullscreenVideoOffset(x = -120f, y = -240f), topLeft)
        assertEquals(FullscreenVideoOffset(x = 0f, y = -240f), topRight)
        assertEquals(
            bottomRight,
            FullscreenVideoRules.nextMiniPlayerAnchor(topRight, 120f, 240f),
        )
    }
}
