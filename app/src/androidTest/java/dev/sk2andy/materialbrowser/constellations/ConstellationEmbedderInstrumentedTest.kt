package dev.sk2andy.materialbrowser.constellations

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Stage the pinned model fixtures in the target app's no_backup/constellation_model first. */
@RunWith(AndroidJUnit4::class)
class ConstellationEmbedderInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun pinnedModelProducesNormalizedCrossLanguageSemanticVectors() {
        ConstellationEmbedder(context).use { embedder ->
            assertTrue("Stage all three pinned model fixtures before this test", embedder.isModelReady())
            val german = requireNotNull(embedder.embed("", "Datenschutz schützt persönliche Daten im Browser."))
            val english = requireNotNull(embedder.embed("", "Privacy protects personal data in the browser."))
            val unrelated = requireNotNull(embedder.embed("", "Chocolate cake with fresh strawberries."))
            listOf(german, english, unrelated).forEach { vector ->
                assertEquals(512, vector.size)
                assertTrue(vector.all(Float::isFinite))
                assertEquals(1.0, sqrt(vector.sumOf { it.toDouble() * it }), 0.0001)
            }
            val relatedSimilarity = cosine(german, english)
            val unrelatedSimilarity = cosine(german, unrelated)
            assertTrue("German and English equivalents should align", relatedSimilarity > 0.8)
            assertTrue("Unrelated food should be distant", unrelatedSimilarity < 0.4)
            assertTrue(relatedSimilarity > unrelatedSimilarity + 0.4)
        }
    }

    private fun cosine(left: List<Float>, right: List<Float>): Double =
        left.indices.sumOf { left[it].toDouble() * right[it] }
}
