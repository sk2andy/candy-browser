package dev.sk2andy.materialbrowser.browser

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageTextExtractionTest {
    @Test
    fun `WebView result decodes into raw engine neutral JSON`() {
        val raw = payload().toString()

        assertEquals(raw, PageTextExtraction.fromJavascriptResult(JSONObject.quote(raw)))
    }

    @Test
    fun `malformed results and non HTTP sources are rejected`() {
        assertNull(PageTextExtraction.fromJavascriptResult(null))
        assertNull(PageTextExtraction.fromJavascriptResult("null"))
        assertNull(PageTextExtraction.fromJavascriptResult("{}"))
        assertNull(PageTextExtraction.fromJavascriptResult("not json"))
        assertNull(PageTextExtraction.validate(payload().put("sourceUrl", "file:///secret")))
        assertNull(PageTextExtraction.validate(payload().put("sourceUrl", "https://user:pass@example.test")))
    }

    @Test
    fun `only bounded string document fields cross extraction bridge`() {
        assertNull(PageTextExtraction.validate(payload().put("text", 42)))
        assertNull(PageTextExtraction.validate(payload().put("text", " ")))
        assertNull(PageTextExtraction.validate(payload().put("text", "x".repeat(64_001))))
        assertNull(PageTextExtraction.validate(payload().put("title", "x".repeat(1_025))))
        assertNull(PageTextExtraction.validate(payload().put("unexpected", "value")))
        val maximum = payload().put("text", "x".repeat(64_000))
        assertEquals(maximum.toString(), PageTextExtraction.validate(maximum))
    }

    private fun payload() = JSONObject()
        .put("sourceUrl", "https://example.test/article")
        .put("title", "Article")
        .put("text", "Visible article text")
}
