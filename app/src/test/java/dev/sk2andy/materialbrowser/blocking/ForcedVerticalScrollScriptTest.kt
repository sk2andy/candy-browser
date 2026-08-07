package dev.sk2andy.materialbrowser.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForcedVerticalScrollScriptTest {
    @Test
    fun `empty host list produces no script`() {
        assertTrue(ForcedVerticalScrollScript.create(emptyList()).isEmpty())
    }

    @Test
    fun `script is exact-host top-frame-only and preserves horizontal scrolling`() {
        val script = ForcedVerticalScrollScript.create(listOf("News.Example", "news.example"))

        assertTrue(script.contains("const forcedHosts = [\"news.example\"]"))
        assertTrue(script.contains("if (window.top !== window) return"))
        assertTrue(script.contains("if (!forcedHosts.includes(host)) return"))
        assertFalse(script.contains("endsWith('.'"))
        assertFalse(script.contains("overflow-x"))
    }

    @Test
    fun `script forces only vertical lock properties and guards observer writes`() {
        val script = ForcedVerticalScrollScript.create(listOf("news.example"))

        assertTrue(script.contains("forceProperty(root, 'overflow-y', 'auto')"))
        assertTrue(script.contains("forceProperty(body, 'position', 'static')"))
        assertTrue(script.contains("forceProperty(body, 'top', 'auto')"))
        assertTrue(script.contains("getPropertyPriority(property) !== 'important'"))
        assertTrue(script.contains("attributeFilter: ['class', 'style']"))
        assertTrue(script.contains("childList: true"))
        assertTrue(script.contains("document.body === observedBody"))
        assertTrue(script.contains("bodyObserver?.disconnect()"))
        assertTrue(script.contains("if (applying || stopped) return"))
        assertTrue(script.contains("document.removeEventListener('readystatechange', startListener)"))
        assertTrue(ForcedVerticalScrollScript.cleanupScript.contains("?.()"))
    }
}
