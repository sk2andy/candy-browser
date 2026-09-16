package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalAppLinkHandlingTest {
    @Test
    fun `stable ids round trip for every handling mode`() {
        ExternalAppLinkHandling.entries.forEach { handling ->
            assertEquals(handling, ExternalAppLinkHandling.fromStableId(handling.stableId))
        }
    }

    @Test
    fun `missing and unknown ids keep automatic handling`() {
        listOf(null, "unknown").forEach { value ->
            assertEquals(
                ExternalAppLinkHandling.Automatic,
                ExternalAppLinkHandling.fromStableId(value),
            )
        }
    }
}
