package dev.sk2andy.materialbrowser.reader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    private val preparation: ReaderSpeechPreparationRepository = ReaderSpeechPreparationRepository.get(context),
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var preparationJob: Job? = null
    private var preparationGeneration = 0L
    private var preparedText: PreparedReaderSpeechText? = null
    private var preparedChoiceRevision = -1L

    var preparingText by mutableStateOf(false)
        private set
    var preparationFallback by mutableStateOf(false)
        private set

    val playbackFailed: Boolean get() = delegate?.state?.status == ReaderSpeechStatus.Unavailable
    val choiceRequested: Boolean get() = setupRequested || playbackFailed
    val modelState: ReaderSpeechModelState get() = repository.state
    val preparationState: ReaderSpeechPreparationState get() = preparation.state
    override val state: ReaderSpeechState get() {
        // Keep pause and stop available while the optional preparation is running.
        if (preparingText) return ReaderSpeechState(ReaderSpeechStatus.Speaking)
        val current = delegate?.state ?: idleState
        return current.copy(characterOffset = preparedText?.originalOffset(current.characterOffset)
            ?: current.characterOffset)
    }

    init { repository.addListener(modelListener) }

    override fun play(content: String) {
        if (closed || content.isBlank()) return
        if (content != playbackText || preparedChoiceRevision != preparation.choiceRevision) {
            cancelPreparation()
            preparedText = null
            if (delegate?.state?.status in setOf(ReaderSpeechStatus.Speaking, ReaderSpeechStatus.Paused)) {
                delegate?.stop()
            }
        }
        playbackText = content
        pendingText = content
        continuePlayback()
    }

    fun refreshPreparationAvailability() = preparation.refresh()
    fun confirmPreparationAdult() = preparation.confirmAdult()
    fun setPreparationEnabled(enabled: Boolean) = preparation.setEnabled(enabled)
    fun downloadPreparationModel() = preparation.download()
    fun cancelPreparationDownload() = preparation.cancelDownload()

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
        cancelPreparation()
        preparedText = null
        preparationFallback = false
        pendingText = null
        playbackText = null
        setupRequested = false
        if (playbackFailed) resetDelegate()
    }

    override fun pause() {
        val wasPreparing = preparingText
        cancelPreparation()
        pendingText = null
        if (wasPreparing) {
            preparedText = null
            delegate?.stop()
        }
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
        scope.cancel()
        idleState = ReaderSpeechState(ReaderSpeechStatus.Closed)
    }

    private fun retryFailedPlayback() {
        if (!playbackFailed) return
        pendingText = playbackText
        resetDelegate()
    }

    private fun resetDelegate() {
        cancelPreparation()
        preparedText = null
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
        val cached = preparedText?.takeIf { it.original == text }
        if (cached != null || !preparationState.enabled) {
            preparedText = cached ?: ReaderSpeechPreparationRules.unchanged(text)
            preparedChoiceRevision = preparation.choiceRevision
            pendingText = null
            current.play(requireNotNull(preparedText).text)
            return
        }
        if (preparingText) return
        val expected = ++preparationGeneration
        preparingText = true
        preparationFallback = false
        preparationJob = scope.launch {
            val result = preparation.prepare(text)
            if (closed || expected != preparationGeneration || pendingText != text || delegate !== current) {
                return@launch
            }
            preparingText = false
            preparationFallback = result.fallback
            preparedText = result.prepared
            preparedChoiceRevision = preparation.choiceRevision
            pendingText = null
            current.play(result.prepared.text)
        }
    }

    private fun cancelPreparation() {
        preparationGeneration++
        preparationJob?.cancel()
        preparationJob = null
        preparingText = false
    }
}
