package dev.sk2andy.materialbrowser.sync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDeviceIconCatalogTest {
    @Test
    fun `shared catalog exposes Android profile icons exactly once`() {
        val root = File(System.getProperty("user.dir").orEmpty())
        val catalogFile = listOf(
            File(root, "sync/protocol/device-icons-v1.json"),
            File(root, "../sync/protocol/device-icons-v1.json"),
        ).first(File::isFile)
        val catalog = catalogFile.inputStream().use(SyncDeviceIconCatalog::decode)
        assertEquals(54, catalog.icons.size)
        assertTrue(catalog.contains("phone"))
        assertTrue(catalog.contains("computer"))
        assertTrue(catalog.icons.all { it.emoji.isNotBlank() && it.label.isNotBlank() })
    }
}
