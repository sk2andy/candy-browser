package dev.sk2andy.materialbrowser.reader

import com.optimaize.langdetect.LanguageDetectorBuilder
import com.optimaize.langdetect.ngram.NgramExtractors
import com.optimaize.langdetect.profiles.BuiltInLanguages
import com.optimaize.langdetect.profiles.LanguageProfileReader
import com.optimaize.langdetect.text.CommonTextObjectFactories
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Bundled character n-grams only: no network requests, article cache, or retained profiles. */
internal object ReaderSpeechLanguageDetector {
    private const val DETECTION_TIMEOUT_MILLIS = 5_000L
    private val detectionLock = Mutex()

    suspend fun detect(content: String): String? = withTimeoutOrNull(DETECTION_TIMEOUT_MILLIS) {
        withContext(Dispatchers.Default) {
            detectionLock.withLock {
                currentCoroutineContext().ensureActive()
                val sample = ReaderSpeechLanguageRules.sample(content) ?: return@withLock null
                try {
                    val reader = LanguageProfileReader()
                    val builder =
                        LanguageDetectorBuilder.create(NgramExtractors.standard())
                            .seed(0L)
                            .minimalConfidence(0.90)
                    // Include unsupported languages too. Restricting the classifier to installed
                    // voices would misidentify other languages as the nearest available voice.
                    for (locale in BuiltInLanguages.getLanguages()) {
                        currentCoroutineContext().ensureActive()
                        builder.withProfile(reader.readBuiltIn(locale))
                    }
                    currentCoroutineContext().ensureActive()
                    val detector = builder.build()
                    val text = CommonTextObjectFactories.forDetectingOnLargeText().forText(sample)
                    val result = detector.detect(text)
                    currentCoroutineContext().ensureActive()
                    if (result.isPresent) result.get().language else null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                } catch (_: IllegalStateException) {
                    null
                }
            }
        }
    }
}
