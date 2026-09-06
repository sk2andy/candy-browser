package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinkPreviewRulesTest {
    private val profiles = listOf(
        BrowserProfile(id = "default", emoji = "🍬"),
        BrowserProfile(id = "work", emoji = "💼"),
    )

    @Test
    fun `target profile honors valid requested profile`() {
        assertEquals(
            "work",
            ExternalLinkPreviewRules.targetProfileId(
                profiles = profiles,
                profilesEnabled = true,
                requestedProfileId = "work",
                activeProfileId = "default",
            ),
        )
    }

    @Test
    fun `target profile falls back to active profile for stale request`() {
        assertEquals(
            "default",
            ExternalLinkPreviewRules.targetProfileId(
                profiles = profiles,
                profilesEnabled = true,
                requestedProfileId = "missing",
                activeProfileId = "default",
            ),
        )
    }

    @Test
    fun `disabled profiles always use first profile`() {
        assertEquals(
            "default",
            ExternalLinkPreviewRules.targetProfileId(
                profiles = profiles,
                profilesEnabled = false,
                requestedProfileId = "work",
                activeProfileId = "work",
            ),
        )
    }

    @Test
    fun `target profile is absent without profiles`() {
        assertNull(
            ExternalLinkPreviewRules.targetProfileId(
                profiles = emptyList(),
                profilesEnabled = true,
                requestedProfileId = null,
                activeProfileId = "default",
            ),
        )
    }

    @Test
    fun `current url accepts only normalized web urls`() {
        assertEquals(
            "https://example.com/path",
            ExternalLinkPreviewRules.safeCurrentUrl(" https://example.com/path "),
        )
        assertNull(ExternalLinkPreviewRules.safeCurrentUrl("javascript:alert(1)"))
        assertNull(ExternalLinkPreviewRules.safeCurrentUrl("file:///tmp/page.html"))
    }

    @Test
    fun `runtime identity rejects stale session and generation callbacks`() {
        val state = ExternalLinkPreviewState(
            sessionId = 7,
            generation = 3,
            currentUrl = "https://example.com/",
            targetProfileId = "default",
        )

        assertTrue(ExternalLinkPreviewRules.isCurrent(state, sessionId = 7, generation = 3))
        assertFalse(ExternalLinkPreviewRules.isCurrent(state, sessionId = 8, generation = 3))
        assertFalse(ExternalLinkPreviewRules.isCurrent(state, sessionId = 7, generation = 4))
        assertFalse(ExternalLinkPreviewRules.isCurrent(null, sessionId = 7, generation = 3))
    }
}
