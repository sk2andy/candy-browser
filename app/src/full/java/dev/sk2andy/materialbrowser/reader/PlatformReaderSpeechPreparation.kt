package dev.sk2andy.materialbrowser.reader

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

internal fun createReaderSpeechPreparationBackend(): ReaderSpeechPreparationBackend =
    GeminiNanoReaderSpeechPreparationBackend()

/** AICore owns the shared model download; Candy never stores model weights or prompt history. */
private class GeminiNanoReaderSpeechPreparationBackend : ReaderSpeechPreparationBackend {
    private val client = lazy { Generation.getClient() }

    override suspend fun availability(): ReaderSpeechPreparationAvailability =
        when (client.value.checkStatus()) {
            FeatureStatus.AVAILABLE -> ReaderSpeechPreparationAvailability.Available
            FeatureStatus.DOWNLOADABLE -> ReaderSpeechPreparationAvailability.Downloadable
            FeatureStatus.DOWNLOADING -> ReaderSpeechPreparationAvailability.Downloading
            else -> ReaderSpeechPreparationAvailability.Unavailable
        }

    override suspend fun download(onProgress: (Long) -> Unit) {
        var completed = false
        client.value.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> onProgress(0L)
                is DownloadStatus.DownloadProgress -> onProgress(status.totalBytesDownloaded.coerceAtLeast(0L))
                is DownloadStatus.DownloadFailed -> throw status.e
                DownloadStatus.DownloadCompleted -> completed = true
            }
        }
        check(completed) { "Gemini Nano download did not complete." }
    }

    override suspend fun generate(prompt: String): String? {
        if (prompt.isBlank() || prompt.length > MAX_PROMPT_CHARACTERS) return null
        val request = generateContentRequest(TextPart(prompt)) {
            temperature = 0f
            topK = 1
            candidateCount = 1
            maxOutputTokens = MAX_OUTPUT_TOKENS
        }
        if (client.value.countTokens(request).totalTokens >= MAX_INPUT_TOKENS) return null
        val candidate = client.value.generateContent(request).candidates.singleOrNull() ?: return null
        if (candidate.finishReason != Candidate.FinishReason.STOP) return null
        return candidate.text.takeIf { it.isNotBlank() && it.length <= MAX_OUTPUT_CHARACTERS }
    }

    override fun close() {
        if (client.isInitialized()) client.value.close()
    }

    private companion object {
        const val MAX_PROMPT_CHARACTERS = 12_000
        const val MAX_INPUT_TOKENS = 4_000
        const val MAX_OUTPUT_TOKENS = 1_024
        const val MAX_OUTPUT_CHARACTERS = 8_192
    }
}
