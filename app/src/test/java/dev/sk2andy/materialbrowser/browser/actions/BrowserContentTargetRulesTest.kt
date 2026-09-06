package dev.sk2andy.materialbrowser.browser.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserContentTargetRulesTest {
    @Test
    fun `link context keeps only a safe HTTP link`() {
        assertEquals(
            WebContentTarget(linkUrl = "https://example.com/article"),
            BrowserContentTargetRules.resolve(
                kind = BrowserContentTargetKind.None,
                linkUrl = " https://example.com/article ",
                sourceUrl = null,
            ),
        )
        assertNull(
            BrowserContentTargetRules.resolve(
                kind = BrowserContentTargetKind.None,
                linkUrl = "javascript:alert(1)",
                sourceUrl = null,
            ),
        )
    }

    @Test
    fun `image link context preserves both independently normalized targets`() {
        assertEquals(
            WebContentTarget(
                linkUrl = "https://example.com/article",
                imageUrl = "https://cdn.example.com/cover.png",
            ),
            BrowserContentTargetRules.resolve(
                kind = BrowserContentTargetKind.Image,
                linkUrl = "https://example.com/article",
                sourceUrl = "https://cdn.example.com/cover.png",
            ),
        )
        assertEquals(
            WebContentTarget(imageUrl = "http://127.0.0.1/image.png"),
            BrowserContentTargetRules.resolve(
                kind = BrowserContentTargetKind.Image,
                linkUrl = "intent://unsafe",
                sourceUrl = "http://127.0.0.1/image.png",
            ),
        )
    }

    @Test
    fun `video and audio sources never become image actions`() {
        assertNull(
            BrowserContentTargetRules.resolve(
                kind = BrowserContentTargetKind.Video,
                linkUrl = null,
                sourceUrl = "https://cdn.example.com/movie.mp4",
            ),
        )
        assertEquals(
            WebContentTarget(linkUrl = "https://example.com/details"),
            BrowserContentTargetRules.resolve(
                kind = BrowserContentTargetKind.Audio,
                linkUrl = "https://example.com/details",
                sourceUrl = "https://cdn.example.com/audio.mp3",
            ),
        )
    }
}
