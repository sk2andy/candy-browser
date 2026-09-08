package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeckoDefaultExtensionCatalogTest {
    @Test
    fun `catalog parser preserves bundled and remote delivery boundaries`() {
        val catalog = GeckoDefaultExtensionCatalog.parse(
            """
            {
              "schemaVersion": 1,
              "extensions": [
                {
                  "id": "bundled@example.com",
                  "name": "Bundled",
                  "version": "1.0",
                  "delivery": "bundled",
                  "installUri": "resource://android/assets/gecko_default_extensions/bundled.xpi",
                  "assetPath": "gecko_default_extensions/bundled.xpi",
                  "sha256": "${"a".repeat(64)}",
                  "size": 1024
                },
                {
                  "id": "remote@example.com",
                  "name": "Remote",
                  "version": "2.0",
                  "delivery": "remote",
                  "installUri": "https://addons.mozilla.org/firefox/downloads/file/2/remote-2.0.xpi",
                  "sha256": "${"b".repeat(64)}",
                  "size": 2048
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(GeckoDefaultExtensionDelivery.Bundled, catalog[0].delivery)
        assertEquals("gecko_default_extensions/bundled.xpi", catalog[0].assetPath)
        assertEquals(GeckoDefaultExtensionDelivery.Remote, catalog[1].delivery)
        assertNull(catalog[1].assetPath)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `catalog parser rejects floating remote URLs`() {
        GeckoDefaultExtensionCatalog.parse(
            """
            {
              "schemaVersion": 1,
              "extensions": [
                {
                  "id": "remote@example.com",
                  "name": "Remote",
                  "version": "2.0",
                  "delivery": "remote",
                  "installUri": "https://addons.mozilla.org/firefox/downloads/latest/remote/latest.xpi",
                  "sha256": "${"b".repeat(64)}",
                  "size": 2048
                }
              ]
            }
            """.trimIndent(),
        )
    }
}
