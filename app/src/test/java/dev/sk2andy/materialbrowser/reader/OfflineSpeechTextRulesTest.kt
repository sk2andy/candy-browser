package dev.sk2andy.materialbrowser.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineSpeechTextRulesTest {
    @Test
    fun `German umlauts use decomposed model input`() {
        assertEquals("<na>Gru\u0308ße aus Ko\u0308ln.</na>", OfflineSpeechTextRules.prepare("Grüße aus Köln"))
    }

    @Test
    fun `multilingual characters remain in original language`() {
        assertEquals("<na>Bonjour! 世界。</na>", OfflineSpeechTextRules.prepare("Bonjour! 世界。"))
    }

    @Test
    fun `emoji only content has no synthetic speech`() {
        assertEquals("", OfflineSpeechTextRules.prepare("😀 🎉"))
    }

    @Test
    fun `whitespace punctuation and quoted text normalize consistently`() {
        assertEquals("<na>\"Hallo\" - Welt!</na>", OfflineSpeechTextRules.prepare("  “Hallo”\n— Welt ! "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized chunk rejects before inference`() {
        OfflineSpeechTextRules.prepare("a".repeat(301))
    }
}
