package dev.sk2andy.materialbrowser.browser.cast

import dev.sk2andy.materialbrowser.browser.WebMediaKind
import dev.sk2andy.materialbrowser.browser.WebMediaState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastMediaRulesTest {
    @Test
    fun `direct selected regular video becomes cast source`() {
        val source = CastMediaRules.source(
            state = videoState(),
            isPrivate = false,
            isSelectedTab = true,
        )

        requireNotNull(source)
        assertEquals("https://media.example/video.m3u8?token=short", source.url)
        assertEquals("application/x-mpegurl", source.contentType)
        assertEquals("https://media.example/poster.jpg", source.posterUrl)
        assertEquals(1_000L, source.startPositionMillis)
    }

    @Test
    fun `file extension supplies missing content type`() {
        val source = CastMediaRules.source(
            state = videoState().copy(contentType = null, sourceUrl = "https://media.example/v.mp4"),
            isPrivate = false,
            isSelectedTab = true,
        )

        assertEquals("video/mp4", requireNotNull(source).contentType)
    }

    @Test
    fun `private stale blob and unsupported media never become cast source`() {
        assertNull(
            CastMediaRules.source(
                state = videoState(),
                isPrivate = true,
                isSelectedTab = true,
            ),
        )
        assertNull(
            CastMediaRules.source(
                state = videoState(),
                isPrivate = false,
                isSelectedTab = false,
            ),
        )
        assertNull(
            CastMediaRules.source(
                state = videoState().copy(sourceUrl = "blob:https://media.example/id"),
                isPrivate = false,
                isSelectedTab = true,
            ),
        )
        assertNull(
            CastMediaRules.source(
                state = videoState().copy(
                    sourceUrl = "https://media.example/video.bin",
                    contentType = "application/octet-stream",
                ),
                isPrivate = false,
                isSelectedTab = true,
            ),
        )
    }

    private fun videoState(): WebMediaState = WebMediaState(
        tabId = "tab",
        title = "Video",
        origin = "media.example",
        kind = WebMediaKind.Video,
        isPlaying = true,
        currentPositionMillis = 1_000,
        durationMillis = 20_000,
        playbackRate = 1f,
        muted = false,
        volume = 1f,
        videoWidth = 1920,
        videoHeight = 1080,
        clientWidth = 960,
        clientHeight = 540,
        visibleRatio = 1f,
        sourceUrl = "https://media.example/video.m3u8?token=short",
        contentType = "Application/X-MpegURL; codecs=avc1",
        posterUrl = "https://media.example/poster.jpg",
    )
}
