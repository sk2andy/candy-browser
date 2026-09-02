package dev.sk2andy.materialbrowser.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncConnectionRulesTest {
    private val catalog = SyncDeviceIconCatalog(
        listOf(SyncDeviceIconDefinition("phone", "📱", "Phone")),
    )

    @Test
    fun `normalization trims existing local profile identity`() {
        val normalized = SyncConnectionRules.normalize(
            settings(localProfileId = "  personal  "),
            catalog,
        )

        assertEquals("personal", normalized?.localProfileId)
    }

    @Test
    fun `normalization rejects missing or oversized local profile identity`() {
        assertNull(SyncConnectionRules.normalize(settings(localProfileId = "   "), catalog))
        assertNull(SyncConnectionRules.normalize(settings(localProfileId = "p".repeat(129)), catalog))
    }

    private fun settings(localProfileId: String) = SyncConnectionSettings(
        endpoint = "https://sync.example/",
        username = "candy",
        deviceName = "Phone",
        iconCatalogId = "phone",
        iconAccentHue = 312,
        localProfileId = localProfileId,
    )
}
