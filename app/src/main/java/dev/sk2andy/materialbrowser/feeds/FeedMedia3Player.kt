package dev.sk2andy.materialbrowser.feeds

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

internal data class FeedMediaPlaybackSnapshot(
    val profileId: String,
    val articleId: String,
    val title: String,
    val source: String,
    val playWhenReady: Boolean,
    val loading: Boolean,
    val speed: Float,
    val hasNext: Boolean,
)

/** TTS provides character progress, not a measured audio duration. System seeking stays absent. */
@OptIn(markerClass = [UnstableApi::class])
internal class FeedMedia3Player(
    applicationLooper: Looper,
    private val manager: FeedPlaybackManager,
) : SimpleBasePlayer(applicationLooper) {
    private var snapshot: FeedMediaPlaybackSnapshot? = null

    fun publish(value: FeedMediaPlaybackSnapshot?) {
        verifyApplicationThread()
        snapshot = value
        invalidateState()
    }

    override fun getState(): State {
        val current =
            snapshot
                ?: return State.Builder()
                    .setAvailableCommands(Player.Commands.EMPTY)
                    .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    .setPlaybackState(Player.STATE_IDLE)
                    .build()
        val metadata =
            MediaMetadata.Builder()
                .setTitle(FeedPlaybackRules.metadata(current.title))
                .setArtist(FeedPlaybackRules.metadata(current.source))
                .build()
        val uid = current.profileId to current.articleId
        val item =
            MediaItemData.Builder(uid)
                .setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(current.articleId)
                        .setMediaMetadata(metadata)
                        .build()
                )
                .setMediaMetadata(metadata)
                .setIsSeekable(false)
                .setDurationUs(C.TIME_UNSET)
                .setPeriods(listOf(PeriodData.Builder(uid).setDurationUs(C.TIME_UNSET).build()))
                .build()
        return State.Builder()
            .setAvailableCommands(controllerCommands(current.hasNext))
            .setPlayWhenReady(
                current.playWhenReady,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setPlaybackState(if (current.loading) Player.STATE_BUFFERING else Player.STATE_READY)
            .setIsLoading(current.loading)
            .setPlaybackParameters(PlaybackParameters(current.speed))
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(0)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        snapshot?.let { current ->
            if (playWhenReady) manager.play(current.profileId) else manager.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        manager.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        if (
            seekCommand == Player.COMMAND_SEEK_TO_NEXT ||
                seekCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        ) {
            manager.next()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(
        playbackParameters: PlaybackParameters
    ): ListenableFuture<*> {
        manager.setSpeed(playbackParameters.speed)
        return Futures.immediateVoidFuture()
    }

    companion object {
        fun controllerCommands(hasNext: Boolean = true): Player.Commands =
            Player.Commands.Builder()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_GET_TIMELINE)
                .add(Player.COMMAND_GET_METADATA)
                .add(Player.COMMAND_SET_SPEED_AND_PITCH)
                .addIf(Player.COMMAND_SEEK_TO_NEXT, hasNext)
                .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNext)
                .build()
    }
}

internal object FeedPlaybackRules {
    fun metadata(value: String): String =
        value
            .asSequence()
            .filterNot { it.isISOControl() || it.category == CharCategory.FORMAT }
            .take(160)
            .joinToString("")
            .trim()
}
