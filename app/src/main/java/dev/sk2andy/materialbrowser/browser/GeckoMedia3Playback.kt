package dev.sk2andy.materialbrowser.browser

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
import java.util.ArrayDeque
import java.util.IdentityHashMap

/**
 * Memory-only identity for the engine session that currently owns Android media controls.
 *
 * Phase C must allocate [engineSessionId] when it creates or replaces a Gecko engine session and
 * pair it with BrowserController's current [navigationGeneration] when it publishes content
 * state. Both values must be invalidated when that session or navigation ends; neither may be
 * restored, derived from a URL, or persisted.
 */
internal data class GeckoMediaPlaybackOwner(
    val tabId: String,
    val engineSessionId: Long,
    val navigationGeneration: Int,
)

/** Sanitized, bounded data allowed to cross from web content into Android media controls. */
internal data class GeckoMediaPlaybackSnapshot(
    val owner: GeckoMediaPlaybackOwner,
    val title: String,
    val origin: String,
    val isPlaying: Boolean,
    val positionMillis: Long,
    val durationMillis: Long?,
    val playbackRate: Float,
)

internal data class GeckoMediaPlaybackPublication(
    val snapshot: GeckoMediaPlaybackSnapshot,
    val isPrivate: Boolean,
    val isProfileLocked: Boolean,
    val isAppDataTransferActive: Boolean,
    val isPictureInPictureOwner: Boolean,
)

/** Non-content identity safe to expose in bounded diagnostic traces. */
internal data class BrowserMediaTraceOwner(
    val tabToken: String,
    val engineSessionId: Long,
    val navigationGeneration: Int,
)

/** No URL, title, origin, artwork, or private-mode data crosses this diagnostic boundary. */
internal data class BrowserMediaTraceSnapshot(
    val isPlaying: Boolean,
    val positionMillis: Long,
    val durationMillis: Long?,
    val playbackRate: Float,
    val isPictureInPictureOwner: Boolean,
)

/** A bounded, process-local sequence for diagnosing Media3 lifecycle ordering. */
internal data class BrowserMediaLifecycleTraceEvent(
    val sequence: Long,
    val timestampNanos: Long,
    val source: String,
    val action: String,
    val owner: BrowserMediaTraceOwner?,
    val snapshot: BrowserMediaTraceSnapshot?,
    val serviceInstanceId: Int?,
    val hasPublishedMedia: Boolean?,
)

/**
 * Keeps only a small non-sensitive timeline. This is deliberately memory-only and bounded so a
 * failing device test can report ordering without retaining web content or private data.
 */
internal object BrowserMediaLifecycleTrace {
    private const val MAX_EVENTS = 64
    private val events = ArrayDeque<BrowserMediaLifecycleTraceEvent>()
    private var nextSequence = 0L

    @Synchronized
    fun record(
        source: String,
        action: String,
        publication: GeckoMediaPlaybackPublication? = null,
        owner: GeckoMediaPlaybackOwner? = null,
        serviceInstanceId: Int? = null,
        hasPublishedMedia: Boolean? = null,
    ) {
        // Never trace metadata for private media. Owner token remains a non-content correlation
        // value for explicit invalidation and teardown paths.
        val visiblePublication = publication?.takeUnless { candidate -> candidate.isPrivate }
        val snapshot = visiblePublication?.snapshot
        events += BrowserMediaLifecycleTraceEvent(
            sequence = ++nextSequence,
            timestampNanos = System.nanoTime(),
            source = source,
            action = action,
            owner = (snapshot?.owner ?: owner)?.toTraceOwner(),
            snapshot = snapshot?.let { current ->
                BrowserMediaTraceSnapshot(
                    isPlaying = current.isPlaying,
                    positionMillis = current.positionMillis,
                    durationMillis = current.durationMillis,
                    playbackRate = current.playbackRate,
                    isPictureInPictureOwner = visiblePublication.isPictureInPictureOwner,
                )
            },
            serviceInstanceId = serviceInstanceId,
            hasPublishedMedia = hasPublishedMedia,
        )
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    @Synchronized
    fun eventsForTesting(): List<BrowserMediaLifecycleTraceEvent> = events.toList()

    @Synchronized
    fun resetForTesting() {
        events.clear()
        nextSequence = 0L
    }

    private fun GeckoMediaPlaybackOwner.toTraceOwner(): BrowserMediaTraceOwner =
        BrowserMediaTraceOwner(
            tabToken = tabId.hashCode().toUInt().toString(radix = 16),
            engineSessionId = engineSessionId,
            navigationGeneration = navigationGeneration,
        )
}

internal sealed interface GeckoMediaPlaybackCommand {
    data object Play : GeckoMediaPlaybackCommand

