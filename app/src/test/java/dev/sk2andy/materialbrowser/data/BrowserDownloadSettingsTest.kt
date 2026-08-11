package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDownloadSettingsTest {
    @Test
    fun `defaults to built in without session sharing`() {
        val settings = BrowserDownloadSettings()

        assertEquals(DownloadManagerMode.BuiltIn, settings.managerMode)
        assertNull(settings.externalManagerId)
        assertFalse(settings.shareSessionDataWithOneDm)
    }

    @Test
    fun `external mode without valid target falls back to built in`() {
        val settings = BrowserDownloadSettings(
            managerMode = DownloadManagerMode.External,
            externalManagerId = "\n",
            shareSessionDataWithOneDm = true,
        ).normalized()

        assertEquals(DownloadManagerMode.BuiltIn, settings.managerMode)
        assertNull(settings.externalManagerId)
        assertTrue(settings.shareSessionDataWithOneDm)
    }

    @Test
    fun `unknown mode wire value falls back to built in`() {
        assertEquals(DownloadManagerMode.BuiltIn, DownloadManagerMode.fromStableId("future"))
    }
}
