package dev.sk2andy.materialbrowser.data.sync

import dev.sk2andy.materialbrowser.sync.SyncCache
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDescriptor
import dev.sk2andy.materialbrowser.sync.SyncEncryptedChange
import dev.sk2andy.materialbrowser.sync.SyncPendingMutation
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncStateCodecTest {
    @Test
    fun `cache round trip preserves exact encrypted outbox attempt`() {
        val mutation = SyncPendingMutation.Navigate(
            mutationId = "logical-1",
            targetDeviceId = "device-1",
            candyId = "tab-1",
            title = "Changed",
            url = "https://example.com/changed",
        )
        val prepared = SyncEncryptedChange(
            changeId = "attempt-1",
            writerDeviceId = "writer-1",
            targetDeviceId = "device-1",
            baseRevision = 7,
            revision = 8,
            nonce = "AAECAwQFBgcICQoL",
            ciphertext = "AAAAAAAAAAAAAAAAAAAAAA",
        )
        val cache = SyncCache(
            cursor = "epoch:7",
            profiles = mapOf("device-1" to profile()),
            pendingMutations = listOf(mutation),
            preparedWrites = mapOf(mutation.mutationId to prepared),
        )
        assertEquals(cache, SyncStateCodec.decodeCache(SyncStateCodec.encodeCache(cache)))
    }

    @Test
    fun `cache rejects unknown fields`() {
        val encoded = SyncStateCodec.encodeCache(SyncCache("", emptyMap())).toString(Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) {
            SyncStateCodec.decodeCache(encoded.replaceFirst("{", "{\"passphrase\":\"leak\",").toByteArray())
        }
    }

    private fun profile() = SyncProfile(
        deviceId = "device-1",
        displayName = "Phone",
        icon = SyncDeviceIconDescriptor("phone", 42),
        revision = 7,
        tabs = listOf(SyncTab("tab-1", 0, 0, null, true, false, "Tab", "https://example.com/")),
        lastSeenAt = "2026-09-02T10:00:00Z",
    )
}
