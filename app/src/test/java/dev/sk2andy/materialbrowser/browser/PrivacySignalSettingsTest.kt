package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySignalSettingsTest {
    @Test
    fun `privacy signals default to enabled request and document values`() {
        val settings = PrivacySignalSettings.Default

        assertEquals(
            mapOf("DNT" to "1", "Sec-GPC" to "1"),
            settings.requestHeaders(),
        )
        val script = PrivacySignalDocumentScript.installScript(settings)
        assertTrue(script.contains("\"doNotTrack\","))
        assertTrue(script.contains("\"1\""))
        assertTrue(script.contains("\"globalPrivacyControl\","))
        assertTrue(script.contains("true"))
        assertFalse(script.contains("get:"))
        assertTrue(script.contains("writable: false"))
    }

    @Test
    fun `disabled signals emit no request headers and neutral document values`() {
        val settings = PrivacySignalSettings(
            doNotTrackEnabled = false,
            globalPrivacyControlEnabled = false,
        )

        assertTrue(settings.requestHeaders().isEmpty())
        val script = PrivacySignalDocumentScript.installScript(settings)
        assertTrue(script.contains("null"))
        assertTrue(script.contains("false"))
        assertFalse(script.contains("DNT"))
        assertFalse(script.contains("Sec-GPC"))
    }
}
