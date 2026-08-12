package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AtomicTabFilesTest {
    @Test
    fun createsStableNamesForEachTabArtifactType() {
        val tabId = "123e4567-e89b-12d3-a456-426614174000"

        assertEquals("$tabId.png", tabArtifactFileName(tabId, "png"))
        assertEquals("$tabId.webp", tabArtifactFileName(tabId, "webp"))
        assertEquals("$tabId.bin", tabArtifactFileName(tabId, "bin"))
    }

    @Test
    fun rejectsUnsafeTabIdsAndExtensions() {
        assertNull(tabArtifactFileName("../../shared_prefs/browser_session", "png"))
        assertNull(tabArtifactFileName("not-a-uuid", "png"))
        assertNull(
            tabArtifactFileName(
                "123e4567-e89b-12d3-a456-426614174000",
                "../shared_prefs",
            ),
        )
    }
}
