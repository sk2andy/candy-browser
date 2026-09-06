package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoMediaRulesTest {
    @Test
    fun `autoplay toggle denies audible and inaudible playback`() {
        assertEquals(
            GeckoAutoplayDecision.Deny,
            GeckoAutoplayRules.resolve(GeckoAutoplayPermission.Audible, blocked = true),
        )
        assertEquals(
            GeckoAutoplayDecision.Deny,
            GeckoAutoplayRules.resolve(GeckoAutoplayPermission.Inaudible, blocked = true),
        )
    }

    @Test
    fun `disabled autoplay toggle allows playback and defers unrelated permissions`() {
        assertEquals(
            GeckoAutoplayDecision.Allow,
            GeckoAutoplayRules.resolve(GeckoAutoplayPermission.Audible, blocked = false),
        )
        assertEquals(
            GeckoAutoplayDecision.Defer,
            GeckoAutoplayRules.resolve(GeckoAutoplayPermission.Other, blocked = true),
        )
    }

    @Test
    fun `autoplay permission sync reloads only after both stored values are confirmed`() {
        assertEquals(
            GeckoAutoplayPermissionSyncAction.Retry,
            GeckoAutoplayPermissionSyncRules.resolve(
                storedValues = mapOf(
                    GeckoAutoplayPermission.Audible to GeckoAutoplayDecision.Allow,
                    GeckoAutoplayPermission.Inaudible to GeckoAutoplayDecision.Deny,
                ),
                desiredValue = GeckoAutoplayDecision.Allow,
                attempt = 0,
            ),
        )
        assertEquals(
            GeckoAutoplayPermissionSyncAction.Reload,
            GeckoAutoplayPermissionSyncRules.resolve(
                storedValues = mapOf(
                    GeckoAutoplayPermission.Audible to GeckoAutoplayDecision.Allow,
                    GeckoAutoplayPermission.Inaudible to GeckoAutoplayDecision.Allow,
                ),
                desiredValue = GeckoAutoplayDecision.Allow,
                attempt = 1,
            ),
        )
    }

    @Test
    fun `autoplay permission sync keeps current document after bounded timeout`() {
        assertEquals(
            GeckoAutoplayPermissionSyncAction.KeepCurrentDocument,
            GeckoAutoplayPermissionSyncRules.resolve(
                storedValues = emptyMap(),
                desiredValue = GeckoAutoplayDecision.Deny,
                attempt = GeckoAutoplayPermissionSyncRules.MAX_ATTEMPTS,
            ),
        )
    }

    @Test
    fun `picture in picture requires selected regular playing fullscreen video`() {
        val eligible = GeckoMediaSessionState(
            isActive = true,
            isPlaying = true,
            isFullscreen = true,
            videoWidth = 1_920,
            videoHeight = 1_080,
            videoTrackCount = 1,
        )

        assertTrue(
            GeckoPictureInPictureRules.isEligible(
                state = eligible,
                isPrivate = false,
                isSelectedTab = true,
            ),
        )
        assertFalse(
            GeckoPictureInPictureRules.isEligible(
                state = eligible,
                isPrivate = true,
                isSelectedTab = true,
            ),
        )
        assertFalse(
            GeckoPictureInPictureRules.isEligible(
                state = eligible.copy(isFullscreen = false),
                isPrivate = false,
                isSelectedTab = true,
            ),
        )
        assertFalse(
            GeckoPictureInPictureRules.isEligible(
                state = eligible.copy(videoTrackCount = 0),
                isPrivate = false,
                isSelectedTab = true,
            ),
        )
    }

    @Test
    fun `picture in picture preserves playback intent after gecko pauses during transition`() {
        assertTrue(
            GeckoPictureInPictureRules.playbackExpectedDuringTransition(
                currentExpected = true,
                transitionPending = true,
                inPictureInPicture = false,
                mediaIsPlaying = false,
            ),
        )
        assertTrue(
            GeckoPictureInPictureRules.playbackExpectedDuringTransition(
                currentExpected = true,
                transitionPending = false,
                inPictureInPicture = true,
                mediaIsPlaying = false,
            ),
        )
        assertFalse(
            GeckoPictureInPictureRules.playbackExpectedDuringTransition(
                currentExpected = false,
                transitionPending = false,
                inPictureInPicture = false,
                mediaIsPlaying = false,
            ),
        )
    }
}
