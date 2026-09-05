package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserChromeControllerTest {
    @Test
    fun `reload action follows loading state`() {
        val controller = BrowserChromeController()

        assertEquals(BrowserChromeAction.Reload, controller.reloadAction())
        controller.navigationStarted()
        assertEquals(BrowserChromeAction.Stop, controller.reloadAction())
    }

    @Test
    fun `finished navigation publishes canonical browser chrome`() {
        val controller = BrowserChromeController()

        val state = controller.navigationFinished(
            address = "https://mozilla.org/",
            pageTitle = "Mozilla",
            canGoBack = true,
            canGoForward = false,
        )

        assertEquals("https://mozilla.org/", state.address)
        assertEquals("Mozilla", state.pageTitle)
        assertTrue(state.canGoBack)
        assertFalse(state.canGoForward)
        assertFalse(state.isLoading)
    }
}
