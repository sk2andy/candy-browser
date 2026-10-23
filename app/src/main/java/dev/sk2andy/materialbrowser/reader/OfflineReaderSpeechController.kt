package dev.sk2andy.materialbrowser.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

/** Keeps bounded sentence audio in memory and releases native sessions after each playback. */
internal class OfflineReaderSpeechController(
    context: Context,
    private val repository: ReaderSpeechModelRepository,
    private val onCompleted: () -> Unit = {},
    private val manageAudioFocus: Boolean = true,
    private val withEngine: ((LocalSpeechEngine) -> Unit) -> Unit = { block ->
        repository.withEngine(block)
    },
) : ReaderSpeechTransport {
    override var state by mutableStateOf(ReaderSpeechState(ReaderSpeechStatus.Ready))
        private set

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "reader-local-speech") }
    private val playback = Executors.newSingleThreadExecutor { Thread(it, "reader-local-audio") }
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong()
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val audioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    private val focusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(
                { focus ->
                    if (
                        focus == AudioManager.AUDIOFOCUS_LOSS ||
                            focus == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                            focus == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                    )
                        pause()
                },
                main,
            )
            .build()
    @Volatile private var track: AudioTrack? = null
    private var text = ""
    @Volatile private var rate = 1f

    override fun play(content: String) {
        if (state.status !in setOf(ReaderSpeechStatus.Ready, ReaderSpeechStatus.Paused)) return
        if (content.isBlank()) return
        if (
            manageAudioFocus &&
                audioManager.requestAudioFocus(focusRequest) !=
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {
            state = ReaderSpeechState(ReaderSpeechStatus.Unavailable)
            return
        }
        val offset = if (text == content) state.characterOffset else 0
        text = content
        val expected = generation.incrementAndGet()
        state = ReaderSpeechState(ReaderSpeechStatus.Speaking, offset)
        worker.execute {
            if (!isCurrent(expected)) return@execute
            try {
                withEngine { engine ->
                    var cursor = offset
                    var playing: Future<*>? = null
                    while (isCurrent(expected)) {
                        val chunk = OfflineSpeechRules.nextChunk(content, cursor) ?: break
                        // Prepare one sentence while the previous one plays; never queue a whole
                        // article.
                        val samples = engine.synthesize(chunk.text) { !isCurrent(expected) }
                        playing?.get()
                        if (!isCurrent(expected)) break
                        playing =
                            playback.submit {
                                main.post {
                                    if (isCurrent(expected))
                                        state = state.copy(characterOffset = chunk.start)
                                }
                                playSamples(samples, engine.sampleRate, expected)
                            }
                        cursor = chunk.end
                    }
                    playing?.get()
                }
                main.post {
                    if (isCurrent(expected)) {
                        releaseAudioFocus()
                        state = ReaderSpeechState(ReaderSpeechStatus.Ready)
                        onCompleted()
                    }
                }
            } catch (_: Exception) {
                main.post {
                    if (isCurrent(expected)) {
                        cancelAudio()
                        state = ReaderSpeechState(ReaderSpeechStatus.Unavailable)
                    }
                }
            }
        }
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
        if (!rate.isFinite()) return
        this.rate = rate.coerceIn(0.5f, 2f)
        track?.let { audio ->
            runCatching { audio.playbackParams = PlaybackParams().setSpeed(this.rate) }
        }
    }

    override fun pause() {
        if (state.status != ReaderSpeechStatus.Speaking) return
        cancelAudio()
        state = state.copy(status = ReaderSpeechStatus.Paused)
    }

    override fun stop() {
        if (state.status == ReaderSpeechStatus.Closed) return
        cancelAudio()
        state = ReaderSpeechState(ReaderSpeechStatus.Ready)
    }

    override fun close() {
        cancelAudio()
        text = ""
        state = ReaderSpeechState(ReaderSpeechStatus.Closed)
        worker.shutdownNow()
        playback.shutdownNow()
    }

    private fun cancelAudio() {
        generation.incrementAndGet()
        releaseAudioFocus()
        track?.let { audio ->
            runCatching {
                audio.pause()
                audio.flush()
            }
        }
    }

    private fun releaseAudioFocus() {
        if (manageAudioFocus) audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun isCurrent(expected: Long): Boolean =
        generation.get() == expected && !Thread.currentThread().isInterrupted

    private fun playSamples(samples: FloatArray, sampleRate: Int, expected: Long) {
        if (samples.isEmpty() || !isCurrent(expected)) return
        val minimumBytes =
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            )
        check(minimumBytes > 0)
        val audio =
            AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minimumBytes, sampleRate / 5 * Float.SIZE_BYTES))
                .build()
        track = audio
        try {
            if (!isCurrent(expected)) return
            audio.playbackParams = PlaybackParams().setSpeed(rate)
            audio.play()
            var written = 0
            while (written < samples.size && isCurrent(expected)) {
                val count =
                    audio.write(
                        samples,
                        written,
                        minOf(4_096, samples.size - written),
                        AudioTrack.WRITE_BLOCKING,
                    )
                check(count > 0)
                written += count
            }
            while (isCurrent(expected) && audio.playbackHeadPosition < written) Thread.sleep(20)
        } finally {
            track = null
            runCatching { audio.stop() }
            audio.release()
        }
    }
}
