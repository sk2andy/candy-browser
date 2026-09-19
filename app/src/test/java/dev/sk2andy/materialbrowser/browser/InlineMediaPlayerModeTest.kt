package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineMediaPlayerModeTest {
    @Test
    fun `stable ids round trip and unknown values use button fullscreen`() {
        InlineMediaPlayerMode.entries.forEach { mode ->
            assertEquals(mode, InlineMediaPlayerMode.fromStableId(mode.stableId))
        }

        assertEquals(
            InlineMediaPlayerMode.ButtonFullscreen,
            InlineMediaPlayerMode.fromStableId("future-mode"),
        )
        assertEquals(
            InlineMediaPlayerMode.ButtonFullscreen,
            InlineMediaPlayerMode.fromStableId(null),
        )
    }

    @Test
    fun `button modes expose only their requested presentation choices`() {
        assertTrue(
            InlineMediaPlayerModeRules.showsOpenButton(
                InlineMediaPlayerMode.ButtonFullscreen,
            ),
        )
        assertTrue(
            InlineMediaPlayerModeRules.buttonStartsFullscreen(
                InlineMediaPlayerMode.ButtonFullscreen,
            ),
        )
        assertTrue(
            InlineMediaPlayerModeRules.detectsInlineVideo(
                InlineMediaPlayerMode.ButtonFullscreen,
            ),
        )
        assertFalse(
            InlineMediaPlayerModeRules.supportsInlinePresentation(
                InlineMediaPlayerMode.ButtonFullscreen,
            ),
        )

        assertTrue(
            InlineMediaPlayerModeRules.showsOpenButton(
                InlineMediaPlayerMode.ButtonInlineAndFullscreen,
            ),
        )
        assertFalse(
            InlineMediaPlayerModeRules.buttonStartsFullscreen(
                InlineMediaPlayerMode.ButtonInlineAndFullscreen,
            ),
        )
        assertTrue(
            InlineMediaPlayerModeRules.supportsInlinePresentation(
                InlineMediaPlayerMode.ButtonInlineAndFullscreen,
            ),
        )
    }

    @Test
    fun `automatic and fullscreen replacement modes have distinct triggers`() {
        assertTrue(
            InlineMediaPlayerModeRules.replacesWebsiteFullscreen(
                InlineMediaPlayerMode.AlwaysForFullscreen,
            ),
        )
        assertFalse(
            InlineMediaPlayerModeRules.startsWhenVideoDetected(
                InlineMediaPlayerMode.AlwaysForFullscreen,
            ),
        )
        assertFalse(
            InlineMediaPlayerModeRules.showsOpenButton(
                InlineMediaPlayerMode.AlwaysForFullscreen,
            ),
        )
        assertFalse(
            InlineMediaPlayerModeRules.detectsInlineVideo(
                InlineMediaPlayerMode.AlwaysForFullscreen,
            ),
        )

        assertTrue(
            InlineMediaPlayerModeRules.startsWhenVideoDetected(
                InlineMediaPlayerMode.Automatic,
            ),
        )
        assertTrue(
            InlineMediaPlayerModeRules.supportsInlinePresentation(
                InlineMediaPlayerMode.Automatic,
            ),
        )
        assertFalse(
            InlineMediaPlayerModeRules.showsOpenButton(
                InlineMediaPlayerMode.Automatic,
            ),
        )
        assertTrue(
            InlineMediaPlayerModeRules.detectsInlineVideo(
                InlineMediaPlayerMode.Automatic,
            ),
        )
    }
}
