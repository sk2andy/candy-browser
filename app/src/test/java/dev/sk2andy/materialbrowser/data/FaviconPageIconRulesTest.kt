package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaviconPageIconRulesTest {
    @Test
    fun `requests only origin root without page path query or fragment`() {
        assertEquals("https://example.com/", FaviconPageIconRules.originPageUrl("https://example.com/private?q=secret#fragment"))
        assertNull(FaviconPageIconRules.originPageUrl("https://user@example.com/private"))
    }

    @Test
    fun `prefers largest declared icon and includes touch icons`() {
        val source = """
            <link rel="shortcut icon" href="/favicon.ico">
            <link rel="icon" href="/small.png" sizes="32x32">
            <link rel="apple-touch-icon" href="/touch.png">
            <link rel="ICON" href="/large.png" sizes="48x48 144x144">
        """.trimIndent()

        assertEquals(
            listOf("https://example.com/large.png", "https://example.com/touch.png", "https://example.com/small.png"),
            FaviconPageIconRules.candidates("https://example.com/page", source),
        )
    }

    @Test
    fun `rejects third party credentialed and non http icon sources`() {
        val source = """
            <link rel="icon" href="https://tracker.example/icon.png" sizes="512x512">
            <link rel="icon" href="http://example.com/icon.png" sizes="512x512">
            <link rel="icon" href="https://example.com:8443/icon.png" sizes="512x512">
            <link rel="icon" href="https://user@example.com/icon.png" sizes="512x512">
            <link rel="icon" href="data:image/png;base64,abc" sizes="512x512">
            <link rel="icon" href="/safe.png" sizes="144x144">
        """.trimIndent()

        assertEquals(listOf("https://example.com/safe.png"), FaviconPageIconRules.candidates("https://example.com/private", source))
    }

    @Test
    fun `ignores icon links inside comments scripts and styles`() {
        val source = """
            <!--<link rel="icon" href="/comment.png">-->
            <script>const icon = '<link rel="icon" href="/script.png">';</script>
            <style><link rel="icon" href="/style.png"></style>
            <link rel="icon" href="/safe.png">
        """.trimIndent()

        assertEquals(listOf("https://example.com/safe.png"), FaviconPageIconRules.candidates("https://example.com/", source))
    }

    @Test
    fun `deduplicates icon sources and bounds fetch candidates`() {
        val source = (1..10).joinToString("") { index ->
            """<link rel="icon" href="/$index.png" sizes="${index * 16}x${index * 16}">"""
        } + """<link rel="icon" href="/10.png" sizes="160x160">"""

        assertEquals(
            listOf("https://example.com/10.png", "https://example.com/9.png", "https://example.com/8.png"),
            FaviconPageIconRules.candidates("https://example.com/", source),
        )
    }
}
