package dev.sk2andy.materialbrowser.reader

/** Keep the FOSS variant independent of Google AICore and proprietary ML Kit SDKs. */
internal fun createReaderSpeechPreparationBackend(): ReaderSpeechPreparationBackend =
    object : ReaderSpeechPreparationBackend {
        override suspend fun availability() = ReaderSpeechPreparationAvailability.Unavailable

        override suspend fun download(onProgress: (Long) -> Unit) = Unit

        override suspend fun generate(prompt: String): String? = null

        override fun close() = Unit
    }
