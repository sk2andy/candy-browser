package dev.sk2andy.materialbrowser.reader

internal enum class ReaderSpeechPreparationAvailability {
    Checking, Unavailable, Downloadable, Downloading, Available,
}

/** Flavor boundary: the FOSS adapter never loads Google services. */
internal interface ReaderSpeechPreparationBackend : AutoCloseable {
    suspend fun availability(): ReaderSpeechPreparationAvailability
    suspend fun download(onProgress: (Long) -> Unit)
    suspend fun generate(prompt: String): String?
    override fun close()
}
