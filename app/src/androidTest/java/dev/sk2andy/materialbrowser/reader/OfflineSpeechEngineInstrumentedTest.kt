package dev.sk2andy.materialbrowser.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.InterruptedIOException
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in real-model test: preload verified assets and pass offlineSpeechModelDirectory. No network. */
@RunWith(AndroidJUnit4::class)
class OfflineSpeechEngineInstrumentedTest {
    @Test
    fun downloadedModelSynthesizesGermanAndEnglishLocally() {
        val path = InstrumentationRegistry.getArguments().getString("offlineSpeechModelDirectory")
        assumeNotNull(path)
        val files = OfflineSpeechModelFiles(File(requireNotNull(path)))
        assertTrue("Preloaded model must pass every checksum", files.isReady())
        OfflineSpeechEngine(files).use { engine ->
            assertEquals(44_100, engine.sampleRate)
            listOf("Guten Morgen. Das ist ein lokaler Sprachtest.", "Good morning. This is a local speech test.").forEach { text ->
                val samples = engine.synthesize(text) { false }
                assertTrue("Speech must contain at least half a second", samples.size > engine.sampleRate / 2)
                assertTrue("Speech samples must be finite", samples.all(Float::isFinite))
                assertTrue("Speech must not be silent", samples.any { abs(it) > 0.01f })
            }
            var checks = 0
            try {
                engine.synthesize("Diese Wiedergabe wird abgebrochen.") { ++checks > 4 }
                fail("Cancellation must stop the denoising loop")
            } catch (_: InterruptedIOException) {
                assertTrue(checks > 4)
            }
            assertTrue(engine.synthesize("Weiter geht es.") { false }.isNotEmpty())
        }
    }
}
