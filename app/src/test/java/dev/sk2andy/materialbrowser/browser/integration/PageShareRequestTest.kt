package dev.sk2andy.materialbrowser.browser.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageShareRequestTest {
    @Test
    fun createsRequestForNormalizedWebUrl() {
        val request = PageShareRequest.create(
            url = " https://example.com/article ",
            title = " Example article ",
        )

        assertEquals("https://example.com/article", request?.url)
        assertEquals("Example article", request?.title)
    }

    @Test
    fun acceptsBlankTitleAndRejectsNonWebUrl() {
        val request = PageShareRequest.create(
            url = "https://example.com",
            title = "   ",
        )

        assertEquals("", request?.title)
        assertNull(PageShareRequest.create(url = "about:blank", title = "New tab"))
    }
}