    data object Pause : GeckoMediaPlaybackCommand

    data object Stop : GeckoMediaPlaybackCommand

    data class Seek(val positionMillis: Long) : GeckoMediaPlaybackCommand
}

/** Keeps a PiP-preempted owner from surviving navigation, closing, or profile locking. */
internal object BrowserMedia3OwnerInvalidationRules {
    fun matches(
        capturedOwner: GeckoMediaPlaybackOwner?,
        backgroundOwner: GeckoMediaPlaybackOwner?,
        tabId: String,
    ): Boolean = capturedOwner?.tabId == tabId || backgroundOwner?.tabId == tabId
}

/** Allows only the owner that is currently exposed through the Media3 handoff. */
internal object BrowserMedia3CommandOwnerRules {
    fun accepts(
        owner: GeckoMediaPlaybackOwner,
        capturedOwner: GeckoMediaPlaybackOwner?,
        pictureInPictureOwner: GeckoMediaPlaybackOwner?,
    ): Boolean = owner == capturedOwner &&
        (pictureInPictureOwner == null || owner == pictureInPictureOwner)
}

/** An Activity may create the initial bound service only while it is visibly active. */
internal object BrowserMediaServiceStartRules {
    fun mayStartNewService(isPlaying: Boolean, isForegroundActivity: Boolean): Boolean =
        isPlaying && isForegroundActivity

    /** A paused Gecko handoff waits for a later play callback instead of stopping its element. */
    fun defersPausedPublication(isPlaying: Boolean): Boolean = !isPlaying
}

/**
 * A delayed Gecko navigation event may describe the document that is still publishing media.
 * Only that exact document may replace its owner without first clearing system controls.
 */
internal object BrowserMediaNavigationRules {
    fun mayReplaceActiveMediaOwner(
        currentDocumentUrl: String?,
        navigationAddress: String?,
        mediaIsActive: Boolean,
    ): Boolean = mediaIsActive &&
        !currentDocumentUrl.isNullOrBlank() &&
        currentDocumentUrl == navigationAddress
}

/** Memory-only identity allocation, including explicit release when a Gecko session closes. */
internal class GeckoMediaSessionIdentityRegistry {
    private val identities = IdentityHashMap<Any, Long>()
    private var nextIdentity = 0L

    fun identityFor(session: Any): Long = identities.getOrPut(session) { ++nextIdentity }

    fun remove(session: Any) {
        identities.remove(session)
    }

    fun size(): Int = identities.size
}

/** Pure ownership, privacy and input rules for the Media3 adapter. */
@OptIn(markerClass = [UnstableApi::class])
internal object GeckoMediaPlaybackRules {
    private const val MAX_TITLE_LENGTH = 160
    private const val MAX_ORIGIN_LENGTH = 160
    private const val MAX_MEDIA_DURATION_MILLIS = Long.MAX_VALUE / C.MICROS_PER_SECOND

    fun snapshot(
        state: BrowserMediaState,
        owner: GeckoMediaPlaybackOwner,
    ): GeckoMediaPlaybackSnapshot? {
        if (
            state.tabId != owner.tabId ||
            owner.tabId.isBlank() ||
            owner.engineSessionId < 0 ||
            owner.navigationGeneration < 0
        ) {
            return null
        }
        val durationMillis = state.durationMillis?.takeIf { duration ->
            duration in 0..MAX_MEDIA_DURATION_MILLIS
        }
        val positionMillis = state.currentPositionMillis
            .coerceIn(0L, MAX_MEDIA_DURATION_MILLIS)
            .let { position -> durationMillis?.let(position::coerceAtMost) ?: position }
        val origin = sanitize(state.origin, MAX_ORIGIN_LENGTH).ifBlank { "Website" }
        return GeckoMediaPlaybackSnapshot(
            owner = owner,
            title = sanitize(state.title, MAX_TITLE_LENGTH).ifBlank { origin },
            origin = origin,
            isPlaying = state.isPlaying,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            playbackRate = state.playbackRate
                .takeIf(Float::isFinite)
                ?.coerceIn(MIN_PLAYBACK_RATE, MAX_PLAYBACK_RATE)
                ?: DEFAULT_PLAYBACK_RATE,
        )
    }

