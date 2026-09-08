package dev.sk2andy.materialbrowser.browser.systemwebview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWebViewMediaBridgeTest {
    @Test
    fun `valid state is bounded and parsed`() {
        val token = "a".repeat(32)
        val state = SystemWebViewMediaBridge.parse(
            """{"v":1,"token":"$token","active":true,"playing":true,"fullscreen":true,"title":"Video","position":2.5,"duration":8,"rate":1.25,"source":"https://example.com/v.mp4","videoWidth":1920,"videoHeight":1080,"video":true}""",
            token,
        )

        assertEquals(true, state?.isActive)
        assertEquals(true, state?.isPlaying)
        assertEquals(true, state?.isFullscreen)
        assertEquals(2_500L, state?.currentPositionMillis)
        assertEquals(8_000L, state?.durationMillis)
        assertTrue(state?.isVideo == true)
    }

    @Test
    fun `ended media is inactive`() {
        val token = "a".repeat(32)
        val state = SystemWebViewMediaBridge.parse(
            """{"v":1,"token":"$token","active":false,"playing":false}""",
            token,
        )

        assertEquals(false, state?.isActive)
        assertEquals(false, state?.isPlaying)
    }

    @Test
    fun `wrong token and oversized messages are rejected`() {
        val token = "a".repeat(32)

        assertNull(SystemWebViewMediaBridge.parse("{\"v\":1,\"token\":\"wrong\"}", token))
        assertNull(SystemWebViewMediaBridge.parse("x".repeat(4_097), token))
    }
}
