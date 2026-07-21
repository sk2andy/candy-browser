package dev.sk2andy.materialbrowser.blocking

import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentBlockerScriptTest {
    @Test
    fun `embeds css as utf8 base64 instead of executable source`() {
        val css = "#cookie-ä { display: none } </style><script>bad()</script>"

        val script = ConsentBlockerScript.create(css.toByteArray())

        val encodedCss = Base64.getEncoder().encodeToString(css.toByteArray())
        assertTrue(script.contains(encodedCss))
        assertTrue(script.contains("new TextDecoder('utf-8')"))
        assertFalse(script.contains("<script>bad()</script>"))
    }

    @Test
    fun `script is idempotent per document`() {
        val script = ConsentBlockerScript.create("#banner {}".toByteArray())

        assertTrue(script.contains("document.getElementById(styleId)"))
        assertTrue(script.contains("material-browser-easylist-cookie-css"))
    }

    @Test
    fun `scroll cleanup requires a known hidden cmp`() {
        val script = ConsentBlockerScript.create("#banner {}".toByteArray())

        assertTrue(script.contains("document.querySelector"))
        assertTrue(script.contains("getComputedStyle(banner).display !== 'none'"))
        assertTrue(ConsentBlockerScript.cleanupScript.contains("__materialBrowserUnlockCookieScroll"))
    }

    @Test
    fun `document start work stays in top frame and observes late cmp locks`() {
        val script = ConsentBlockerScript.create("#banner {}".toByteArray())

        assertTrue(script.contains("window.top !== window"))
        assertTrue(script.contains("new MutationObserver"))
        assertTrue(script.contains("attributeFilter: ['class', 'style']"))
        assertTrue(ConsentBlockerScript.removalScript.contains(".disconnect()"))
    }
}
