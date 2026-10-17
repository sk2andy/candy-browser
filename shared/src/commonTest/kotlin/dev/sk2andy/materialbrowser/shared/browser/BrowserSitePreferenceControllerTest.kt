package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserSitePreferenceControllerTest {
    @Test
    fun `desktop mode follows exact host and regular profile`() {
        val controller = BrowserSitePreferenceController()
        val tab = tab("https://www.example.com/article")
        controller.setDesktop(tab, true)
        assertTrue(controller.isDesktopForAddress(tab, "https://WWW.EXAMPLE.COM:443/other"))
        assertFalse(controller.isDesktopForAddress(tab, "https://example.com/"))
        assertFalse(controller.isDesktopForAddress(tab, "https://unrelated.example/"))
        assertFalse(controller.isDesktop(tab.copy(profileId = "work")))
        assertEquals(listOf(BrowserDesktopPreference("candy", "www.example.com", true)), controller.persistentEntries())
    }

    @Test
    fun `private overrides stay inside live tab and never enter persistence`() {
        val controller = BrowserSitePreferenceController()
        val regular = tab("https://example.com/")
        val private = regular.copy(id = "private", isPrivate = true)
        controller.setDesktop(regular, true)
        assertFalse(controller.isDesktop(private))
        val before = controller.persistentEntries()
        controller.setDesktop(private, true)
        assertTrue(controller.isDesktop(private))
        assertFalse(controller.isDesktop(private.copy(id = "other-private")))
        assertEquals(before, controller.persistentEntries())
        controller.retainTabs(listOf(regular))
        assertFalse(controller.isDesktop(private))
        assertEquals(before, controller.persistentEntries())
    }

    @Test
    fun `restore validates host identities drops disabled records and caps profile entries`() {
        val controller = BrowserSitePreferenceController()
        controller.restore(
            listOf(
                BrowserDesktopPreference("candy", "EXAMPLE.COM", true),
                BrowserDesktopPreference("candy", "example.com/path", true),
                BrowserDesktopPreference("candy", "user:password@example.com", true),
                BrowserDesktopPreference("candy", "disabled.example", false),
                BrowserDesktopPreference("", "blank.example", true),
            ) + List(100) { BrowserDesktopPreference("work", "host$it.example", true) },
        )
        assertEquals(65, controller.persistentEntries().size)
        assertTrue(controller.isDesktop(tab("https://example.com/")))
        assertFalse(controller.isDesktop(tab("https://disabled.example/")))
        assertEquals(64, controller.persistentEntries().count { it.profileId == "work" })
    }

    @Test
    fun `unsupported pages cannot mutate desktop preference`() {
        val controller = BrowserSitePreferenceController()
        listOf("", "about:blank", "file:///private", "javascript:alert(1)").forEach { address ->
            val tab = tab(address)
            assertFalse(controller.canToggle(tab))
            controller.setDesktop(tab, true)
        }
        assertTrue(controller.persistentEntries().isEmpty())
    }

    @Test
    fun `site host retention keeps Android disable and eviction semantics`() {
        assertEquals(
            linkedSetOf("first", "latest"),
            BrowserSitePreferenceRules.withEnabledHost(listOf("first", "middle", "first"), "latest", true, 2),
        )
        assertEquals(
            linkedSetOf("first"),
            BrowserSitePreferenceRules.withEnabledHost(listOf("first", "middle", "first"), "middle", false, 0),
        )
        assertTrue(BrowserSitePreferenceRules.withEnabledHost(listOf("first"), "latest", true, 0).isEmpty())
    }

    private fun tab(address: String): BrowserTabState = BrowserTabState(
        id = "regular", address = address, title = "Example",
    )
}
