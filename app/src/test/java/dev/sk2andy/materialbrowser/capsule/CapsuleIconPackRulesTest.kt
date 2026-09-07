package dev.sk2andy.materialbrowser.capsule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleIconPackRulesTest {
    @Test
    fun `entry creates readable deterministic label and searchable component terms`() {
        val entry = CapsuleIconPackRules.entry(
            packageName = "icons.pack",
            drawableName = "google_chrome_beta",
            components = listOf("ComponentInfo{com.chrome/com.google.MainActivity}"),
        )

        assertEquals("Google Chrome Beta", entry.label)
        assertEquals(
            listOf(entry),
            CapsuleIconPackRules.visibleEntries(listOf(entry), "chrome main"),
        )
        assertTrue(CapsuleIconPackRules.visibleEntries(listOf(entry), "firefox").isEmpty())
    }

    @Test
    fun `visible entries cap unfiltered and filtered results`() {
        val entries = List(CapsuleIconPackRules.MAX_VISIBLE_ENTRIES + 20) { index ->
            CapsuleIconPackRules.entry(
                packageName = "icons.pack",
                drawableName = "shared_icon_$index",
                components = emptyList(),
            )
        }

        assertEquals(
            CapsuleIconPackRules.MAX_VISIBLE_ENTRIES,
            CapsuleIconPackRules.visibleEntries(entries, "").size,
        )
        assertEquals(
            CapsuleIconPackRules.MAX_VISIBLE_ENTRIES,
            CapsuleIconPackRules.visibleEntries(entries, "shared").size,
        )
    }

    @Test
    fun `merge combines manual catalog icons with component mapping terms`() {
        val manual = CapsuleIconPackRules.entry(
            packageName = "icons.pack",
            drawableName = "shared_mail",
            components = emptyList(),
        )
        val mapping = CapsuleIconPackRules.entry(
            packageName = "icons.pack",
            drawableName = "shared_mail",
            components = listOf("ComponentInfo{com.mail/.InboxActivity}"),
        )
        val extra = CapsuleIconPackRules.entry(
            packageName = "icons.pack",
            drawableName = "web_browser",
            components = emptyList(),
        )

        val merged = CapsuleIconPackRules.mergeEntries(listOf(manual, mapping, extra))

        assertEquals(listOf("shared_mail", "web_browser"), merged.map { it.drawableName })
        assertEquals(
            listOf(merged.first()),
            CapsuleIconPackRules.visibleEntries(merged, "mail inbox"),
        )
    }
}
