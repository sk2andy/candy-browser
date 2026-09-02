package dev.sk2andy.materialbrowser.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.json.JSONObject

class SyncProtocolCodecTest {
    @Test
    fun `bootstrap requires exact versioned shape and immutable KDF parameters`() {
        val valid = """{
            "protocolVersion":1,"cryptoVersion":1,"workspaceId":"workspace_1",
            "serverEpoch":"epoch_1","initialized":false,
            "kdf":{"algorithm":"argon2id-v1","salt":"AAAAAAAAAAAAAAAAAAAAAA","memoryKiB":65536,"iterations":3,"parallelism":4,"keyBytes":32},
            "recoveryEnvelope":null
        }""".trimIndent()
        assertEquals("workspace_1", SyncProtocolCodec.decodeBootstrap(valid).workspaceId)
        assertThrows(IllegalArgumentException::class.java) {
            SyncProtocolCodec.decodeBootstrap(valid.replace("\"parallelism\":4", "\"parallelism\":1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncProtocolCodec.decodeBootstrap(valid.replace("\"recoveryEnvelope\":null", "\"recoveryEnvelope\":null,\"passphrase\":\"leak\""))
        }
    }

    @Test
    fun `tab payload rejects extra fields duplicates and internal URLs`() {
        val valid = """{
            "schemaVersion":1,"capturedAt":"2026-09-02T10:00:00Z","tabs":[
              {"candyId":"tab-1","windowId":0,"index":0,"groupId":null,"active":true,"pinned":false,"title":"Tab","url":"https://example.com/"}
            ]
        }""".trimIndent()
        assertEquals("tab-1", SyncProtocolCodec.decodeTabSnapshot(valid).tabs.single().candyId)
        assertEquals(
            "tab:desktop.1",
            SyncProtocolCodec.decodeTabSnapshot(valid.replace("tab-1", "tab:desktop.1")).tabs.single().candyId,
        )
        assertThrows(IllegalArgumentException::class.java) {
            SyncProtocolCodec.decodeTabSnapshot(valid.replace("\"title\":\"Tab\"", "\"title\":\"Tab\",\"secret\":true"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncProtocolCodec.decodeTabSnapshot(valid.replace("https://example.com/", "file:///secret"))
        }
        val duplicate = JSONObject(valid).also { root ->
            val tabs = root.getJSONArray("tabs")
            tabs.put(JSONObject(tabs.getJSONObject(0).toString()))
        }.toString()
        assertThrows(IllegalArgumentException::class.java) {
            SyncProtocolCodec.decodeTabSnapshot(duplicate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncProtocolCodec.decodeTabSnapshot("$valid {}")
        }
    }

    @Test
    fun `device icon accepts only shared descriptor shape`() {
        assertEquals(
            SyncDeviceIconDescriptor("phone", 42),
            SyncProtocolCodec.decodeDeviceIcon("""{"schemaVersion":1,"catalogId":"phone","accentHue":42}"""),
        )
        assertThrows(IllegalArgumentException::class.java) {
            SyncProtocolCodec.decodeDeviceIcon("""{"schemaVersion":1,"catalogId":"phone","accentHue":360}""")
        }
    }
}
