package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabPreviewStoreTest {
    @Test
    fun createsStableFileNameForTabUuid() {
        assertEquals(
            "123e4567-e89b-12d3-a456-426614174000.webp",
            previewFileName("123e4567-e89b-12d3-a456-426614174000"),
        )
    }

    @Test
    fun rejectsInvalidTabIdInsteadOfCreatingUnsafePath() {
        assertNull(previewFileName("../../shared_prefs/browser_session"))
        assertNull(previewFileName("not-a-uuid"))
    }
}
