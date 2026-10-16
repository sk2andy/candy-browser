package dev.sk2andy.materialbrowser.constellations

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ConstellationWordPieceTokenizerTest {
    private val vocabulary = listOf(
        "[PAD]", "[UNK]", "[CLS]", "[SEP]", "Hello", "hello", "Welt", "!", "Daten", "##schutz", "Äpfel", "中", "文",
    )
    private val tokenizer = ConstellationWordPieceTokenizer(vocabulary)

    @Test
    fun `cased German and English retain accents and separate punctuation`() {
        assertArrayEquals(longArrayOf(2, 4, 6, 7, 10, 5, 3), tokenizer.encode("Hello Welt! Äpfel hello"))
    }

    @Test
    fun `word pieces require complete match or become one unknown token`() {
        assertArrayEquals(longArrayOf(2, 8, 9, 1, 3), tokenizer.encode("Datenschutz Datenschutzamt"))
    }

    @Test
    fun `control characters disappear and unicode whitespace separates words`() {
        assertArrayEquals(longArrayOf(2, 4, 6, 7, 3), tokenizer.encode("Hel\u0000lo\u00a0Welt\t!"))
    }

    @Test
    fun `Chinese codepoints form separate tokens without spaces`() {
        assertArrayEquals(longArrayOf(2, 11, 12, 3), tokenizer.encode("中文"))
    }

    @Test
    fun `literal special tokens retain published tokenizer behavior`() {
        assertArrayEquals(longArrayOf(2, 4, 1, 6, 3), tokenizer.encode("Hello[UNK]Welt"))
    }

    @Test
    fun `input stops at model limit and preserves end token`() {
        val result = tokenizer.encode("Hello ".repeat(300))
        assertEquals(128, result.size)
        assertEquals(2L, result.first())
        assertEquals(3L, result.last())
        assertEquals(126, result.count { it == 4L })
    }

    @Test
    fun `oversized word becomes single unknown token`() {
        assertArrayEquals(longArrayOf(2, 1, 3), tokenizer.encode("a".repeat(101)))
    }

    @Test
    fun `empty input retains special tokens`() {
        assertArrayEquals(longArrayOf(2, 3), tokenizer.encode(""))
    }

    @Test
    fun `short content needs one window`() {
        val windows = tokenizer.encodeWindows("Hello", "Datenschutz Welt")
        assertEquals(1, windows.size)
        assertArrayEquals(longArrayOf(2, 4, 8, 9, 6, 3), windows.single())
    }

    @Test
    fun `long content samples beginning middle and end`() {
        val windows = tokenizer.encodeWindows("Hello", "Hello ".repeat(150) + "Welt ".repeat(150) + "Äpfel ".repeat(150))
        assertEquals(3, windows.size)
        assertEquals(128, windows[0].size)
        assertEquals(4L, windows[0][2])
        assertEquals(6L, windows[1][2])
        assertEquals(10L, windows[2][2])
        windows.forEach { window ->
            assertEquals(2L, window.first())
            assertEquals(4L, window[1])
            assertEquals(3L, window.last())
        }
    }

    @Test
    fun `long title leaves body room in every window`() {
        val windows = tokenizer.encodeWindows("Hello ".repeat(200), "Welt ".repeat(300))
        assertEquals(3, windows.size)
        windows.forEach { window ->
            assertEquals(128, window.size)
            assertEquals(24, window.count { it == 4L })
            assertEquals(102, window.count { it == 6L })
        }
    }
}
