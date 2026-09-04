package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopNavigationRulesTest {
    @Test
    fun `replays main frame GET when target user agent differs`() {
        assertTrue(
            DesktopNavigationRules.shouldReplayForUserAgentChange(
                isForMainFrame = true,
                method = "GET",
                isTargetUserAgentApplied = false,
            ),
        )
        assertTrue(
            DesktopNavigationRules.shouldReplayForUserAgentChange(
                isForMainFrame = true,
                method = "get",
                isTargetUserAgentApplied = false,
            ),
        )
    }

    @Test
    fun `rejects subframe non GET and matching user agent replay`() {
        assertFalse(
            DesktopNavigationRules.shouldReplayForUserAgentChange(
                isForMainFrame = false,
                method = "GET",
                isTargetUserAgentApplied = false,
            ),
        )
        assertFalse(
            DesktopNavigationRules.shouldReplayForUserAgentChange(
                isForMainFrame = true,
                method = "POST",
                isTargetUserAgentApplied = false,
            ),
        )
        assertFalse(
            DesktopNavigationRules.shouldReplayForUserAgentChange(
                isForMainFrame = true,
                method = null,
                isTargetUserAgentApplied = false,
            ),
        )
        assertFalse(
            DesktopNavigationRules.shouldReplayForUserAgentChange(
                isForMainFrame = true,
                method = "GET",
                isTargetUserAgentApplied = true,
            ),
        )
    }
}
