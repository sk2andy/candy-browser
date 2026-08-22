package dev.sk2andy.materialbrowser.browser.userscript

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScriptApiTest {
    @Test
    fun `bootstrap exposes only granted local APIs`() {
        val source = UserScriptApi.bootstrap(
            script = script(
                """
                // @grant GM_info
                // @grant GM_addStyle
                // @grant GM_getValue
                // @grant GM_setValue
                """.trimIndent(),
            ),
            encodedValues = mapOf("theme" to "\"dark\""),
        )

        assertTrue(source.contains("GM_info"))
        assertTrue(source.contains("GM_addStyle"))
        assertTrue(source.contains("GM_getValue"))
        assertTrue(source.contains("GM_setValue"))
        assertTrue(source.contains(UserScriptBridgeContract.BRIDGE_NAME))
        assertTrue(source.contains("\\\"dark\\\""))
        assertFalse(source.contains("GM_deleteValue"))
        assertFalse(source.contains("GM_xmlhttpRequest"))
    }

    @Test
    fun `grant none keeps metadata without native bridge`() {
        val source = UserScriptApi.bootstrap(script("// @grant none"), emptyMap())

        assertTrue(source.contains("GM_info"))
        assertTrue(source.contains("Object.freeze"))
        assertFalse(source.contains(UserScriptBridgeContract.BRIDGE_NAME))
    }

    @Test
    fun `resource grants expose legacy and promise apis with bundled data`() {
        val parsed = script(
            """
            // @resource greeting https://cdn.example/greeting.txt
            // @grant GM_getResourceText
            // @grant GM.getResourceUrl
            """.trimIndent(),
        )
        val resolved = parsed.copy(
            resources = parsed.resources.map { resource ->
                resource.copy(
                    encodedContent = "SGFsbG8=",
                    mimeType = "text/plain",
                )
            },
        )
        val source = UserScriptApi.bootstrap(resolved, emptyMap())

        assertTrue(source.contains("GM_getResourceText"))
        assertTrue(source.contains("GM_getResourceURL"))
        assertTrue(source.contains("getResourceText"))
        assertTrue(source.contains("getResourceUrl"))
        assertTrue(source.contains("SGFsbG8="))
        assertTrue(source.contains("data:${'$'}{resource.mimeType};base64,"))
        assertFalse(source.contains(UserScriptBridgeContract.BRIDGE_NAME))
    }

    @Test
    fun `menu and open tab APIs are exposed only when granted`() {
        val source = UserScriptApi.bootstrap(
            script = script(
                """
                // @grant GM_registerMenuCommand
                // @grant GM_unregisterMenuCommand
                // @grant GM_openInTab
                """.trimIndent(),
            ),
            encodedValues = emptyMap(),
        )

        assertTrue(source.contains("GM_registerMenuCommand"))
        assertTrue(source.contains("GM_unregisterMenuCommand"))
        assertTrue(source.contains("GM_openInTab"))
        assertTrue(source.contains("menu-invoke"))
        assertTrue(source.contains("new URL(String(url), location.href)"))
        assertFalse(source.contains("GM_setValue"))
    }

    private fun script(grants: String): UserScript {
        val source = """
            // ==UserScript==
            // @name API test
            // @match https://example.com/*
            $grants
            // ==/UserScript==
            window.ran = true;
        """.trimIndent()
        return (UserScriptParser.parse("api-test", source) as UserScriptParseResult.Accepted).script
    }
}
