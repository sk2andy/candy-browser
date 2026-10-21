package dev.sk2andy.materialbrowser.reader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One first-use choice shared by Reader and Android selected-text speech. */
internal class ReaderSpeechController(
    context: Context,
    private val repository: ReaderSpeechModelRepository = ReaderSpeechModelRepository.get(context),
    private val createAndroidSpeech: ((Boolean) -> Unit) -> ReaderSpeech = { onInitialized ->
        AndroidReaderSpeechController(context.applicationContext, onInitialized)
    },
    private val createOfflineSpeech: () -> ReaderSpeech = {
        OfflineReaderSpeechController(context.applicationContext, repository)
    },
) : ReaderSpeech {
    private var delegate by mutableStateOf<ReaderSpeech?>(null)
    private var idleState by mutableStateOf(ReaderSpeechState(ReaderSpeechStatus.Ready))
    private var setupRequested by mutableStateOf(false)
    private var pendingText: String? = null
    private var playbackText: String? = null
    private var localVoice = false
    private var closed = false
    private var delegateGeneration = 0L
    private val modelListener: () -> Unit = ::modelChanged

    val playbackFailed: Boolean get() = delegate?.state?.status == ReaderSpeechStatus.Unavailable
    val choiceRequested: Boolean get() = setupRequested || playbackFailed
    val modelState: ReaderSpeechModelState get() = repository.state
    override val state: ReaderSpeechState get() = delegate?.state ?: idleState

    init { repository.addListener(modelListener) }

    override fun play(content: String) {
        if (closed || content.isBlank()) return
        playbackText = content
        pendingText = content
        continuePlayback()
    }

    fun chooseOfflineVoice() {
        if (closed) return
        retryFailedPlayback()
        repository.download()
        continuePlayback()
    }

    fun chooseSystemVoice() {
        if (closed) return
        retryFailedPlayback()
        setupRequested = false
        repository.chooseSystemVoice()
        continuePlayback()
    }

    fun dismissChoice() {
        pendingText = null
        playbackText = null
        setupRequested = false
        if (playbackFailed) resetDelegate()
    }

    override fun pause() {
        pendingText = null
        delegate?.pause()
    }

    override fun stop() {
        if (closed) return
        dismissChoice()
        delegate?.stop()
    }

    override fun close() {
        if (closed) return
        closed = true
        dismissChoice()
        repository.removeListener(modelListener)
        resetDelegate()
        idleState = ReaderSpeechState(ReaderSpeechStatus.Closed)
    }

    private fun retryFailedPlayback() {
        if (!playbackFailed) return
        pendingText = playbackText
        resetDelegate()
    }

    private fun resetDelegate() {
        delegateGeneration++
        delegate?.close()
        delegate = null
        idleState = ReaderSpeechState(ReaderSpeechStatus.Ready)
    }

    private fun modelChanged() {
        if (closed) return
        if (delegate != null && localVoice != modelState.enabled) resetDelegate()
        continuePlayback()
    }

    private fun continuePlayback() {
        val text = pendingText ?: return
        if (!modelState.loaded || closed) return
        if (!modelState.offered || (modelState.enabled && !modelState.ready)) {
            setupRequested = true
            return
        }
        setupRequested = false
        if (delegate == null) {
            localVoice = modelState.enabled
            val expectedGeneration = ++delegateGeneration
            delegate = if (localVoice) {
                createOfflineSpeech()
            } else {
                createAndroidSpeech { success ->
                    if (!closed && expectedGeneration == delegateGeneration) {
                        if (success) continuePlayback() else pendingText = null
                    }
                }
            }
        }
        val current = delegate ?: return
        if (current.state.status == ReaderSpeechStatus.Initializing) return
        pendingText = null
        current.play(text)
    }
}
