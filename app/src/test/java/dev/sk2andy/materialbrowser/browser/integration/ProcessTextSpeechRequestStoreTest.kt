package dev.sk2andy.materialbrowser.browser.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessTextSpeechRequestStoreTest {
    @Test
    fun `speech tokens keep sanitized text once and expire after a minute`() {
        val requests = ProcessTextSpeechRequestStore()
        val token = requests.put(" \u0000Selected\ntext\tvalue\u0007 ", 100)!!
        assertEquals("Selected\ntext\tvalue", requests.take(token, 101))
        assertNull(requests.take(token, 102))
        assertNull(requests.take("unknown", 102))
        val expired = requests.put("private text", 200)!!
        assertNull(requests.take(expired, 60_201))
        val future = requests.put("private text", 100_000)!!
        assertNull(requests.take(future, 99_999))
    }

    @Test
    fun `speech token storage caps pending requests and rejects oversized selections`() {
        val requests = ProcessTextSpeechRequestStore()
        val tokens = List(9) { requests.put("selection $it", it.toLong())!! }
        assertNull(requests.take(tokens.first(), 10))
        assertEquals("selection 8", requests.take(tokens.last(), 10))
        assertNull(requests.put(" \n\t", 10))
        assertNull(requests.put("x".repeat(20_001), 10))
        val accepted = requests.put("x".repeat(20_000), 10)!!
        assertEquals(20_000, requests.take(accepted, 11)?.length)
    }

    @Test
    fun `new process cannot resolve previously issued private speech token`() {
        val token = ProcessTextSpeechRequestStore().put("private article", 100)!!
        assertNull(ProcessTextSpeechRequestStore().take(token, 101))
    }
}
