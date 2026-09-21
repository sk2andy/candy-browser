package dev.sk2andy.materialbrowser.browser

import android.os.Looper
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoMedia3PlayerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun media3PlayerPublishesExactCommandsAndForwardsGeckoTransportCommands() {
        val commands = mutableListOf<Pair<GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand>>()
        val owner = owner()

        instrumentation.runOnMainSync {
            val player = TestGeckoMedia3Player(Looper.getMainLooper()) { commandOwner, command ->
                commands += commandOwner to command
            }
            try {
                player.publish(publication(owner, durationMillis = 10_000L))

                assertEquals(Looper.getMainLooper(), player.applicationLooper)
                assertEquals(
                    Player.Commands.Builder()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_STOP)
                        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_GET_TIMELINE)
                        .add(Player.COMMAND_GET_METADATA)
                        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .build(),
                    player.availableCommands,
                )
                assertNull(player.currentMediaItem?.localConfiguration)
                assertEquals("gecko-active-media", player.currentMediaItem?.mediaId)
                assertTrue(player.playWhenReady)
                assertEquals(Player.STATE_READY, player.playbackState)
                assertEquals(
                    Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                    player.playbackSuppressionReason,
                )
                assertTrue(player.isPlaying)

                player.play()
                player.pause()
                player.stop()
                player.requestSeek(
                    mediaItemIndex = 0,
                    positionMillis = 20_000L,
                    seekCommand = Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                )
                player.requestSeek(
                    mediaItemIndex = 0,
                    positionMillis = 1_000L,
                    seekCommand = Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                )
            } finally {
                player.release()
            }
        }

        assertEquals(
            listOf(
                owner to GeckoMediaPlaybackCommand.Play,
                owner to GeckoMediaPlaybackCommand.Pause,
                owner to GeckoMediaPlaybackCommand.Stop,
                owner to GeckoMediaPlaybackCommand.Seek(10_000L),
            ),
            commands,
        )
    }

    @Test
    fun media3PlayerRemovesSeekForUnknownDurationAndRejectsInvalidatedOwner() {
        val commands = mutableListOf<Pair<GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand>>()
        val owner = owner()

        instrumentation.runOnMainSync {
            val player = TestGeckoMedia3Player(Looper.getMainLooper()) { commandOwner, command ->
                commands += commandOwner to command
            }
            try {
                player.publish(publication(owner, durationMillis = null))

                assertEquals(
                    Player.Commands.Builder()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_STOP)
                        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_GET_TIMELINE)
                        .add(Player.COMMAND_GET_METADATA)
                        .build(),
                    player.availableCommands,
                )
                assertFalse(
                    player.availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
                )
                player.requestSeek(
                    mediaItemIndex = 0,
                    positionMillis = 1_000L,
                    seekCommand = Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                )
                player.invalidate(owner)
                player.play()

                assertTrue(commands.isEmpty())
            } finally {
                player.release()
            }
        }
    }

    private fun publication(
        owner: GeckoMediaPlaybackOwner,
        durationMillis: Long?,
    ): GeckoMediaPlaybackPublication = GeckoMediaPlaybackPublication(
        snapshot = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(
                BrowserMediaState(
                    tabId = owner.tabId,
                    title = "Song",
                    origin = "media.example",
                    kind = BrowserMediaKind.Audio,
                    isPlaying = true,
                    currentPositionMillis = 1_000L,
                    durationMillis = durationMillis,
                    playbackRate = 1f,
                    sourceUrl = "https://media.example/private.mp3",
                    contentType = "audio/mpeg",
                    posterUrl = "https://media.example/artwork.png",
                ),
                owner,
            ),
        ),
        isPrivate = false,
        isProfileLocked = false,
        isAppDataTransferActive = false,
        isPictureInPictureOwner = false,
    )

    private fun owner() = GeckoMediaPlaybackOwner(
        tabId = "media-tab",
        engineSessionId = 7L,
        navigationGeneration = 3,
    )

    private class TestGeckoMedia3Player(
        applicationLooper: Looper,
        onCommand: (GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand) -> Unit,
    ) : GeckoMedia3Player(applicationLooper, onCommand) {
        fun requestSeek(
            mediaItemIndex: Int,
            positionMillis: Long,
            seekCommand: Int,
        ) {
            handleSeek(mediaItemIndex, positionMillis, seekCommand)
        }
    }
}