    fun publicationOrNull(
        publication: GeckoMediaPlaybackPublication,
    ): GeckoMediaPlaybackSnapshot? = if (
        !publication.isPrivate &&
        !publication.isProfileLocked &&
        !publication.isAppDataTransferActive
    ) {
        publication.snapshot
    } else {
        null
    }

    fun commandOrNull(
        current: GeckoMediaPlaybackSnapshot?,
        target: GeckoMediaPlaybackOwner,
        command: GeckoMediaPlaybackCommand,
    ): GeckoMediaPlaybackCommand? {
        val snapshot = current ?: return null
        if (snapshot.owner != target) return null
        return when (command) {
            GeckoMediaPlaybackCommand.Play,
            GeckoMediaPlaybackCommand.Pause,
            GeckoMediaPlaybackCommand.Stop,
            -> command

            is GeckoMediaPlaybackCommand.Seek -> snapshot.durationMillis?.let { duration ->
                GeckoMediaPlaybackCommand.Seek(command.positionMillis.coerceIn(0L, duration))
            }
        }
    }

    private fun sanitize(value: String, maximumLength: Int): String = value
        .asSequence()
        .filterNot { character -> character.isISOControl() || character.category == CharCategory.FORMAT }
        .joinToString(separator = "")
        .trim()
        .take(maximumLength)

    private const val MIN_PLAYBACK_RATE = 0.1f
    private const val MAX_PLAYBACK_RATE = 16f
    private const val DEFAULT_PLAYBACK_RATE = 1f
}

/**
 * In-memory handoff point between engine state publication and Media3 transport commands.
 *
 * A background owner remains active across tab selection changes. A newer owner can take over
 * only after the current one is invalidated, except that an explicit PiP owner takes precedence.
 */
internal class GeckoMediaPlaybackCoordinator {
    private var current: GeckoMediaPlaybackPublication? = null

    fun current(): GeckoMediaPlaybackSnapshot? = current?.snapshot

    fun publish(publication: GeckoMediaPlaybackPublication?): GeckoMediaPlaybackSnapshot? {
        val candidate = publication?.takeIf { candidate ->
            GeckoMediaPlaybackRules.publicationOrNull(candidate) != null
        } ?: return clear()
        val retained = current
        current = when {
            retained == null -> candidate
            retained.snapshot.owner == candidate.snapshot.owner -> candidate
            candidate.isPictureInPictureOwner -> candidate
            retained.isPictureInPictureOwner -> retained
            else -> retained
        }
        return current()
    }

    fun clearForPrivateContext(): GeckoMediaPlaybackSnapshot? = clear()

    fun clearForProfileLock(): GeckoMediaPlaybackSnapshot? = clear()

    fun clearForAppDataTransfer(): GeckoMediaPlaybackSnapshot? = clear()

    fun invalidate(owner: GeckoMediaPlaybackOwner): GeckoMediaPlaybackSnapshot? {
        if (current?.snapshot?.owner == owner) clear()
        return current()
    }

    fun replacePublication(
        publication: GeckoMediaPlaybackPublication?,
    ): GeckoMediaPlaybackSnapshot? {
        current = null
        return publish(publication)
    }

    fun command(
        target: GeckoMediaPlaybackOwner,
        command: GeckoMediaPlaybackCommand,
    ): GeckoMediaPlaybackCommand? = GeckoMediaPlaybackRules.commandOrNull(
        current = current(),
        target = target,
        command = command,
    )

    private fun clear(): GeckoMediaPlaybackSnapshot? {
        current = null
        return null
    }
}

internal data class GeckoMedia3PlayerState(
    val snapshot: GeckoMediaPlaybackSnapshot?,
    val isReady: Boolean,
    val playWhenReady: Boolean,
    val supportsSeeking: Boolean,
)

internal object GeckoMedia3PlayerRules {
    /**
     * Connection results are fixed when Media3's notification controller connects. Keep the
     * adapter's complete, safe command contract available even if Gecko has not published its
     * first state yet; [GeckoMedia3Player.stateFor] still narrows current availability.
     */
    @OptIn(markerClass = [UnstableApi::class])
    fun controllerCommands(): Player.Commands = Player.Commands.Builder()
        .add(Player.COMMAND_PLAY_PAUSE)
        .add(Player.COMMAND_STOP)
        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
        .add(Player.COMMAND_GET_TIMELINE)
        .add(Player.COMMAND_GET_METADATA)
        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        .build()

