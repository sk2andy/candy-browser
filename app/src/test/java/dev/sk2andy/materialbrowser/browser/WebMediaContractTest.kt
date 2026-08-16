package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class WebMediaContractTest {
    @Test
    fun parsesBoundedVideoState() {
        val payload = WebMediaContract.parse(
            """
            {
              "v":1,
              "bridgeToken":"native_token",
              "event":"state",
              "documentId":"doc_1",
              "mediaId":"m1",
              "kind":"video",
              "paused":false,
              "ended":false,
              "currentTime":12.5,
              "duration":180.25,
              "playbackRate":1.25,
              "muted":false,
              "volume":0.8,
              "videoWidth":1920,
              "videoHeight":1080,
              "clientWidth":960,
              "clientHeight":540,
              "visibleRatio":0.9
            }
            """.trimIndent(),
            expectedBridgeToken = "native_token",
        )

        requireNotNull(payload)
        assertEquals(WebMediaKind.Video, payload.kind)
        assertTrue(payload.isPlaying)
        assertEquals(12_500L, payload.currentPositionMillis)
        assertEquals(180_250L, payload.durationMillis)
        assertEquals(0.9f, payload.visibleRatio)
    }

    @Test
    fun rejectsOversizedMalformedAndUnsafeMessages() {
        assertNull(
            WebMediaContract.parse(
                "x".repeat(WebMediaContract.MAX_MESSAGE_BYTES + 1),
                expectedBridgeToken = "token",
            ),
        )
        assertNull(WebMediaContract.parse("not-json", expectedBridgeToken = "token"))
        assertNull(
            WebMediaContract.parse(
                """{"v":1,"bridgeToken":"token","event":"state","documentId":"../bad","mediaId":"m1","kind":"video"}""",
                expectedBridgeToken = "token",
            ),
        )
        assertNull(
            WebMediaContract.parse(
                """{"v":1,"bridgeToken":"forged","event":"document-gone","documentId":"doc"}""",
                expectedBridgeToken = "native",
            ),
        )
    }

    @Test
    fun documentGoneNeedsOnlyDocumentIdentity() {
        val payload = WebMediaContract.parse(
            """{"v":1,"bridgeToken":"token","event":"document-gone","documentId":"document42"}""",
            expectedBridgeToken = "token",
        )

        requireNotNull(payload)
        assertEquals(WebMediaEvent.DocumentGone, payload.event)
        assertNull(payload.mediaId)
        assertTrue(payload.ended)
    }

    @Test
    fun privateMediaNeverBecomesExternalOrSystemSession() {
        val state = videoState()

        assertFalse(WebMediaRules.isExternalPresentationEligible(state, isPrivate = true))
        assertFalse(WebMediaRules.isSystemSessionEligible(state, isPrivate = true))
        assertTrue(WebMediaRules.isExternalPresentationEligible(state, isPrivate = false))
        assertTrue(WebMediaRules.isSystemSessionEligible(state, isPrivate = false))
    }

    @Test
    fun candidateScorePrefersPresentedThenPlayingThenSelected() {
        val playing = payload(paused = false)
        val paused = payload(paused = true)

        val presented = WebMediaRules.score(
            payload = paused,
            isSelectedTab = false,
            isPresented = true,
        )
        val background = WebMediaRules.score(
            payload = playing,
            isSelectedTab = false,
            isPresented = false,
        )
        val selected = WebMediaRules.score(
            payload = paused,
            isSelectedTab = true,
            isPresented = false,
        )

        assertTrue(presented > background)
        assertTrue(background > selected)
    }

    @Test
    fun nativeCommandsCarryOnlyValidatedTargetAndValue() {
        val command = JSONObject(WebMediaContract.seekCommand("doc", "m1", -200))

        assertEquals(1, command.getInt("v"))
        assertEquals("seek-to", command.getString("command"))
        assertEquals("doc", command.getString("documentId"))
        assertEquals("m1", command.getString("mediaId"))
        assertEquals(0.0, command.getDouble("position"), 0.0)

        val bounded = JSONObject(WebMediaContract.seekCommand("doc", "m1", Long.MAX_VALUE))
        assertEquals(604_800.0, bounded.getDouble("position"), 0.0)

        val keepPlaying = JSONObject(
            WebMediaContract.command(WebMediaCommand.KeepPlaying, "doc", "m1"),
        )
        assertEquals("keep-playing", keepPlaying.getString("command"))
    }

    private fun payload(paused: Boolean): WebMediaPayload = WebMediaPayload(
        event = WebMediaEvent.State,
        bridgeToken = "token",
        documentId = "doc",
        mediaId = "media",
        kind = WebMediaKind.Video,
        paused = paused,
        ended = false,
        currentPositionMillis = 1_000,
        durationMillis = 2_000,
        playbackRate = 1f,
        muted = false,
        volume = 1f,
        videoWidth = 1920,
        videoHeight = 1080,
        clientWidth = 960,
        clientHeight = 540,
        visibleRatio = 1f,
    )

    private fun videoState(): WebMediaState = WebMediaState(
        tabId = "tab",
        title = "Video",
        origin = "example.com",
        kind = WebMediaKind.Video,
        isPlaying = true,
        currentPositionMillis = 1_000,
        durationMillis = 2_000,
        playbackRate = 1f,
        muted = false,
        volume = 1f,
        videoWidth = 1920,
        videoHeight = 1080,
        clientWidth = 960,
        clientHeight = 540,
        visibleRatio = 1f,
    )
}
