package dev.sk2andy.materialbrowser.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Observable bridge for a platform speech engine; speech policy remains in [ReaderSpeechRules]. */
class BrowserReaderSpeech(
    private val onPlay: (String) -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    private val onClose: () -> Unit,
) : ReaderSpeech {
    override var state: ReaderSpeechState by mutableStateOf(ReaderSpeechState())
        private set

    fun updateState(value: ReaderSpeechState) {
        if (state.status != ReaderSpeechStatus.Closed) state = value
    }

    override fun play(content: String) {
        if (state.status != ReaderSpeechStatus.Closed) onPlay(content)
    }

    override fun pause() {
        if (state.status != ReaderSpeechStatus.Closed) onPause()
    }

    override fun stop() {
        if (state.status != ReaderSpeechStatus.Closed) onStop()
    }

    override fun close() {
        if (state.status == ReaderSpeechStatus.Closed) return
        state = ReaderSpeechRules.reduce(state, ReaderSpeechEvent.Close, textLength = 0)
        onClose()
    }
}
