package dev.sk2andy.materialbrowser.reader

import dev.sk2andy.materialbrowser.pocket.NativePocketSpeech
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException

/** Own serially inside the repository model lease; no article or PCM is written to disk. */
internal class PocketSpeechEngine(
    private val directory: File,
    private val voice: String,
) : LocalSpeechEngine {
    override val sampleRate: Int = 24_000
    private var handle = 0L
    private var closed = false

    override fun synthesize(text: String, isCancelled: () -> Boolean): FloatArray {
        check(!closed)
        if (text.isBlank()) return FloatArray(0)
        require(text.length <= MAX_TEXT_CHARACTERS)
        checkActive(isCancelled)
        if (handle == 0L) {
            handle = NativePocketSpeech.create(directory.absolutePath, voice)
            if (handle == 0L) throw IOException("Pocket speech initialization failed")
        }
        checkActive(isCancelled)
        val chunks = mutableListOf<FloatArray>()
        var count = 0
        val success = NativePocketSpeech.synthesize(
            handle,
            text.toByteArray(Charsets.UTF_8),
            object : NativePocketSpeech.Sink {
                override fun isCancelled(): Boolean =
                    isCancelled.invoke() || Thread.currentThread().isInterrupted

                override fun onAudio(samples: FloatArray): Boolean {
                    if (isCancelled()) return false
                    if (samples.size > MAX_SAMPLES - count || samples.any { !it.isFinite() }) {
                        throw IOException("Invalid Pocket speech audio")
                    }
                    samples.indices.forEach { index -> samples[index] = samples[index].coerceIn(-1f, 1f) }
                    chunks += samples
                    count += samples.size
                    return true
                }
            },
        )
        checkActive(isCancelled)
        if (!success || count == 0) throw IOException("Pocket speech synthesis failed")
        return FloatArray(count).also { output ->
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(output, offset)
                offset += chunk.size
            }
        }
    }

    override fun close() {
        closed = true
        if (handle != 0L) NativePocketSpeech.destroy(handle)
        handle = 0L
    }

    private fun checkActive(isCancelled: () -> Boolean) {
        if (isCancelled() || Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("Speech cancelled")
        }
    }

    private companion object {
        const val MAX_TEXT_CHARACTERS = 1_024
        const val MAX_SAMPLES = 24_000 * 120
    }
}
