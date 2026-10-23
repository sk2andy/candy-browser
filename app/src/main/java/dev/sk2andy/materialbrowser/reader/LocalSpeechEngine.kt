package dev.sk2andy.materialbrowser.reader

/** A serially owned local synthesizer; text and PCM never leave memory. */
internal interface LocalSpeechEngine : AutoCloseable {
    val sampleRate: Int

    fun synthesize(text: String, isCancelled: () -> Boolean): FloatArray
}
