package dev.sk2andy.materialbrowser.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSpeechLanguageRulesTest {
    @Test
    fun `empty short and numeric content remains unknown`() {
        assertNull(ReaderSpeechLanguageRules.sample(""))
        assertNull(ReaderSpeechLanguageRules.sample("Hallo Welt"))
        assertNull(ReaderSpeechLanguageRules.sample("1234567890 ".repeat(1_000)))
    }

    @Test
    fun `short article retains its original language clues`() {
        val text = "Die neue Bibliothek öffnet am kommenden Montag ihre Türen für alle Besucher."
        assertEquals(text, ReaderSpeechLanguageRules.sample(text))
    }

    @Test
    fun `large articles sample beginning middle and end within fixed bound`() {
        val sample = requireNotNull(ReaderSpeechLanguageRules.sample(
            "a".repeat(10_000) + "b".repeat(10_000) + "c".repeat(10_000)
        ))
        assertEquals(9_002, sample.length)
        assertTrue(sample.startsWith("a".repeat(3_000) + "\n"))
        assertTrue(sample.contains("\n" + "b".repeat(3_000) + "\n"))
        assertTrue(sample.endsWith("c".repeat(3_000)))
    }
}
