package dev.sk2andy.materialbrowser.recall

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecallExtractionParserTest {
    @Test
    fun `parser accepts matching bounded web payload`() {
        val payload = JSONObject()
            .put("sourceUrl", "https://example.com/page#fragment")
            .put("title", " Example ")
            .put("text", " readable   page text ")
            .toString()

        val result = RecallExtractionParser.parse(
            webViewResult = JSONObject.quote(payload),
            profileId = "personal",
            expectedUrl = "https://example.com/page",
            visitedAt = 4L,
        )

        assertEquals("https://example.com/page", result?.url)
        assertEquals("readable page text", result?.text)
    }

    @Test
    fun `parser rejects stale source malformed and oversized payloads`() {
        val payload = JSONObject()
            .put("sourceUrl", "https://other.example/")
            .put("title", "Other")
            .put("text", "text")
            .toString()

        assertNull(
            RecallExtractionParser.parse(
                JSONObject.quote(payload),
                "personal",
                "https://expected.example/",
                1L,
            ),
        )
        assertNull(RecallExtractionParser.parse("not-json", "personal", "https://example.com", 1L))
        assertNull(
            RecallExtractionParser.parse(
                "\"${"x".repeat(150_001)}\"",
                "personal",
                "https://example.com",
                1L,
            ),
        )
    }
}
