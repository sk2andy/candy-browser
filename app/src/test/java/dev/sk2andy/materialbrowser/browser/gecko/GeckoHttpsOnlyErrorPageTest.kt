package dev.sk2andy.materialbrowser.browser.gecko

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoHttpsOnlyErrorPageTest {
    @Test
    fun `warning only displays host and escapes localized markup`() {
        val html = GeckoHttpsOnlyErrorPage.html(
            url = "http://user:secret@example.com/path?token=private#fragment",
            strings = strings.copy(title = "HTTPS <unsafe> & \"quoted\"", language = "de\" dir=\"rtl"),
        )

        assertTrue(html.contains("<bdi>example.com</bdi>"))
        assertTrue(html.contains("HTTPS &lt;unsafe&gt; &amp; &quot;quoted&quot;"))
        assertTrue(html.contains("lang=\"de&quot; dir=&quot;rtl\""))
        assertFalse(html.contains("secret"))
        assertFalse(html.contains("token=private"))
        assertFalse(html.contains("<unsafe>"))
    }

    @Test
    fun `native exception is explicit and never disables HTTPS or certificates globally`() {
        val html = GeckoHttpsOnlyErrorPage.html("http://example.com", strings)

        assertTrue(html.contains("aria-expanded=\"false\" aria-controls=\"http-options\""))
        assertTrue(html.contains("id=\"http-options\" hidden"))
        assertTrue(html.contains("document.reloadWithHttpsOnlyException()"))
        assertTrue(html.contains("alert('${GeckoHttpsOnlyErrorPage.BACK_ACTION_MESSAGE}')"))
        assertTrue(html.contains("alert('${GeckoHttpsOnlyErrorPage.RETRY_ACTION_MESSAGE}')"))
        assertFalse(html.contains("addCertException"))
        assertFalse(html.contains("fetch("))
        assertFalse(html.contains("<img"))
        assertFalse(html.contains("<iframe"))
        assertTrue(html.contains("default-src 'none'"))
    }

    @Test
    fun `back delegates to native history and data URI round trips Unicode`() {
        val localized = strings.copy(title = "Keine sichere Verbindung verfügbar")
        val html = GeckoHttpsOnlyErrorPage.html("http://example.com", localized)
        val uri = GeckoHttpsOnlyErrorPage.uri("http://example.com", localized)

        assertTrue(html.contains(GeckoHttpsOnlyErrorPage.BACK_ACTION_MESSAGE))
        assertFalse(html.contains("history.length"))
        assertTrue(uri.startsWith("data:text/html;charset=utf-8;base64,"))
        assertEquals(html, Base64.getDecoder().decode(uri.substringAfter(',')).toString(Charsets.UTF_8))
    }

    @Test
    fun `malformed URL never enters error page markup or script`() {
        val html = GeckoHttpsOnlyErrorPage.html(
            url = "http://<script>alert('private')</script>",
            strings = strings,
        )

        assertTrue(html.contains("<bdi></bdi>"))
        assertFalse(html.contains("alert('private')"))
    }

    private val strings = GeckoHttpsOnlyErrorPageStrings(
        language = "en",
        title = "No secure connection available",
        protection = "HTTPS protection",
        explanation = "An HTTP connection would be unencrypted.",
        risk = "Others could read or change your data.",
        back = "Go back",
        retry = "Try HTTPS again",
        options = "More options",
        exception = "Only continue if you trust this website.",
        openHttp = "Temporarily open over HTTP",
        lifetime = "Exception during the current browser session.",
        unavailable = "The exception could not be applied.",
    )
}
