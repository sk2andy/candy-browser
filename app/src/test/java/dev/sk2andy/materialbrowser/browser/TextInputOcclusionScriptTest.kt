package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextInputOcclusionScriptTest {
    @Test
    fun `script uses fixed bounded editable and scroll checks`() {
        val script = TextInputOcclusionScript.javascript(
            BrowserViewportRect(
                leftFraction = 0.1f,
                topFraction = 0.8f,
                rightFraction = 0.9f,
                bottomFraction = 0.9f,
            ),
        )

        assertTrue(script.contains("textarea,input,[contenteditable]"))
        assertTrue(script.contains("textInputTypes"))
        assertTrue(script.contains("scrollHeight - viewportPageTop - viewportHeight > 1"))
        assertTrue(script.contains("visualViewport"))
        assertTrue(script.contains("element.shadowRoot"))
        assertTrue(script.contains("rootIndex < 64"))
        assertTrue(script.contains("visitedElements < 4096"))
        assertTrue(script.contains("visitedCandidates < 512"))
        assertTrue(script.contains("depth < 24"))
        assertFalse(script.contains("eval("))
        assertFalse(script.contains("document.write"))
    }
}
