package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GeckoMedia3PlaybackTest {
    @Test
    fun `snapshot bounds metadata position duration and playback rate`() {
        val snapshot = GeckoMediaPlaybackRules.snapshot(
            state = mediaState(
                title = "  Song\nTitle  ",
                origin = "  media.example\u0000  ",
                currentPositionMillis = 90_000L,
                durationMillis = 60_000L,
                playbackRate = Float.NaN,
            ),
            owner = owner(),
        )

        requireNotNull(snapshot)
        assertEquals("SongTitle", snapshot.title)
        assertEquals("media.example", snapshot.origin)
        assertEquals(60_000L, snapshot.positionMillis)
        assertEquals(60_000L, snapshot.durationMillis)
        assertEquals(1f, snapshot.playbackRate)
    }

    @Test
    fun `snapshot removes bidi and Unicode format controls from notification metadata`() {
        val snapshot = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(
                state = mediaState(
                    title = "Album\u202e\u2066title",
                    origin = "media\u200e.example",
                ),
                owner = owner(),
            ),
        )

        assertEquals("Albumtitle", snapshot.title)
        assertEquals("media.example", snapshot.origin)
    }

    @Test
    fun `snapshot rejects a state owned by another tab`() {
        assertNull(
            GeckoMediaPlaybackRules.snapshot(
                state = mediaState(tabId = "other-tab"),
                owner = owner(),
            ),
        )
    }

    @Test
    fun `publication rejects private locked and transfer contexts`() {
        val snapshot = requireNotNull(GeckoMediaPlaybackRules.snapshot(mediaState(), owner()))

        assertNull(
            GeckoMediaPlaybackRules.publicationOrNull(
                publication(snapshot, isPrivate = true),
            ),
        )
        assertNull(
            GeckoMediaPlaybackRules.publicationOrNull(
                publication(snapshot, isProfileLocked = true),
            ),
        )
        assertNull(
            GeckoMediaPlaybackRules.publicationOrNull(
                publication(snapshot, isAppDataTransferActive = true),
            ),
        )
    }

    @Test
    fun `selection-only competing tab does not replace captured background owner`() {
        val coordinator = GeckoMediaPlaybackCoordinator()
        val capturedOwner = owner()
        val selectedOwner = owner(tabId = "newly-selected-tab", engineSessionId = 8L)
        val capturedSnapshot = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(mediaState(), capturedOwner),
        )
        val selectedSnapshot = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(
                mediaState(tabId = selectedOwner.tabId),
                selectedOwner,
            ),
        )

        coordinator.publish(publication(capturedSnapshot))
        assertEquals(capturedSnapshot, coordinator.publish(publication(selectedSnapshot)))
        assertEquals(
            GeckoMediaPlaybackCommand.Pause,
            coordinator.command(capturedOwner, GeckoMediaPlaybackCommand.Pause),
        )
        assertNull(coordinator.command(selectedOwner, GeckoMediaPlaybackCommand.Play))
    }

    @Test
    fun `picture in picture owner explicitly takes precedence until invalidated`() {
        val coordinator = GeckoMediaPlaybackCoordinator()
        val backgroundOwner = owner()
        val pipOwner = owner(tabId = "pip-tab", engineSessionId = 8L)
        val backgroundSnapshot = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(mediaState(), backgroundOwner),
        )
        val pipSnapshot = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(mediaState(tabId = pipOwner.tabId), pipOwner),
        )

        coordinator.publish(publication(backgroundSnapshot))
        assertEquals(
            pipSnapshot,
            coordinator.publish(publication(pipSnapshot, isPictureInPictureOwner = true)),
        )
        assertEquals(pipSnapshot, coordinator.publish(publication(backgroundSnapshot)))
        assertEquals(null, coordinator.invalidate(pipOwner))
        assertEquals(backgroundSnapshot, coordinator.publish(publication(backgroundSnapshot)))
    }

    @Test
    fun `navigation owner replacement stays published without an intermediate clear`() {
        val coordinator = GeckoMediaPlaybackCoordinator()
        val previousOwner = owner(navigationGeneration = 3)
        val nextOwner = owner(navigationGeneration = 4)
        val previousSnapshot = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(mediaState(), previousOwner),
        )
        val nextSnapshot = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(mediaState(), nextOwner),
        )

        coordinator.publish(publication(previousSnapshot))
        assertEquals(nextSnapshot, coordinator.replacePublication(publication(nextSnapshot)))
        assertNull(coordinator.command(previousOwner, GeckoMediaPlaybackCommand.Pause))
        assertEquals(
            GeckoMediaPlaybackCommand.Pause,
            coordinator.command(nextOwner, GeckoMediaPlaybackCommand.Pause),
        )
    }

    @Test
    fun `paused owner remains publishable until a later play state`() {
        val coordinator = GeckoMediaPlaybackCoordinator()
        val activeOwner = owner()
        val playing = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(mediaState(), activeOwner),
        )
        val paused = playing.copy(isPlaying = false)

        assertEquals(paused, coordinator.publish(publication(paused)))
        assertEquals(playing, coordinator.publish(publication(playing)))
        assertEquals(
            GeckoMediaPlaybackCommand.Pause,
            coordinator.command(activeOwner, GeckoMediaPlaybackCommand.Pause),
        )
    }

    @Test
    fun `navigation replacement preserves media only for same active document`() {
        assertEquals(
            true,
            BrowserMediaNavigationRules.mayReplaceActiveMediaOwner(
                currentDocumentUrl = "https://media.example/fixture.html",
                navigationAddress = "https://media.example/fixture.html",
                mediaIsActive = true,
            ),
        )
        assertEquals(
            false,
            BrowserMediaNavigationRules.mayReplaceActiveMediaOwner(
                currentDocumentUrl = "https://media.example/fixture.html",
                navigationAddress = "https://media.example/next.html",
                mediaIsActive = true,
            ),
        )
        assertEquals(
            false,
            BrowserMediaNavigationRules.mayReplaceActiveMediaOwner(
                currentDocumentUrl = "https://media.example/fixture.html",
                navigationAddress = null,
                mediaIsActive = true,
            ),
        )
        assertEquals(
            false,
            BrowserMediaNavigationRules.mayReplaceActiveMediaOwner(
                currentDocumentUrl = "https://media.example/fixture.html",
                navigationAddress = "https://media.example/fixture.html",
                mediaIsActive = false,
            ),
        )
    }

    @Test
    fun `player state maps active playback and finite seeking`() {
        val snapshot = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(
                mediaState(durationMillis = 10_000L),
                owner(),
            ),
        )

        val state = GeckoMedia3PlayerRules.state(snapshot)

        assertEquals(snapshot, state.snapshot)
        assertEquals(true, state.isReady)
        assertEquals(true, state.playWhenReady)
        assertEquals(true, state.supportsSeeking)
        assertEquals(
            GeckoMedia3PlayerState(
                snapshot = null,
                isReady = false,
                playWhenReady = false,
                supportsSeeking = false,
            ),
            GeckoMedia3PlayerRules.state(null),
        )
        assertEquals(
            false,
            GeckoMedia3PlayerRules.state(snapshot.copy(isPlaying = false)).playWhenReady,
        )
    }

    @Test
    fun `coordinator rejects a stale navigation or engine owner`() {
        val coordinator = GeckoMediaPlaybackCoordinator()
        coordinator.publish(publication(requireNotNull(GeckoMediaPlaybackRules.snapshot(mediaState(), owner()))))

        assertNull(
            coordinator.command(
                target = owner(navigationGeneration = 8),
                command = GeckoMediaPlaybackCommand.Play,
            ),
        )
        assertNull(
            coordinator.command(
                target = owner(engineSessionId = 12L),
                command = GeckoMediaPlaybackCommand.Pause,
            ),
        )
    }

    @Test
    fun `coordinator hard clears state and commands for private lock and transfer`() {
        val snapshot = requireNotNull(GeckoMediaPlaybackRules.snapshot(mediaState(), owner()))
        val coordinator = GeckoMediaPlaybackCoordinator()

        coordinator.publish(publication(snapshot))
        assertNull(coordinator.clearForPrivateContext())
        assertNull(coordinator.command(owner(), GeckoMediaPlaybackCommand.Play))

        coordinator.publish(publication(snapshot))
        assertNull(coordinator.clearForProfileLock())
        assertNull(coordinator.command(owner(), GeckoMediaPlaybackCommand.Pause))

        coordinator.publish(publication(snapshot))
        assertNull(coordinator.clearForAppDataTransfer())
        assertNull(coordinator.command(owner(), GeckoMediaPlaybackCommand.Stop))
    }

    @Test
    fun `privacy lock and transfer publications hard clear retained owner`() {
        val coordinator = GeckoMediaPlaybackCoordinator()
        val snapshot = requireNotNull(GeckoMediaPlaybackRules.snapshot(mediaState(), owner()))

        coordinator.publish(publication(snapshot))
        assertNull(coordinator.publish(publication(snapshot, isPrivate = true)))
        assertNull(coordinator.command(owner(), GeckoMediaPlaybackCommand.Play))

        coordinator.publish(publication(snapshot))
        assertNull(coordinator.publish(publication(snapshot, isProfileLocked = true)))
        assertNull(coordinator.command(owner(), GeckoMediaPlaybackCommand.Pause))

        coordinator.publish(publication(snapshot))
        assertNull(coordinator.publish(publication(snapshot, isAppDataTransferActive = true)))
        assertNull(coordinator.command(owner(), GeckoMediaPlaybackCommand.Stop))
    }

    @Test
    fun `navigation and session invalidation hard clear retained commands`() {
        val coordinator = GeckoMediaPlaybackCoordinator()
        val navigationOwner = owner(navigationGeneration = 4)
        val sessionOwner = owner(engineSessionId = 9L)

        coordinator.publish(
            publication(requireNotNull(GeckoMediaPlaybackRules.snapshot(mediaState(), navigationOwner))),
        )
        assertNull(coordinator.invalidate(navigationOwner))
        assertNull(coordinator.command(navigationOwner, GeckoMediaPlaybackCommand.Play))

        coordinator.publish(
            publication(requireNotNull(GeckoMediaPlaybackRules.snapshot(mediaState(), sessionOwner))),
        )
        assertNull(coordinator.invalidate(sessionOwner))
        assertNull(coordinator.command(sessionOwner, GeckoMediaPlaybackCommand.Play))
    }

    @Test
    fun `PiP-preempted owners invalidate as one handoff for navigation close and profile lock`() {
        val pictureInPictureOwner = owner(tabId = "pip-tab", engineSessionId = 8L)
        val backgroundOwner = owner(tabId = "background-tab", engineSessionId = 9L)

        assertEquals(
            true,
            BrowserMedia3OwnerInvalidationRules.matches(
                capturedOwner = pictureInPictureOwner,
                backgroundOwner = backgroundOwner,
                tabId = pictureInPictureOwner.tabId,
            ),
        )
        assertEquals(
            true,
            BrowserMedia3OwnerInvalidationRules.matches(
                capturedOwner = pictureInPictureOwner,
                backgroundOwner = backgroundOwner,
                tabId = backgroundOwner.tabId,
            ),
        )
        assertEquals(
            false,
            BrowserMedia3OwnerInvalidationRules.matches(
                capturedOwner = pictureInPictureOwner,
                backgroundOwner = backgroundOwner,
                tabId = "unrelated-tab",
            ),
        )
    }

    @Test
    fun `stale Media3 callback is rejected unless it remains the captured owner`() {
        val capturedOwner = owner()
        val staleOwner = owner(tabId = "former-tab", engineSessionId = 8L)

        assertEquals(
            true,
            BrowserMedia3CommandOwnerRules.accepts(
                owner = capturedOwner,
                capturedOwner = capturedOwner,
                pictureInPictureOwner = null,
            ),
        )
        assertEquals(
            false,
            BrowserMedia3CommandOwnerRules.accepts(
                owner = staleOwner,
                capturedOwner = capturedOwner,
                pictureInPictureOwner = null,
            ),
        )
        assertEquals(
            false,
            BrowserMedia3CommandOwnerRules.accepts(
                owner = capturedOwner,
                capturedOwner = capturedOwner,
                pictureInPictureOwner = staleOwner,
            ),
        )
    }

    @Test
    fun `new service starts require a foreground Activity and released session identities are removed`() {
        assertEquals(true, BrowserMediaServiceStartRules.mayStartNewService(true, true))
        assertEquals(false, BrowserMediaServiceStartRules.mayStartNewService(true, false))
        assertEquals(false, BrowserMediaServiceStartRules.mayStartNewService(false, true))
        assertEquals(true, BrowserMediaServiceStartRules.defersPausedPublication(false))
        assertEquals(false, BrowserMediaServiceStartRules.defersPausedPublication(true))

        val identities = GeckoMediaSessionIdentityRegistry()
        val firstSession = Any()
        val secondSession = Any()
        assertEquals(1L, identities.identityFor(firstSession))
        assertEquals(2L, identities.identityFor(secondSession))
        assertEquals(2, identities.size())
        identities.remove(firstSession)
        assertEquals(1, identities.size())
    }

    @Test
    fun `lifecycle trace is bounded and redacts private web metadata`() {
        val snapshot = requireNotNull(GeckoMediaPlaybackRules.snapshot(mediaState(), owner()))
        BrowserMediaLifecycleTrace.resetForTesting()

        BrowserMediaLifecycleTrace.record(
            source = "test",
            action = "private-publication",
            publication = publication(snapshot, isPrivate = true),
        )
        assertNull(BrowserMediaLifecycleTrace.eventsForTesting().single().owner)
        assertNull(BrowserMediaLifecycleTrace.eventsForTesting().single().snapshot)

        repeat(64) { index ->
            BrowserMediaLifecycleTrace.record(
                source = "test",
                action = "publication-$index",
                publication = publication(snapshot),
            )
        }

        val events = BrowserMediaLifecycleTrace.eventsForTesting()
        assertEquals(64, events.size)
        assertEquals(2L, events.first().sequence)
        assertEquals(65L, events.last().sequence)
        assertFalse(events.toString().contains("secret.mp3"))
        assertFalse(events.toString().contains("media.example"))
        BrowserMediaLifecycleTrace.resetForTesting()
    }

    @Test
    fun `seek is unavailable without a finite duration`() {
        val current = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(mediaState(durationMillis = null), owner()),
        )

        assertNull(
            GeckoMediaPlaybackRules.commandOrNull(
                current = current,
                target = owner(),
                command = GeckoMediaPlaybackCommand.Seek(1_000L),
            ),
        )
    }

    @Test
    fun `seek clamps to media duration while supported transport commands remain available`() {
        val current = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(mediaState(durationMillis = 10_000L), owner()),
        )

        assertEquals(
            GeckoMediaPlaybackCommand.Seek(10_000L),
            GeckoMediaPlaybackRules.commandOrNull(
                current = current,
                target = owner(),
                command = GeckoMediaPlaybackCommand.Seek(30_000L),
            ),
        )
        assertEquals(
            GeckoMediaPlaybackCommand.Stop,
            GeckoMediaPlaybackRules.commandOrNull(
                current = current,
                target = owner(),
                command = GeckoMediaPlaybackCommand.Stop,
            ),
        )
    }

    private fun publication(
        snapshot: GeckoMediaPlaybackSnapshot,
        isPrivate: Boolean = false,
        isProfileLocked: Boolean = false,
        isAppDataTransferActive: Boolean = false,
        isPictureInPictureOwner: Boolean = false,
    ) = GeckoMediaPlaybackPublication(
        snapshot = snapshot,
        isPrivate = isPrivate,
        isProfileLocked = isProfileLocked,
        isAppDataTransferActive = isAppDataTransferActive,
        isPictureInPictureOwner = isPictureInPictureOwner,
    )

    private fun owner(
        tabId: String = "media-tab",
        engineSessionId: Long = 7L,
        navigationGeneration: Int = 3,
    ) = GeckoMediaPlaybackOwner(
        tabId = tabId,
        engineSessionId = engineSessionId,
        navigationGeneration = navigationGeneration,
    )

    private fun mediaState(
        tabId: String = "media-tab",
        title: String = "Song",
        origin: String = "media.example",
        currentPositionMillis: Long = 1_000L,
        durationMillis: Long? = 20_000L,
        playbackRate: Float = 1f,
    ) = BrowserMediaState(
        tabId = tabId,
        title = title,
        origin = origin,
        kind = BrowserMediaKind.Audio,
        isPlaying = true,
        currentPositionMillis = currentPositionMillis,
        durationMillis = durationMillis,
        playbackRate = playbackRate,
        sourceUrl = "https://media.example/secret.mp3",
        contentType = "audio/mpeg",
        posterUrl = "https://media.example/artwork.png",
    )
}
