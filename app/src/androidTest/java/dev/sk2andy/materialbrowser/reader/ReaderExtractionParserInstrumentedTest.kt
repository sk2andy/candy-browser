package dev.sk2andy.materialbrowser.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderExtractionParserInstrumentedTest {
    @Test
    fun webViewEncodedJsonBecomesSanitizedDocument() {
        val paragraph = "Readable local article text ".repeat(5)
        val payload = JSONObject()
            .put("title", "Local story")
            .put("sourceUrl", "https://example.com/story")
            .put("siteName", "Example")
            .put(
                "blocks",
                org.json.JSONArray()
                    .put(
                        JSONObject()
                            .put("kind", "heading")
                            .put("level", 2)
                            .put("text", "Article heading"),
                    )
                    .put(
                        JSONObject()
                            .put("kind", "paragraph")
                            .put("text", paragraph)
                            .put(
                                "links",
                                org.json.JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("label", "Safe")
                                            .put("url", "https://example.com/more"),
                                    )
                                    .put(
                                        JSONObject()
                                            .put("label", "Unsafe")
                                            .put("url", "javascript:alert(1)"),
                                    ),
                            ),
                    )
            )
            .toString()
        val webViewResult = JSONObject.quote(payload)

        val result = ReaderExtractionParser.parse(webViewResult)

        assertTrue(result is ReaderExtractionResult.Success)
        val document = (result as ReaderExtractionResult.Success).document
        assertEquals("Local story", document.title)
        assertEquals(listOf("https://example.com/more"), document.blocks.last().links.map { it.url })
    }
}
