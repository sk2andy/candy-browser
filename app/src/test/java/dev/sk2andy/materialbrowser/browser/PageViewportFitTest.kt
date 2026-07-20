package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageViewportFitTest {
    @Test
    fun observerReadsViewportMetaAndTracksChangesWithoutMutatingPage() {
        val script = PageViewportFit.observerScript(navigationGeneration = 7)

        assertTrue(script.contains("const generation = 7"))
        assertTrue(script.contains("document.querySelectorAll('meta')"))
        assertTrue(script.contains("viewport-fit"))
        assertTrue(script.contains("cover"))
        assertTrue(script.contains("MutationObserver"))
        assertTrue(script.contains("attributeFilter: ['name', 'content']"))
        assertTrue(script.contains("window.${PageViewportFit.bridgeName}.update"))
        assertFalse(script.contains("appendChild"))
        assertFalse(script.contains("setAttribute"))
    }

    @Test
    fun onlyJavascriptTrueOptsPageIntoEdgeToEdge() {
        assertTrue(PageViewportFit.isCoverResult("true"))
        assertFalse(PageViewportFit.isCoverResult("false"))
        assertFalse(PageViewportFit.isCoverResult("null"))
        assertFalse(PageViewportFit.isCoverResult(null))
    }
}
