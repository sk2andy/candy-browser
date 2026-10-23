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

/** Preload verified language packs. Synthesis itself performs no network requests. */
@RunWith(AndroidJUnit4::class)
class PocketSpeechEngineInstrumentedTest {
    @Test
    fun downloadedGermanAndEnglishModelsSynthesizeAndCancelLocally() {
        val path = InstrumentationRegistry.getArguments().getString("pocketSpeechModelDirectory")
        assumeNotNull(path)
        listOf(
            "de" to "Guten Morgen. Heute lesen wir einen Artikel auf diesem Gerät.",
            "en" to "Good morning. Today we are reading an article on this device.",
        ).forEach { (code, text) ->
            val language = requireNotNull(PocketSpeechCatalog.find(code))
            val directory = File(requireNotNull(path), code)
            assertTrue(PocketSpeechModelFiles(directory, language).isReady())
            val originalFiles = directory.walkTopDown().filter(File::isFile).map { it.relativeTo(directory).path }.toSet()
            PocketSpeechEngine(directory, language.voiceId).use { engine ->
                assertEquals(24_000, engine.sampleRate)
                val samples = engine.synthesize(text) { false }
                assertTrue(samples.size > engine.sampleRate / 2)
                assertTrue(samples.all(Float::isFinite))
                assertTrue(samples.any { abs(it) > 0.01f })
                var checks = 0
                try {
                    engine.synthesize(text) { ++checks > 8 }
                    fail("Cancellation must stop native generation")
                } catch (_: InterruptedIOException) {
                    assertTrue(checks > 8)
                }
                assertTrue(engine.synthesize(text) { false }.isNotEmpty())
                assertEquals("No article/audio caches", originalFiles,
                    directory.walkTopDown().filter(File::isFile).map { it.relativeTo(directory).path }.toSet())
            }
        }
    }
}
