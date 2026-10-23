package dev.sk2andy.materialbrowser.feeds

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.snapshotFlow
import dev.sk2andy.materialbrowser.browser.BrowserMediaPlaybackService
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleSessionStore
import dev.sk2andy.materialbrowser.reader.ReaderSpeechController
import dev.sk2andy.materialbrowser.reader.ReaderSpeechStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FeedPlaybackState(
    val profileId: String? = null,
    val articleId: String? = null,
    val status: ReaderSpeechStatus = ReaderSpeechStatus.Ready,
    val progress: Float = 0f,
    val speed: Float = 1f,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val mutationFailed: Boolean = false,
    val busy: Boolean = false,
    val isActive: Boolean = false,
)

/** Process-local playback and durable queue writes; no Activity or composable owns these jobs. */
class FeedPlaybackManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutations = Mutex()
    private val mutableState = MutableStateFlow(FeedPlaybackState())
    val state: StateFlow<FeedPlaybackState> = mutableState.asStateFlow()

    private var repository: FeedRepository? = null
    private var repositoryJob: Job? = null
    private var documentJob: Job? = null
    private var speechJob: Job? = null
    private var accessJob: Job? = null
    private var service: FeedMediaPlaybackService? = null
    private var speech: ReaderSpeechController? = null
    private var text = ""
    private var wantsPlay = false
    private var completing = false
    private var generation = 0L
    private var pendingMutations = 0
    private var savedProgress = 0f

    fun prepare(profileId: String) {
        if (repository?.profileId == profileId) return
        stop()
        repositoryJob?.cancel()
        val selected = FeedRepository.get(context, profileId)
        repository = selected
        mutableState.value = FeedPlaybackState(profileId = profileId, speed = state.value.speed)
        repositoryJob =
            scope.launch {
                selected.load()
                selected.state.collect { snapshot ->
                    if (repository !== selected) return@collect
                    if (!snapshot.loaded) {
                        invalidate(profileId)
                        return@collect
                    }
                    val head = snapshot.queue.firstOrNull()
                    if (head != state.value.articleId) selectArticle(head)
                    publish()
                }
            }
    }

    /**
     * Called only by an explicit user play action; Android media controls reuse the live service.
     */
    fun play(profileId: String) {
        if (GuestBubbleSessionStore(context).isLocked()) {
            invalidate()
            return
        }
        prepare(profileId)
        if (state.value.busy || state.value.loadFailed) return
        val snapshot = repository?.state?.value
        if (snapshot?.loaded == true && snapshot.queue.isEmpty()) return
        BrowserMediaPlaybackService.stopAndClear(context)
        wantsPlay = true
        mutableState.value = state.value.copy(isActive = true)
        runCatching {
                // A new start also supersedes an earlier stopSelf request during rapid Stop/Play.
                context.startService(Intent(context, FeedMediaPlaybackService::class.java))
            }
            .onFailure {
                wantsPlay = false
                mutableState.value = state.value.copy(isActive = false, loadFailed = true)
            }
        publish()
        trySpeak()
    }

    fun togglePlayback(profileId: String) {
        if (wantsPlay && state.value.profileId == profileId) pause() else play(profileId)
    }

    fun pause() {
        wantsPlay = false
        speech?.pause()
        saveProgress()
        service?.abandonAudioFocus()
        mutableState.value =
            state.value.copy(
                status =
                    if (state.value.status == ReaderSpeechStatus.Speaking) {
                        ReaderSpeechStatus.Paused
                    } else {
                        state.value.status
                    }
            )
        publish()
    }

    fun next() {
        val selected = repository ?: return
        val id = state.value.articleId ?: return
        val progress = state.value.progress
        suspendSpeech()
        mutate(selected) {
            selected.updateProgress(id, progress).getOrThrow()
            selected.setQueue(selected.state.value.queue.filterNot { it == id }).getOrThrow()
        }
    }

    fun setQueue(ids: List<String>) {
        val selected = repository ?: return
        val id = state.value.articleId
        val progress = state.value.progress
        // Pause synchronously when the current article is removed/reordered, before committing.
        if (ids.firstOrNull() != id) suspendSpeech()
        mutate(selected) {
            id?.let { selected.updateProgress(it, progress).getOrThrow() }
            selected.setQueue(ids).getOrThrow()
        }
    }

    fun setSpeed(rate: Float) {
        val speed = rate.takeIf(Float::isFinite)?.coerceIn(0.5f, 2f) ?: return
        mutableState.value = state.value.copy(speed = speed)
        speech?.setRate(speed)
        publish()
    }

    fun retry() {
        mutableState.value = state.value.copy(loadFailed = false, mutationFailed = false)
        val current = state.value.articleId
        selectArticle(current, force = true)
    }

    fun stop() {
        pause()
        documentJob?.cancel()
        accessJob?.cancel()
        generation++
        stopSpeech()
        mutableState.value = state.value.copy(isActive = false, loading = false)
        publish()
        service?.let { attached ->
            detach(attached)
            attached.stopSelf()
        }
    }

    /** Clears metadata immediately, before profile locking/deletion or an app-data transfer. */
    fun invalidate(profileId: String? = null) {
        if (profileId != null && state.value.profileId != profileId) return
        stop()
        repositoryJob?.cancel()
        repositoryJob = null
        repository = null
        text = ""
        mutableState.value = FeedPlaybackState(speed = state.value.speed)
        service?.publish(null)
    }

    internal fun attach(attached: FeedMediaPlaybackService) {
        if (service === attached) return
        service = attached
        speech =
            ReaderSpeechController(
                context,
                onCompleted = ::completeArticle,
                audioAttributes = attached.speechAudioAttributes,
            )
        speechJob =
            scope.launch {
                snapshotFlow { speech?.state }
                    .collect { current ->
                        current ?: return@collect
                        if (completing) return@collect
                        val progress =
                            if (
                                text.isNotEmpty() &&
                                    current.status in
                                        setOf(
                                            ReaderSpeechStatus.Speaking,
                                            ReaderSpeechStatus.Paused,
                                        )
                            ) {
                                (current.characterOffset.toFloat() / text.length).coerceIn(0f, 1f)
                            } else {
                                state.value.progress
                            }
                        mutableState.value =
                            state.value.copy(status = current.status, progress = progress)
                        if (current.status == ReaderSpeechStatus.Unavailable) {
                            wantsPlay = false
                            attached.abandonAudioFocus()
                        }
                        if (progress - savedProgress >= 0.01f) saveProgress()
                        publish()
                        trySpeak()
                    }
            }
        if (text.isEmpty() && state.value.articleId != null) {
            selectArticle(state.value.articleId, force = true)
        }
        publish()
    }

    internal fun detach(detached: FeedMediaPlaybackService) {
        if (service !== detached) return
        accessJob?.cancel()
        accessJob = null
        saveProgress()
        wantsPlay = false
        service = null
        speechJob?.cancel()
        speechJob = null
        speech?.close()
        speech = null
        documentJob?.cancel()
        generation++
        mutableState.value =
            state.value.copy(
                isActive = false,
                loading = false,
                status =
                    if (state.value.status == ReaderSpeechStatus.Unavailable) {
                        ReaderSpeechStatus.Unavailable
                    } else {
                        ReaderSpeechStatus.Paused
                    },
            )
    }

    internal fun foregroundReady() = trySpeak()

    internal fun hasPendingPlayback(): Boolean = state.value.isActive && wantsPlay

    internal fun isAttached(attached: FeedMediaPlaybackService): Boolean = service === attached

    private fun selectArticle(id: String?, force: Boolean = false) {
        if (id == state.value.articleId && !force) return
        accessJob?.cancel()
        accessJob = null
        documentJob?.cancel()
        val token = ++generation
        stopSpeech()
        completing = false
        text = ""
        val selected = repository ?: return
        val article = selected.state.value.articles.firstOrNull { it.id == id }
        savedProgress = article?.readingProgress ?: 0f
        mutableState.value =
            state.value.copy(
                articleId = article?.id,
                progress = savedProgress,
                loading = article != null,
                loadFailed = false,
            )
        if (article == null) {
            wantsPlay = false
            mutableState.value = state.value.copy(isActive = false)
            service?.abandonAudioFocus()
            publish()
            service?.stopSelf()
            return
        }
        publish()
        documentJob =
            scope.launch {
                try {
                    val document = FeedArticleLoader(context, selected).read(article)
                    val content = document.speechText
                    if (token != generation || repository !== selected) return@launch
                    text = content
                    if (content.isBlank()) {
                        wantsPlay = false
                        service?.abandonAudioFocus()
                    }
                    mutableState.value =
                        state.value.copy(loading = false, loadFailed = content.isBlank())
                    publish()
                    trySpeak()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (token != generation) return@launch
                    wantsPlay = false
                    mutableState.value = state.value.copy(loading = false, loadFailed = true)
                    service?.abandonAudioFocus()
                    publish()
                }
            }
    }

    private fun trySpeak() {
        val speaker = speech ?: return
        if (!wantsPlay || completing || state.value.busy || text.isBlank() || state.value.loading)
            return
        if (speaker.state.status !in setOf(ReaderSpeechStatus.Ready, ReaderSpeechStatus.Paused))
            return
        if (accessJob?.isActive == true) return
        val selected = repository ?: return
        val attached = service ?: return
        val token = generation
        accessJob =
            scope.launch {
                try {
                    selected.requireAccess()
                    check(!GuestBubbleSessionStore(context).isLocked())
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (repository === selected) invalidate(selected.profileId)
                    return@launch
                }
                if (
                    token != generation ||
                        speech !== speaker ||
                        service !== attached ||
                        !wantsPlay ||
                        completing ||
                        state.value.busy
                )
                    return@launch
                if (!attached.acquireAudioFocus()) return@launch
                speaker.setRate(state.value.speed)
                speaker.playFrom(text, state.value.progress.takeIf { it < 1f } ?: 0f)
            }
    }

    private fun completeArticle() {
        val selected = repository ?: return
        val id = state.value.articleId ?: return
        completing = true
        mutableState.value = state.value.copy(progress = 1f)
        mutate(selected) {
            selected.completeQueuedArticle(id).getOrThrow()
            if (repository === selected) {
                val head = selected.state.value.queue.firstOrNull()
                if (state.value.articleId != head) selectArticle(head)
                completing = false
            }
        }
    }

    private fun suspendSpeech() {
        speech?.pause()
        service?.abandonAudioFocus()
    }

    private fun stopSpeech() {
        if (
            speech?.state?.status !in
                setOf(ReaderSpeechStatus.Unavailable, ReaderSpeechStatus.Closed)
        ) {
            speech?.stop()
        }
    }

    private fun saveProgress() {
        if (completing) return
        val selected = repository ?: return
        val id = state.value.articleId ?: return
        val progress = state.value.progress
        savedProgress = progress
        scope.launch {
            val result = mutations.withLock { selected.updateProgress(id, progress) }
            if (repository === selected && result.isFailure) {
                mutableState.value = state.value.copy(mutationFailed = true)
            }
        }
    }

    private fun mutate(selected: FeedRepository, action: suspend () -> Unit) {
        pendingMutations++
        mutableState.value = state.value.copy(busy = true, mutationFailed = false)
        scope.launch {
            val result = runCatching { mutations.withLock { action() } }
            pendingMutations--
            if (repository !== selected) return@launch
            mutableState.value =
                state.value.copy(busy = pendingMutations > 0, mutationFailed = result.isFailure)
            if (result.isFailure) {
                completing = false
                pause()
            }
            publish()
            trySpeak()
        }
    }

    private fun publish() {
        val selected = repository
        val snapshot = selected?.state?.value
        val article = snapshot?.articles?.firstOrNull { it.id == state.value.articleId }
        val current = state.value
        service?.publish(
            if (current.isActive && article != null && snapshot.loaded) {
                FeedMediaPlaybackSnapshot(
                    profileId = selected.profileId,
                    articleId = article.id,
                    title = article.title,
                    source =
                        snapshot.sources.firstOrNull { it.id in article.sourceIds }?.title
                            ?: article.url.substringAfter("://").substringBefore('/'),
                    playWhenReady = wantsPlay,
                    loading =
                        current.loading || speech?.state?.status == ReaderSpeechStatus.Initializing,
                    speed = current.speed,
                    hasNext = snapshot.queue.size > 1,
                )
            } else {
                null
            }
        )
    }

    companion object {
        @Volatile private var instance: FeedPlaybackManager? = null

        fun get(context: Context): FeedPlaybackManager =
            instance
                ?: synchronized(this) {
                    instance
                        ?: FeedPlaybackManager(context.applicationContext).also { instance = it }
                }
    }
}
