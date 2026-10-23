package dev.sk2andy.materialbrowser.reader

/** Android playback controls shared by the Reader and its process-owned listening session. */
internal interface ReaderSpeechTransport : ReaderSpeech {
    fun playFrom(content: String, progress: Float)

    fun setRate(rate: Float)
}
