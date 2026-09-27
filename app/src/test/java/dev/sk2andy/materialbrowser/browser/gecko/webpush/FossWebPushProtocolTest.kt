package dev.sk2andy.materialbrowser.browser.gecko.webpush

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FossWebPushProtocolTest {
    @Test
    fun `first hello omits persistent identifier and subsequent hello restores channels`() {
        val first = Json.parseToJsonElement(FossWebPushProtocol.hello(null, emptyList())) as JsonObject
        assertFalse("uaid" in first)
        assertEquals(JsonPrimitive(true), first["use_webpush"])

        val next = Json.parseToJsonElement(
            FossWebPushProtocol.hello(
                uaid = "0123456789abcdef0123456789abcdef",
                channelIds = listOf("11111111-2222-3333-4444-555555555555"),
            ),
        ) as JsonObject
        assertEquals(JsonPrimitive("0123456789abcdef0123456789abcdef"), next["uaid"])
        assertEquals(1, (next["channelIDs"] as JsonArray).size)
    }

    @Test
    fun `register sends padded VAPID key and acknowledgement keeps opaque version`() {
        val key = ByteArray(65) { it.toByte() }
        val request = Json.parseToJsonElement(
            FossWebPushProtocol.register("11111111-2222-3333-4444-555555555555", key),
        ) as JsonObject
        assertEquals(
            Base64.getUrlEncoder().encodeToString(key),
            (request["key"] as JsonPrimitive).content,
        )

        val ack = Json.parseToJsonElement(
            FossWebPushProtocol.acknowledge(
                "11111111-2222-3333-4444-555555555555",
                JsonPrimitive("opaque:version"),
            ),
        ) as JsonObject
        val update = (ack["updates"] as JsonArray).single() as JsonObject
        assertEquals(JsonPrimitive("opaque:version"), update["version"])
        assertEquals(JsonPrimitive(100), update["code"])
    }

    @Test
    fun `notification parser preserves headers and rejects oversized data`() {
        val message = FossWebPushProtocol.decode(
            """{"messageType":"notification","channelID":"11111111-2222-3333-4444-555555555555","version":"v1","data":"AQID","headers":{"encoding":"aes128gcm"}}""",
        ) as FossWebPushServerMessage.Notification
        assertEquals("v1", (message.version as JsonPrimitive).content)
        assertEquals("AQID", message.data)
        assertEquals("aes128gcm", message.headers["encoding"])
        assertNull(FossWebPushProtocol.decode("{"))
        assertNull(FossWebPushProtocol.decode("x".repeat(128 * 1_024 + 1)))
    }

    @Test
    fun `private Gecko origin attributes never qualify for persistence`() {
        assertTrue(FossWebPushScopeRules.isPersistentScope("https://example.com/sw/"))
        assertTrue(FossWebPushScopeRules.isPersistentScope("https://example.com/sw/^userContextId=2"))
        assertFalse(FossWebPushScopeRules.isPersistentScope("https://example.com/sw/^privateBrowsingId=1"))
        assertFalse(FossWebPushScopeRules.isPersistentScope("https://example.com/sw/^userContextId=2&privateBrowsingId=2"))
        assertFalse(FossWebPushScopeRules.isPersistentScope("https://example.com/sw/", isPrivate = true))
        assertFalse(FossWebPushScopeRules.isPersistentScope("http://example.com/sw/"))
        assertFalse(FossWebPushScopeRules.isSecureEndpoint("http://push.example.com/endpoint"))
    }

    @Test
    fun `isolated profile matching requires exact Gecko context attribute`() {
        val owned = "https://example.com/sw/^geckoViewSessionContextId=gvctx616c706861"
        assertTrue(FossWebPushScopeRules.belongsToIsolatedProfile(owned, "alpha"))
        assertFalse(FossWebPushScopeRules.belongsToIsolatedProfile(owned, "alph"))
        assertFalse(FossWebPushScopeRules.belongsToIsolatedProfile(
            "https://example.com/sw/^geckoViewSessionContextId=gvctx616c70686100",
            "alpha",
        ))
        assertFalse(FossWebPushScopeRules.belongsToIsolatedProfile(
            "https://example.com/sw/^geckoViewSessionContextId=gvctx616c706861&geckoViewSessionContextId=gvctx616c706861",
            "alpha",
        ))
        assertFalse(FossWebPushScopeRules.belongsToIsolatedProfile("https://example.com/sw/", "alpha"))
    }
}
