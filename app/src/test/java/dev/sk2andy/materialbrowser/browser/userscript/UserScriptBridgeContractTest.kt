package dev.sk2andy.materialbrowser.browser.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

class UserScriptBridgeContractTest {
    @Test
    fun `parses bounded value mutations`() {
        assertEquals(
            UserScriptBridgeRequest.SetValue(1L, "theme", "\"dark\""),
            UserScriptBridgeContract.parse(
                JSONObject()
                    .put("type", "set-value")
                    .put("id", 1)
                    .put("key", "theme")
                    .put("value", "\"dark\"")
                    .toString(),
            ),
        )
        assertEquals(
            UserScriptBridgeRequest.DeleteValue(2L, "theme"),
            UserScriptBridgeContract.parse(
                """{"type":"delete-value","id":2,"key":"theme"}""",
            ),
        )
    }

    @Test
    fun `rejects unknown malformed and oversized messages`() {
        assertNull(UserScriptBridgeContract.parse("{}"))
        assertNull(UserScriptBridgeContract.parse("not-json"))
        assertNull(UserScriptBridgeContract.parse("""{"type":"read","id":1,"key":"theme"}"""))
        assertNull(UserScriptBridgeContract.parse("""{"type":"set-value","id":0,"key":"x","value":"1"}"""))
        assertNull(UserScriptBridgeContract.parse("""{"type":"set-value","id":1,"key":1,"value":"1"}"""))
        assertNull(
            UserScriptBridgeContract.parse(
                JSONObject()
                    .put("type", "set-value")
                    .put("id", 1)
                    .put(
                        "key",
                        "x".repeat(UserScriptBridgeContract.MAX_KEY_CHARS + 1),
                    )
                    .put("value", "1")
                    .toString(),
            ),
        )
        assertNull(UserScriptBridgeContract.parse("x".repeat(UserScriptBridgeContract.MAX_MESSAGE_BYTES + 1)))
    }

    @Test
    fun `parses bounded menu and open tab requests`() {
        assertEquals(
            UserScriptBridgeRequest.RegisterMenu("1", "Reveal passwords"),
            UserScriptBridgeContract.parse(
                """{"type":"register-menu","commandId":"1","caption":"Reveal passwords"}""",
            ),
        )
        assertEquals(
            UserScriptBridgeRequest.UnregisterMenu("1"),
            UserScriptBridgeContract.parse(
                """{"type":"unregister-menu","commandId":"1"}""",
            ),
        )
        assertEquals(
            UserScriptBridgeRequest.OpenTab("https://example.com/path", true),
            UserScriptBridgeContract.parse(
                """{"type":"open-tab","url":"https://example.com/path","active":true}""",
            ),
        )
    }

    @Test
    fun `rejects unsafe interaction request shapes`() {
        assertNull(
            UserScriptBridgeContract.parse(
                """{"type":"register-menu","commandId":"1","caption":""}""",
            ),
        )
        assertNull(
            UserScriptBridgeContract.parse(
                """{"type":"register-menu","commandId":"1","caption":"bad\ncaption"}""",
            ),
        )
        assertNull(
            UserScriptBridgeContract.parse(
                """{"type":"open-tab","url":"https://example.com","active":"yes"}""",
            ),
        )
        assertNull(
            UserScriptBridgeContract.parse(
                """{"type":"unregister-menu","commandId":"1","foreign":true}""",
            ),
        )
    }
}
