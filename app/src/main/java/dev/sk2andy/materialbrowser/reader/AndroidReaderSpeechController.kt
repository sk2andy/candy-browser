package dev.sk2andy.materialbrowser.reader

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class AndroidReaderSpeechController(
    context: Context,
    private val onInitialized: (Boolean) -> Unit = {},
    private val audioAttributes: AudioAttributes? = null,
    private val onCompleted: () -> Unit = {},
) : ReaderSpeechTransport {
    override var state by mutableStateOf(ReaderSpeechState())
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var text: String = ""
    private var generation = 0L
    private var chunkStarts: Map<String, Int> = emptyMap()

    init {
        engine =
            TextToSpeech(context.applicationContext) { result ->
                handler.post {
                    if (state.status == ReaderSpeechStatus.Closed) return@post
                    if (result == TextToSpeech.SUCCESS) {
                        audioAttributes?.let { engine?.setAudioAttributes(it) }
                        engine?.setOnUtteranceProgressListener(listener)
                        dispatch(ReaderSpeechEvent.Initialized)
                        onInitialized(true)
                    } else {
                        dispatch(ReaderSpeechEvent.InitializationFailed)
                        onInitialized(false)
                    }
                }
            }
    }

    override fun play(content: String) {
        if (state.status != ReaderSpeechStatus.Ready && state.status != ReaderSpeechStatus.Paused)
            return
        val start = if (text == content) state.characterOffset.coerceIn(0, content.length) else 0
        text = content
        if (start >= text.length) dispatch(ReaderSpeechEvent.Stop)
        if (text.isBlank() || start >= text.length) return
        val tts = engine ?: return
        generation++
        val chunks = chunks(text, start, TextToSpeech.getMaxSpeechInputLength().coerceAtMost(3_500))
        chunkStarts = chunks.associate { it.first to it.second }
        chunks.forEachIndexed { index, (id, _, chunk) ->
            val result =
                tts.speak(
                    chunk,
                    if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                    Bundle(),
                    id,
                )
            if (result == TextToSpeech.ERROR) {
                dispatch(ReaderSpeechEvent.InitializationFailed)
                return
            }
        }
        dispatch(ReaderSpeechEvent.Play)
    }

    override fun pause() {
        if (state.status != ReaderSpeechStatus.Speaking) return
        chunkStarts = emptyMap()
        engine?.stop()
        dispatch(ReaderSpeechEvent.Pause)
    }

    override fun stop() {
        chunkStarts = emptyMap()
        engine?.stop()
        generation++
        if (state.status != ReaderSpeechStatus.Initializing) dispatch(ReaderSpeechEvent.Stop)
    }

    override fun playFrom(content: String, progress: Float) {
        if (state.status == ReaderSpeechStatus.Ready) {
            text = content
            state =
                state.copy(characterOffset = (content.length * progress.coerceIn(0f, 1f)).toInt())
        }
        play(content)
    }

    override fun setRate(rate: Float) {
        val wasSpeaking = state.status == ReaderSpeechStatus.Speaking
        if (wasSpeaking) pause()
        engine?.setSpeechRate(rate.coerceIn(0.5f, 2f))
        if (wasSpeaking) play(text)
    }

    override fun close() {
        chunkStarts = emptyMap()
        engine?.stop()
        engine?.shutdown()
        engine = null
        text = ""
        dispatch(ReaderSpeechEvent.Close)
    }

    private val listener =
        object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                handler.post {
                    if (
                        utteranceId != null &&
                            utteranceId == chunkStarts.keys.lastOrNull() &&
                            state.status == ReaderSpeechStatus.Speaking
                    ) {
                        dispatch(ReaderSpeechEvent.Completed)
                        onCompleted()
                    }
                }
            }

            @Deprecated("Deprecated in Android")
            override fun onError(utteranceId: String?) {
                handler.post {
                    if (utteranceId in chunkStarts) dispatch(ReaderSpeechEvent.InitializationFailed)
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                handler.post {
                    val base = chunkStarts[utteranceId] ?: return@post
                    dispatch(ReaderSpeechEvent.RangeStarted(base + start))
                }
            }
        }

    private fun dispatch(event: ReaderSpeechEvent) {
        state = ReaderSpeechRules.reduce(state, event, text.length)
    }

    private fun chunks(
        content: String,
        startOffset: Int,
        maxLength: Int,
    ): List<Triple<String, Int, String>> {
        val result = mutableListOf<Triple<String, Int, String>>()
        var cursor = startOffset
        var ordinal = 0
        while (cursor < content.length) {
            val hardEnd = minOf(cursor + maxLength, content.length)
            val split =
                if (hardEnd == content.length) {
                    hardEnd
                } else {
                    content
                        .lastIndexOfAny(charArrayOf('\n', '.', '!', '?', ' '), hardEnd)
                        .takeIf { it > cursor + maxLength / 2 }
                        ?.plus(1) ?: hardEnd
                }
            result +=
                Triple(
                    "reader-$generation-$ordinal-$cursor",
                    cursor,
                    content.substring(cursor, split),
                )
            cursor = split
            ordinal++
        }
        return result
    }
}
