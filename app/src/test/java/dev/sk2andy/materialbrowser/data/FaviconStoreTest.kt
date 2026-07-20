package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaviconStoreTest {
    @Test
    fun createsStableFileNameForTabUuid() {
        assertEquals(
            "123e4567-e89b-12d3-a456-426614174000.png",
            faviconFileName("123e4567-e89b-12d3-a456-426614174000"),
        )
    }

    @Test
    fun rejectsInvalidTabIdInsteadOfCreatingUnsafePath() {
        assertNull(faviconFileName("../../shared_prefs/browser_session"))
        assertNull(faviconFileName("not-a-uuid"))
    }
}
