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
    fun `empty and out of range offsets are safe`() {
        assertNull(OfflineSpeechRules.nextChunk("   ", -1))
        assertNull(OfflineSpeechRules.nextChunk("text", 100))
        assertEquals("text", OfflineSpeechRules.nextChunk("text", -20)?.text)
    }
}
