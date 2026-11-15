package dev.sk2andy.materialbrowser.data.sync

import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.SyncEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MozillaAccountWebChannelTest {
    @Test
    fun `status reply preserves channel command and request id`() {
        val request = requireNotNull(MozillaAccountWebChannelRules.parse(
            """{"id":"account_updates","message":{"command":"fxaccounts:fxa_status","messageId":"request-7"}}""",
        ))
        val response = MozillaAccountWebChannelRules.response(
            request,
            MozillaAccountWebChannelRules.status(listOf("tabs"), null),
        )
        assertEquals("account_updates", response.getString("id"))
        val message = response.getJSONObject("message")
        assertEquals("fxaccounts:fxa_status", message.getString("command"))
        assertEquals("request-7", message.getString("messageId"))
        val data = message.getJSONObject("data")
        assertTrue(data.isNull("signedInUser"))
        val capabilities = data.getJSONObject("capabilities")
        assertEquals("[\"tabs\"]", capabilities.getJSONArray("engines").toString())
        assertTrue(capabilities.getBoolean("can_link_account_uid"))
        val scopes = capabilities.getJSONObject("services").getJSONObject("sync").getJSONArray("scope")
        assertTrue((0 until scopes.length()).any { scopes.getString(it) == "https://identity.mozilla.com/apps/oldsync" })
        assertFalse(capabilities.has("pairingVersion"))
        assertFalse(capabilities.has("keys_optional"))
        assertFalse(capabilities.has("choose_what_to_sync"))
    }

    @Test
    fun `authenticated status preserves SDK account payload`() {
        val data = MozillaAccountWebChannelRules.status(
            listOf("tabs"),
            """{"uid":"account-1","email":"user@example.com","verified":true}""",
        )
        val user = data.getJSONObject("signedInUser")
        assertEquals("account-1", user.getString("uid"))
        assertEquals("user@example.com", user.getString("email"))
        assertTrue(user.getBoolean("verified"))
    }

    @Test
    fun `different account cannot be linked over an existing account`() {
        assertTrue(MozillaAccountWebChannelRules.canLinkAccount(JSONObject(), null, null))
        assertTrue(MozillaAccountWebChannelRules.canLinkAccount(
            JSONObject("""{"uid":"account-1","email":"new@example.com"}"""),
            "account-1",
            "old@example.com",
        ))
        assertFalse(MozillaAccountWebChannelRules.canLinkAccount(
            JSONObject("""{"uid":"account-2","email":"old@example.com"}"""),
            "account-1",
            "old@example.com",
        ))
        assertTrue(MozillaAccountWebChannelRules.canLinkAccount(
            JSONObject("""{"email":"old@example.com"}"""), "account-1", "old@example.com",
        ))
        assertFalse(MozillaAccountWebChannelRules.canLinkAccount(null, "account-1", "old@example.com"))
    }

    @Test
    fun `sign in and sign up retain action code state and declined engines`() {
        for ((action, type) in listOf("signin" to AuthType.Signin, "signup" to AuthType.Signup)) {
            val data = JSONObject()
                .put("action", action)
                .put("code", "authorization-code")
                .put("state", "sdk-state")
                .put("declinedSyncEngines", JSONArray(listOf("tabs")))
            val auth = requireNotNull(MozillaAccountWebChannelRules.authData(data))
            assertEquals(type, auth.authType)
            assertEquals("authorization-code", auth.code)
            assertEquals("sdk-state", auth.state)
            assertEquals(setOf(SyncEngine.Tabs), auth.declinedEngines)
        }
    }

    @Test
    fun `malformed auth payload never reaches SDK completion`() {
        val payloads = listOf(
            "{}",
            """{"action":"signin","code":"code"}""",
            """{"action":"signin","code":"code","state":""}""",
            """{"action":"signup","code":42,"state":"state"}""",
            """{"action":"signin","code":"code","state":"state","declinedSyncEngines":[42]}""",
        )
        payloads.forEach { assertNull(MozillaAccountWebChannelRules.authData(JSONObject(it))) }
    }

    @Test
    fun `wrong channel malformed envelope and oversized messages are rejected`() {
        val messages = listOf(
            "not-json",
            "[]",
            """{"id":"other","message":{"command":"fxaccounts:logout"}}""",
            """{"id":"account_updates","message":{"command":7}}""",
            """{"id":"account_updates","message":{"command":"fxaccounts:login","data":"token"}}""",
            """{"id":"account_updates","message":{"command":"fxaccounts:fxa_status","messageId":42}}""",
            " ".repeat(65_537),
        )
        messages.forEach { assertNull(MozillaAccountWebChannelRules.parse(it)) }
    }

    @Test
    fun `session token and password change payloads remain intact for SDK processing`() {
        for (command in listOf("fxaccounts:login", "fxaccounts:change_password")) {
            val request = requireNotNull(MozillaAccountWebChannelRules.parse(
                """{"id":"account_updates","message":{"command":"$command","data":{"uid":"account-1","sessionToken":"opaque-token","verified":false}}}""",
            ))
            assertEquals(command, request.command)
            assertEquals("", request.messageId)
            val data = requireNotNull(request.data)
            assertEquals("opaque-token", data.getString("sessionToken"))
            assertFalse(data.getBoolean("verified"))
        }
    }
}