    fun state(snapshot: GeckoMediaPlaybackSnapshot?): GeckoMedia3PlayerState =
        GeckoMedia3PlayerState(
            snapshot = snapshot,
            isReady = snapshot != null,
            playWhenReady = snapshot?.isPlaying == true,
            supportsSeeking = snapshot?.durationMillis != null,
        )
}

/**
 * Media3 view of the existing Gecko decoder. It owns no source, renderer or persisted state.
 * Call [publish] only on [applicationLooper].
 */
@OptIn(markerClass = [UnstableApi::class])
internal open class GeckoMedia3Player(
    applicationLooper: Looper,
    private val onCommand: (GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand) -> Unit,
    private val coordinator: GeckoMediaPlaybackCoordinator = GeckoMediaPlaybackCoordinator(),
) : SimpleBasePlayer(applicationLooper) {
    /** True immediately after a Gecko publication is accepted, before Media3 delivers state. */
    fun hasPublishedMedia(): Boolean = coordinator.current() != null

    fun publish(publication: GeckoMediaPlaybackPublication?) {
        verifyApplicationThread()
        coordinator.publish(publication)
        invalidateState()
    }

    fun clearForPrivateContext() = clearState(coordinator::clearForPrivateContext)

    fun clearForProfileLock() = clearState(coordinator::clearForProfileLock)

    fun clearForAppDataTransfer() = clearState(coordinator::clearForAppDataTransfer)

    fun invalidate(owner: GeckoMediaPlaybackOwner) {
        verifyApplicationThread()
        coordinator.invalidate(owner)
        invalidateState()
    }

    fun replacePublication(publication: GeckoMediaPlaybackPublication?) {
        verifyApplicationThread()
        coordinator.replacePublication(publication)
        invalidateState()
    }

    fun replaceAfterPictureInPicture(publication: GeckoMediaPlaybackPublication?) =
        replacePublication(publication)

    override fun getState(): State = GeckoMedia3PlayerRules.state(coordinator.current())
        .let(::stateFor)

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        dispatch(if (playWhenReady) GeckoMediaPlaybackCommand.Play else GeckoMediaPlaybackCommand.Pause)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        dispatch(GeckoMediaPlaybackCommand.Stop)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        if (mediaItemIndex == 0 && seekCommand == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) {
            dispatch(GeckoMediaPlaybackCommand.Seek(positionMs))
        }
        return Futures.immediateVoidFuture()
    }

    private fun clearState(coordinatorClear: () -> GeckoMediaPlaybackSnapshot?) {
        verifyApplicationThread()
        coordinatorClear()
        invalidateState()
    }

    private fun dispatch(command: GeckoMediaPlaybackCommand) {
        val owner = coordinator.current()?.owner ?: return
        coordinator.command(owner, command)?.let { accepted -> onCommand(owner, accepted) }
    }

    private fun stateFor(state: GeckoMedia3PlayerState): State {
        val snapshot = state.snapshot ?: return idleState()
        val durationMillis = snapshot.durationMillis
        val metadata = MediaMetadata.Builder()
            .setTitle(snapshot.title)
            .setArtist(snapshot.origin)
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(MEDIA_ITEM_ID)
            .setMediaMetadata(metadata)
            .build()
        val durationUs = durationMillis?.times(C.MICROS_PER_SECOND) ?: C.TIME_UNSET
        val period = PeriodData.Builder(snapshot.owner)
            .setDurationUs(durationUs)
            .build()
        val mediaItemData = MediaItemData.Builder(snapshot.owner)
            .setMediaItem(mediaItem)
            .setMediaMetadata(metadata)
            .setIsSeekable(durationMillis != null)
            .setDurationUs(durationUs)
            .setPeriods(listOf(period))
            .build()
        return State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_STOP)
                    // MediaSessionService's notification controller needs these read commands to
                    // observe the non-empty playlist and media metadata from this custom player.
                    .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_GET_TIMELINE)
                    .add(Player.COMMAND_GET_METADATA)
                    .addIf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, state.supportsSeeking)
                    .build(),
            )
            .setPlayWhenReady(
                state.playWhenReady,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setPlaybackState(Player.STATE_READY)
            .setPlaybackSuppressionReason(Player.PLAYBACK_SUPPRESSION_REASON_NONE)
            .setIsLoading(false)
            .setPlaybackParameters(PlaybackParameters(snapshot.playbackRate))
            .setPlaylist(listOf(mediaItemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(snapshot.positionMillis)
            .build()
    }

    private fun idleState(): State = State.Builder()
        .setAvailableCommands(Player.Commands.EMPTY)
        .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .setPlaybackState(Player.STATE_IDLE)
        .build()

    private companion object {
        const val MEDIA_ITEM_ID = "gecko-active-media"
    }
}
