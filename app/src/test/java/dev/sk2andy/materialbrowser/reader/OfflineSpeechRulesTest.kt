package dev.sk2andy.materialbrowser.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSpeechRulesTest {
    @Test
    fun `sentence boundaries keep multilingual text intact`() {
        val text = "  Guten Tag! Hello. こんにちは。"
        val first = requireNotNull(OfflineSpeechRules.nextChunk(text, 0))
        assertEquals(2, first.start)
        assertEquals("Guten Tag!", first.text)
        val second = requireNotNull(OfflineSpeechRules.nextChunk(text, first.end))
        assertEquals("Hello.", second.text)
        assertEquals("こんにちは。", OfflineSpeechRules.nextChunk(text, second.end)?.text)
        assertNull(OfflineSpeechRules.nextChunk(text, text.length))
    }

    @Test
    fun `long text stays bounded without losing characters or splitting emoji`() {
        val text = "a".repeat(239) + "😀" + "b".repeat(800)
        var cursor = 0
        val result = StringBuilder()
        while (true) {
            val chunk = OfflineSpeechRules.nextChunk(text, cursor) ?: break
            assertTrue(chunk.end > cursor)
            assertTrue(chunk.text.length <= 240)
            assertFalse(chunk.text.last().isHighSurrogate())
            result.append(chunk.text)
            cursor = chunk.end
        }
        assertEquals(text, result.toString())
    }

    @Test
    fun `abbreviations stay with their sentence`() {
        listOf(
            "Dr. Müller liest z. B. Bücher.",
            "Prof. Smith reads, e.g. novels, i.e. fiction.",
            "Die Zahl beträgt ca. fünf bzw. sechs.",
        ).forEach { sentence ->
            assertEquals(sentence, OfflineSpeechRules.nextChunk("$sentence Danach.", 0)?.text)
        }
    }

    @Test
    fun `decimal dates and web addresses stay intact`() {
        listOf(
            "Am 12.10.2026 kostet es 3.50 Euro.",
            "Am 12. Oktober besucht J. Müller example.org.",
            "Siehe https://example.org/path?a=1&b=2 und weiter.",
        ).forEach { sentence ->
            assertEquals(sentence, OfflineSpeechRules.nextChunk("$sentence Danach.", 0)?.text)
        }
    }

    @Test
    fun `hard boundary remains bounded when space is exactly at limit`() {
        val text = "a".repeat(240) + " rest"
        val chunk = requireNotNull(OfflineSpeechRules.nextChunk(text, 0))
        assertEquals(240, chunk.text.length)
        assertEquals("rest", OfflineSpeechRules.nextChunk(text, chunk.end)?.text)
    }

    @Test
    fun `empty and out of range offsets are safe`() {
        assertNull(OfflineSpeechRules.nextChunk("   ", -1))
        assertNull(OfflineSpeechRules.nextChunk("text", 100))
        assertEquals("text", OfflineSpeechRules.nextChunk("text", -20)?.text)
    }
}
