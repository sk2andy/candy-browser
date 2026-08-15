package dev.sk2andy.materialbrowser.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForcedPageZoomScriptTest {
    @Test
    fun `empty host list produces no script`() {
        assertTrue(ForcedPageZoomScript.create(emptyList()).isEmpty())
    }

    @Test
    fun `script is exact-host and top-frame only`() {
        val script = ForcedPageZoomScript.create(listOf("News.Example", "news.example"))

        assertTrue(script.contains("const forcedHosts = [\"news.example\"]"))
        assertTrue(script.contains("if (window.top !== window) return"))
        assertTrue(script.contains("if (!forcedHosts.includes(host)) return"))
        assertFalse(script.contains("endsWith('.'"))
    }

    @Test
    fun `script replaces viewport zoom locks and watches later changes`() {
        val script = ForcedPageZoomScript.create(listOf("news.example"))

        assertTrue(script.contains("['user-scalable', 'minimum-scale', 'maximum-scale']"))
        assertTrue(script.contains("directives.push('user-scalable=yes', 'maximum-scale=10')"))
        assertTrue(script.contains(".replace(/\\s*=\\s*/g, '=')"))
        assertTrue(script.contains("attributeFilter: ['name', 'content']"))
        assertTrue(script.contains("childList: true"))
        assertTrue(script.contains("subtree: true"))
        assertTrue(script.contains("if (applying || stopped) return"))
        assertTrue(script.contains("document.removeEventListener('readystatechange', startListener)"))
        assertTrue(ForcedPageZoomScript.cleanupScript.contains("?.()"))
    }
}
